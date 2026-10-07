package com.is2c.hrm.org.service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.is2c.hrm.common.error.NotFoundException;
import com.is2c.hrm.org.api.EmployeePlacement;
import com.is2c.hrm.org.api.EmployeePlacementLookup;
import com.is2c.hrm.org.api.EmployeeScope;
import com.is2c.hrm.org.domain.Branch;
import com.is2c.hrm.org.domain.BranchRepository;
import com.is2c.hrm.org.domain.Department;
import com.is2c.hrm.org.domain.DepartmentRepository;
import com.is2c.hrm.security.api.AuthenticatedUser;
import com.is2c.hrm.security.api.CurrentUser;

/** Resolves the current user's data scope (FR-006); department scope includes all descendant departments. */
@Service
public class DataScopeService {

    private final BranchRepository branches;
    private final DepartmentRepository departments;
    private final ObjectProvider<EmployeePlacementLookup> placements;

    public DataScopeService(BranchRepository branches, DepartmentRepository departments,
            ObjectProvider<EmployeePlacementLookup> placements) {
        this.branches = branches;
        this.departments = departments;
        this.placements = placements;
    }

    @Transactional(readOnly = true)
    public EmployeeScope current() {
        return forUser(CurrentUser.get());
    }

    @Transactional(readOnly = true)
    public EmployeeScope forUser(AuthenticatedUser user) {
        return switch (user.scopeType()) {
            case ALL -> EmployeeScope.everything();
            case COMPANY -> new EmployeeScope(false, user.scopeCompanyIds(), Set.of(), Set.of(), null,
                    user.scopeCompanyIds());
            case BRANCH -> {
                Set<UUID> companies = branches.findByIdIn(user.scopeBranchIds()).stream()
                        .map(Branch::getCompanyId).collect(Collectors.toSet());
                yield new EmployeeScope(false, Set.of(), user.scopeBranchIds(), Set.of(), null, companies);
            }
            case DEPARTMENT -> {
                List<Department> roots = departments.findByIdIn(user.scopeDepartmentIds());
                Set<UUID> companies = roots.stream().map(Department::getCompanyId).collect(Collectors.toSet());
                Set<UUID> expanded = withDescendants(user.scopeDepartmentIds(), departments.findByCompanyIdIn(companies));
                yield new EmployeeScope(false, Set.of(), Set.of(), expanded, null, companies);
            }
            case OWN -> {
                UUID own = user.employeeId();
                Set<UUID> companies = own == null ? Set.of()
                        : placements.stream().flatMap(p -> p.find(own).stream()).map(EmployeePlacement::companyId)
                                .collect(Collectors.toSet());
                yield new EmployeeScope(false, Set.of(), Set.of(), Set.of(), own, companies);
            }
        };
    }

    /** 404 unless the current user may read company-level data of the company. */
    public void assertCompanyReadable(UUID companyId) {
        if (!current().coversCompany(companyId)) {
            throw new NotFoundException("Company", companyId);
        }
    }

    /** 404 unless the current user may change company-level configuration of the company. */
    public void assertCompanyWritable(UUID companyId) {
        if (!current().coversWholeCompany(companyId)) {
            throw new NotFoundException("Company", companyId);
        }
    }

    /** 403 unless the user covers the whole company (company-wide operations such as payroll runs, FR-006). */
    public void assertCompanyWide(UUID companyId) {
        if (!current().coversWholeCompany(companyId)) {
            throw new com.is2c.hrm.common.error.ForbiddenException("This action needs access to the whole company");
        }
    }

    /** 403 unless the user covers the whole company or, for a department-level item, that department. */
    public void assertDepartmentManageable(UUID companyId, UUID departmentId) {
        EmployeeScope s = current();
        if (!s.coversWholeCompany(companyId) && (departmentId == null || !s.departmentIds().contains(departmentId))) {
            throw new com.is2c.hrm.common.error.ForbiddenException(departmentId == null
                    ? "This action needs access to the whole company" : "This department is outside your scope");
        }
    }

    public void assertCovers(EmployeePlacement placement) {
        if (!current().covers(placement)) {
            throw new NotFoundException("Employee", placement.employeeId());
        }
    }

    static Set<UUID> withDescendants(Set<UUID> roots, List<Department> all) {
        Map<UUID, List<UUID>> children = all.stream().filter(d -> d.getParentId() != null)
                .collect(Collectors.groupingBy(Department::getParentId,
                        Collectors.mapping(Department::getId, Collectors.toList())));
        Set<UUID> result = new HashSet<>();
        java.util.ArrayDeque<UUID> queue = new java.util.ArrayDeque<>(roots);
        while (!queue.isEmpty()) {
            UUID id = queue.poll();
            if (result.add(id)) {
                queue.addAll(children.getOrDefault(id, List.of()));
            }
        }
        return Set.copyOf(result);
    }
}
