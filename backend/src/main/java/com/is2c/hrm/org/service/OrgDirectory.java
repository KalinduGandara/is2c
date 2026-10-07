package com.is2c.hrm.org.service;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.is2c.hrm.common.error.NotFoundException;
import com.is2c.hrm.org.api.CompanySettingsView;
import com.is2c.hrm.org.domain.Branch;
import com.is2c.hrm.org.domain.BranchRepository;
import com.is2c.hrm.org.domain.Company;
import com.is2c.hrm.org.domain.CompanyRepository;
import com.is2c.hrm.org.domain.CompanySettings;
import com.is2c.hrm.org.domain.CompanySettingsRepository;
import com.is2c.hrm.org.domain.Department;
import com.is2c.hrm.org.domain.DepartmentRepository;
import com.is2c.hrm.org.domain.Designation;
import com.is2c.hrm.org.domain.DesignationRepository;
import com.is2c.hrm.org.domain.EmploymentCategory;
import com.is2c.hrm.org.domain.EmploymentCategoryRepository;
import com.is2c.hrm.org.domain.Grade;
import com.is2c.hrm.org.domain.GradeRepository;

/** Read-only organization lookups for other modules. */
@Service
@Transactional(readOnly = true)
public class OrgDirectory {

    private final CompanyRepository companies;
    private final CompanySettingsRepository settings;
    private final BranchRepository branches;
    private final DepartmentRepository departments;
    private final DesignationRepository designations;
    private final GradeRepository grades;
    private final EmploymentCategoryRepository categories;

    public OrgDirectory(CompanyRepository companies, CompanySettingsRepository settings, BranchRepository branches,
            DepartmentRepository departments, DesignationRepository designations, GradeRepository grades,
            EmploymentCategoryRepository categories) {
        this.companies = companies;
        this.settings = settings;
        this.branches = branches;
        this.departments = departments;
        this.designations = designations;
        this.grades = grades;
        this.categories = categories;
    }

    public Company company(UUID companyId) {
        return companies.findById(companyId).orElseThrow(() -> new NotFoundException("Company", companyId));
    }

    public List<Company> allCompanies() {
        return companies.findAll();
    }

    public CompanySettingsView settings(UUID companyId) {
        Company c = company(companyId);
        CompanySettings s = settings.findByCompanyId(companyId).orElseThrow(() -> new NotFoundException("Company settings", companyId));
        return new CompanySettingsView(companyId, c.getCode(), ZoneId.of(c.getTimeZone()), c.getCurrency(),
                Set.copyOf(s.getWeeklyOffDays()), s.getEmployeeNumberFormat(), s.getPayrollStartDay(),
                s.isAbsentAsNoPay(), s.getNoPayDivisor(), s.getPunchWindowBeforeMinutes(),
                s.getPunchWindowAfterMinutes(), s.getWeekStartDay());
    }

    public Optional<Branch> branch(UUID id) {
        return id == null ? Optional.empty() : branches.findById(id);
    }

    public Optional<Department> department(UUID id) {
        return id == null ? Optional.empty() : departments.findById(id);
    }

    public Optional<Designation> designation(UUID id) {
        return id == null ? Optional.empty() : designations.findById(id);
    }

    public Optional<Grade> grade(UUID id) {
        return id == null ? Optional.empty() : grades.findById(id);
    }

    public Optional<EmploymentCategory> category(UUID id) {
        return id == null ? Optional.empty() : categories.findById(id);
    }

    /** Display names by id for batches of org records (used by list views and reports). */
    public java.util.Map<UUID, String> branchNames(java.util.Collection<UUID> ids) {
        return branches.findAllById(ids).stream().collect(java.util.stream.Collectors.toMap(Branch::getId, Branch::getName));
    }

    public java.util.Map<UUID, String> departmentNames(java.util.Collection<UUID> ids) {
        return departments.findAllById(ids).stream()
                .collect(java.util.stream.Collectors.toMap(Department::getId, Department::getName));
    }

    public java.util.Map<UUID, String> designationNames(java.util.Collection<UUID> ids) {
        return designations.findAllById(ids).stream()
                .collect(java.util.stream.Collectors.toMap(Designation::getId, Designation::getName));
    }

    public java.util.Map<UUID, Boolean> categoryOtEligibility(java.util.Collection<UUID> ids) {
        return categories.findAllById(ids).stream()
                .collect(java.util.stream.Collectors.toMap(EmploymentCategory::getId, EmploymentCategory::isOtEligible));
    }

    /** The department and all its descendants. */
    public Set<UUID> departmentWithDescendants(UUID departmentId) {
        Department d = department(departmentId).orElseThrow(() -> new NotFoundException("Department", departmentId));
        return DataScopeService.withDescendants(Set.of(departmentId), departments.findByCompanyIdOrderByCode(d.getCompanyId()));
    }

    /** The department followed by its ancestors, nearest first. */
    public List<UUID> departmentAncestry(UUID departmentId) {
        List<UUID> chain = new ArrayList<>();
        UUID current = departmentId;
        while (current != null && !chain.contains(current)) {
            chain.add(current);
            current = departments.findById(current).map(Department::getParentId).orElse(null);
        }
        return chain;
    }
}
