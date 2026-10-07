package com.is2c.hrm.report.service;

import static com.is2c.hrm.report.service.ReportDefinition.param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import com.is2c.hrm.attendance.domain.AttendanceAdjustment;
import com.is2c.hrm.attendance.domain.AttendanceDaily;
import com.is2c.hrm.attendance.service.AdjustmentService;
import com.is2c.hrm.attendance.service.AttendanceCalculator.DayStatus;
import com.is2c.hrm.attendance.service.AttendanceQueryService;
import com.is2c.hrm.employee.domain.Employee;
import com.is2c.hrm.employee.service.EmployeeService;
import com.is2c.hrm.org.service.OrgDirectory;
import com.is2c.hrm.platform.export.TableData;
import com.is2c.hrm.platform.export.TableData.Column;
import com.is2c.hrm.security.domain.AppUser;
import com.is2c.hrm.security.domain.AppUserRepository;
import com.is2c.hrm.shift.domain.Shift;
import com.is2c.hrm.shift.service.ShiftService;

/** FR-095 reports 2–4: daily attendance, attendance summary, attendance edits. */
@Component
class AttendanceReports implements ReportProvider {

    private final AttendanceQueryService attendance;
    private final AdjustmentService adjustments;
    private final EmployeeService employees;
    private final ShiftService shifts;
    private final OrgDirectory org;
    private final AppUserRepository users;

    AttendanceReports(AttendanceQueryService attendance, AdjustmentService adjustments, EmployeeService employees,
            ShiftService shifts, OrgDirectory org, AppUserRepository users) {
        this.attendance = attendance;
        this.adjustments = adjustments;
        this.employees = employees;
        this.shifts = shifts;
        this.org = org;
        this.users = users;
    }

    @Override
    public List<ReportDefinition> reports() {
        var company = param("companyId", "Company", "company", true);
        var department = param("departmentId", "Department", "department", false);
        var from = param("from", "From", "date", true);
        var to = param("to", "To", "date", true);
        return List.of(
                new ReportDefinition("daily-attendance", "Daily attendance",
                        List.of(company, param("date", "Date", "date", true), department), Set.of(), true, this::daily),
                new ReportDefinition("attendance-summary", "Attendance summary", List.of(company, from, to, department),
                        Set.of(), true, this::summary),
                new ReportDefinition("attendance-edits", "Attendance edits", List.of(company, from, to), Set.of(), true,
                        this::edits));
    }

    private List<AttendanceDaily> rows(ReportParams p, LocalDate from, LocalDate to) {
        return attendance.daily(new AttendanceQueryService.DailyFilter(p.uuid("companyId"), from, to,
                p.uuid("departmentId"), null, null, null), PageRequest.of(0, WorkforceReports.MAX_ROWS)).getContent();
    }

    private List<String> header(ReportParams p, String range) {
        List<String> h = new ArrayList<>(List.of(org.company(p.uuid("companyId")).getLegalName(), range));
        UUID dept = p.uuid("departmentId");
        if (dept != null) {
            h.add("Department: " + org.departmentNames(List.of(dept)).get(dept));
        }
        return h;
    }

    private TableData daily(ReportParams p) {
        LocalDate date = p.date("date");
        List<AttendanceDaily> list = rows(p, date, date);
        Map<UUID, Employee> people = employees.byIds(list.stream().map(AttendanceDaily::getEmployeeId).toList());
        Map<UUID, String> codes = shifts.forCompany(p.uuid("companyId")).stream()
                .collect(Collectors.toMap(Shift::getId, Shift::getCode));
        List<List<Object>> rows = new ArrayList<>();
        list.stream().sorted(Comparator.comparing(d -> ReportSupport.number(people.get(d.getEmployeeId())))).forEach(d -> {
            Employee e = people.get(d.getEmployeeId());
            rows.add(java.util.Arrays.asList(ReportSupport.number(e), ReportSupport.name(e),
                    d.getShiftId() == null ? null : codes.get(d.getShiftId()), d.getStatus().name(),
                    ReportSupport.punch(d.getFirstIn(), d.getWorkDate()), ReportSupport.punch(d.getLastOut(), d.getWorkDate()),
                    d.getLateMinutes(), d.getEarlyMinutes(),
                    ReportSupport.hours(d.getOtWorkdayMinutes() + d.getOtOffdayMinutes() + d.getOtHolidayMinutes())));
        });
        return p.table(header(p, "Date: " + date), List.of(Column.text("Employee No"), Column.text("Name"),
                Column.text("Shift"), Column.text("Status"), Column.text("IN"), Column.text("OUT"),
                Column.number("Late (min)"), Column.number("Early (min)"), Column.number("OT (h)")), rows, null);
    }

    private static final class Totals {
        int present;
        int absent;
        int half;
        BigDecimal leave = BigDecimal.ZERO;
        int lateCount;
        long late;
        int earlyCount;
        long otWork;
        long otOff;
        long otHoliday;
    }

    private TableData summary(ReportParams p) {
        LocalDate from = p.date("from");
        LocalDate to = p.date("to");
        p.requireOrder(from, to);
        List<AttendanceDaily> list = rows(p, from, to);
        Map<UUID, Totals> byEmployee = new LinkedHashMap<>();
        for (AttendanceDaily d : list) {
            Totals t = byEmployee.computeIfAbsent(d.getEmployeeId(), k -> new Totals());
            switch (d.getStatus()) {
                case PRESENT, INCOMPLETE -> t.present++;
                case ABSENT -> t.absent++;
                case HALF_DAY -> t.half++;
                default -> {
                    // leave, off days and holidays are counted below or not at all
                }
            }
            if (d.getStatus() == DayStatus.ON_LEAVE || d.getStatus() == DayStatus.HALF_DAY_LEAVE) {
                t.leave = t.leave.add(d.getLeaveFraction() == null ? BigDecimal.ONE : d.getLeaveFraction());
            }
            if (d.getLateMinutes() > 0) {
                t.lateCount++;
                t.late += d.getLateMinutes();
            }
            if (d.getEarlyMinutes() > 0) {
                t.earlyCount++;
            }
            t.otWork += d.getOtWorkdayMinutes();
            t.otOff += d.getOtOffdayMinutes();
            t.otHoliday += d.getOtHolidayMinutes();
        }
        Map<UUID, Employee> people = employees.byIds(byEmployee.keySet());
        List<List<Object>> rows = new ArrayList<>();
        byEmployee.entrySet().stream().sorted(Comparator.comparing(en -> ReportSupport.number(people.get(en.getKey()))))
                .forEach(en -> {
                    Totals t = en.getValue();
                    Employee e = people.get(en.getKey());
                    rows.add(List.of(ReportSupport.number(e), ReportSupport.name(e), t.present, t.absent, t.half, t.leave,
                            t.lateCount, t.late, t.earlyCount, ReportSupport.hours(t.otWork), ReportSupport.hours(t.otOff),
                            ReportSupport.hours(t.otHoliday)));
                });
        return p.table(header(p, "From " + from + " to " + to), List.of(Column.text("Employee No"), Column.text("Name"),
                Column.number("Present"), Column.number("Absent"), Column.number("Half days"), Column.number("Leave days"),
                Column.number("Late count"), Column.number("Late (min)"), Column.number("Early count"),
                Column.number("OT workday (h)"), Column.number("OT off day (h)"), Column.number("OT holiday (h)")),
                rows, null);
    }

    private TableData edits(ReportParams p) {
        LocalDate from = p.date("from");
        LocalDate to = p.date("to");
        p.requireOrder(from, to);
        List<AttendanceAdjustment> list = adjustments.list(p.uuid("companyId"), from, to).stream()
                .sorted(Comparator.comparing(AttendanceAdjustment::getWorkDate).thenComparing(AttendanceAdjustment::getRequestedAt))
                .toList();
        Map<UUID, Employee> people = employees.byIds(list.stream().map(AttendanceAdjustment::getEmployeeId).toList());
        Map<UUID, String> userNames = users.findAllById(list.stream().map(AttendanceAdjustment::getRequestedBy)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(AppUser::getId, AppUser::getUsername));
        List<List<Object>> rows = new ArrayList<>();
        for (AttendanceAdjustment a : list) {
            Employee e = people.get(a.getEmployeeId());
            rows.add(java.util.Arrays.asList(a.getWorkDate(), ReportSupport.number(e), ReportSupport.name(e),
                    a.getType().name(), ReportSupport.dateTime(a.getBeforeIn()), ReportSupport.dateTime(a.getBeforeOut()),
                    ReportSupport.dateTime(a.getNewIn()), ReportSupport.dateTime(a.getNewOut()), a.getReason(),
                    userNames.get(a.getRequestedBy()), a.getStatus().name()));
        }
        return p.table(header(p, "From " + from + " to " + to), List.of(Column.text("Date"), Column.text("Employee No"),
                Column.text("Name"), Column.text("Type"), Column.text("Before IN"), Column.text("Before OUT"),
                Column.text("After IN"), Column.text("After OUT"), Column.text("Reason"), Column.text("Requested by"),
                Column.text("Status")), rows, null);
    }
}
