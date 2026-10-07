package com.is2c.hrm.employee.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.is2c.hrm.employee.domain.Gender;
import com.is2c.hrm.employee.domain.MaritalStatus;

/** Create/update payload for an employee (FR-015). Status is changed only via the status endpoint (FR-017). */
public record EmployeeData(
        UUID companyId,
        UUID branchId,
        UUID departmentId,
        UUID designationId,
        UUID gradeId,
        UUID categoryId,
        String employeeNumber,
        String title,
        String firstName,
        String lastName,
        String preferredName,
        String nic,
        LocalDate dateOfBirth,
        Gender gender,
        MaritalStatus maritalStatus,
        String nationality,
        String personalEmail,
        String mobile,
        String address,
        LocalDate joinDate,
        LocalDate probationEndDate,
        LocalDate confirmationDate,
        LocalDate contractEndDate,
        UUID reportingManagerId,
        UUID defaultShiftId,
        Set<DayOfWeek> weeklyOffDays,
        String badgeId,
        List<Contact> emergencyContacts,
        Map<String, String> customFields,
        LocalDate effectiveDate,
        String changeReason,
        Long version) {

    public record Contact(String name, String relationship, String phone) {
    }
}
