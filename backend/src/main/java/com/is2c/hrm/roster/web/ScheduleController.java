package com.is2c.hrm.roster.web;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.is2c.hrm.common.error.ValidationException;
import com.is2c.hrm.employee.domain.Employee;
import com.is2c.hrm.employee.service.EmployeeService;
import com.is2c.hrm.org.service.DataScopeService;
import com.is2c.hrm.org.service.OrgDirectory;
import com.is2c.hrm.roster.api.DaySchedule;
import com.is2c.hrm.roster.service.ScheduleResolver;

@RestController
@RequestMapping("/api/v1/schedules")
public class ScheduleController {

    static final int MAX_DAYS = 62;

    private final ScheduleResolver resolver;
    private final EmployeeService employees;
    private final OrgDirectory org;
    private final DataScopeService scope;

    public ScheduleController(ScheduleResolver resolver, EmployeeService employees, OrgDirectory org,
            DataScopeService scope) {
        this.resolver = resolver;
        this.employees = employees;
        this.org = org;
        this.scope = scope;
    }

    public record DayDto(LocalDate date, DaySchedule.DayType dayType, UUID shiftId, String shiftCode,
            DaySchedule.Source source, String holidayName) {
        public static DayDto of(DaySchedule d) {
            return new DayDto(d.date(), d.dayType(), d.shift() == null ? null : d.shift().id(),
                    d.shift() == null ? null : d.shift().code(), d.source(), d.holidayName());
        }
    }

    public record ScheduleRow(UUID employeeId, String employeeNumber, String name, UUID departmentId,
            List<DayDto> days) {
    }

    /** FR-040 resolved schedules (roster > rotation > default) for employees in scope. */
    @GetMapping
    @PreAuthorize("hasAuthority('ROSTER:VIEW')")
    public List<ScheduleRow> schedules(@RequestParam UUID companyId, @RequestParam(required = false) UUID departmentId,
            @RequestParam(required = false) UUID employeeId, @RequestParam LocalDate from, @RequestParam LocalDate to) {
        if (to.isBefore(from) || ChronoUnit.DAYS.between(from, to) >= MAX_DAYS) {
            throw new ValidationException("to", "the range must be 1-" + MAX_DAYS + " days");
        }
        scope.assertCompanyReadable(companyId);
        Set<UUID> departments = departmentId == null ? null : org.departmentWithDescendants(departmentId);
        List<Employee> list = employees.employedInScope(companyId, departments, employeeId, from, to);
        Map<UUID, List<DaySchedule>> schedules = resolver.resolve(list, from, to);
        return list.stream().map(e -> new ScheduleRow(e.getId(), e.getEmployeeNumber(), e.fullName(),
                e.getDepartmentId(), schedules.get(e.getId()).stream().map(DayDto::of).toList())).toList();
    }
}
