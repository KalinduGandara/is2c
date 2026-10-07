package com.is2c.hrm.roster.web;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.is2c.hrm.common.error.NotFoundException;
import com.is2c.hrm.common.error.ValidationException;
import com.is2c.hrm.employee.domain.Employee;
import com.is2c.hrm.employee.service.EmployeeService;
import com.is2c.hrm.roster.service.ScheduleResolver;
import com.is2c.hrm.roster.web.ScheduleController.DayDto;
import com.is2c.hrm.security.api.CurrentUser;

/** FR-040 own schedule via self-service. */
@RestController
public class MyScheduleController {

    static final int MAX_DAYS = 62;

    private final ScheduleResolver resolver;
    private final EmployeeService employees;

    public MyScheduleController(ScheduleResolver resolver, EmployeeService employees) {
        this.resolver = resolver;
        this.employees = employees;
    }

    @GetMapping("/api/v1/me/schedule")
    public List<DayDto> mine(@RequestParam LocalDate from, @RequestParam LocalDate to) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            throw new ValidationException("to", "the range must be 1-" + MAX_DAYS + " days");
        }
        UUID id = CurrentUser.get().employeeId();
        Employee e = id == null ? null : employees.byIds(List.of(id)).get(id);
        if (e == null) {
            throw new NotFoundException("No employee is linked to this user");
        }
        return resolver.resolve(List.of(e), from, to).getOrDefault(id, List.of()).stream().map(DayDto::of).toList();
    }
}
