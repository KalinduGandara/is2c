package com.is2c.hrm.employee.service;

import java.util.Map;

import com.is2c.hrm.employee.domain.Employee;

/** Adds extra data (custom field values) to the employee detail response. */
public interface EmployeeDetailContributor {

    Map<String, String> customFields(Employee employee);
}
