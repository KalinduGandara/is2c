package com.is2c.hrm.leave.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.is2c.hrm.common.error.ValidationException;
import com.is2c.hrm.employee.domain.Employee;
import com.is2c.hrm.employee.service.EmployeeService;
import com.is2c.hrm.leave.domain.LeaveRequest;
import com.is2c.hrm.leave.domain.LeaveRequest.Status;
import com.is2c.hrm.leave.domain.LeaveRequestRepository;
import com.is2c.hrm.leave.domain.LeaveType;
import com.is2c.hrm.leave.domain.LeaveTypeRepository;
import com.is2c.hrm.org.domain.Holiday;
import com.is2c.hrm.org.service.DataScopeService;
import com.is2c.hrm.org.service.HolidayService;
import com.is2c.hrm.security.api.AuthenticatedUser;
import com.is2c.hrm.security.api.CurrentUser;
import com.is2c.hrm.security.api.Permissions;
import com.is2c.hrm.security.api.PermissionAction;
import com.is2c.hrm.security.api.PermissionModule;

import jakarta.persistence.criteria.Predicate;

/** FR-065 leave calendar and request lists, limited by permission and scope. */
@Service
public class LeaveCalendarService {

    private final LeaveRequestRepository requests;
    private final LeaveTypeRepository types;
    private final EmployeeService employees;
    private final HolidayService holidays;
    private final DataScopeService scope;

    public LeaveCalendarService(LeaveRequestRepository requests, LeaveTypeRepository types, EmployeeService employees,
            HolidayService holidays, DataScopeService scope) {
        this.requests = requests;
        this.types = types;
        this.employees = employees;
        this.holidays = holidays;
        this.scope = scope;
    }

    public record CalendarEntry(String kind, UUID id, UUID employeeId, String employeeName, String leaveType,
            String color, LocalDate from, LocalDate to, String status, String halfDayPart) {
    }

    public record RequestFilter(UUID companyId, Status status, UUID employeeId, LocalDate from, LocalDate to) {
    }

    @Transactional(readOnly = true)
    public List<CalendarEntry> calendar(UUID companyId, LocalDate from, LocalDate to, boolean includePending) {
        if (from == null || to == null || to.isBefore(from) || from.plusDays(400).isBefore(to)) {
            throw new ValidationException("to", "invalid date range");
        }
        scope.assertCompanyReadable(companyId);
        AuthenticatedUser user = CurrentUser.get();
        boolean viewer = user.hasPermission(Permissions.of(PermissionModule.LEAVE, PermissionAction.VIEW));
        List<CalendarEntry> result = new ArrayList<>();
        for (Holiday h : holidays.between(Set.of(companyId), from, to)) {
            result.add(new CalendarEntry("HOLIDAY", h.getId(), null, h.getName(), null, null, h.getDate(), h.getDate(),
                    null, null));
        }
        List<Employee> visible = viewer ? employees.employedInScope(companyId, null, null, from, to)
                : user.employeeId() == null ? List.of() : List.copyOf(employees.byIds(List.of(user.employeeId())).values());
        Map<UUID, Employee> byId = visible.stream().collect(Collectors.toMap(Employee::getId, e -> e));
        Map<UUID, LeaveType> typeById = types.findByCompanyIdOrderByCode(companyId).stream()
                .collect(Collectors.toMap(LeaveType::getId, t -> t));
        Specification<LeaveRequest> spec = (root, q, cb) -> cb.and(
                cb.equal(root.get("companyId"), companyId),
                cb.lessThanOrEqualTo(root.get("fromDate"), to),
                cb.greaterThanOrEqualTo(root.get("toDate"), from),
                includePending ? root.get("status").in(Status.APPROVED, Status.PENDING) : cb.equal(root.get("status"), Status.APPROVED),
                byId.isEmpty() ? cb.disjunction() : root.get("employeeId").in(byId.keySet()));
        for (LeaveRequest r : requests.findAll(spec)) {
            LeaveType t = typeById.get(r.getLeaveTypeId());
            Employee e = byId.get(r.getEmployeeId());
            result.add(new CalendarEntry("LEAVE", r.getId(), r.getEmployeeId(), e.fullName(), t.getName(), t.getColor(),
                    r.getFromDate(), r.getToDate(), r.getStatus().name(),
                    r.getHalfDayPart() == null ? null : r.getHalfDayPart().name()));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<LeaveRequest> requests(RequestFilter f) {
        scope.assertCompanyReadable(f.companyId());
        LocalDate from = f.from() == null ? LocalDate.of(1900, 1, 1) : f.from();
        LocalDate to = f.to() == null ? LocalDate.of(9999, 12, 31) : f.to();
        Set<UUID> visible = employees.employedInScope(f.companyId(), null, f.employeeId(), from, to).stream()
                .map(Employee::getId).collect(Collectors.toSet());
        Specification<LeaveRequest> spec = (root, q, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("companyId"), f.companyId()));
            p.add(cb.lessThanOrEqualTo(root.get("fromDate"), to));
            p.add(cb.greaterThanOrEqualTo(root.get("toDate"), from));
            p.add(visible.isEmpty() ? cb.disjunction() : root.get("employeeId").in(visible));
            if (f.status() != null) {
                p.add(cb.equal(root.get("status"), f.status()));
            }
            q.orderBy(cb.desc(root.get("fromDate")));
            return cb.and(p.toArray(Predicate[]::new));
        };
        return requests.findAll(spec);
    }
}
