package com.is2c.hrm.employee.service;

import java.util.List;

import com.is2c.hrm.common.error.FieldError;
import com.is2c.hrm.employee.domain.Employee;

/** Hook for employee sub-features (e.g. custom fields, default shift) to validate and persist extra data. */
public interface EmployeeExtension {

    void validate(EmployeeData data, List<FieldError> errors);

    void saved(Employee employee, EmployeeData data);
}
