package com.is2c.hrm.org.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

/**
 * The resolved data scope of the current user (FR-006). Department ids already include descendants.
 * {@code readableCompanyIds} are companies whose company-level configuration the user may read.
 */
public record EmployeeScope(boolean all, Set<UUID> companyIds, Set<UUID> branchIds, Set<UUID> departmentIds,
        UUID ownEmployeeId, Set<UUID> readableCompanyIds) {

    public static EmployeeScope everything() {
        return new EmployeeScope(true, Set.of(), Set.of(), Set.of(), null, Set.of());
    }

    public boolean covers(EmployeePlacement p) {
        return covers(p.employeeId(), p.companyId(), p.branchId(), p.departmentId());
    }

    public boolean covers(UUID employeeId, UUID companyId, UUID branchId, UUID departmentId) {
        return all
                || companyIds.contains(companyId)
                || (branchId != null && branchIds.contains(branchId))
                || (departmentId != null && departmentIds.contains(departmentId))
                || (ownEmployeeId != null && ownEmployeeId.equals(employeeId));
    }

    public boolean coversCompany(UUID companyId) {
        return all || readableCompanyIds.contains(companyId);
    }

    /** Full company access: ALL scope or the company is explicitly in a COMPANY scope. */
    public boolean coversWholeCompany(UUID companyId) {
        return all || companyIds.contains(companyId);
    }

    /** JPA predicate restricting employee-related rows to this scope. */
    public Predicate toPredicate(CriteriaBuilder cb, Expression<UUID> employeeId, Expression<UUID> companyId,
            Expression<UUID> branchId, Expression<UUID> departmentId) {
        if (all) {
            return cb.conjunction();
        }
        List<Predicate> any = new ArrayList<>();
        if (!companyIds.isEmpty()) {
            any.add(companyId.in(companyIds));
        }
        if (!branchIds.isEmpty()) {
            any.add(branchId.in(branchIds));
        }
        if (!departmentIds.isEmpty()) {
            any.add(departmentId.in(departmentIds));
        }
        if (ownEmployeeId != null) {
            any.add(cb.equal(employeeId, ownEmployeeId));
        }
        return any.isEmpty() ? cb.disjunction() : cb.or(any.toArray(Predicate[]::new));
    }
}
