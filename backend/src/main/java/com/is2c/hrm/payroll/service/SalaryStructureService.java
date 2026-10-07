package com.is2c.hrm.payroll.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.is2c.hrm.audit.service.AuditEntry;
import com.is2c.hrm.audit.service.AuditService;
import com.is2c.hrm.common.error.FieldError;
import com.is2c.hrm.common.error.ValidationException;
import com.is2c.hrm.employee.domain.Employee;
import com.is2c.hrm.employee.service.EmployeeService;
import com.is2c.hrm.payroll.api.CalcType;
import com.is2c.hrm.payroll.domain.PayrollComponent;
import com.is2c.hrm.payroll.domain.PayrollComponent.Assignment;
import com.is2c.hrm.payroll.domain.PayrollComponent.Frequency;
import com.is2c.hrm.payroll.domain.PayrollComponentRepository;
import com.is2c.hrm.payroll.domain.PayrollPeriod;
import com.is2c.hrm.payroll.domain.PayrollPeriodRepository;
import com.is2c.hrm.payroll.domain.SalaryStructure;
import com.is2c.hrm.payroll.domain.SalaryStructureRepository;
import com.is2c.hrm.payroll.domain.StructureLine;

/** FR-082 effective-dated salary structures. Visible only with PAYROLL:VIEW (FR-007). */
@Service
public class SalaryStructureService {

    private final SalaryStructureRepository structures;
    private final PayrollComponentRepository components;
    private final PayrollPeriodRepository periods;
    private final EmployeeService employees;
    private final AuditService audit;

    public SalaryStructureService(SalaryStructureRepository structures, PayrollComponentRepository components,
            PayrollPeriodRepository periods, EmployeeService employees, AuditService audit) {
        this.structures = structures;
        this.components = components;
        this.periods = periods;
        this.employees = employees;
        this.audit = audit;
    }

    public record StructureData(LocalDate effectiveFrom, BigDecimal basicSalary, List<StructureLine> lines) {
    }

    @Transactional(readOnly = true)
    public List<SalaryStructure> history(UUID employeeId) {
        employees.get(employeeId);
        return structures.findByEmployeeIdOrderByEffectiveFromDesc(employeeId);
    }

    @Transactional
    public SalaryStructure create(UUID employeeId, StructureData d) {
        Employee e = employees.get(employeeId);
        List<FieldError> errors = new ArrayList<>();
        if (d.effectiveFrom() == null) {
            errors.add(new FieldError("effectiveFrom", "is required"));
        }
        if (d.basicSalary() == null || d.basicSalary().signum() <= 0) {
            errors.add(new FieldError("basicSalary", "must be greater than 0"));
        } else if (d.basicSalary().scale() > 2) {
            errors.add(new FieldError("basicSalary", "at most 2 decimal places"));
        }
        validateLines(e.getCompanyId(), d.lines() == null ? List.of() : d.lines(), errors);
        List<SalaryStructure> existing = structures.findByEmployeeIdOrderByEffectiveFromDesc(employeeId);
        if (d.effectiveFrom() != null) {
            if (!existing.isEmpty() && !d.effectiveFrom().isAfter(existing.get(0).getEffectiveFrom())) {
                errors.add(new FieldError("effectiveFrom", "must be after the current structure's start ("
                        + existing.get(0).getEffectiveFrom() + ")"));
            }
            boolean finalized = periods.findOverlapping(e.getCompanyId(), d.effectiveFrom(), d.effectiveFrom()).stream()
                    .anyMatch(p -> p.getStatus() == PayrollPeriod.Status.FINALIZED);
            if (finalized) {
                errors.add(new FieldError("effectiveFrom", "falls inside a finalized payroll period"));
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("Validation failed", errors);
        }
        if (!existing.isEmpty() && existing.get(0).getEffectiveTo() == null) {
            existing.get(0).setEffectiveTo(d.effectiveFrom().minusDays(1));
        }
        SalaryStructure s = new SalaryStructure(employeeId, e.getCompanyId(), d.effectiveFrom(), d.basicSalary());
        if (d.lines() != null) {
            s.getLines().addAll(d.lines());
        }
        structures.save(s);
        audit.record(AuditEntry.of("CREATE", "SALARY_STRUCTURE", s.getId()).company(e.getCompanyId())
                .after(Map.of("employeeId", employeeId, "effectiveFrom", d.effectiveFrom(), "basicSalary", d.basicSalary(),
                        "lines", s.getLines().size())));
        markStale(e.getCompanyId(), d.effectiveFrom());
        return s;
    }

    /** DRAFT periods affected by a change must be reprocessed before review (FR-086). */
    void markStale(UUID companyId, LocalDate from) {
        periods.findOverlapping(companyId, from, LocalDate.of(9999, 12, 31)).stream()
                .filter(p -> p.getStatus() == PayrollPeriod.Status.DRAFT).forEach(p -> p.setStale(true));
    }

    private void validateLines(UUID companyId, List<StructureLine> lines, List<FieldError> errors) {
        Map<UUID, PayrollComponent> byId = components.findByCompanyIdOrderBySequenceAscCodeAsc(companyId).stream()
                .collect(Collectors.toMap(PayrollComponent::getId, c -> c));
        Set<UUID> seen = new HashSet<>();
        for (int i = 0; i < lines.size(); i++) {
            StructureLine l = lines.get(i);
            String f = "lines[" + i + "]";
            PayrollComponent c = l == null ? null : byId.get(l.componentId());
            if (c == null || !c.isActive()) {
                errors.add(new FieldError(f, "unknown or inactive component"));
                continue;
            }
            if (!seen.add(c.getId())) {
                errors.add(new FieldError(f, c.getCode() + " appears more than once"));
            }
            if (c.isSystemComponent()) {
                errors.add(new FieldError(f, "BASIC is set through the basic salary"));
            } else if (c.getFrequency() == Frequency.ONE_TIME) {
                errors.add(new FieldError(f, c.getCode() + " is one-time; add it to a payroll period instead"));
            } else if (l.excluded()) {
                if (c.getAssignment() != Assignment.AUTOMATIC) {
                    errors.add(new FieldError(f, "only automatic components can be excluded"));
                }
            } else if (c.getCalcType() == CalcType.FIXED) {
                if (l.amount() == null || l.amount().signum() < 0) {
                    errors.add(new FieldError(f, c.getCode() + " needs an amount"));
                }
            } else if ((l.rateOverride() != null && l.rateOverride().signum() < 0)
                    || (l.multiplierOverride() != null && l.multiplierOverride().signum() < 0)) {
                errors.add(new FieldError(f, "overrides must not be negative"));
            }
        }
    }
}
