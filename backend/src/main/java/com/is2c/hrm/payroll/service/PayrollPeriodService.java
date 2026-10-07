package com.is2c.hrm.payroll.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.is2c.hrm.attendance.api.AttendanceLockPolicy;
import com.is2c.hrm.audit.service.AuditEntry;
import com.is2c.hrm.audit.service.AuditService;
import com.is2c.hrm.common.error.FieldError;
import com.is2c.hrm.common.error.InvalidStateException;
import com.is2c.hrm.common.error.NotFoundException;
import com.is2c.hrm.common.error.ValidationException;
import com.is2c.hrm.common.domain.Versions;
import com.is2c.hrm.employee.domain.Employee;
import com.is2c.hrm.employee.service.EmployeeService;
import com.is2c.hrm.org.service.DataScopeService;
import com.is2c.hrm.org.service.OrgDirectory;
import com.is2c.hrm.payroll.api.CalcType;
import com.is2c.hrm.payroll.domain.PayrollComponent;
import com.is2c.hrm.payroll.domain.PayrollComponentRepository;
import com.is2c.hrm.payroll.domain.PayrollException;
import com.is2c.hrm.payroll.domain.PayrollExceptionRepository;
import com.is2c.hrm.payroll.domain.PayrollOneTimeItem;
import com.is2c.hrm.payroll.domain.PayrollOneTimeItemRepository;
import com.is2c.hrm.payroll.domain.PayrollPeriod;
import com.is2c.hrm.payroll.domain.PayrollPeriod.Status;
import com.is2c.hrm.payroll.domain.PayrollPeriodRepository;
import com.is2c.hrm.payroll.domain.PayrollResult;
import com.is2c.hrm.payroll.domain.PayrollResultLine;
import com.is2c.hrm.payroll.domain.PayrollResultLineRepository;
import com.is2c.hrm.payroll.domain.PayrollResultRepository;

/** FR-083/084/086 periods, one-time items, processing and review; implements the attendance lock (FR-053). */
@Service
public class PayrollPeriodService implements AttendanceLockPolicy {

    private final PayrollPeriodRepository periods;
    private final PayrollOneTimeItemRepository items;
    private final PayrollResultRepository results;
    private final PayrollResultLineRepository lines;
    private final PayrollExceptionRepository exceptions;
    private final PayrollComponentRepository components;
    private final PayrollProcessor processor;
    private final EmployeeService employees;
    private final OrgDirectory org;
    private final DataScopeService scope;
    private final AuditService audit;
    private final Clock clock;

    public PayrollPeriodService(PayrollPeriodRepository periods, PayrollOneTimeItemRepository items,
            PayrollResultRepository results, PayrollResultLineRepository lines, PayrollExceptionRepository exceptions,
            PayrollComponentRepository components, @Lazy PayrollProcessor processor, EmployeeService employees,
            OrgDirectory org, DataScopeService scope, AuditService audit, Clock clock) {
        this.periods = periods;
        this.items = items;
        this.results = results;
        this.lines = lines;
        this.exceptions = exceptions;
        this.components = components;
        this.processor = processor;
        this.employees = employees;
        this.org = org;
        this.scope = scope;
        this.audit = audit;
        this.clock = clock;
    }

    public record ItemData(UUID employeeId, UUID componentId, BigDecimal amount, String note, Long version) {
    }

    public record ResultView(PayrollResult result, List<PayrollResultLine> lines) {
    }

    public record Totals(BigDecimal gross, BigDecimal deductions, BigDecimal net, BigDecimal employerContributions,
            int employees, Map<String, BigDecimal> byComponent, Map<String, BigDecimal> netByDepartment) {
    }

    // ---------------------------------------------------------------- periods (FR-084)

    @Transactional(readOnly = true)
    public List<PayrollPeriod> list(UUID companyId) {
        scope.assertCompanyReadable(companyId);
        return periods.findByCompanyIdOrderByYearDescMonthDesc(companyId);
    }

    @Transactional(readOnly = true)
    public PayrollPeriod get(UUID id) {
        PayrollPeriod p = periods.findById(id).orElseThrow(() -> new NotFoundException("Payroll period", id));
        scope.assertCompanyReadable(p.getCompanyId());
        return p;
    }

    @Transactional
    public PayrollPeriod open(UUID companyId, int year, int month) {
        scope.assertCompanyReadable(companyId);
        List<FieldError> errors = new ArrayList<>();
        if (month < 1 || month > 12 || year < 2000 || year > 2100) {
            errors.add(new FieldError("month", "a valid year and month are required"));
        } else {
            if (periods.findByCompanyIdAndYearAndMonth(companyId, year, month).isPresent()) {
                errors.add(new FieldError("month", "this period already exists"));
            }
            PeriodDates.Range r = PeriodDates.of(org.settings(companyId).payrollStartDay(), year, month);
            if (errors.isEmpty() && !periods.findOverlapping(companyId, r.start(), r.end()).isEmpty()) {
                errors.add(new FieldError("month", "overlaps an existing period"));
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("Validation failed", errors);
        }
        PeriodDates.Range r = PeriodDates.of(org.settings(companyId).payrollStartDay(), year, month);
        PayrollPeriod p = periods.save(new PayrollPeriod(companyId, year, month, r.start(), r.end()));
        audit.record(AuditEntry.of("OPEN", "PAYROLL_PERIOD", p.getId()).company(companyId)
                .after(Map.of("year", year, "month", month, "start", r.start(), "end", r.end())));
        return p;
    }

    @Transactional
    public PayrollProcessor.Summary process(UUID id) {
        PayrollPeriod p = get(id);
        if (p.getStatus() != Status.OPEN && p.getStatus() != Status.DRAFT) {
            throw new InvalidStateException("Only OPEN or DRAFT periods can be processed (current: " + p.getStatus() + ")");
        }
        PayrollProcessor.Summary s = processor.process(p);
        p.setStatus(Status.DRAFT);
        p.setStale(false);
        p.setProcessedAt(clock.instant());
        audit.record(AuditEntry.of("PROCESS", "PAYROLL_PERIOD", id).company(p.getCompanyId())
                .after(Map.of("processed", s.processed(), "skipped", s.skipped(), "blocking", s.blocking())));
        return s;
    }

    // ---------------------------------------------------------------- one-time items (FR-083)

    @Transactional(readOnly = true)
    public List<PayrollOneTimeItem> items(UUID periodId) {
        get(periodId);
        return items.findByPeriodId(periodId);
    }

    @Transactional
    public PayrollOneTimeItem addItem(UUID periodId, ItemData d) {
        PayrollPeriod p = editable(periodId);
        validateItem(p, d);
        PayrollOneTimeItem item = items.save(new PayrollOneTimeItem(periodId, d.employeeId(), d.componentId(),
                d.amount(), d.note()));
        changed(p, "ADD_ITEM", item.getId(), d);
        return item;
    }

    @Transactional
    public PayrollOneTimeItem updateItem(UUID itemId, ItemData d) {
        PayrollOneTimeItem item = items.findById(itemId).orElseThrow(() -> new NotFoundException("Item", itemId));
        PayrollPeriod p = editable(item.getPeriodId());
        Versions.check(item, d.version());
        validateItem(p, new ItemData(item.getEmployeeId(), d.componentId(), d.amount(), d.note(), d.version()));
        item.setComponentId(d.componentId());
        item.setAmount(d.amount());
        item.setNote(d.note());
        changed(p, "UPDATE_ITEM", itemId, d);
        return item;
    }

    @Transactional
    public void deleteItem(UUID itemId) {
        PayrollOneTimeItem item = items.findById(itemId).orElseThrow(() -> new NotFoundException("Item", itemId));
        PayrollPeriod p = editable(item.getPeriodId());
        items.delete(item);
        changed(p, "DELETE_ITEM", itemId, Map.of("employeeId", item.getEmployeeId(), "amount", item.getAmount()));
    }

    private void validateItem(PayrollPeriod p, ItemData d) {
        List<FieldError> errors = new ArrayList<>();
        PayrollComponent c = d.componentId() == null ? null : components.findById(d.componentId())
                .filter(x -> x.getCompanyId().equals(p.getCompanyId())).orElse(null);
        if (c == null || !c.isActive() || c.getFrequency() != PayrollComponent.Frequency.ONE_TIME
                || c.getCalcType() != CalcType.FIXED) {
            errors.add(new FieldError("componentId", "must be an active one-time fixed component"));
        }
        if (d.amount() == null || d.amount().signum() <= 0 || d.amount().scale() > 2) {
            errors.add(new FieldError("amount", "must be greater than 0 with at most 2 decimals"));
        }
        if (d.employeeId() == null || employees.employedInScope(p.getCompanyId(), null, d.employeeId(),
                p.getStartDate(), p.getEndDate()).isEmpty()) {
            errors.add(new FieldError("employeeId", "employee is not employed in this period or outside your scope"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("Validation failed", errors);
        }
    }

    private PayrollPeriod editable(UUID periodId) {
        PayrollPeriod p = get(periodId);
        if (p.getStatus() != Status.OPEN && p.getStatus() != Status.DRAFT) {
            throw new InvalidStateException("Items can only change while the period is OPEN or DRAFT");
        }
        return p;
    }

    private void changed(PayrollPeriod p, String action, UUID itemId, Object after) {
        if (p.getStatus() == Status.DRAFT) {
            p.setStale(true);
        }
        audit.record(AuditEntry.of(action, "PAYROLL_PERIOD", p.getId()).company(p.getCompanyId())
                .after(Map.of("itemId", itemId, "data", after)));
    }

    // ---------------------------------------------------------------- review (FR-086)

    @Transactional(readOnly = true)
    public List<PayrollResult> results(UUID periodId) {
        PayrollPeriod p = get(periodId);
        Set<UUID> visible = visibleEmployees(p);
        return results.findByPeriodIdOrderByEmployeeNumber(periodId).stream()
                .filter(r -> visible.contains(r.getEmployeeId())).toList();
    }

    @Transactional(readOnly = true)
    public ResultView result(UUID periodId, UUID employeeId) {
        get(periodId);
        employees.get(employeeId);
        PayrollResult r = results.findByPeriodIdAndEmployeeId(periodId, employeeId)
                .orElseThrow(() -> new NotFoundException("No result for this employee in the period"));
        return new ResultView(r, lines.findByResultIdOrderBySortOrder(r.getId()));
    }

    @Transactional(readOnly = true)
    public List<PayrollException> exceptions(UUID periodId) {
        get(periodId);
        return exceptions.findByPeriodId(periodId);
    }

    @Transactional(readOnly = true)
    public Totals totals(UUID periodId) {
        List<PayrollResult> visible = results(periodId);
        Map<UUID, PayrollResult> byId = visible.stream().collect(Collectors.toMap(PayrollResult::getId, r -> r));
        Map<String, BigDecimal> byComponent = new java.util.TreeMap<>();
        if (!byId.isEmpty()) {
            for (PayrollResultLine l : lines.findByResultIdIn(byId.keySet())) {
                byComponent.merge(l.getComponentCode(), l.getAmount(), BigDecimal::add);
            }
        }
        Map<String, BigDecimal> byDept = new java.util.TreeMap<>();
        visible.forEach(r -> byDept.merge(r.getDepartmentName() == null ? "—" : r.getDepartmentName(), r.getNet(), BigDecimal::add));
        return new Totals(sum(visible, PayrollResult::getGross), sum(visible, PayrollResult::getDeductions),
                sum(visible, PayrollResult::getNet), sum(visible, PayrollResult::getEmployerContributions),
                visible.size(), byComponent, byDept);
    }

    // ---------------------------------------------------------------- dashboard (FR-096)

    /** Most recent period of the company (by year/month), if any. */
    @Transactional(readOnly = true)
    public java.util.Optional<PayrollPeriod> latest(UUID companyId) {
        return list(companyId).stream().findFirst();
    }

    /** Most recent period that has been processed at least once. */
    @Transactional(readOnly = true)
    public java.util.Optional<PayrollPeriod> latestProcessed(UUID companyId) {
        return list(companyId).stream().filter(p -> p.getProcessedAt() != null).findFirst();
    }

    /** Sum of overtime component lines (components paid per OT hour) of the visible results of a period. */
    @Transactional(readOnly = true)
    public BigDecimal otCost(UUID periodId) {
        PayrollPeriod p = get(periodId);
        Set<String> otCodes = components.findByCompanyIdOrderBySequenceAscCodeAsc(p.getCompanyId()).stream()
                .filter(c -> c.getUnitMetric() != null && c.getUnitMetric().name().startsWith("OT_"))
                .map(PayrollComponent::getCode).collect(Collectors.toSet());
        List<UUID> ids = results(periodId).stream().map(PayrollResult::getId).toList();
        if (ids.isEmpty() || otCodes.isEmpty()) {
            return BigDecimal.ZERO.setScale(2);
        }
        return lines.findByResultIdIn(ids).stream().filter(l -> otCodes.contains(l.getComponentCode()))
                .map(PayrollResultLine::getAmount).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    private Set<UUID> visibleEmployees(PayrollPeriod p) {
        return employees.employedInScope(p.getCompanyId(), null, null, p.getStartDate(), p.getEndDate()).stream()
                .map(Employee::getId).collect(Collectors.toSet());
    }

    private static BigDecimal sum(List<PayrollResult> list, java.util.function.Function<PayrollResult, BigDecimal> f) {
        return list.stream().map(f).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ---------------------------------------------------------------- AttendanceLockPolicy (FR-053, FR-086)

    @Override
    @Transactional(readOnly = true)
    public boolean isLocked(UUID companyId, LocalDate date) {
        return periods.findOverlapping(companyId, date, date).stream().anyMatch(PayrollPeriod::locksAttendance);
    }

    @Override
    @Transactional
    public void attendanceChanged(UUID companyId, Collection<LocalDate> dates) {
        LocalDate min = dates.stream().min(LocalDate::compareTo).orElse(null);
        LocalDate max = dates.stream().max(LocalDate::compareTo).orElse(null);
        if (min == null) {
            return;
        }
        periods.findOverlapping(companyId, min, max).stream()
                .filter(p -> p.getStatus() == Status.DRAFT && dates.stream().anyMatch(p::contains))
                .forEach(p -> p.setStale(true));
    }
}
