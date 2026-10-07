package com.is2c.hrm.attendance.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.is2c.hrm.attendance.domain.AttendanceAdjustment;
import com.is2c.hrm.attendance.domain.AttendanceAdjustmentRepository;
import com.is2c.hrm.attendance.domain.AttendanceDaily;
import com.is2c.hrm.attendance.domain.AttendanceDailyRepository;
import com.is2c.hrm.attendance.domain.AttendanceEvent;
import com.is2c.hrm.attendance.domain.AttendanceEventRepository;
import com.is2c.hrm.attendance.service.AttendanceCalculator.DayStatus;
import com.is2c.hrm.common.error.ValidationException;
import com.is2c.hrm.employee.domain.Employee;
import com.is2c.hrm.employee.service.EmployeeService;
import com.is2c.hrm.org.api.EmployeeScope;
import com.is2c.hrm.org.service.DataScopeService;
import com.is2c.hrm.org.service.OrgDirectory;

import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

/** FR-054/FR-055 attendance views restricted to the user's data scope. */
@Service
public class AttendanceQueryService {

    static final int MAX_RANGE_DAYS = 62;

    private final AttendanceDailyRepository dailies;
    private final AttendanceEventRepository events;
    private final AttendanceAdjustmentRepository adjustments;
    private final EmployeeService employees;
    private final DataScopeService scope;
    private final OrgDirectory org;

    public AttendanceQueryService(AttendanceDailyRepository dailies, AttendanceEventRepository events,
            AttendanceAdjustmentRepository adjustments, EmployeeService employees, DataScopeService scope,
            OrgDirectory org) {
        this.dailies = dailies;
        this.events = events;
        this.adjustments = adjustments;
        this.employees = employees;
        this.scope = scope;
        this.org = org;
    }

    public record DailyFilter(UUID companyId, LocalDate from, LocalDate to, UUID departmentId, UUID employeeId,
            DayStatus status, String exception) {
    }

    public record DayDetail(Employee employee, AttendanceDaily daily, List<AttendanceEvent> events,
            List<AttendanceAdjustment> adjustments) {
    }

    @Transactional(readOnly = true)
    public Page<AttendanceDaily> daily(DailyFilter f, Pageable pageable) {
        if (f.from() == null || f.to() == null || f.to().isBefore(f.from())
                || java.time.temporal.ChronoUnit.DAYS.between(f.from(), f.to()) >= MAX_RANGE_DAYS) {
            throw new ValidationException("to", "the range must be 1-" + MAX_RANGE_DAYS + " days");
        }
        scope.assertCompanyReadable(f.companyId());
        EmployeeScope s = scope.current();
        java.util.Set<UUID> departments = f.departmentId() == null ? null : org.departmentWithDescendants(f.departmentId());
        Specification<AttendanceDaily> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            p.add(cb.equal(root.get("companyId"), f.companyId()));
            p.add(cb.between(root.get("workDate"), f.from(), f.to()));
            if (f.employeeId() != null) {
                p.add(cb.equal(root.get("employeeId"), f.employeeId()));
            }
            if (f.status() != null) {
                p.add(cb.equal(root.get("status"), f.status()));
            }
            if (f.exception() != null && !f.exception().isBlank()) {
                p.add(cb.like(root.get("exceptions"), "%" + f.exception().replaceAll("[^A-Z_]", "") + "%"));
            }
            Subquery<UUID> visible = query.subquery(UUID.class);
            Root<Employee> e = visible.from(Employee.class);
            List<Predicate> ep = new ArrayList<>();
            ep.add(s.toPredicate(cb, e.get("id"), e.get("companyId"), e.get("branchId"), e.get("departmentId")));
            if (departments != null) {
                ep.add(e.get("departmentId").in(departments));
            }
            visible.select(e.get("id")).where(ep.toArray(Predicate[]::new));
            p.add(root.get("employeeId").in(visible));
            return cb.and(p.toArray(Predicate[]::new));
        };
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                Sort.by("workDate").ascending().and(Sort.by("employeeId")));
        return dailies.findAll(spec, sorted);
    }

    @Transactional(readOnly = true)
    public DayDetail day(UUID employeeId, LocalDate date) {
        Employee e = employees.get(employeeId);
        return detail(e, date);
    }

    /** Self-service: own attendance without scope checks (FR-055, FR-100). */
    @Transactional(readOnly = true)
    public List<AttendanceDaily> own(UUID employeeId, LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || java.time.temporal.ChronoUnit.DAYS.between(from, to) > 366) {
            throw new ValidationException("to", "invalid date range");
        }
        return dailies.findByEmployeeIdAndWorkDateBetweenOrderByWorkDate(employeeId, from, to);
    }

    DayDetail detail(Employee e, LocalDate date) {
        ZoneId zone = org.settings(e.getCompanyId()).timeZone();
        Instant from = date.minusDays(1).atStartOfDay(zone).toInstant();
        Instant to = date.plusDays(2).atStartOfDay(zone).toInstant();
        return new DayDetail(e, dailies.findByEmployeeIdAndWorkDate(e.getId(), date).orElse(null),
                events.findForEmployees(List.of(e.getId()), from, to),
                adjustments.findByEmployeeIdAndWorkDateOrderByRequestedAtDesc(e.getId(), date));
    }
}
