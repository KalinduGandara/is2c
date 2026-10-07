package com.is2c.hrm.shift.service;

import java.util.List;

import org.springframework.stereotype.Component;

import com.is2c.hrm.common.error.FieldError;
import com.is2c.hrm.employee.domain.Employee;
import com.is2c.hrm.employee.service.EmployeeData;
import com.is2c.hrm.employee.service.EmployeeExtension;
import com.is2c.hrm.shift.domain.ShiftRepository;

/** FR-015: an employee's default shift must be an active shift of the same company. */
@Component
public class DefaultShiftValidator implements EmployeeExtension {

    private final ShiftRepository shifts;

    public DefaultShiftValidator(ShiftRepository shifts) {
        this.shifts = shifts;
    }

    @Override
    public void validate(EmployeeData data, List<FieldError> errors) {
        if (data.defaultShiftId() == null) {
            return;
        }
        boolean ok = shifts.findById(data.defaultShiftId())
                .filter(s -> s.getCompanyId().equals(data.companyId()) && s.isActive()).isPresent();
        if (!ok) {
            errors.add(new FieldError("defaultShiftId", "must be an active shift of the employee's company"));
        }
    }

    @Override
    public void saved(Employee employee, EmployeeData data) {
        // nothing extra to persist
    }
}
