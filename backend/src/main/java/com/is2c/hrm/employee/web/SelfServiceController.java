package com.is2c.hrm.employee.web;

import java.util.List;
import java.util.UUID;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.is2c.hrm.common.error.NotFoundException;
import com.is2c.hrm.employee.domain.Employee;
import com.is2c.hrm.employee.service.DocumentService;
import com.is2c.hrm.employee.service.EmployeeService;
import com.is2c.hrm.employee.web.EmployeeController.EmployeeDto;
import com.is2c.hrm.employee.web.EmployeeExtrasController.DocumentDto;
import com.is2c.hrm.org.service.OrgDirectory;
import com.is2c.hrm.security.api.CurrentUser;

/** FR-100 self-service: own profile and documents (authentication + linked employee only). */
@RestController
@RequestMapping("/api/v1/me")
public class SelfServiceController {

    private final EmployeeService employees;
    private final DocumentService documents;
    private final EmployeeController employeeController;
    private final OrgDirectory org;

    public SelfServiceController(EmployeeService employees, DocumentService documents,
            EmployeeController employeeController, OrgDirectory org) {
        this.employees = employees;
        this.documents = documents;
        this.employeeController = employeeController;
        this.org = org;
    }

    public record ProfileDto(EmployeeDto employee, String companyName, String branchName, String departmentName,
            String designationName, String reportingManagerName) {
    }

    /** The current user's employee id, or 404 when the user is not linked to an employee. */
    public static UUID linkedEmployee() {
        UUID id = CurrentUser.get().employeeId();
        if (id == null) {
            throw new NotFoundException("No employee is linked to this user");
        }
        return id;
    }

    @GetMapping("/profile")
    public ProfileDto profile() {
        UUID id = linkedEmployee();
        Employee e = employees.byIds(List.of(id)).get(id);
        if (e == null) {
            throw new NotFoundException("Employee", id);
        }
        Employee m = e.getReportingManagerId() == null ? null
                : employees.byIds(List.of(e.getReportingManagerId())).get(e.getReportingManagerId());
        return new ProfileDto(employeeController.toDto(e), org.company(e.getCompanyId()).getLegalName(),
                org.branchNames(List.of(e.getBranchId())).get(e.getBranchId()),
                e.getDepartmentId() == null ? null : org.departmentNames(List.of(e.getDepartmentId())).get(e.getDepartmentId()),
                e.getDesignationId() == null ? null : org.designationNames(List.of(e.getDesignationId())).get(e.getDesignationId()),
                m == null ? null : m.getFirstName() + " " + m.getLastName());
    }

    @GetMapping("/documents")
    public List<DocumentDto> documents() {
        return documents.listOwn(linkedEmployee()).stream().map(DocumentDto::of).toList();
    }

    @GetMapping("/documents/{documentId}/content")
    public ResponseEntity<InputStreamResource> document(@PathVariable UUID documentId) {
        return EmployeeExtrasController.asAttachment(documents.openOwn(linkedEmployee(), documentId));
    }
}
