package com.is2c.hrm.org.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.is2c.hrm.audit.service.AuditEntry;
import com.is2c.hrm.audit.service.AuditService;
import com.is2c.hrm.common.domain.Versions;
import com.is2c.hrm.common.error.FieldError;
import com.is2c.hrm.common.error.NotFoundException;
import com.is2c.hrm.common.error.ValidationException;
import com.is2c.hrm.org.api.EmployeePlacementLookup;
import com.is2c.hrm.org.api.OrgReferenceChecker;
import com.is2c.hrm.org.api.OrgReferenceChecker.RefType;
import com.is2c.hrm.org.domain.Branch;
import com.is2c.hrm.org.domain.BranchRepository;
import com.is2c.hrm.org.domain.CompanyLookup;
import com.is2c.hrm.org.domain.CompanyRepository;
import com.is2c.hrm.org.domain.Department;
import com.is2c.hrm.org.domain.DepartmentRepository;
import com.is2c.hrm.org.domain.Designation;
import com.is2c.hrm.org.domain.DesignationRepository;
import com.is2c.hrm.org.domain.EmploymentCategory;
import com.is2c.hrm.org.domain.EmploymentCategoryRepository;
import com.is2c.hrm.org.domain.Grade;
import com.is2c.hrm.org.domain.GradeRepository;

/** FR-011 branches, FR-012 departments, FR-013 lookups. */
@Service
public class StructureService {

    private static final String CODE_PATTERN = "[A-Za-z0-9_-]{1,20}";

    private final CompanyRepository companies;
    private final BranchRepository branches;
    private final DepartmentRepository departments;
    private final DesignationRepository designations;
    private final GradeRepository grades;
    private final EmploymentCategoryRepository categories;
    private final DataScopeService scope;
    private final AuditService audit;
    private final ObjectProvider<OrgReferenceChecker> referenceCheckers;
    private final ObjectProvider<EmployeePlacementLookup> placements;
    private final Clock clock;

    public StructureService(CompanyRepository companies, BranchRepository branches, DepartmentRepository departments,
            DesignationRepository designations, GradeRepository grades, EmploymentCategoryRepository categories,
            DataScopeService scope, AuditService audit, ObjectProvider<OrgReferenceChecker> referenceCheckers,
            ObjectProvider<EmployeePlacementLookup> placements, Clock clock) {
        this.companies = companies;
        this.branches = branches;
        this.departments = departments;
        this.designations = designations;
        this.grades = grades;
        this.categories = categories;
        this.scope = scope;
        this.audit = audit;
        this.referenceCheckers = referenceCheckers;
        this.placements = placements;
        this.clock = clock;
    }

    // ---------- Branches ----------

    public record BranchData(String code, String name, String address, String phone, Long version) {
    }

    @Transactional(readOnly = true)
    public List<Branch> branches(UUID companyId) {
        scope.assertCompanyReadable(companyId);
        return branches.findByCompanyIdOrderByCode(companyId);
    }

    @Transactional
    public Branch createBranch(UUID companyId, BranchData d) {
        requireCompany(companyId);
        scope.assertCompanyWritable(companyId);
        validateCodeName(d.code(), d.name());
        branches.findByCompanyIdAndCodeIgnoreCase(companyId, d.code()).ifPresent(b -> duplicateCode());
        Branch b = new Branch(companyId, d.code().toUpperCase());
        b.setName(d.name().trim());
        b.setAddress(d.address());
        b.setPhone(d.phone());
        branches.save(b);
        audit.record(AuditEntry.of("CREATE", "BRANCH", b.getId()).company(companyId).after(d));
        return b;
    }

    @Transactional
    public Branch updateBranch(UUID id, BranchData d) {
        Branch b = branches.findById(id).orElseThrow(() -> new NotFoundException("Branch", id));
        scope.assertCompanyWritable(b.getCompanyId());
        Versions.check(b, d.version());
        validateCodeName(d.code(), d.name());
        branches.findByCompanyIdAndCodeIgnoreCase(b.getCompanyId(), d.code()).filter(o -> !o.getId().equals(id))
                .ifPresent(o -> duplicateCode());
        BranchData before = new BranchData(b.getCode(), b.getName(), b.getAddress(), b.getPhone(), b.getVersion());
        b.setCode(d.code().toUpperCase());
        b.setName(d.name().trim());
        b.setAddress(d.address());
        b.setPhone(d.phone());
        audit.record(AuditEntry.of("UPDATE", "BRANCH", id).company(b.getCompanyId()).before(before).after(d));
        return b;
    }

    @Transactional
    public void deleteBranch(UUID id) {
        Branch b = branches.findById(id).orElseThrow(() -> new NotFoundException("Branch", id));
        scope.assertCompanyWritable(b.getCompanyId());
        if (references(RefType.BRANCH, id) > 0) {
            throw new ValidationException("Branch has active employees and cannot be deleted");
        }
        b.markDeleted(clock.instant());
        audit.record(AuditEntry.of("DELETE", "BRANCH", id).company(b.getCompanyId()));
    }

    // ---------- Departments ----------

    public record DepartmentData(String code, String name, UUID parentId, UUID branchId, String costCenterCode,
            UUID managerEmployeeId, Long version) {
    }

    public record DepartmentNode(Department department, List<DepartmentNode> children) {
    }

    @Transactional(readOnly = true)
    public List<DepartmentNode> departmentTree(UUID companyId) {
        scope.assertCompanyReadable(companyId);
        List<Department> all = departments.findByCompanyIdOrderByCode(companyId);
        return childrenOf(null, all);
    }

    @Transactional(readOnly = true)
    public List<Department> departments(UUID companyId) {
        scope.assertCompanyReadable(companyId);
        return departments.findByCompanyIdOrderByCode(companyId);
    }

    @Transactional
    public Department createDepartment(UUID companyId, DepartmentData d) {
        requireCompany(companyId);
        scope.assertCompanyWritable(companyId);
        validateDepartment(companyId, null, d);
        departments.findByCompanyIdAndCodeIgnoreCase(companyId, d.code()).ifPresent(o -> duplicateCode());
        Department dep = new Department(companyId, d.code().toUpperCase());
        applyDepartment(dep, d);
        departments.save(dep);
        audit.record(AuditEntry.of("CREATE", "DEPARTMENT", dep.getId()).company(companyId).after(d));
        return dep;
    }

    @Transactional
    public Department updateDepartment(UUID id, DepartmentData d) {
        Department dep = departments.findById(id).orElseThrow(() -> new NotFoundException("Department", id));
        scope.assertCompanyWritable(dep.getCompanyId());
        Versions.check(dep, d.version());
        validateDepartment(dep.getCompanyId(), id, d);
        departments.findByCompanyIdAndCodeIgnoreCase(dep.getCompanyId(), d.code())
                .filter(o -> !o.getId().equals(id)).ifPresent(o -> duplicateCode());
        DepartmentData before = new DepartmentData(dep.getCode(), dep.getName(), dep.getParentId(), dep.getBranchId(),
                dep.getCostCenterCode(), dep.getManagerEmployeeId(), dep.getVersion());
        dep.setCode(d.code().toUpperCase());
        applyDepartment(dep, d);
        audit.record(AuditEntry.of("UPDATE", "DEPARTMENT", id).company(dep.getCompanyId()).before(before).after(d));
        return dep;
    }

    @Transactional
    public void deleteDepartment(UUID id) {
        Department dep = departments.findById(id).orElseThrow(() -> new NotFoundException("Department", id));
        scope.assertCompanyWritable(dep.getCompanyId());
        if (!departments.findByParentId(id).isEmpty()) {
            throw new ValidationException("Department has child departments and cannot be deleted");
        }
        if (references(RefType.DEPARTMENT, id) > 0) {
            throw new ValidationException("Department has active employees and cannot be deleted");
        }
        dep.markDeleted(clock.instant());
        audit.record(AuditEntry.of("DELETE", "DEPARTMENT", id).company(dep.getCompanyId()));
    }

    private void applyDepartment(Department dep, DepartmentData d) {
        dep.setName(d.name().trim());
        dep.setParentId(d.parentId());
        dep.setBranchId(d.branchId());
        dep.setCostCenterCode(d.costCenterCode());
        dep.setManagerEmployeeId(d.managerEmployeeId());
    }

    private void validateDepartment(UUID companyId, UUID selfId, DepartmentData d) {
        validateCodeName(d.code(), d.name());
        List<FieldError> errors = new ArrayList<>();
        if (d.parentId() != null) {
            Optional<Department> parent = departments.findById(d.parentId());
            if (parent.isEmpty() || !parent.get().getCompanyId().equals(companyId)) {
                errors.add(new FieldError("parentId", "parent department must belong to the same company"));
            } else if (selfId != null && createsCycle(selfId, d.parentId())) {
                errors.add(new FieldError("parentId", "parent assignment would create a cycle"));
            }
        }
        if (d.branchId() != null) {
            Optional<Branch> branch = branches.findById(d.branchId());
            if (branch.isEmpty() || !branch.get().getCompanyId().equals(companyId)) {
                errors.add(new FieldError("branchId", "branch must belong to the same company"));
            }
        }
        if (d.managerEmployeeId() != null) {
            boolean ok = placements.stream().flatMap(p -> p.find(d.managerEmployeeId()).stream())
                    .anyMatch(p -> p.companyId().equals(companyId));
            if (!ok) {
                errors.add(new FieldError("managerEmployeeId", "manager must be an employee of the same company"));
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("Validation failed", errors);
        }
    }

    private boolean createsCycle(UUID selfId, UUID newParentId) {
        UUID current = newParentId;
        int guard = 0;
        while (current != null && guard++ < 10_000) {
            if (current.equals(selfId)) {
                return true;
            }
            current = departments.findById(current).map(Department::getParentId).orElse(null);
        }
        return false;
    }

    private static List<DepartmentNode> childrenOf(UUID parentId, List<Department> all) {
        return all.stream().filter(d -> Objects.equals(d.getParentId(), parentId))
                .map(d -> new DepartmentNode(d, childrenOf(d.getId(), all))).toList();
    }

    // ---------- Lookups (designation, grade, employment category) ----------

    public enum LookupKind { DESIGNATION, GRADE, EMPLOYMENT_CATEGORY }

    public record LookupData(String code, String name, Integer rank, Boolean otEligible, Long version) {
    }

    @Transactional(readOnly = true)
    public List<? extends CompanyLookup> lookups(LookupKind kind, UUID companyId) {
        scope.assertCompanyReadable(companyId);
        return switch (kind) {
            case DESIGNATION -> designations.findByCompanyIdOrderByCode(companyId);
            case GRADE -> grades.findByCompanyIdOrderByCode(companyId);
            case EMPLOYMENT_CATEGORY -> categories.findByCompanyIdOrderByCode(companyId);
        };
    }

    @Transactional
    public CompanyLookup createLookup(LookupKind kind, UUID companyId, LookupData d) {
        requireCompany(companyId);
        scope.assertCompanyWritable(companyId);
        validateCodeName(d.code(), d.name());
        if (findByCode(kind, companyId, d.code()).isPresent()) {
            duplicateCode();
        }
        CompanyLookup l = switch (kind) {
            case DESIGNATION -> new Designation(companyId);
            case GRADE -> new Grade(companyId);
            case EMPLOYMENT_CATEGORY -> new EmploymentCategory(companyId);
        };
        applyLookup(l, d);
        repo(kind).save(l);
        audit.record(AuditEntry.of("CREATE", kind.name(), l.getId()).company(companyId).after(d));
        return l;
    }

    @Transactional
    public CompanyLookup updateLookup(LookupKind kind, UUID id, LookupData d) {
        CompanyLookup l = findLookup(kind, id);
        scope.assertCompanyWritable(l.getCompanyId());
        Versions.check(l, d.version());
        validateCodeName(d.code(), d.name());
        findByCode(kind, l.getCompanyId(), d.code()).filter(o -> !o.getId().equals(id)).ifPresent(o -> duplicateCode());
        LookupData before = toLookupData(l);
        applyLookup(l, d);
        audit.record(AuditEntry.of("UPDATE", kind.name(), id).company(l.getCompanyId()).before(before).after(d));
        return l;
    }

    @Transactional
    public void deleteLookup(LookupKind kind, UUID id) {
        CompanyLookup l = findLookup(kind, id);
        scope.assertCompanyWritable(l.getCompanyId());
        if (references(RefType.valueOf(kind.name()), id) > 0) {
            throw new ValidationException("The record is used by active employees and cannot be deleted");
        }
        l.markDeleted(clock.instant());
        audit.record(AuditEntry.of("DELETE", kind.name(), id).company(l.getCompanyId()));
    }

    public static LookupData toLookupData(CompanyLookup l) {
        Integer rank = l instanceof Grade g ? g.getRank() : null;
        Boolean ot = l instanceof EmploymentCategory c ? c.isOtEligible() : null;
        return new LookupData(l.getCode(), l.getName(), rank, ot, l.getVersion());
    }

    private void applyLookup(CompanyLookup l, LookupData d) {
        l.setCode(d.code().toUpperCase());
        l.setName(d.name().trim());
        if (l instanceof Grade g) {
            g.setRank(d.rank() == null ? 0 : d.rank());
        }
        if (l instanceof EmploymentCategory c) {
            c.setOtEligible(d.otEligible() == null || d.otEligible());
        }
    }

    private CompanyLookup findLookup(LookupKind kind, UUID id) {
        Function<UUID, Optional<? extends CompanyLookup>> finder = switch (kind) {
            case DESIGNATION -> designations::findById;
            case GRADE -> grades::findById;
            case EMPLOYMENT_CATEGORY -> categories::findById;
        };
        return finder.apply(id).orElseThrow(() -> new NotFoundException(kind.name(), id));
    }

    private Optional<? extends CompanyLookup> findByCode(LookupKind kind, UUID companyId, String code) {
        return switch (kind) {
            case DESIGNATION -> designations.findByCompanyIdAndCodeIgnoreCase(companyId, code);
            case GRADE -> grades.findByCompanyIdAndCodeIgnoreCase(companyId, code);
            case EMPLOYMENT_CATEGORY -> categories.findByCompanyIdAndCodeIgnoreCase(companyId, code);
        };
    }

    @SuppressWarnings("unchecked")
    private JpaRepository<CompanyLookup, UUID> repo(LookupKind kind) {
        Object r = switch (kind) {
            case DESIGNATION -> designations;
            case GRADE -> grades;
            case EMPLOYMENT_CATEGORY -> categories;
        };
        return (JpaRepository<CompanyLookup, UUID>) r;
    }

    // ---------- helpers ----------

    private void requireCompany(UUID companyId) {
        if (!companies.existsById(companyId)) {
            throw new NotFoundException("Company", companyId);
        }
    }

    private long references(RefType type, UUID id) {
        return referenceCheckers.stream().mapToLong(c -> c.countActiveReferences(type, id)).sum();
    }

    private static void validateCodeName(String code, String name) {
        List<FieldError> errors = new ArrayList<>();
        if (code == null || !code.matches(CODE_PATTERN)) {
            errors.add(new FieldError("code", "must be 1-20 letters, digits, '-' or '_'"));
        }
        if (name == null || name.isBlank()) {
            errors.add(new FieldError("name", "must not be blank"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("Validation failed", errors);
        }
    }

    private static void duplicateCode() {
        throw new ValidationException("code", "Code already exists in this company");
    }
}
