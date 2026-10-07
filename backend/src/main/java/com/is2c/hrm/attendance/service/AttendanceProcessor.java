package com.is2c.hrm.attendance.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.is2c.hrm.attendance.api.ApprovedLeaveLookup;
import com.is2c.hrm.attendance.api.AttendanceLockPolicy;
import com.is2c.hrm.attendance.domain.AttendanceAdjustment;
import com.is2c.hrm.attendance.domain.AttendanceAdjustmentRepository;
import com.is2c.hrm.attendance.domain.AttendanceDaily;
import com.is2c.hrm.attendance.domain.AttendanceDailyRepository;
import com.is2c.hrm.attendance.domain.AttendanceEvent;
import com.is2c.hrm.attendance.domain.AttendanceEventRepository;
import com.is2c.hrm.attendance.service.AttendanceCalculator.CalcInput;
import com.is2c.hrm.attendance.service.AttendanceCalculator.LeaveInfo;
import com.is2c.hrm.attendance.service.AttendanceCalculator.Punch;
import com.is2c.hrm.employee.domain.Employee;
import com.is2c.hrm.employee.service.EmployeeService;
import com.is2c.hrm.org.api.CompanySettingsView;
import com.is2c.hrm.org.service.OrgDirectory;
import com.is2c.hrm.roster.api.DaySchedule;
import com.is2c.hrm.roster.service.ScheduleResolver;
import com.is2c.hrm.shift.api.OtPolicyDefinition;
import com.is2c.hrm.shift.api.ShiftDefinition;
import com.is2c.hrm.shift.service.ShiftService;

/**
 * Builds calculator inputs in batch and upserts AttendanceDaily rows (FR-048). Never processes future dates and
 * skips dates locked by payroll (FR-053). Idempotent: the same inputs give the same rows.
 */
@Service
public class AttendanceProcessor {

    private final EmployeeService employees;
    private final ScheduleResolver schedules;
    private final AttendanceEventRepository events;
    private final AttendanceDailyRepository dailies;
    private final AttendanceAdjustmentRepository adjustments;
    private final ShiftService shifts;
    private final OrgDirectory org;
    private final ObjectProvider<ApprovedLeaveLookup> leaves;
    private final ObjectProvider<AttendanceLockPolicy> locks;
    private final Clock clock;

    public AttendanceProcessor(EmployeeService employees, ScheduleResolver schedules, AttendanceEventRepository events,
            AttendanceDailyRepository dailies, AttendanceAdjustmentRepository adjustments, ShiftService shifts,
            OrgDirectory org, ObjectProvider<ApprovedLeaveLookup> leaves, ObjectProvider<AttendanceLockPolicy> locks,
            Clock clock) {
        this.employees = employees;
        this.schedules = schedules;
        this.events = events;
        this.dailies = dailies;
        this.adjustments = adjustments;
        this.shifts = shifts;
        this.org = org;
        this.leaves = leaves;
        this.locks = locks;
        this.clock = clock;
    }

    public record ProcessResult(int processed, int skippedLocked, int changed) {
    }

    @Transactional
    public ProcessResult process(UUID companyId, Collection<UUID> employeeIds, LocalDate from, LocalDate to) {
        return process(companyId, employeeIds, from, to, false);
    }

    /** {@code ignoreLock} is used only for audited lock overrides (FR-053). */
    @Transactional
    public ProcessResult process(UUID companyId, Collection<UUID> employeeIds, LocalDate from, LocalDate to,
            boolean ignoreLock) {
        CompanySettingsView settings = org.settings(companyId);
        ZoneId zone = settings.timeZone();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate end = to.isAfter(today) ? today : to;
        if (from.isAfter(end)) {
            return new ProcessResult(0, 0, 0);
        }
        List<Employee> list = employees.employedBetween(companyId, employeeIds, from, end);
        if (list.isEmpty()) {
            return new ProcessResult(0, 0, 0);
        }
        Set<UUID> ids = list.stream().map(Employee::getId).collect(Collectors.toSet());

        Map<UUID, List<DaySchedule>> scheduleMap = schedules.resolve(list, from.minusDays(1), end.plusDays(1));
        Instant eventsFrom = from.minusDays(1).atStartOfDay(zone).toInstant();
        Instant eventsTo = end.plusDays(2).atTime(12, 0).atZone(zone).toInstant();
        Map<UUID, List<Punch>> punches = new HashMap<>();
        for (AttendanceEvent e : events.findForEmployees(ids, eventsFrom, eventsTo)) {
            punches.computeIfAbsent(e.getEmployeeId(), k -> new ArrayList<>())
                    .add(new Punch(e.getEventTime().atZone(zone).toLocalDateTime(), e.getDirection()));
        }
        Map<ApprovedLeaveLookup.Key, LeaveInfo> leaveMap = new HashMap<>();
        leaves.ifAvailable(l -> leaveMap.putAll(l.find(ids, from, end)));
        Map<ApprovedLeaveLookup.Key, AttendanceAdjustment> applied = new HashMap<>();
        adjustments.findApplied(ids, from, end).forEach(a ->
                applied.put(new ApprovedLeaveLookup.Key(a.getEmployeeId(), a.getWorkDate()), a));
        Map<UUID, Boolean> otEligible = org.categoryOtEligibility(
                list.stream().map(Employee::getCategoryId).collect(Collectors.toSet()));
        Map<UUID, ShiftDefinition> defaults = shifts.definitions(
                list.stream().map(Employee::getDefaultShiftId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<String, AttendanceDaily> existing = new HashMap<>();
        dailies.findByEmployeeIdInAndWorkDateBetween(ids, from, end)
                .forEach(d -> existing.put(d.getEmployeeId() + "|" + d.getWorkDate(), d));

        AttendanceLockPolicy lock = ignoreLock ? null : locks.getIfAvailable();
        AttendanceLockPolicy staleNotifier = locks.getIfAvailable();
        Map<LocalDate, Boolean> lockedCache = new HashMap<>();
        Instant now = clock.instant();
        int processed = 0;
        int skipped = 0;
        Set<LocalDate> changedDates = new HashSet<>();
        List<AttendanceDaily> toSave = new ArrayList<>();
        for (Employee e : list) {
            List<DaySchedule> days = scheduleMap.get(e.getId());
            OtPolicyDefinition fallback = e.getDefaultShiftId() == null || defaults.get(e.getDefaultShiftId()) == null
                    ? null : defaults.get(e.getDefaultShiftId()).otPolicy();
            List<Punch> empPunches = punches.getOrDefault(e.getId(), List.of());
            LocalDate first = from.isBefore(e.getJoinDate()) ? e.getJoinDate() : from;
            LocalDate last = e.getExitDate() != null && e.getExitDate().isBefore(end) ? e.getExitDate() : end;
            for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1)) {
                LocalDate date = d;
                if (lock != null && lockedCache.computeIfAbsent(date, x -> lock.isLocked(companyId, x))) {
                    skipped++;
                    continue;
                }
                int idx = (int) java.time.temporal.ChronoUnit.DAYS.between(from.minusDays(1), date);
                ApprovedLeaveLookup.Key key = new ApprovedLeaveLookup.Key(e.getId(), date);
                AttendanceAdjustment adj = applied.get(key);
                CalcInput input = new CalcInput(date, days.get(idx - 1), days.get(idx), days.get(idx + 1), empPunches,
                        adj != null, adj == null ? null : adj.getNewIn(), adj == null ? null : adj.getNewOut(),
                        leaveMap.getOrDefault(key, LeaveInfo.NONE), otEligible.getOrDefault(e.getCategoryId(), true),
                        fallback, settings.punchWindowBeforeMinutes(), settings.punchWindowAfterMinutes());
                AttendanceCalculator.Result result = AttendanceCalculator.calculate(input);
                AttendanceDaily daily = existing.computeIfAbsent(e.getId() + "|" + date,
                        k -> new AttendanceDaily(e.getId(), companyId, date));
                if (daily.apply(result, now)) {
                    changedDates.add(date);
                }
                toSave.add(daily);
                processed++;
            }
        }
        dailies.saveAll(toSave);
        if (staleNotifier != null && !changedDates.isEmpty()) {
            staleNotifier.attendanceChanged(companyId, changedDates.stream().sorted(Comparator.naturalOrder()).toList());
        }
        return new ProcessResult(processed, skipped, changedDates.size());
    }
}
