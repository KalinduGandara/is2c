package com.is2c.hrm.employee.service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.is2c.hrm.audit.service.AuditEntry;
import com.is2c.hrm.audit.service.AuditService;
import com.is2c.hrm.common.domain.Versions;
import com.is2c.hrm.common.error.FieldError;
import com.is2c.hrm.common.error.ForbiddenException;
import com.is2c.hrm.common.error.NotFoundException;
import com.is2c.hrm.common.error.ValidationException;
import com.is2c.hrm.employee.api.EmployeeUsageChecker;
import com.is2c.hrm.employee.domain.EmergencyContact;
import com.is2c.hrm.employee.domain.Employee;
import com.is2c.hrm.employee.domain.EmployeeRepository;
import com.is2c.hrm.employee.domain.EmployeeStatus;
import com.is2c.hrm.employee.domain.EmploymentHistory;
import com.is2c.hrm.employee.domain.EmploymentHistory.EventType;
import com.is2c.hrm.employee.domain.EmploymentHistoryRepository;
import com.is2c.hrm.org.api.CompanySettingsView;
import com.is2c.hrm.org.api.EmployeeNumberFormat;
import com.is2c.hrm.org.api.EmployeePlacement;
import com.is2c.hrm.org.api.EmployeeScope;
import com.is2c.hrm.org.domain.Branch;
import com.is2c.hrm.org.domain.CompanyLookup;
import com.is2c.hrm.org.domain.Department;
import com.is2c.hrm.org.service.DataScopeService;
import com.is2c.hrm.org.service.OrgDirectory;
import com.is2c.hrm.platform.db.DatabasePlatform;
import com.is2c.hrm.security.api.CurrentUser;
import com.is2c.hrm.security.api.Permissions;
import com.is2c.hrm.security.api.PermissionAction;
import com.is2c.hrm.security.api.PermissionModule;

import tools.jackson.databind.json.JsonMapper;

/** Employee master (FR-014, FR-015, FR-017, FR-018). */
@Service
public class EmployeeService {

    private static final List<String> TRACKED = List.of("companyId", "branchId", "departmentId", "designationId",
            "gradeId", "categoryId", "reportingManagerId", "status");

    private final EmployeeRepository employees;
    private final EmploymentHistoryRepository history;
    private final OrgDirectory org;
    private final DataScopeService scope;
    private final DatabasePlatform platform;
    private final AuditService audit;
    private final ObjectProvider<EmployeeUsageChecker> usageCheckers;
    private final ObjectProvider<EmployeeExtension> extensions;
    private final JsonMapper json;
    private final Clock clock;

    public EmployeeService(EmployeeRepository employees, EmploymentHistoryRepository history, OrgDirectory org,
            DataScopeService scope, DatabasePlatform platform, AuditService audit,
            ObjectProvider<EmployeeUsageChecker> usageCheckers, ObjectProvider<EmployeeExtension> extensions,
            JsonMapper json, Clock clock) {
        this.employees = employees;
        this.history = history;
        this.org = org;
        this.scope = scope;
        this.platform = platform;
        this.audit = audit;
        this.usageCheckers = usageCheckers;
        this.extensions = extensions;
        this.json = json;
        this.clock = clock;
    }

    public record SearchFilter(String q, UUID companyId, UUID branchId, UUID departmentId, UUID designationId,
            UUID categoryId, EmployeeStatus status) {
    }

    public record StatusChange(EmployeeStatus status, LocalDate effectiveDate, LocalDate exitDate, String reason) {
    }

    // ---------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public Employee get(UUID id) {
        Employee e = employees.findById(id).orElseThrow(() -> new NotFoundException("Employee", id));
        scope.assertCovers(placement(e));
        return e;
    }

    @Transactional(readOnly = true)
    public Page<Employee> search(SearchFilter f, Pageable pageable) {
        return employees.findAll(spec(f, scope.current()), pageable);
    }

    /**
     * Employees of a company within the current user's scope who are employed at some point in [from, to],
     * optionally limited to departments (already expanded) or a single employee.
     */
    @Transactional(readOnly = true)
    public List<Employee> employedInScope(UUID companyId, java.util.Set<UUID> departmentIds, UUID employeeId,
            LocalDate from, LocalDate to) {
        EmployeeScope s = scope.current();
        Specification<Employee> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> p = new ArrayList<>();
            p.add(s.toPredicate(cb, root.get("id"), root.get("companyId"), root.get("branchId"), root.get("departmentId")));
            p.add(cb.equal(root.get("companyId"), companyId));
            p.add(cb.lessThanOrEqualTo(root.get("joinDate"), to));
            p.add(cb.or(cb.isNull(root.get("exitDate")), cb.greaterThanOrEqualTo(root.get("exitDate"), from)));
            p.add(cb.notEqual(root.get("status"), EmployeeStatus.INACTIVE));
            if (departmentIds != null) {
                p.add(root.get("departmentId").in(departmentIds));
            }
            if (employeeId != null) {
                p.add(cb.equal(root.get("id"), employeeId));
            }
            return cb.and(p.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return employees.findAll(spec, Sort.by("employeeNumber"));
    }

    @Transactional(readOnly = true)
    public List<EmploymentHistory> history(UUID employeeId) {
        get(employeeId);
        return history.findByEmployeeIdOrderByEffectiveDateDescRecordedAtDesc(employeeId);
    }

    static Specification<Employee> spec(SearchFilter f, EmployeeScope s) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> p = new ArrayList<>();
            p.add(s.toPredicate(cb, root.get("id"), root.get("companyId"), root.get("branchId"), root.get("departmentId")));
            if (f.companyId() != null) {
                p.add(cb.equal(root.get("companyId"), f.companyId()));
            }
            if (f.branchId() != null) {
                p.add(cb.equal(root.get("branchId"), f.branchId()));
            }
            if (f.departmentId() != null) {
                p.add(cb.equal(root.get("departmentId"), f.departmentId()));
            }
            if (f.designationId() != null) {
                p.add(cb.equal(root.get("designationId"), f.designationId()));
            }
            if (f.categoryId() != null) {
                p.add(cb.equal(root.get("categoryId"), f.categoryId()));
            }
            if (f.status() != null) {
                p.add(cb.equal(root.get("status"), f.status()));
            }
            if (f.q() != null && !f.q().isBlank()) {
                String like = "%" + f.q().trim().toLowerCase(java.util.Locale.ROOT)
                        .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
                p.add(cb.or(
                        cb.like(cb.lower(root.get("employeeNumber")), like, '\\'),
                        cb.like(cb.lower(root.get("firstName")), like, '\\'),
                        cb.like(cb.lower(root.get("lastName")), like, '\\'),
                        cb.like(cb.lower(root.get("preferredName")), like, '\\'),
                        cb.like(cb.lower(root.get("nic")), like, '\\'),
                        cb.like(cb.lower(root.get("badgeId")), like, '\\')));
            }
            return cb.and(p.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }

    // ---------------------------------------------------------------- commands

    @Transactional
    public Employee create(EmployeeData d) {
        List<FieldError> errors = new ArrayList<>();
        validate(d, null, errors);
        assertPlacementInScope(d);
        if (d.employeeNumber() != null && !d.employeeNumber().isBlank()) {
            if (!CurrentUser.get().hasPermission(Permissions.of(PermissionModule.EMPLOYEE, PermissionAction.CONFIGURE))) {
                throw new ForbiddenException("Setting an explicit employee number requires EMPLOYEE:CONFIGURE");
            }
            employees.findByCompanyIdAndEmployeeNumberIgnoreCase(d.companyId(), d.employeeNumber().trim())
                    .ifPresent(o -> errors.add(new FieldError("employeeNumber", "employee number already exists")));
        }
        extensions.forEach(x -> x.validate(d, errors));
        throwIfAny(errors);

        Employee e = new Employee(d.companyId());
        apply(e, d);
        e.setEmployeeNumber(d.employeeNumber() != null && !d.employeeNumber().isBlank()
                ? d.employeeNumber().trim() : nextNumber(d));
        employees.save(e);
        extensions.forEach(x -> x.saved(e, d));
        history.save(new EmploymentHistory(e.getId(), e.getJoinDate(), EventType.JOINED,
                toJson(trackedValues(e)), d.changeReason(), CurrentUser.get().id(), clock.instant()));
        audit.record(AuditEntry.of("CREATE", "EMPLOYEE", e.getId()).company(e.getCompanyId()).after(snapshot(e)));
        return e;
    }

    @Transactional
    public Employee update(UUID id, EmployeeData d) {
        Employee e = get(id);
        Versions.check(e, d.version());
        List<FieldError> errors = new ArrayList<>();
        validate(d, e, errors);
        if (d.employeeNumber() != null && !d.employeeNumber().isBlank()
                && !d.employeeNumber().trim().equalsIgnoreCase(e.getEmployeeNumber())) {
            if (!CurrentUser.get().hasPermission(Permissions.of(PermissionModule.EMPLOYEE, PermissionAction.CONFIGURE))) {
                throw new ForbiddenException("Changing the employee number requires EMPLOYEE:CONFIGURE");
            }
            employees.findByCompanyIdAndEmployeeNumberIgnoreCase(d.companyId(), d.employeeNumber().trim())
                    .ifPresent(o -> errors.add(new FieldError("employeeNumber", "employee number already exists")));
        }
        extensions.forEach(x -> x.validate(d, errors));
        throwIfAny(errors);
        assertPlacementInScope(d);

        Map<String, Object> before = snapshot(e);
        Map<String, Object> trackedBefore = trackedValues(e);
        apply(e, d);
        if (d.employeeNumber() != null && !d.employeeNumber().isBlank()) {
            e.setEmployeeNumber(d.employeeNumber().trim());
        }
        extensions.forEach(x -> x.saved(e, d));
        recordHistory(e, trackedBefore, d.effectiveDate(), d.changeReason());
        audit.record(AuditEntry.of("UPDATE", "EMPLOYEE", id).company(e.getCompanyId()).before(before)
                .after(snapshot(e)).reason(d.changeReason()));
        return e;
    }

    @Transactional
    public Employee changeStatus(UUID id, StatusChange c) {
        Employee e = get(id);
        List<FieldError> errors = new ArrayList<>();
        if (c.status() == null) {
            errors.add(new FieldError("status", "must not be null"));
        }
        if (c.reason() == null || c.reason().isBlank()) {
            if (c.status() != EmployeeStatus.ACTIVE) {
                errors.add(new FieldError("reason", "a reason is required"));
            }
        }
        boolean leaving = c.status() == EmployeeStatus.RESIGNED || c.status() == EmployeeStatus.TERMINATED;
        if (leaving) {
            if (c.exitDate() == null) {
                errors.add(new FieldError("exitDate", "exit date is required"));
            } else if (c.exitDate().isBefore(e.getJoinDate())) {
                errors.add(new FieldError("exitDate", "exit date cannot be before the join date"));
            }
        }
        throwIfAny(errors);
        if (c.status() == e.getStatus()) {
            throw new ValidationException("status", "employee already has status " + c.status());
        }
        Map<String, Object> before = snapshot(e);
        Map<String, Object> trackedBefore = trackedValues(e);
        e.setStatus(c.status());
        if (leaving) {
            e.setExitDate(c.exitDate());
            e.setExitReason(c.reason());
        } else if (c.status() == EmployeeStatus.ACTIVE) {
            e.setExitDate(null);
            e.setExitReason(null);
        } else {
            e.setExitReason(c.reason());
        }
        LocalDate effective = c.effectiveDate() != null ? c.effectiveDate() : leaving ? c.exitDate() : today(e);
        recordHistory(e, trackedBefore, effective, c.reason());
        audit.record(AuditEntry.of("STATUS_CHANGE", "EMPLOYEE", id).company(e.getCompanyId()).before(before)
                .after(snapshot(e)).reason(c.reason()));
        return e;
    }

    @Transactional
    public void delete(UUID id) {
        Employee e = get(id);
        boolean used = usageCheckers.stream().anyMatch(c -> c.hasRecords(id));
        if (used) {
            throw new ValidationException("Employee has attendance, leave or payroll records and cannot be deleted; "
                    + "change the status instead");
        }
        e.markDeleted(clock.instant());
        audit.record(AuditEntry.of("DELETE", "EMPLOYEE", id).company(e.getCompanyId()).before(snapshot(e)));
    }

    // ---------------------------------------------------------------- helpers

    public EmployeePlacement placement(Employee e) {
        return new EmployeePlacement(e.getId(), e.getCompanyId(), e.getBranchId(), e.getDepartmentId());
    }

    private void assertPlacementInScope(EmployeeData d) {
        EmployeeScope s = scope.current();
        if (!s.covers(null, d.companyId(), d.branchId(), d.departmentId())) {
            throw new ForbiddenException("The employee's company, branch or department is outside your data scope");
        }
    }

    private String nextNumber(EmployeeData d) {
        CompanySettingsView settings = org.settings(d.companyId());
        EmployeeNumberFormat format = EmployeeNumberFormat.parse(settings.employeeNumberFormat());
        String branchCode = org.branch(d.branchId()).map(Branch::getCode).orElse("");
        int year = d.joinDate().getYear();
        String key = format.sequenceKey(branchCode, year);
        for (int attempt = 0; attempt < 1000; attempt++) {
            String candidate = format.render(settings.companyCode(), branchCode, year,
                    platform.nextSequenceValue(d.companyId(), key));
            if (employees.findByCompanyIdAndEmployeeNumberIgnoreCase(d.companyId(), candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not allocate a unique employee number");
    }

    private void apply(Employee e, EmployeeData d) {
        e.setCompanyId(d.companyId());
        e.setBranchId(d.branchId());
        e.setDepartmentId(d.departmentId());
        e.setDesignationId(d.designationId());
        e.setGradeId(d.gradeId());
        e.setCategoryId(d.categoryId());
        e.setTitle(trim(d.title()));
        e.setFirstName(d.firstName().trim());
        e.setLastName(d.lastName().trim());
        e.setPreferredName(trim(d.preferredName()));
        e.setNic(trim(d.nic()));
        e.setDateOfBirth(d.dateOfBirth());
        e.setGender(d.gender());
        e.setMaritalStatus(d.maritalStatus());
        e.setNationality(trim(d.nationality()));
        e.setPersonalEmail(trim(d.personalEmail()));
        e.setMobile(trim(d.mobile()));
        e.setAddress(trim(d.address()));
        e.setJoinDate(d.joinDate());
        e.setProbationEndDate(d.probationEndDate());
        e.setConfirmationDate(d.confirmationDate());
        e.setContractEndDate(d.contractEndDate());
        e.setReportingManagerId(d.reportingManagerId());
        e.setDefaultShiftId(d.defaultShiftId());
        e.setWeeklyOffDays(d.weeklyOffDays() == null ? null : java.util.EnumSet.copyOf(
                d.weeklyOffDays().isEmpty() ? java.util.EnumSet.noneOf(java.time.DayOfWeek.class) : d.weeklyOffDays()));
        e.setBadgeId(trim(d.badgeId()));
        e.getEmergencyContacts().clear();
        if (d.emergencyContacts() != null) {
            d.emergencyContacts().forEach(c -> e.getEmergencyContacts()
                    .add(new EmergencyContact(c.name().trim(), c.relationship().trim(), c.phone().trim())));
        }
    }

    private void validate(EmployeeData d, Employee existing, List<FieldError> errors) {
        require(errors, "companyId", d.companyId());
        require(errors, "branchId", d.branchId());
        require(errors, "departmentId", d.departmentId());
        require(errors, "designationId", d.designationId());
        require(errors, "categoryId", d.categoryId());
        requireText(errors, "firstName", d.firstName());
        requireText(errors, "lastName", d.lastName());
        require(errors, "joinDate", d.joinDate());
        if (d.companyId() == null) {
            return;
        }
        try {
            org.company(d.companyId());
        } catch (NotFoundException ex) {
            errors.add(new FieldError("companyId", "company not found"));
            return;
        }
        if (existing != null && !existing.getCompanyId().equals(d.companyId())) {
            errors.add(new FieldError("companyId", "an employee cannot be moved to another company"));
        }
        UUID company = d.companyId();
        sameCompany(errors, "branchId", d.branchId(), org.branch(d.branchId()).map(Branch::getCompanyId), company);
        sameCompany(errors, "departmentId", d.departmentId(), org.department(d.departmentId()).map(Department::getCompanyId), company);
        sameCompany(errors, "designationId", d.designationId(), org.designation(d.designationId()).map(CompanyLookup::getCompanyId), company);
        sameCompany(errors, "gradeId", d.gradeId(), org.grade(d.gradeId()).map(CompanyLookup::getCompanyId), company);
        sameCompany(errors, "categoryId", d.categoryId(), org.category(d.categoryId()).map(CompanyLookup::getCompanyId), company);

        LocalDate join = d.joinDate();
        if (join != null) {
            after(errors, "probationEndDate", d.probationEndDate(), join);
            after(errors, "confirmationDate", d.confirmationDate(), join);
            after(errors, "contractEndDate", d.contractEndDate(), join);
            if (d.dateOfBirth() != null && !d.dateOfBirth().isBefore(join)) {
                errors.add(new FieldError("dateOfBirth", "date of birth must be before the join date"));
            }
        }
        UUID selfId = existing == null ? null : existing.getId();
        if (d.nic() != null && !d.nic().isBlank()) {
            employees.findByCompanyIdAndNicIgnoreCase(company, d.nic().trim())
                    .filter(o -> !o.getId().equals(selfId))
                    .ifPresent(o -> errors.add(new FieldError("nic", "NIC/passport number already exists")));
        }
        if (d.badgeId() != null && !d.badgeId().isBlank()) {
            employees.findByCompanyIdAndBadgeIdIgnoreCase(company, d.badgeId().trim())
                    .filter(o -> !o.getId().equals(selfId))
                    .ifPresent(o -> errors.add(new FieldError("badgeId", "badge/device ID already exists")));
        }
        if (d.personalEmail() != null && !d.personalEmail().isBlank()
                && !d.personalEmail().trim().matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            errors.add(new FieldError("personalEmail", "must be a valid email address"));
        }
        validateManager(d, selfId, errors);
        if (d.emergencyContacts() != null) {
            for (int i = 0; i < d.emergencyContacts().size(); i++) {
                EmployeeData.Contact c = d.emergencyContacts().get(i);
                if (c == null || blank(c.name()) || blank(c.relationship()) || blank(c.phone())) {
                    errors.add(new FieldError("emergencyContacts[" + i + "]", "name, relationship and phone are required"));
                }
            }
        }
    }

    private void validateManager(EmployeeData d, UUID selfId, List<FieldError> errors) {
        UUID managerId = d.reportingManagerId();
        if (managerId == null) {
            return;
        }
        if (managerId.equals(selfId)) {
            errors.add(new FieldError("reportingManagerId", "an employee cannot report to themselves"));
            return;
        }
        Optional<Employee> manager = employees.findById(managerId);
        if (manager.isEmpty() || !manager.get().getCompanyId().equals(d.companyId())) {
            errors.add(new FieldError("reportingManagerId", "manager must be an employee of the same company"));
            return;
        }
        if (selfId != null) {
            UUID current = manager.get().getReportingManagerId();
            int guard = 0;
            while (current != null && guard++ < 10_000) {
                if (current.equals(selfId)) {
                    errors.add(new FieldError("reportingManagerId", "reporting line would create a cycle"));
                    return;
                }
                current = employees.findById(current).map(Employee::getReportingManagerId).orElse(null);
            }
        }
    }

    private void recordHistory(Employee e, Map<String, Object> before, LocalDate effectiveDate, String reason) {
        Map<String, Object> after = trackedValues(e);
        Map<String, Map<String, Object>> changes = new LinkedHashMap<>();
        for (String key : TRACKED) {
            if (!Objects.equals(before.get(key), after.get(key))) {
                Map<String, Object> change = new LinkedHashMap<>();
                change.put("from", before.get(key));
                change.put("to", after.get(key));
                changes.put(key, change);
            }
        }
        if (changes.isEmpty()) {
            return;
        }
        EventType type;
        if (changes.containsKey("status")) {
            type = EventType.STATUS_CHANGE;
        } else if (changes.containsKey("branchId") || changes.containsKey("departmentId")) {
            type = EventType.TRANSFER;
        } else if (changes.containsKey("designationId") || changes.containsKey("gradeId")) {
            type = EventType.PROMOTION;
        } else {
            type = EventType.CHANGE;
        }
        history.save(new EmploymentHistory(e.getId(), effectiveDate != null ? effectiveDate : today(e), type,
                toJson(changes), reason, CurrentUser.get().id(), clock.instant()));
    }

    private static Map<String, Object> trackedValues(Employee e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("companyId", e.getCompanyId());
        m.put("branchId", e.getBranchId());
        m.put("departmentId", e.getDepartmentId());
        m.put("designationId", e.getDesignationId());
        m.put("gradeId", e.getGradeId());
        m.put("categoryId", e.getCategoryId());
        m.put("reportingManagerId", e.getReportingManagerId());
        m.put("status", e.getStatus());
        return m;
    }

    private static Map<String, Object> snapshot(Employee e) {
        Map<String, Object> m = trackedValues(e);
        m.put("employeeNumber", e.getEmployeeNumber());
        m.put("firstName", e.getFirstName());
        m.put("lastName", e.getLastName());
        m.put("nic", e.getNic());
        m.put("joinDate", e.getJoinDate());
        m.put("exitDate", e.getExitDate());
        m.put("badgeId", e.getBadgeId());
        m.put("defaultShiftId", e.getDefaultShiftId());
        return m;
    }

    private LocalDate today(Employee e) {
        return LocalDate.now(clock.withZone(org.settings(e.getCompanyId()).timeZone()));
    }

    private String toJson(Object o) {
        return json.writeValueAsString(o);
    }

    private static void sameCompany(List<FieldError> errors, String field, UUID id, Optional<UUID> owner, UUID company) {
        if (id != null && (owner.isEmpty() || !owner.get().equals(company))) {
            errors.add(new FieldError(field, "must belong to the employee's company"));
        }
    }

    private static void after(List<FieldError> errors, String field, LocalDate value, LocalDate join) {
        if (value != null && value.isBefore(join)) {
            errors.add(new FieldError(field, "cannot be before the join date"));
        }
    }

    private static void require(List<FieldError> errors, String field, Object value) {
        if (value == null) {
            errors.add(new FieldError(field, "is required"));
        }
    }

    private static void requireText(List<FieldError> errors, String field, String value) {
        if (blank(value)) {
            errors.add(new FieldError(field, "is required"));
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static void throwIfAny(List<FieldError> errors) {
        if (!errors.isEmpty()) {
            throw new ValidationException("Validation failed", errors);
        }
    }
}
