package com.is2c.hrm.employee.web;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.is2c.hrm.common.web.PageResponse;
import com.is2c.hrm.employee.domain.Employee;
import com.is2c.hrm.employee.domain.EmployeeStatus;
import com.is2c.hrm.employee.domain.EmploymentHistory;
import com.is2c.hrm.employee.domain.Gender;
import com.is2c.hrm.employee.domain.MaritalStatus;
import com.is2c.hrm.employee.service.EmployeeData;
import com.is2c.hrm.employee.service.EmployeeDetailContributor;
import com.is2c.hrm.employee.service.EmployeeService;
import com.is2c.hrm.org.service.OrgDirectory;

@RestController
@RequestMapping("/api/v1/employees")
public class EmployeeController {

    private final EmployeeService employees;
    private final OrgDirectory org;
    private final ObjectProvider<EmployeeDetailContributor> contributors;

    public EmployeeController(EmployeeService employees, OrgDirectory org,
            ObjectProvider<EmployeeDetailContributor> contributors) {
        this.employees = employees;
        this.org = org;
        this.contributors = contributors;
    }

    public record EmployeeListItem(UUID id, UUID companyId, String employeeNumber, String firstName, String lastName,
            String preferredName, String branchName, String departmentName, String designationName,
            EmployeeStatus status, LocalDate joinDate) {
    }

    public record EmployeeDto(UUID id, UUID companyId, UUID branchId, UUID departmentId, UUID designationId,
            UUID gradeId, UUID categoryId, String employeeNumber, String title, String firstName, String lastName,
            String preferredName, String nic, LocalDate dateOfBirth, Gender gender, MaritalStatus maritalStatus,
            String nationality, String personalEmail, String mobile, String address, LocalDate joinDate,
            LocalDate probationEndDate, LocalDate confirmationDate, LocalDate contractEndDate, LocalDate exitDate,
            String exitReason, UUID reportingManagerId, UUID defaultShiftId, Set<DayOfWeek> weeklyOffDays,
            String badgeId, EmployeeStatus status, List<EmployeeData.Contact> emergencyContacts,
            Map<String, String> customFields, Long version) {
    }

    public record HistoryDto(UUID id, LocalDate effectiveDate, EmploymentHistory.EventType eventType, String changes,
            String reason, UUID recordedBy, Instant recordedAt) {
    }

    @GetMapping
    @PreAuthorize("hasAuthority('EMPLOYEE:VIEW')")
    public PageResponse<EmployeeListItem> search(@RequestParam(required = false) String q,
            @RequestParam(required = false) UUID companyId, @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) UUID departmentId, @RequestParam(required = false) UUID designationId,
            @RequestParam(required = false) UUID categoryId, @RequestParam(required = false) EmployeeStatus status,
            Pageable pageable) {
        Page<Employee> page = employees.search(new EmployeeService.SearchFilter(q, companyId, branchId, departmentId,
                designationId, categoryId, status), pageable);
        Set<UUID> branches = new HashSet<>();
        Set<UUID> departments = new HashSet<>();
        Set<UUID> designations = new HashSet<>();
        page.forEach(e -> {
            branches.add(e.getBranchId());
            departments.add(e.getDepartmentId());
            designations.add(e.getDesignationId());
        });
        Map<UUID, String> bn = org.branchNames(branches);
        Map<UUID, String> dn = org.departmentNames(departments);
        Map<UUID, String> gn = org.designationNames(designations);
        return PageResponse.of(page, e -> new EmployeeListItem(e.getId(), e.getCompanyId(), e.getEmployeeNumber(),
                e.getFirstName(), e.getLastName(), e.getPreferredName(), bn.get(e.getBranchId()),
                dn.get(e.getDepartmentId()), gn.get(e.getDesignationId()), e.getStatus(), e.getJoinDate()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('EMPLOYEE:ADD')")
    public EmployeeDto create(@RequestBody EmployeeData body) {
        return toDto(employees.create(body));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('EMPLOYEE:VIEW')")
    public EmployeeDto get(@PathVariable UUID id) {
        return toDto(employees.get(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('EMPLOYEE:EDIT')")
    public EmployeeDto update(@PathVariable UUID id, @RequestBody EmployeeData body) {
        return toDto(employees.update(id, body));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('EMPLOYEE:EDIT')")
    public EmployeeDto changeStatus(@PathVariable UUID id, @RequestBody EmployeeService.StatusChange body) {
        return toDto(employees.changeStatus(id, body));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('EMPLOYEE:DELETE')")
    public void delete(@PathVariable UUID id) {
        employees.delete(id);
    }

    @GetMapping("/{id}/history")
    @PreAuthorize("hasAuthority('EMPLOYEE:VIEW')")
    public List<HistoryDto> history(@PathVariable UUID id) {
        return employees.history(id).stream().map(h -> new HistoryDto(h.getId(), h.getEffectiveDate(),
                h.getEventType(), h.getChangesJson(), h.getReason(), h.getRecordedBy(), h.getRecordedAt())).toList();
    }

    EmployeeDto toDto(Employee e) {
        Map<String, String> custom = new java.util.TreeMap<>();
        contributors.forEach(c -> custom.putAll(c.customFields(e)));
        return new EmployeeDto(e.getId(), e.getCompanyId(), e.getBranchId(), e.getDepartmentId(), e.getDesignationId(),
                e.getGradeId(), e.getCategoryId(), e.getEmployeeNumber(), e.getTitle(), e.getFirstName(),
                e.getLastName(), e.getPreferredName(), e.getNic(), e.getDateOfBirth(), e.getGender(),
                e.getMaritalStatus(), e.getNationality(), e.getPersonalEmail(), e.getMobile(), e.getAddress(),
                e.getJoinDate(), e.getProbationEndDate(), e.getConfirmationDate(), e.getContractEndDate(),
                e.getExitDate(), e.getExitReason(), e.getReportingManagerId(), e.getDefaultShiftId(),
                e.getWeeklyOffDays(), e.getBadgeId(), e.getStatus(),
                e.getEmergencyContacts().stream()
                        .map(c -> new EmployeeData.Contact(c.name(), c.relationship(), c.phone())).toList(),
                custom, e.getVersion());
    }
}
