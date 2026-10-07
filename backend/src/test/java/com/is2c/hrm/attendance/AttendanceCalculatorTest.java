package com.is2c.hrm.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.is2c.hrm.attendance.api.EventDirection;
import com.is2c.hrm.attendance.service.AttendanceCalculator;
import com.is2c.hrm.attendance.service.AttendanceCalculator.CalcInput;
import com.is2c.hrm.attendance.service.AttendanceCalculator.DayStatus;
import com.is2c.hrm.attendance.service.AttendanceCalculator.LeaveInfo;
import com.is2c.hrm.attendance.service.AttendanceCalculator.Punch;
import com.is2c.hrm.attendance.service.AttendanceCalculator.Result;
import com.is2c.hrm.roster.api.DaySchedule;
import com.is2c.hrm.roster.api.DaySchedule.DayType;
import com.is2c.hrm.roster.api.DaySchedule.Source;
import com.is2c.hrm.shift.api.OtPolicyDefinition;
import com.is2c.hrm.shift.api.ShiftDefinition;
import com.is2c.hrm.shift.api.ShiftDefinition.BreakMode;
import com.is2c.hrm.shift.api.ShiftDefinition.Session;
import com.is2c.hrm.shift.api.ShiftDefinition.ShiftType;

/** FR-048 daily attendance rules. Monday 2026-03-16 is the day under test unless stated. */
class AttendanceCalculatorTest {

    static final LocalDate D = LocalDate.of(2026, 3, 16);
    static final OtPolicyDefinition OT = new OtPolicyDefinition(UUID.randomUUID(), 30, 15, false, null, false);
    static final OtPolicyDefinition OT_APPROVAL = new OtPolicyDefinition(UUID.randomUUID(), 30, 15, false, null, true);
    static final OtPolicyDefinition OT_PRE = new OtPolicyDefinition(UUID.randomUUID(), 30, 15, true, null, false);

    // 08:30-17:30 with a fixed 60-minute break when worked >= 300 minutes; 10 minutes grace each side.
    static final ShiftDefinition DAY = shift(ShiftType.FIXED, List.of(s(8, 30, 17, 30)), BreakMode.FIXED, 60, 300, 10, 10, null, OT);
    static final ShiftDefinition NIGHT = shift(ShiftType.NIGHT, List.of(s(22, 0, 6, 0)), BreakMode.FIXED, 30, 300, 0, 0, null, OT);
    static final ShiftDefinition SPLIT = shift(ShiftType.SPLIT, List.of(s(7, 0, 11, 0), s(16, 0, 20, 0)), BreakMode.PUNCHED, 0, 0, 5, 5, null, OT);

    static Session s(int h1, int m1, int h2, int m2) {
        return new Session(LocalTime.of(h1, m1), LocalTime.of(h2, m2));
    }

    static ShiftDefinition shift(ShiftType type, List<Session> sessions, BreakMode mode, int breakMin, int threshold,
            int lateGrace, int earlyGrace, Integer halfDay, OtPolicyDefinition ot) {
        return new ShiftDefinition(UUID.randomUUID(), type.name(), type, sessions, null, null, null, lateGrace,
                earlyGrace, mode, breakMin, threshold, halfDay, ot, null, null);
    }

    static ShiftDefinition flex(int required, OtPolicyDefinition ot) {
        return new ShiftDefinition(UUID.randomUUID(), "FLEX", ShiftType.FLEXIBLE, List.of(), LocalTime.of(7, 0),
                LocalTime.of(20, 0), required, 0, 0, BreakMode.FIXED, 60, 300, null, ot, null, null);
    }

    static DaySchedule day(LocalDate date, DayType type, ShiftDefinition shift) {
        return new DaySchedule(UUID.randomUUID(), date, type, shift, Source.DEFAULT, null);
    }

    static Punch p(LocalDate date, int h, int m, EventDirection dir) {
        return new Punch(LocalDateTime.of(date, LocalTime.of(h, m)), dir);
    }

    static Punch in(int h, int m) {
        return p(D, h, m, EventDirection.IN);
    }

    static Punch out(int h, int m) {
        return p(D, h, m, EventDirection.OUT);
    }

    /** Builder for inputs with sensible defaults: workday with the DAY shift on both neighbours. */
    static final class In {
        DaySchedule yesterday = day(D.minusDays(1), DayType.WORKDAY, DAY);
        DaySchedule today = day(D, DayType.WORKDAY, DAY);
        DaySchedule tomorrow = day(D.plusDays(1), DayType.WORKDAY, DAY);
        List<Punch> punches = new ArrayList<>();
        LocalDateTime overrideIn;
        LocalDateTime overrideOut;
        boolean override;
        LeaveInfo leave = LeaveInfo.NONE;
        boolean otEligible = true;
        OtPolicyDefinition fallbackOt = OT;

        In today(DayType type, ShiftDefinition shift) {
            today = day(D, type, shift);
            return this;
        }

        In punches(Punch... ps) {
            punches.addAll(List.of(ps));
            return this;
        }

        Result calc() {
            return AttendanceCalculator.calculate(new CalcInput(D, yesterday, today, tomorrow, punches,
                    override, overrideIn, overrideOut, leave, otEligible, fallbackOt, 240, 240));
        }
    }

    @Nested
    class StatusRules {

        @Test
        @DisplayName("present on time: worked minutes after fixed break, no late/early/short")
        void present() {
            Result r = new In().punches(in(8, 25), out(17, 35)).calc();
            assertThat(r.status()).isEqualTo(DayStatus.PRESENT);
            assertThat(r.firstIn()).isEqualTo(LocalDateTime.of(D, LocalTime.of(8, 25)));
            assertThat(r.lastOut()).isEqualTo(LocalDateTime.of(D, LocalTime.of(17, 35)));
            assertThat(r.workedMinutes()).isEqualTo(550 - 60);
            assertThat(r.scheduledMinutes()).isEqualTo(480);
            assertThat(r.lateMinutes()).isZero();
            assertThat(r.earlyMinutes()).isZero();
            assertThat(r.shortMinutes()).isZero();
            assertThat(r.absentFraction()).isEqualByComparingTo("0");
            assertThat(r.exceptions()).isEmpty();
        }

        @Test
        @DisplayName("no punches on a workday → ABSENT with absent fraction 1")
        void absent() {
            Result r = new In().calc();
            assertThat(r.status()).isEqualTo(DayStatus.ABSENT);
            assertThat(r.absentFraction()).isEqualByComparingTo("1");
            assertThat(r.shortMinutes()).isZero();
            assertThat(r.exceptions()).contains("ABSENT");
        }

        @Test
        @DisplayName("weekly-off and holiday without punches keep their day type as status")
        void offDays() {
            assertThat(new In().today(DayType.WEEKLY_OFF, null).calc().status()).isEqualTo(DayStatus.WEEKLY_OFF);
            Result holiday = new In().today(DayType.HOLIDAY, DAY).calc();
            assertThat(holiday.status()).isEqualTo(DayStatus.HOLIDAY);
            assertThat(holiday.absentFraction()).isEqualByComparingTo("0");
            assertThat(holiday.scheduledMinutes()).isZero();
        }

        @Test
        @DisplayName("an unmatched IN leaves the day INCOMPLETE (not absent) and flagged")
        void incomplete() {
            Result r = new In().punches(in(8, 30)).calc();
            assertThat(r.status()).isEqualTo(DayStatus.INCOMPLETE);
            assertThat(r.absentFraction()).isEqualByComparingTo("0");
            assertThat(r.exceptions()).contains("INCOMPLETE");
        }

        @Test
        @DisplayName("FR-048 an unmatched IN still records the first IN and lateness (no OUT, no early)")
        void incompleteKeepsFirstIn() {
            Result r = new In().punches(in(8, 45)).calc();
            assertThat(r.status()).isEqualTo(DayStatus.INCOMPLETE);
            assertThat(r.firstIn()).isEqualTo(LocalDateTime.of(D, java.time.LocalTime.of(8, 45)));
            assertThat(r.lastOut()).isNull();
            assertThat(r.lateMinutes()).isEqualTo(15);
            assertThat(r.earlyMinutes()).isZero();
            assertThat(r.exceptions()).contains("INCOMPLETE", "LATE");
        }

        @Test
        @DisplayName("worked below the half-day threshold → HALF_DAY, absent 0.5")
        void halfDay() {
            ShiftDefinition withHalf = shift(ShiftType.FIXED, List.of(s(8, 30, 17, 30)), BreakMode.FIXED, 60, 300, 10, 10, 240, OT);
            Result r = new In().today(DayType.WORKDAY, withHalf).punches(in(8, 30), out(11, 30)).calc();
            assertThat(r.status()).isEqualTo(DayStatus.HALF_DAY);
            assertThat(r.absentFraction()).isEqualByComparingTo("0.5");
        }

        @Test
        @DisplayName("a workday without any shift is UNSCHEDULED and flagged, never absent")
        void unscheduled() {
            Result r = new In().today(DayType.WORKDAY, null).calc();
            assertThat(r.status()).isEqualTo(DayStatus.UNSCHEDULED);
            assertThat(r.absentFraction()).isEqualByComparingTo("0");
            assertThat(r.exceptions()).contains("UNSCHEDULED");
        }
    }

    @Nested
    class LateEarlyShort {

        @Test
        @DisplayName("late beyond grace counts from shift start; within grace is forgiven")
        void late() {
            assertThat(new In().punches(in(8, 40), out(17, 30)).calc().lateMinutes()).isZero();
            Result r = new In().punches(in(8, 41), out(17, 30)).calc();
            assertThat(r.lateMinutes()).isEqualTo(11);
            assertThat(r.exceptions()).contains("LATE");
        }

        @Test
        @DisplayName("early departure beyond grace counts to shift end; short = scheduled - worked")
        void earlyAndShort() {
            Result r = new In().punches(in(8, 30), out(16, 30)).calc();
            assertThat(r.earlyMinutes()).isEqualTo(60);
            assertThat(r.workedMinutes()).isEqualTo(480 - 60);
            assertThat(r.shortMinutes()).isEqualTo(60);
            assertThat(r.exceptions()).contains("EARLY");
        }

        @Test
        @DisplayName("fixed break is not deducted below the threshold")
        void breakThreshold() {
            Result r = new In().punches(in(8, 30), out(12, 30)).calc();
            assertThat(r.workedMinutes()).isEqualTo(240);
        }
    }

    @Nested
    class MultiplePunches {

        @Test
        @DisplayName("several IN/OUT pairs are summed; punched breaks are not double-deducted")
        void pairs() {
            ShiftDefinition punched = shift(ShiftType.FIXED, List.of(s(8, 0, 17, 0)), BreakMode.PUNCHED, 60, 0, 0, 0, null, OT);
            Result r = new In().today(DayType.WORKDAY, punched)
                    .punches(in(8, 0), out(12, 0), in(13, 0), out(17, 0)).calc();
            assertThat(r.workedMinutes()).isEqualTo(480);
            assertThat(r.status()).isEqualTo(DayStatus.PRESENT);
        }

        @Test
        @DisplayName("duplicate INs keep the first; a stray OUT without IN is ignored")
        void duplicates() {
            Result r = new In().punches(out(7, 0), in(8, 30), in(8, 31), out(17, 30)).calc();
            assertThat(r.firstIn()).isEqualTo(LocalDateTime.of(D, LocalTime.of(8, 30)));
            assertThat(r.workedMinutes()).isEqualTo(540 - 60);
        }

        @Test
        @DisplayName("punches without direction alternate IN/OUT")
        void unknownDirections() {
            Result r = new In().punches(p(D, 8, 30, EventDirection.UNKNOWN), p(D, 12, 0, EventDirection.UNKNOWN),
                    p(D, 13, 0, EventDirection.UNKNOWN), p(D, 17, 30, EventDirection.UNKNOWN)).calc();
            assertThat(r.workedMinutes()).isEqualTo(210 + 270 - 60);
        }
    }

    @Nested
    class Overtime {

        @Test
        @DisplayName("workday OT counts after shift end, minimum 30, rounded down to 15")
        void workdayOt() {
            assertThat(new In().punches(in(8, 30), out(17, 59)).calc().otWorkdayMinutes()).isZero();
            Result r = new In().punches(in(8, 30), out(18, 44)).calc();
            assertThat(r.otWorkdayMinutes()).isEqualTo(60);
            assertThat(r.extraMinutes()).isZero();
        }

        @Test
        @DisplayName("pre-shift time counts only when the policy allows it")
        void preShift() {
            assertThat(new In().punches(in(7, 30), out(17, 30)).calc().otWorkdayMinutes()).isZero();
            ShiftDefinition withPre = shift(ShiftType.FIXED, List.of(s(8, 30, 17, 30)), BreakMode.FIXED, 60, 300, 10, 10, null, OT_PRE);
            assertThat(new In().today(DayType.WORKDAY, withPre).punches(in(7, 30), out(17, 30)).calc().otWorkdayMinutes())
                    .isEqualTo(60);
        }

        @Test
        @DisplayName("all work on weekly-off / holiday is OT of that day type (fallback policy when no shift)")
        void offDayOt() {
            Result off = new In().today(DayType.WEEKLY_OFF, null).punches(in(9, 0), out(13, 10)).calc();
            assertThat(off.status()).isEqualTo(DayStatus.PRESENT);
            assertThat(off.otOffdayMinutes()).isEqualTo(240);
            assertThat(off.otWorkdayMinutes()).isZero();
            Result holiday = new In().today(DayType.HOLIDAY, DAY).punches(in(8, 30), out(17, 30)).calc();
            assertThat(holiday.otHolidayMinutes()).isEqualTo(480);
        }

        @Test
        @DisplayName("employees not eligible for OT get extra minutes instead")
        void extraHours() {
            In input = new In().punches(in(8, 30), out(18, 44));
            input.otEligible = false;
            Result r = input.calc();
            assertThat(r.otWorkdayMinutes()).isZero();
            assertThat(r.extraMinutes()).isEqualTo(74);
        }

        @Test
        @DisplayName("no OT policy (shift or fallback) → no OT and no extra")
        void noPolicy() {
            ShiftDefinition noOt = shift(ShiftType.FIXED, List.of(s(8, 30, 17, 30)), BreakMode.FIXED, 60, 300, 10, 10, null, null);
            In input = new In().today(DayType.WORKDAY, noOt).punches(in(8, 30), out(20, 0));
            input.fallbackOt = null;
            Result r = input.calc();
            assertThat(r.otWorkdayMinutes()).isZero();
            assertThat(r.extraMinutes()).isZero();
        }

        @Test
        @DisplayName("OT under an approval policy is flagged pending")
        void approval() {
            ShiftDefinition approve = shift(ShiftType.FIXED, List.of(s(8, 30, 17, 30)), BreakMode.FIXED, 60, 300, 10, 10, null, OT_APPROVAL);
            Result r = new In().today(DayType.WORKDAY, approve).punches(in(8, 30), out(19, 0)).calc();
            assertThat(r.otRequiresApproval()).isTrue();
            assertThat(r.exceptions()).contains("OT_PENDING");
        }
    }

    @Nested
    class NightAndSplit {

        @Test
        @DisplayName("night shift punches after midnight belong to the start date (CL-C3)")
        void nightShift() {
            In input = new In().today(DayType.WORKDAY, NIGHT);
            input.tomorrow = day(D.plusDays(1), DayType.WORKDAY, NIGHT);
            input.punches(p(D, 21, 55, EventDirection.IN), p(D.plusDays(1), 6, 5, EventDirection.OUT));
            Result r = input.calc();
            assertThat(r.status()).isEqualTo(DayStatus.PRESENT);
            assertThat(r.workedMinutes()).isEqualTo(490 - 30);
            assertThat(r.lastOut()).isEqualTo(LocalDateTime.of(D.plusDays(1), LocalTime.of(6, 5)));
        }

        @Test
        @DisplayName("the morning OUT of last night's shift is not counted on today's day shift")
        void previousNightOutGoesToYesterday() {
            In input = new In();
            input.yesterday = day(D.minusDays(1), DayType.WORKDAY, NIGHT);
            input.punches(p(D, 6, 5, EventDirection.OUT), in(8, 25), out(17, 35));
            Result r = input.calc();
            assertThat(r.firstIn()).isEqualTo(LocalDateTime.of(D, LocalTime.of(8, 25)));
            assertThat(r.workedMinutes()).isEqualTo(490);
        }

        @Test
        @DisplayName("split shift: late and early are evaluated per session")
        void split() {
            Result r = new In().today(DayType.WORKDAY, SPLIT)
                    .punches(in(7, 20), out(11, 0), in(16, 0), out(19, 30)).calc();
            assertThat(r.lateMinutes()).isEqualTo(20);
            assertThat(r.earlyMinutes()).isEqualTo(30);
            assertThat(r.workedMinutes()).isEqualTo(220 + 210);
            assertThat(r.scheduledMinutes()).isEqualTo(480);
            assertThat(r.shortMinutes()).isEqualTo(50);
        }

        @Test
        @DisplayName("flexible shift: no late/early; short and OT relative to required minutes")
        void flexible() {
            ShiftDefinition f = flex(480, OT);
            Result shortDay = new In().today(DayType.WORKDAY, f).punches(in(10, 0), out(18, 0)).calc();
            assertThat(shortDay.lateMinutes()).isZero();
            assertThat(shortDay.workedMinutes()).isEqualTo(420);
            assertThat(shortDay.shortMinutes()).isEqualTo(60);
            Result longDay = new In().today(DayType.WORKDAY, f).punches(in(7, 0), out(17, 0)).calc();
            assertThat(longDay.otWorkdayMinutes()).isEqualTo(60);
        }
    }

    @Nested
    class LeaveAndOverride {

        @Test
        @DisplayName("full-day leave → ON_LEAVE, nothing scheduled, not absent")
        void fullLeave() {
            In input = new In();
            input.leave = new LeaveInfo(new java.math.BigDecimal("1"), null, true, UUID.randomUUID());
            Result r = input.calc();
            assertThat(r.status()).isEqualTo(DayStatus.ON_LEAVE);
            assertThat(r.absentFraction()).isEqualByComparingTo("0");
            assertThat(r.leaveFraction()).isEqualByComparingTo("1");
            assertThat(r.scheduledMinutes()).isZero();
        }

        @Test
        @DisplayName("morning half-day leave: arriving at noon is not late; afternoon leave: leaving at noon is not early")
        void halfLeave() {
            In morning = new In().punches(in(13, 0), out(17, 30));
            morning.leave = new LeaveInfo(new java.math.BigDecimal("0.5"), "MORNING", true, UUID.randomUUID());
            Result m = morning.calc();
            assertThat(m.status()).isEqualTo(DayStatus.HALF_DAY_LEAVE);
            assertThat(m.lateMinutes()).isZero();
            assertThat(m.scheduledMinutes()).isEqualTo(240);
            assertThat(m.shortMinutes()).isZero();

            In afternoon = new In().punches(in(8, 30), out(12, 30));
            afternoon.leave = new LeaveInfo(new java.math.BigDecimal("0.5"), "AFTERNOON", false, UUID.randomUUID());
            Result a = afternoon.calc();
            assertThat(a.earlyMinutes()).isZero();
            assertThat(a.leavePaid()).isFalse();
        }

        @Test
        @DisplayName("half-day leave with no punches is absent for the other half")
        void halfLeaveAbsent() {
            In input = new In();
            input.leave = new LeaveInfo(new java.math.BigDecimal("0.5"), "MORNING", true, UUID.randomUUID());
            Result r = input.calc();
            assertThat(r.status()).isEqualTo(DayStatus.HALF_DAY_LEAVE);
            assertThat(r.absentFraction()).isEqualByComparingTo("0.5");
        }

        @Test
        @DisplayName("an applied adjustment replaces punch-derived times (FR-050/051)")
        void override() {
            In input = new In().punches(in(9, 30), out(15, 0));
            input.override = true;
            input.overrideIn = LocalDateTime.of(D, LocalTime.of(8, 30));
            input.overrideOut = LocalDateTime.of(D, LocalTime.of(17, 30));
            Result r = input.calc();
            assertThat(r.lateMinutes()).isZero();
            assertThat(r.workedMinutes()).isEqualTo(480);
            assertThat(r.manual()).isTrue();
        }
    }

    @Test
    @DisplayName("FR-048 the calculation is deterministic")
    void deterministic() {
        In input = new In().punches(out(17, 35), in(8, 25));
        assertThat(input.calc()).isEqualTo(input.calc());
    }
}
