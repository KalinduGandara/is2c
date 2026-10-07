package com.is2c.hrm.roster.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.is2c.hrm.employee.domain.Employee;
import com.is2c.hrm.org.domain.Holiday;
import com.is2c.hrm.org.service.HolidayService;
import com.is2c.hrm.org.service.OrgDirectory;
import com.is2c.hrm.roster.api.DaySchedule;
import com.is2c.hrm.roster.api.RosterEntryValue;
import com.is2c.hrm.roster.service.ScheduleRules.EmployeeInfo;
import com.is2c.hrm.roster.service.ScheduleRules.HolidayInfo;
import com.is2c.hrm.roster.service.ScheduleRules.Rotation;
import com.is2c.hrm.shift.api.ShiftDefinition;
import com.is2c.hrm.shift.domain.RotationAssignment;
import com.is2c.hrm.shift.domain.RotationPattern;
import com.is2c.hrm.shift.service.RotationService;
import com.is2c.hrm.shift.service.ShiftService;

/** Batch schedule resolution for employees over a date range (FR-033). No data-scope checks: callers do that. */
@Service
public class ScheduleResolver {

    private final ObjectProvider<PublishedRosterEntries> rosterEntries;
    private final RotationService rotations;
    private final ShiftService shifts;
    private final HolidayService holidays;
    private final OrgDirectory org;

    public ScheduleResolver(ObjectProvider<PublishedRosterEntries> rosterEntries, RotationService rotations,
            ShiftService shifts, HolidayService holidays, OrgDirectory org) {
        this.rosterEntries = rosterEntries;
        this.rotations = rotations;
        this.shifts = shifts;
        this.holidays = holidays;
        this.org = org;
    }

    /** Schedules keyed by employee id, each ordered by date. */
    @Transactional(readOnly = true)
    public Map<UUID, List<DaySchedule>> resolve(Collection<Employee> employees, LocalDate from, LocalDate to) {
        Map<UUID, List<DaySchedule>> result = new LinkedHashMap<>();
        if (employees.isEmpty()) {
            return result;
        }
        Set<UUID> employeeIds = employees.stream().map(Employee::getId).collect(Collectors.toSet());
        Set<UUID> companyIds = employees.stream().map(Employee::getCompanyId).collect(Collectors.toSet());

        Map<PublishedRosterEntries.Key, RosterEntryValue> roster = new HashMap<>();
        rosterEntries.ifAvailable(r -> roster.putAll(r.find(employeeIds, from, to)));

        List<RotationAssignment> assignments = rotations.activeBetween(employeeIds, from, to);
        Map<UUID, RotationPattern> patterns = rotations.all(assignments.stream().map(RotationAssignment::getPatternId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(RotationPattern::getId, p -> p));
        Map<UUID, List<Rotation>> rotationsByEmployee = new HashMap<>();
        for (RotationAssignment a : assignments) {
            rotationsByEmployee.computeIfAbsent(a.getEmployeeId(), k -> new ArrayList<>()).add(
                    new Rotation(a.getStartDate(), a.getEndDate(), patterns.get(a.getPatternId()).getDays()));
        }

        Set<UUID> shiftIds = new HashSet<>();
        employees.stream().map(Employee::getDefaultShiftId).filter(Objects::nonNull).forEach(shiftIds::add);
        roster.values().stream().map(RosterEntryValue::shiftId).filter(Objects::nonNull).forEach(shiftIds::add);
        patterns.values().forEach(p -> p.getDays().stream().filter(Objects::nonNull).forEach(shiftIds::add));
        Map<UUID, ShiftDefinition> definitions = shifts.definitions(shiftIds);

        Map<UUID, Map<LocalDate, List<HolidayInfo>>> holidaysByCompany = new HashMap<>();
        for (Holiday h : holidays.between(companyIds, from, to)) {
            holidaysByCompany.computeIfAbsent(h.getCompanyId(), k -> new HashMap<>())
                    .computeIfAbsent(h.getDate(), k -> new ArrayList<>())
                    .add(new HolidayInfo(h.getName(), h.isAllBranches(), Set.copyOf(h.getBranchIds())));
        }
        Map<UUID, Set<DayOfWeek>> companyWeeklyOff = new HashMap<>();
        companyIds.forEach(c -> companyWeeklyOff.put(c, org.settings(c).weeklyOffDays()));

        for (Employee e : employees) {
            EmployeeInfo info = new EmployeeInfo(e.getId(), e.getCompanyId(), e.getBranchId(), e.getDefaultShiftId(),
                    e.getWeeklyOffDays());
            List<Rotation> empRotations = rotationsByEmployee.getOrDefault(e.getId(), List.of());
            Map<LocalDate, List<HolidayInfo>> companyHolidays = holidaysByCompany.getOrDefault(e.getCompanyId(), Map.of());
            List<DaySchedule> days = new ArrayList<>();
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                days.add(ScheduleRules.resolve(info, d, roster.get(new PublishedRosterEntries.Key(e.getId(), d)),
                        empRotations, companyHolidays.getOrDefault(d, List.of()), companyWeeklyOff.get(e.getCompanyId()),
                        definitions));
            }
            result.put(e.getId(), days);
        }
        return result;
    }
}
