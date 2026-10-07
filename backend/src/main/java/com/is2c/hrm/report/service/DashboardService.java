package com.is2c.hrm.report.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.is2c.hrm.approval.service.ApprovalService;
import com.is2c.hrm.attendance.api.ApprovedLeaveLookup;
import com.is2c.hrm.attendance.domain.AttendanceDaily;
import com.is2c.hrm.attendance.service.AttendanceQueryService;
import com.is2c.hrm.employee.domain.Employee;
import com.is2c.hrm.employee.domain.EmployeeStatus;
import com.is2c.hrm.employee.service.EmployeeService;
import com.is2c.hrm.org.service.DataScopeService;
import com.is2c.hrm.org.service.OrgDirectory;
import com.is2c.hrm.payroll.domain.PayrollPeriod;
import com.is2c.hrm.payroll.service.PayrollPeriodService;
import com.is2c.hrm.roster.api.DaySchedule;
import com.is2c.hrm.roster.service.ScheduleResolver;
import com.is2c.hrm.security.api.CurrentUser;

/**
 * FR-096 dashboard KPIs for "today" in the company's time zone, restricted to the user's data scope.
 * Present = punched in today; absent = scheduled to work today, not punched in and not on full-day leave.
 */
@Service
public class DashboardService {

    private final OrgDirectory org;
    private final DataScopeService scope;
    private final EmployeeService employees;
    private final AttendanceQueryService attendance;
    private final ScheduleResolver schedules;
    private final ApprovedLeaveLookup leave;
    private final ApprovalService approvals;
    private final PayrollPeriodService payroll;
    private final Clock clock;

    public DashboardService(OrgDirectory org, DataScopeService scope, EmployeeService employees,
            AttendanceQueryService attendance, ScheduleResolver schedules, ApprovedLeaveLookup leave,
            ApprovalService approvals, PayrollPeriodService payroll, Clock clock) {
        this.org = org;
        this.scope = scope;
        this.employees = employees;
        this.attendance = attendance;
        this.schedules = schedules;
        this.leave = leave;
        this.approvals = approvals;
        this.payroll = payroll;
        this.clock = clock;
    }

    public record PeriodSummary(UUID id, int year, int month, PayrollPeriod.Status status) {
    }

    public record PayrollKpis(PeriodSummary currentPeriod, PeriodSummary lastProcessedPeriod, BigDecimal lastProcessedOtCost) {
    }

    public record Dashboard(LocalDate date, int activeEmployees, int presentToday, int absentToday, int lateToday,
            int onLeaveToday, int pendingApprovals, Map<String, Integer> headcountByDepartment, PayrollKpis payroll) {
    }

    @Transactional(readOnly = true)
    public Dashboard dashboard(UUID companyId) {
        scope.assertCompanyReadable(companyId);
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(org.company(companyId).getTimeZone())));
        List<Employee> active = employees.employedInScope(companyId, null, null, today, today).stream()
                .filter(e -> e.getStatus() == EmployeeStatus.ACTIVE).toList();
        Set<UUID> ids = active.stream().map(Employee::getId).collect(Collectors.toSet());

        List<AttendanceDaily> daily = attendance.daily(new AttendanceQueryService.DailyFilter(companyId, today, today,
                null, null, null, null), PageRequest.of(0, WorkforceReports.MAX_ROWS)).getContent();
        Set<UUID> present = new HashSet<>();
        int late = 0;
        for (AttendanceDaily d : daily) {
            if (ids.contains(d.getEmployeeId()) && d.getFirstIn() != null) {
                present.add(d.getEmployeeId());
                if (d.getLateMinutes() > 0) {
                    late++;
                }
            }
        }
        Set<UUID> onLeave = new HashSet<>();
        Set<UUID> fullDayLeave = new HashSet<>();
        if (!ids.isEmpty()) {
            leave.find(ids, today, today).forEach((k, info) -> {
                onLeave.add(k.employeeId());
                if (info.fraction().compareTo(BigDecimal.ONE) >= 0) {
                    fullDayLeave.add(k.employeeId());
                }
            });
        }
        int absent = 0;
        for (Map.Entry<UUID, List<DaySchedule>> s : schedules.resolve(active, today, today).entrySet()) {
            boolean workday = s.getValue().stream().anyMatch(d -> d.dayType() == DaySchedule.DayType.WORKDAY);
            if (workday && !present.contains(s.getKey()) && !fullDayLeave.contains(s.getKey())) {
                absent++;
            }
        }
        Map<UUID, String> deptNames = org.departmentNames(active.stream().map(Employee::getDepartmentId).toList());
        Map<String, Integer> headcount = new TreeMap<>();
        active.forEach(e -> headcount.merge(deptNames.getOrDefault(e.getDepartmentId(), "—"), 1, Integer::sum));

        PayrollKpis kpis = null;
        if (CurrentUser.get().hasPermission("PAYROLL:VIEW")) {
            PeriodSummary current = payroll.latest(companyId).map(DashboardService::summary).orElse(null);
            PayrollPeriod processed = payroll.latestProcessed(companyId).orElse(null);
            kpis = new PayrollKpis(current, processed == null ? null : summary(processed),
                    processed == null ? null : payroll.otCost(processed.getId()));
        }
        return new Dashboard(today, active.size(), present.size(), absent, late, onLeave.size(),
                approvals.inbox().size(), headcount, kpis);
    }

    private static PeriodSummary summary(PayrollPeriod p) {
        return new PeriodSummary(p.getId(), p.getYear(), p.getMonth(), p.getStatus());
    }
}
