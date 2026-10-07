package com.is2c.hrm.shift.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.is2c.hrm.audit.service.AuditEntry;
import com.is2c.hrm.audit.service.AuditService;
import com.is2c.hrm.common.domain.Versions;
import com.is2c.hrm.common.error.FieldError;
import com.is2c.hrm.common.error.NotFoundException;
import com.is2c.hrm.common.error.ValidationException;
import com.is2c.hrm.employee.domain.Employee;
import com.is2c.hrm.employee.service.EmployeeService;
import com.is2c.hrm.org.service.DataScopeService;
import com.is2c.hrm.shift.domain.RotationAssignment;
import com.is2c.hrm.shift.domain.RotationAssignmentRepository;
import com.is2c.hrm.shift.domain.RotationPattern;
import com.is2c.hrm.shift.domain.RotationPatternRepository;
import com.is2c.hrm.shift.domain.Shift;
import com.is2c.hrm.shift.domain.ShiftRepository;

/** FR-031 rotating shifts: patterns and employee assignments. */
@Service
public class RotationService {

    static final int MAX_CYCLE_DAYS = 62;

    private final RotationPatternRepository patterns;
    private final RotationAssignmentRepository assignments;
    private final ShiftRepository shifts;
    private final EmployeeService employees;
    private final DataScopeService scope;
    private final AuditService audit;

    public RotationService(RotationPatternRepository patterns, RotationAssignmentRepository assignments,
            ShiftRepository shifts, EmployeeService employees, DataScopeService scope, AuditService audit) {
        this.patterns = patterns;
        this.assignments = assignments;
        this.shifts = shifts;
        this.employees = employees;
        this.scope = scope;
        this.audit = audit;
    }

    public record PatternData(String name, List<UUID> days, Long version) {
    }

    public record AssignmentData(List<UUID> employeeIds, LocalDate startDate, LocalDate endDate) {
    }

    @Transactional(readOnly = true)
    public List<RotationPattern> list(UUID companyId) {
        scope.assertCompanyReadable(companyId);
        return patterns.findByCompanyIdOrderByName(companyId);
    }

    @Transactional(readOnly = true)
    public List<RotationPattern> all(Collection<UUID> ids) {
        return patterns.findAllById(ids);
    }

    @Transactional(readOnly = true)
    public List<RotationAssignment> activeBetween(Collection<UUID> employeeIds, LocalDate from, LocalDate to) {
        return employeeIds.isEmpty() ? List.of() : assignments.findActiveBetween(employeeIds, from, to);
    }

    @Transactional
    public RotationPattern create(UUID companyId, PatternData d) {
        scope.assertCompanyWritable(companyId);
        validate(companyId, d);
        RotationPattern p = new RotationPattern(companyId);
        p.setName(d.name().trim());
        p.getDays().addAll(d.days());
        patterns.save(p);
        audit.record(AuditEntry.of("CREATE", "ROTATION_PATTERN", p.getId()).company(companyId).after(d));
        return p;
    }

    @Transactional
    public RotationPattern update(UUID id, PatternData d) {
        RotationPattern p = patterns.findById(id).orElseThrow(() -> new NotFoundException("Rotation pattern", id));
        scope.assertCompanyWritable(p.getCompanyId());
        Versions.check(p, d.version());
        validate(p.getCompanyId(), d);
        p.setName(d.name().trim());
        p.getDays().clear();
        p.getDays().addAll(d.days());
        audit.record(AuditEntry.of("UPDATE", "ROTATION_PATTERN", id).company(p.getCompanyId()).after(d));
        return p;
    }

    @Transactional
    public void delete(UUID id) {
        RotationPattern p = patterns.findById(id).orElseThrow(() -> new NotFoundException("Rotation pattern", id));
        scope.assertCompanyWritable(p.getCompanyId());
        if (assignments.countByPatternId(id) > 0) {
            throw new ValidationException("The pattern is assigned to employees and cannot be deleted");
        }
        patterns.delete(p);
        audit.record(AuditEntry.of("DELETE", "ROTATION_PATTERN", id).company(p.getCompanyId()));
    }

    @Transactional
    public List<RotationAssignment> assign(UUID patternId, AssignmentData d) {
        RotationPattern p = patterns.findById(patternId)
                .orElseThrow(() -> new NotFoundException("Rotation pattern", patternId));
        scope.assertCompanyReadable(p.getCompanyId());
        List<FieldError> errors = new ArrayList<>();
        if (d.startDate() == null) {
            errors.add(new FieldError("startDate", "is required"));
        }
        if (d.startDate() != null && d.endDate() != null && d.endDate().isBefore(d.startDate())) {
            errors.add(new FieldError("endDate", "cannot be before the start date"));
        }
        if (d.employeeIds() == null || d.employeeIds().isEmpty()) {
            errors.add(new FieldError("employeeIds", "select at least one employee"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("Validation failed", errors);
        }
        List<RotationAssignment> created = new ArrayList<>();
        for (UUID employeeId : d.employeeIds()) {
            Employee e = employees.get(employeeId);
            if (!e.getCompanyId().equals(p.getCompanyId())) {
                errors.add(new FieldError("employeeIds", e.getEmployeeNumber() + " belongs to another company"));
                continue;
            }
            boolean overlap = assignments.findByEmployeeIdOrderByStartDate(employeeId).stream()
                    .anyMatch(a -> a.overlaps(d.startDate(), d.endDate()));
            if (overlap) {
                errors.add(new FieldError("employeeIds", e.getEmployeeNumber() + " already has a rotation in this period"));
                continue;
            }
            created.add(assignments.save(new RotationAssignment(employeeId, patternId, d.startDate(), d.endDate())));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("Validation failed", errors);
        }
        created.forEach(a -> audit.record(AuditEntry.of("ASSIGN", "ROTATION_ASSIGNMENT", a.getId())
                .company(p.getCompanyId()).after(java.util.Map.of("employeeId", a.getEmployeeId(),
                        "patternId", patternId, "startDate", a.getStartDate()))));
        return created;
    }

    @Transactional(readOnly = true)
    public List<RotationAssignment> assignmentsOf(UUID employeeId) {
        employees.get(employeeId);
        return assignments.findByEmployeeIdOrderByStartDate(employeeId);
    }

    @Transactional
    public void unassign(UUID assignmentId) {
        RotationAssignment a = assignments.findById(assignmentId)
                .orElseThrow(() -> new NotFoundException("Rotation assignment", assignmentId));
        Employee e = employees.get(a.getEmployeeId());
        assignments.delete(a);
        audit.record(AuditEntry.of("UNASSIGN", "ROTATION_ASSIGNMENT", assignmentId).company(e.getCompanyId()));
    }

    private void validate(UUID companyId, PatternData d) {
        List<FieldError> errors = new ArrayList<>();
        if (d.name() == null || d.name().isBlank()) {
            errors.add(new FieldError("name", "is required"));
        }
        if (d.days() == null || d.days().isEmpty() || d.days().size() > MAX_CYCLE_DAYS) {
            errors.add(new FieldError("days", "the cycle must have between 1 and " + MAX_CYCLE_DAYS + " days"));
        } else {
            List<UUID> shiftIds = d.days().stream().filter(Objects::nonNull).distinct().toList();
            List<Shift> found = shifts.findAllById(shiftIds);
            boolean ok = found.size() == shiftIds.size()
                    && found.stream().allMatch(s -> s.getCompanyId().equals(companyId) && s.isActive());
            if (!ok) {
                errors.add(new FieldError("days", "shifts must be active shifts of the same company"));
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("Validation failed", errors);
        }
    }
}
