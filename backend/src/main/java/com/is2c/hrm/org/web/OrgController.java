package com.is2c.hrm.org.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.is2c.hrm.common.error.NotFoundException;
import com.is2c.hrm.org.service.CompanyService;
import com.is2c.hrm.org.service.StructureService;
import com.is2c.hrm.org.service.StructureService.LookupKind;
import com.is2c.hrm.org.web.OrgDtos.BranchDto;
import com.is2c.hrm.org.web.OrgDtos.CompanyDto;
import com.is2c.hrm.org.web.OrgDtos.DepartmentDto;
import com.is2c.hrm.org.web.OrgDtos.LookupDto;
import com.is2c.hrm.org.web.OrgDtos.SettingsDto;

@RestController
@RequestMapping("/api/v1")
public class OrgController {

    private final CompanyService companies;
    private final StructureService structure;

    public OrgController(CompanyService companies, StructureService structure) {
        this.companies = companies;
        this.structure = structure;
    }

    // Companies (FR-010) and settings (FR-016)

    @GetMapping("/companies")
    @PreAuthorize("hasAuthority('ORG:VIEW')")
    public List<CompanyDto> listCompanies() {
        return companies.list().stream().map(CompanyDto::of).toList();
    }

    @PostMapping("/companies")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ORG:CONFIGURE')")
    public CompanyDto createCompany(@RequestBody CompanyService.CompanyData body) {
        return CompanyDto.of(companies.create(body));
    }

    @GetMapping("/companies/{id}")
    @PreAuthorize("hasAuthority('ORG:VIEW')")
    public CompanyDto getCompany(@PathVariable UUID id) {
        return CompanyDto.of(companies.get(id));
    }

    @PutMapping("/companies/{id}")
    @PreAuthorize("hasAuthority('ORG:EDIT')")
    public CompanyDto updateCompany(@PathVariable UUID id, @RequestBody CompanyService.CompanyData body) {
        return CompanyDto.of(companies.update(id, body));
    }

    @GetMapping("/companies/{id}/settings")
    @PreAuthorize("hasAuthority('ORG:VIEW')")
    public SettingsDto getSettings(@PathVariable UUID id) {
        return SettingsDto.of(companies.settings(id));
    }

    @PutMapping("/companies/{id}/settings")
    @PreAuthorize("hasAuthority('ORG:CONFIGURE')")
    public SettingsDto updateSettings(@PathVariable UUID id, @RequestBody CompanyService.SettingsData body) {
        return SettingsDto.of(companies.updateSettings(id, body));
    }

    // Branches (FR-011)

    @GetMapping("/companies/{companyId}/branches")
    @PreAuthorize("hasAuthority('ORG:VIEW')")
    public List<BranchDto> branches(@PathVariable UUID companyId) {
        return structure.branches(companyId).stream().map(BranchDto::of).toList();
    }

    @PostMapping("/companies/{companyId}/branches")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ORG:ADD')")
    public BranchDto createBranch(@PathVariable UUID companyId, @RequestBody StructureService.BranchData body) {
        return BranchDto.of(structure.createBranch(companyId, body));
    }

    @PutMapping("/branches/{id}")
    @PreAuthorize("hasAuthority('ORG:EDIT')")
    public BranchDto updateBranch(@PathVariable UUID id, @RequestBody StructureService.BranchData body) {
        return BranchDto.of(structure.updateBranch(id, body));
    }

    @DeleteMapping("/branches/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ORG:DELETE')")
    public void deleteBranch(@PathVariable UUID id) {
        structure.deleteBranch(id);
    }

    // Departments (FR-012)

    @GetMapping("/companies/{companyId}/departments")
    @PreAuthorize("hasAuthority('ORG:VIEW')")
    public List<DepartmentDto> departments(@PathVariable UUID companyId) {
        return structure.departments(companyId).stream().map(DepartmentDto::of).toList();
    }

    @GetMapping("/companies/{companyId}/departments/tree")
    @PreAuthorize("hasAuthority('ORG:VIEW')")
    public List<DepartmentDto> departmentTree(@PathVariable UUID companyId) {
        return structure.departmentTree(companyId).stream().map(DepartmentDto::of).toList();
    }

    @PostMapping("/companies/{companyId}/departments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ORG:ADD')")
    public DepartmentDto createDepartment(@PathVariable UUID companyId,
            @RequestBody StructureService.DepartmentData body) {
        return DepartmentDto.of(structure.createDepartment(companyId, body));
    }

    @PutMapping("/departments/{id}")
    @PreAuthorize("hasAuthority('ORG:EDIT')")
    public DepartmentDto updateDepartment(@PathVariable UUID id, @RequestBody StructureService.DepartmentData body) {
        return DepartmentDto.of(structure.updateDepartment(id, body));
    }

    @DeleteMapping("/departments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ORG:DELETE')")
    public void deleteDepartment(@PathVariable UUID id) {
        structure.deleteDepartment(id);
    }

    // Lookups (FR-013): /designations, /grades, /employment-categories

    @GetMapping("/companies/{companyId}/{kind:designations|grades|employment-categories}")
    @PreAuthorize("hasAuthority('ORG:VIEW')")
    public List<LookupDto> lookups(@PathVariable UUID companyId, @PathVariable String kind) {
        return structure.lookups(kind(kind), companyId).stream().map(LookupDto::of).toList();
    }

    @PostMapping("/companies/{companyId}/{kind:designations|grades|employment-categories}")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ORG:ADD')")
    public LookupDto createLookup(@PathVariable UUID companyId, @PathVariable String kind,
            @RequestBody StructureService.LookupData body) {
        return LookupDto.of(structure.createLookup(kind(kind), companyId, body));
    }

    @PutMapping("/{kind:designations|grades|employment-categories}/{id}")
    @PreAuthorize("hasAuthority('ORG:EDIT')")
    public LookupDto updateLookup(@PathVariable String kind, @PathVariable UUID id,
            @RequestBody StructureService.LookupData body) {
        return LookupDto.of(structure.updateLookup(kind(kind), id, body));
    }

    @DeleteMapping("/{kind:designations|grades|employment-categories}/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('ORG:DELETE')")
    public void deleteLookup(@PathVariable String kind, @PathVariable UUID id) {
        structure.deleteLookup(kind(kind), id);
    }

    private static LookupKind kind(String path) {
        return switch (path) {
            case "designations" -> LookupKind.DESIGNATION;
            case "grades" -> LookupKind.GRADE;
            case "employment-categories" -> LookupKind.EMPLOYMENT_CATEGORY;
            default -> throw new NotFoundException("Resource " + path + " not found");
        };
    }
}
