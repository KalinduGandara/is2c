package com.is2c.hrm.roster.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.is2c.hrm.roster.api.DaySchedule;
import com.is2c.hrm.roster.api.DaySchedule.DayType;
import com.is2c.hrm.roster.api.DaySchedule.Source;
import com.is2c.hrm.roster.api.RosterEntryValue;
import com.is2c.hrm.shift.api.ShiftDefinition;

/**
 * Pure schedule resolution (FR-033). Precedence: published roster entry, then active rotation assignment, then
 * the employee's default shift and weekly-off days (falling back to the company's). A holiday that applies to the
 * employee's branch turns the day type into HOLIDAY while keeping any shift for overtime purposes.
 */
public final class ScheduleRules {

    private ScheduleRules() {
    }

    public record EmployeeInfo(UUID employeeId, UUID companyId, UUID branchId, UUID defaultShiftId,
            Set<DayOfWeek> weeklyOffDays) {
    }

    public record Rotation(LocalDate startDate, LocalDate endDate, List<UUID> days) {

        boolean covers(LocalDate date) {
            return !date.isBefore(startDate) && (endDate == null || !date.isAfter(endDate));
        }

        UUID shiftOn(LocalDate date) {
            return days.get((int) Math.floorMod(ChronoUnit.DAYS.between(startDate, date), days.size()));
        }
    }

    public record HolidayInfo(String name, boolean allBranches, Set<UUID> branchIds) {

        boolean appliesTo(UUID branchId) {
            return allBranches || branchIds.contains(branchId);
        }
    }

    public static DaySchedule resolve(EmployeeInfo e, LocalDate date, RosterEntryValue rosterEntry,
            List<Rotation> rotations, List<HolidayInfo> holidaysOnDate, Set<DayOfWeek> companyWeeklyOff,
            Map<UUID, ShiftDefinition> shifts) {
        Source source;
        UUID shiftId;
        boolean off;
        if (rosterEntry != null) {
            source = Source.ROSTER;
            shiftId = rosterEntry.shiftId();
            off = rosterEntry.isOff();
        } else {
            Rotation rotation = rotations.stream().filter(r -> r.covers(date)).findFirst().orElse(null);
            if (rotation != null) {
                source = Source.ROTATION;
                shiftId = rotation.shiftOn(date);
                off = shiftId == null;
            } else {
                source = Source.DEFAULT;
                Set<DayOfWeek> weeklyOff = e.weeklyOffDays() != null ? e.weeklyOffDays() : companyWeeklyOff;
                off = weeklyOff.contains(date.getDayOfWeek());
                shiftId = off ? null : e.defaultShiftId();
            }
        }
        ShiftDefinition shift = shiftId == null ? null : shifts.get(shiftId);
        HolidayInfo holiday = holidaysOnDate.stream().filter(h -> h.appliesTo(e.branchId())).findFirst().orElse(null);
        DayType type = holiday != null ? DayType.HOLIDAY : off ? DayType.WEEKLY_OFF : DayType.WORKDAY;
        return new DaySchedule(e.employeeId(), date, type, shift, source, holiday == null ? null : holiday.name());
    }
}
