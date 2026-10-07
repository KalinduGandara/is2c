package com.is2c.hrm.payroll.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.is2c.hrm.attendance.domain.AttendanceDaily;
import com.is2c.hrm.attendance.service.AttendanceProcessor;
import com.is2c.hrm.attendance.service.AttendanceQueryService;
import com.is2c.hrm.employee.domain.Employee;
import com.is2c.hrm.employee.service.EmployeeService;
import com.is2c.hrm.org.api.CompanySettingsView;
import com.is2c.hrm.org.service.OrgDirectory;
import com.is2c.hrm.payroll.api.UnitMetric;
import com.is2c.hrm.payroll.domain.PayrollComponent;
import com.is2c.hrm.payroll.domain.PayrollException;
import com.is2c.hrm.payroll.domain.PayrollExceptionRepository;
import com.is2c.hrm.payroll.domain.PayrollOneTimeItem;
import com.is2c.hrm.payroll.domain.PayrollOneTimeItemRepository;
import com.is2c.hrm.payroll.domain.PayrollPeriod;
import com.is2c.hrm.payroll.domain.PayrollResult;
import com.is2c.hrm.payroll.domain.PayrollResultLine;
import com.is2c.hrm.payroll.domain.PayrollResultLineRepository;
import com.is2c.hrm.payroll.domain.PayrollResultRepository;
import com.is2c.hrm.payroll.domain.SalaryStructure;
import com.is2c.hrm.payroll.domain.SalaryStructureRepository;
import com.is2c.hrm.payroll.domain.SlabTable;
import com.is2c.hrm.payroll.engine.PayrollEngine;
import com.is2c.hrm.payroll.engine.PayrollEngine.ComponentDef;
import com.is2c.hrm.payroll.engine.PayrollEngine.EmployeeInput;
import com.is2c.hrm.payroll.engine.PayrollEngine.LineInput;
import com.is2c.hrm.payroll.engine.PayrollEngine.OneTime;
import com.is2c.hrm.payroll.engine.PayrollEngine.SlabBand;

/** FR-085 batch processing of a period: attendance → metrics → engine → result snapshots and exceptions. */
@Service
public class PayrollProcessor {

    private static final BigDecimal SIXTY = BigDecimal.valueOf(60);

    private final AttendanceProcessor attendance;
    private final AttendanceQueryService attendanceQuery;
    private final EmployeeService employees;
    private final OrgDirectory org;
    private final PayrollConfigService config;
    private final SalaryStructureRepository structures;
    private final PayrollOneTimeItemRepository items;
    private final PayrollResultRepository results;
    private final PayrollResultLineRepository lines;
    private final PayrollExceptionRepository exceptions;

    public PayrollProcessor(AttendanceProcessor attendance, AttendanceQueryService attendanceQuery,
            EmployeeService employees, OrgDirectory org, PayrollConfigService config,
            SalaryStructureRepository structures, PayrollOneTimeItemRepository items, PayrollResultRepository results,
            PayrollResultLineRepository lines, PayrollExceptionRepository exceptions) {
        this.attendance = attendance;
        this.attendanceQuery = attendanceQuery;
        this.employees = employees;
        this.org = org;
        this.config = config;
        this.structures = structures;
        this.items = items;
        this.results = results;
        this.lines = lines;
        this.exceptions = exceptions;
    }

    public record Summary(int processed, int skipped, int blocking) {
    }

    @Transactional
    public Summary process(PayrollPeriod period) {
        UUID companyId = period.getCompanyId();
        CompanySettingsView settings = org.settings(companyId);
        attendance.process(companyId, null, period.getStartDate(), period.getEndDate());

        lines.deleteByPeriod(period.getId());
        results.deleteByPeriod(period.getId());
        exceptions.deleteByPeriod(period.getId());

        List<Employee> staff = employees.employedBetween(companyId, null, period.getStartDate(), period.getEndDate());
        Set<UUID> ids = staff.stream().map(Employee::getId).collect(Collectors.toSet());
        List<ComponentDef> defs = definitions(companyId);
        Map<UUID, String> codeById = config.componentsForProcessing(companyId).stream()
                .collect(Collectors.toMap(PayrollComponent::getId, PayrollComponent::getCode));

        Map<UUID, List<AttendanceDaily>> days = attendanceQuery.forPayroll(companyId, period.getStartDate(), period.getEndDate())
                .stream().collect(Collectors.groupingBy(AttendanceDaily::getEmployeeId));
        Map<UUID, List<PayrollOneTimeItem>> oneTime = items.findByPeriodId(period.getId()).stream()
                .collect(Collectors.groupingBy(PayrollOneTimeItem::getEmployeeId));
        Set<UUID> deptIds = new HashSet<>();
        Set<UUID> desigIds = new HashSet<>();
        staff.forEach(e -> {
            deptIds.add(e.getDepartmentId());
            desigIds.add(e.getDesignationId());
        });
        Map<UUID, String> deptNames = org.departmentNames(deptIds);
        Map<UUID, String> desigNames = org.designationNames(desigIds);

        // one query up front: per-employee queries inside the loop would each auto-flush (and dirty-check) the
        // attendance rows held in this transaction (NFR-004)
        Map<UUID, SalaryStructure> structuresAtEnd = ids.isEmpty() ? Map.of()
                : structures.findEffectiveOn(ids, period.getEndDate()).stream()
                        .collect(Collectors.toMap(SalaryStructure::getEmployeeId, x -> x, (a, b) -> a));

        long periodDays = ChronoUnit.DAYS.between(period.getStartDate(), period.getEndDate()) + 1;
        int processed = 0;
        int skipped = 0;
        int blocking = 0;
        for (Employee e : staff) {
            LocalDate structureDate = e.getExitDate() != null && e.getExitDate().isBefore(period.getEndDate())
                    ? e.getExitDate() : period.getEndDate();
            SalaryStructure s = structureDate.equals(period.getEndDate()) ? structuresAtEnd.get(e.getId())
                    : structures.findEffectiveOn(List.of(e.getId()), structureDate).stream().findFirst().orElse(null);
            if (s == null) {
                exceptions.save(new PayrollException(period.getId(), e.getId(), "NO_SALARY_STRUCTURE",
                        e.getEmployeeNumber() + " has no salary structure effective on " + structureDate + "; skipped", false));
                skipped++;
                continue;
            }
            List<AttendanceDaily> empDays = days.getOrDefault(e.getId(), List.of());
            EmployeeInput input = new EmployeeInput(s.getBasicSalary(),
                    s.getLines().stream().map(l -> new LineInput(codeById.get(l.componentId()), l.amount(),
                            l.rateOverride(), l.multiplierOverride(), l.excluded())).toList(),
                    oneTime.getOrDefault(e.getId(), List.of()).stream()
                            .map(i -> new OneTime(codeById.get(i.getComponentId()), i.getAmount())).toList(),
                    metrics(empDays, settings.absentAsNoPay()),
                    proration(e, period, periodDays), settings.noPayDivisor());
            PayrollEngine.Result r = PayrollEngine.calculate(defs, input);
            Map<UnitMetric, BigDecimal> m = input.metrics();
            BigDecimal otHours = m.getOrDefault(UnitMetric.OT_WORKDAY_HOURS, BigDecimal.ZERO)
                    .add(m.getOrDefault(UnitMetric.OT_OFFDAY_HOURS, BigDecimal.ZERO))
                    .add(m.getOrDefault(UnitMetric.OT_HOLIDAY_HOURS, BigDecimal.ZERO));
            PayrollResult result = results.save(new PayrollResult(period.getId(), e.getId(), e.getEmployeeNumber(),
                    e.fullName(), deptNames.get(e.getDepartmentId()), desigNames.get(e.getDesignationId()),
                    e.getBranchId(), e.getDepartmentId(), r.gross(), r.deductions(), r.net(), r.employerContributions(),
                    m.getOrDefault(UnitMetric.WORKED_DAYS, BigDecimal.ZERO),
                    m.getOrDefault(UnitMetric.NO_PAY_DAYS, BigDecimal.ZERO), otHours));
            List<PayrollResultLine> resultLines = new ArrayList<>();
            for (int i = 0; i < r.lines().size(); i++) {
                PayrollEngine.Line l = r.lines().get(i);
                resultLines.add(new PayrollResultLine(result.getId(), i, l.componentCode(), l.componentName(), l.kind(),
                        l.units(), l.rate(), l.amount(), l.showOnPayslip()));
            }
            lines.saveAll(resultLines);
            if (r.net().signum() < 0) {
                exceptions.save(new PayrollException(period.getId(), e.getId(), "NEGATIVE_NET",
                        e.getEmployeeNumber() + " has a negative net pay of " + r.net().toPlainString(), true));
                blocking++;
            }
            long unresolved = empDays.stream().filter(d -> d.exceptionList().stream()
                    .anyMatch(x -> x.equals("INCOMPLETE") || x.equals("UNSCHEDULED") || x.equals("OT_PENDING"))).count();
            if (unresolved > 0) {
                exceptions.save(new PayrollException(period.getId(), e.getId(), "ATTENDANCE_EXCEPTIONS",
                        e.getEmployeeNumber() + " has " + unresolved + " day(s) with unresolved attendance exceptions", false));
            }
            processed++;
        }
        return new Summary(processed, skipped, blocking);
    }

    List<ComponentDef> definitions(UUID companyId) {
        Map<UUID, SlabTable> slabs = config.slabTablesForProcessing(companyId);
        return config.componentsForProcessing(companyId).stream().filter(PayrollComponent::isActive)
                .map(c -> new ComponentDef(c.getCode(), c.getName(), c.getKind(), c.getFrequency(), c.getAssignment(),
                        c.getCalcType(), c.getSequence(), c.baseSet(), c.getBasisRef(), c.getRatePercent(),
                        c.getUnitMetric(), c.getUnitRateMode(), c.getUnitFixedAmount(), c.getDivisor(), c.getMultiplier(),
                        c.getSlabTableId() == null ? List.of() : slabs.get(c.getSlabTableId()).getRows().stream()
                                .map(row -> new SlabBand(row.lowerBound(), row.upperBound(), row.ratePercent())).toList(),
                        c.isProrate(), c.isShowOnPayslip(), c.isSystemComponent()))
                .toList();
    }

    /** FR-085 attendance metrics; OT needing approval counts only once approved (FR-056). */
    static Map<UnitMetric, BigDecimal> metrics(List<AttendanceDaily> days, boolean absentAsNoPay) {
        long otWork = 0;
        long otOff = 0;
        long otHoliday = 0;
        long extra = 0;
        long late = 0;
        long shortMin = 0;
        long worked = 0;
        BigDecimal absent = BigDecimal.ZERO;
        BigDecimal unpaidLeave = BigDecimal.ZERO;
        for (AttendanceDaily d : days) {
            if (d.otPayable()) {
                otWork += d.getOtWorkdayMinutes();
                otOff += d.getOtOffdayMinutes();
                otHoliday += d.getOtHolidayMinutes();
            }
            extra += d.getExtraMinutes();
            late += d.getLateMinutes();
            shortMin += d.getShortMinutes();
            if (d.getWorkedMinutes() > 0) {
                worked++;
            }
            absent = absent.add(d.getAbsentFraction());
            if (!d.isLeavePaid()) {
                unpaidLeave = unpaidLeave.add(d.getLeaveFraction());
            }
        }
        Map<UnitMetric, BigDecimal> m = new HashMap<>();
        m.put(UnitMetric.OT_WORKDAY_HOURS, hours(otWork));
        m.put(UnitMetric.OT_OFFDAY_HOURS, hours(otOff));
        m.put(UnitMetric.OT_HOLIDAY_HOURS, hours(otHoliday));
        m.put(UnitMetric.EXTRA_HOURS, hours(extra));
        m.put(UnitMetric.LATE_HOURS, hours(late));
        m.put(UnitMetric.SHORT_HOURS, hours(shortMin));
        m.put(UnitMetric.ABSENT_DAYS, absent);
        m.put(UnitMetric.WORKED_DAYS, BigDecimal.valueOf(worked));
        m.put(UnitMetric.NO_PAY_DAYS, (absentAsNoPay ? absent : BigDecimal.ZERO).add(unpaidLeave));
        return m;
    }

    /** Employed calendar days / period days when joining or leaving inside the period (FR-085). */
    static BigDecimal proration(Employee e, PayrollPeriod p, long periodDays) {
        LocalDate from = e.getJoinDate().isAfter(p.getStartDate()) ? e.getJoinDate() : p.getStartDate();
        LocalDate to = e.getExitDate() != null && e.getExitDate().isBefore(p.getEndDate()) ? e.getExitDate() : p.getEndDate();
        long employed = Math.max(0, ChronoUnit.DAYS.between(from, to) + 1);
        if (employed >= periodDays) {
            return BigDecimal.ONE;
        }
        return BigDecimal.valueOf(employed).divide(BigDecimal.valueOf(periodDays), 10, RoundingMode.HALF_UP);
    }

    private static BigDecimal hours(long minutes) {
        return BigDecimal.valueOf(minutes).divide(SIXTY, 2, RoundingMode.HALF_UP);
    }
}
