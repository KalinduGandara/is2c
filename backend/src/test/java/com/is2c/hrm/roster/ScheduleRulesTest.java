package com.is2c.hrm.roster;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.is2c.hrm.roster.api.DaySchedule;
import com.is2c.hrm.roster.api.DaySchedule.DayType;
import com.is2c.hrm.roster.api.DaySchedule.Source;
import com.is2c.hrm.roster.api.RosterEntryValue;
import com.is2c.hrm.roster.service.ScheduleRules;
import com.is2c.hrm.roster.service.ScheduleRules.EmployeeInfo;
import com.is2c.hrm.roster.service.ScheduleRules.HolidayInfo;
import com.is2c.hrm.roster.service.ScheduleRules.Rotation;
import com.is2c.hrm.shift.api.ShiftDefinition;
import com.is2c.hrm.shift.api.ShiftDefinition.BreakMode;
import com.is2c.hrm.shift.api.ShiftDefinition.Session;
import com.is2c.hrm.shift.api.ShiftDefinition.ShiftType;

class ScheduleRulesTest {

    static final UUID DAY = UUID.randomUUID();
    static final UUID NIGHT = UUID.randomUUID();
    static final UUID BRANCH = UUID.randomUUID();
    static final Map<UUID, ShiftDefinition> SHIFTS = Map.of(
            DAY, def(DAY, "DAY", ShiftType.FIXED, 8, 17),
            NIGHT, def(NIGHT, "NIGHT", ShiftType.NIGHT, 22, 6));
    static final Set<DayOfWeek> SUNDAY_OFF = Set.of(DayOfWeek.SUNDAY);
    // 2026-03-16 is a Monday; 2026-03-22 a Sunday.
    static final LocalDate MONDAY = LocalDate.of(2026, 3, 16);
    static final LocalDate SUNDAY = LocalDate.of(2026, 3, 22);

    static ShiftDefinition def(UUID id, String code, ShiftType type, int from, int to) {
        return new ShiftDefinition(id, code, type, List.of(new Session(LocalTime.of(from, 0), LocalTime.of(to, 0))),
                null, null, null, 0, 0, BreakMode.FIXED, 0, 0, null, null, null, null);
    }

    static EmployeeInfo employee(Set<DayOfWeek> weeklyOff) {
        return new EmployeeInfo(UUID.randomUUID(), UUID.randomUUID(), BRANCH, DAY, weeklyOff);
    }

    static DaySchedule resolve(EmployeeInfo e, LocalDate date, RosterEntryValue roster, List<Rotation> rotations,
            List<HolidayInfo> holidays) {
        return ScheduleRules.resolve(e, date, roster, rotations, holidays, SUNDAY_OFF, SHIFTS);
    }

    @Test
    @DisplayName("FR-033 default: employee default shift on workdays, company weekly-off applies")
    void defaults() {
        DaySchedule mon = resolve(employee(null), MONDAY, null, List.of(), List.of());
        assertThat(mon.dayType()).isEqualTo(DayType.WORKDAY);
        assertThat(mon.shift().code()).isEqualTo("DAY");
        assertThat(mon.source()).isEqualTo(Source.DEFAULT);
        DaySchedule sun = resolve(employee(null), SUNDAY, null, List.of(), List.of());
        assertThat(sun.dayType()).isEqualTo(DayType.WEEKLY_OFF);
        assertThat(sun.shift()).isNull();
    }

    @Test
    @DisplayName("FR-033 employee weekly-off days override the company default")
    void employeeWeeklyOff() {
        EmployeeInfo e = employee(Set.of(DayOfWeek.MONDAY));
        assertThat(resolve(e, MONDAY, null, List.of(), List.of()).dayType()).isEqualTo(DayType.WEEKLY_OFF);
        assertThat(resolve(e, SUNDAY, null, List.of(), List.of()).dayType()).isEqualTo(DayType.WORKDAY);
    }

    @Test
    @DisplayName("FR-033 rotation overrides the default; OFF days in the cycle are weekly-off")
    void rotation() {
        Rotation r = new Rotation(MONDAY, null, Arrays.asList(NIGHT, NIGHT, null));
        EmployeeInfo e = employee(null);
        DaySchedule d0 = resolve(e, MONDAY, null, List.of(r), List.of());
        assertThat(d0.source()).isEqualTo(Source.ROTATION);
        assertThat(d0.shift().code()).isEqualTo("NIGHT");
        assertThat(resolve(e, MONDAY.plusDays(2), null, List.of(r), List.of()).dayType()).isEqualTo(DayType.WEEKLY_OFF);
        assertThat(resolve(e, MONDAY.plusDays(3), null, List.of(r), List.of()).shift().code()).isEqualTo("NIGHT");
        // Sunday covered by the rotation is a working day (cycle day 7 % 3 = 0 → NIGHT).
        assertThat(resolve(e, SUNDAY.plusDays(1), null, List.of(r), List.of()).dayType()).isEqualTo(DayType.WORKDAY);
        // Before the rotation starts the default applies.
        assertThat(resolve(e, MONDAY.minusDays(1), null, List.of(r), List.of()).source()).isEqualTo(Source.DEFAULT);
    }

    @Test
    @DisplayName("FR-033 a published roster entry has the highest precedence (shift or OFF)")
    void rosterWins() {
        Rotation r = new Rotation(MONDAY, null, List.of(NIGHT));
        EmployeeInfo e = employee(null);
        DaySchedule rostered = resolve(e, MONDAY, new RosterEntryValue(DAY), List.of(r), List.of());
        assertThat(rostered.source()).isEqualTo(Source.ROSTER);
        assertThat(rostered.shift().code()).isEqualTo("DAY");
        DaySchedule off = resolve(e, MONDAY, new RosterEntryValue(null), List.of(r), List.of());
        assertThat(off.dayType()).isEqualTo(DayType.WEEKLY_OFF);
        assertThat(resolve(e, SUNDAY, new RosterEntryValue(DAY), List.of(), List.of()).dayType()).isEqualTo(DayType.WORKDAY);
    }

    @Test
    @DisplayName("FR-033 holidays for the employee's branch make the day a HOLIDAY but keep the shift")
    void holidays() {
        EmployeeInfo e = employee(null);
        DaySchedule h = resolve(e, MONDAY, null, List.of(), List.of(new HolidayInfo("Poya", true, Set.of())));
        assertThat(h.dayType()).isEqualTo(DayType.HOLIDAY);
        assertThat(h.holidayName()).isEqualTo("Poya");
        assertThat(h.shift().code()).isEqualTo("DAY");
        DaySchedule otherBranch = resolve(e, MONDAY, null, List.of(),
                List.of(new HolidayInfo("Kandy only", false, Set.of(UUID.randomUUID()))));
        assertThat(otherBranch.dayType()).isEqualTo(DayType.WORKDAY);
        DaySchedule thisBranch = resolve(e, MONDAY, null, List.of(), List.of(new HolidayInfo("CMB", false, Set.of(BRANCH))));
        assertThat(thisBranch.dayType()).isEqualTo(DayType.HOLIDAY);
    }

    @Test
    @DisplayName("FR-033 workdays without any shift are unscheduled")
    void unscheduled() {
        EmployeeInfo e = new EmployeeInfo(UUID.randomUUID(), UUID.randomUUID(), BRANCH, null, null);
        DaySchedule d = resolve(e, MONDAY, null, List.of(), List.of());
        assertThat(d.isUnscheduledWorkday()).isTrue();
    }
}
