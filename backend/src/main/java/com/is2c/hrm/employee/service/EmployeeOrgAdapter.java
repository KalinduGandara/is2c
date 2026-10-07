package com.is2c.hrm.employee.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.is2c.hrm.employee.domain.EmployeeRepository;
import com.is2c.hrm.org.api.EmployeePlacement;
import com.is2c.hrm.org.api.EmployeePlacementLookup;
import com.is2c.hrm.org.api.OrgReferenceChecker;

/** Supplies employee placement and org-reference counts to the org module (FR-006, FR-011..013). */
@Component
public class EmployeeOrgAdapter implements EmployeePlacementLookup, OrgReferenceChecker {

    private final EmployeeRepository employees;

    public EmployeeOrgAdapter(EmployeeRepository employees) {
        this.employees = employees;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EmployeePlacement> find(UUID employeeId) {
        return employees.findById(employeeId)
                .map(e -> new EmployeePlacement(e.getId(), e.getCompanyId(), e.getBranchId(), e.getDepartmentId()));
    }

    @Override
    @Transactional(readOnly = true)
    public long countActiveReferences(RefType type, UUID id) {
        return employees.countActiveReferencing(id);
    }
}
