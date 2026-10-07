package com.is2c.hrm.attendance.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.is2c.hrm.attendance.api.EventDirection;
import com.is2c.hrm.roster.api.DaySchedule;
import com.is2c.hrm.roster.api.DaySchedule.DayType;
import com.is2c.hrm.shift.api.OtPolicyDefinition;
import com.is2c.hrm.shift.api.ShiftDefinition;
import com.is2c.hrm.shift.api.ShiftDefinition.BreakMode;
import com.is2c.hrm.shift.api.ShiftDefinition.TimedSession;

/**
 * Pure daily attendance calculation (FR-048, design §7.2). Times are company-local. No I/O; deterministic.
 */
public final class AttendanceCalculator {

    static final BigDecimal HALF = new BigDecimal("0.5");

    private AttendanceCalculator() {
    }

    public enum DayStatus {
        PRESENT, ABSENT, HALF_DAY, ON_LEAVE, HALF_DAY_LEAVE, WEEKLY_OFF, HOLIDAY, INCOMPLETE, UNSCHEDULED
    }

    public record Punch(LocalDateTime time, EventDirection direction) {
    }

    /** Approved leave on the date: fraction 0, 0.5 or 1; part MORNING/AFTERNOON for half days. */
    public record LeaveInfo(BigDecimal fraction, String part, boolean paid, UUID requestId) {
        public static final LeaveInfo NONE = new LeaveInfo(BigDecimal.ZERO, null, true, null);

        boolean full() {
            return fraction.compareTo(BigDecimal.ONE) >= 0;
        }

        boolean half() {
            return fraction.signum() > 0 && !full();
        }
    }

    public record CalcInput(LocalDate date, DaySchedule yesterday, DaySchedule today, DaySchedule tomorrow,
            List<Punch> punches, boolean override, LocalDateTime overrideIn, LocalDateTime overrideOut,
            LeaveInfo leave, boolean otEligible, OtPolicyDefinition fallbackOtPolicy, int defaultWindowBefore,
            int defaultWindowAfter) {
    }

    public record Result(UUID shiftId, DayType dayType, DayStatus status, LocalDateTime firstIn,
            LocalDateTime lastOut, int scheduledMinutes, int workedMinutes, int lateMinutes, int earlyMinutes,
            int shortMinutes, int otWorkdayMinutes, int otOffdayMinutes, int otHolidayMinutes, int extraMinutes,
            boolean otRequiresApproval, BigDecimal absentFraction, BigDecimal leaveFraction, boolean leavePaid,
            UUID leaveRequestId, boolean manual, List<String> exceptions) {
    }

    record Pair(LocalDateTime start, LocalDateTime end) {
        long minutes() {
            return Duration.between(start, end).toMinutes();
        }
    }

    public static Result calculate(CalcInput in) {
        DaySchedule today = in.today();
        ShiftDefinition shift = today.shift();
        LeaveInfo leave = in.leave() == null ? LeaveInfo.NONE : in.leave();

        // 1-3: punches of this date → pairs (or the manual override pair)
        List<Pair> pairs = new ArrayList<>();
        boolean incomplete;
        LocalDateTime openIn = null;
        if (in.override()) {
            incomplete = in.overrideIn() == null || in.overrideOut() == null || !in.overrideOut().isAfter(in.overrideIn());
            if (!incomplete) {
                pairs.add(new Pair(in.overrideIn(), in.overrideOut()));
            }
        } else {
            List<Punch> mine = in.punches().stream().filter(p -> owner(p.time(), in).equals(in.date()))
                    .sorted(Comparator.comparing(Punch::time)).toList();
            openIn = pair(mine, pairs);
            incomplete = openIn != null;
        }
        boolean hasPunches = !pairs.isEmpty() || incomplete;
        // an unclosed IN still counts as the day's first IN (and for lateness), never for worked time or early
        LocalDateTime firstIn = pairs.isEmpty() ? openIn
                : openIn != null && openIn.isBefore(pairs.get(0).start()) ? openIn : pairs.get(0).start();
        List<Pair> arrivals = new ArrayList<>(pairs);
        if (openIn != null) {
            arrivals.add(new Pair(openIn, openIn));
        }
        LocalDateTime lastOut = pairs.stream().map(Pair::end).max(Comparator.naturalOrder()).orElse(null);

        // 4: worked minutes with break deduction
        int gross = (int) pairs.stream().mapToLong(Pair::minutes).sum();
        int worked = gross;
        if (shift != null && shift.breakMode() == BreakMode.FIXED && gross >= shift.breakThresholdMinutes()) {
            worked = Math.max(0, gross - shift.breakMinutes());
        }

        boolean workday = today.dayType() == DayType.WORKDAY && shift != null;
        boolean ignoreLate = leave.half() && "MORNING".equals(leave.part());
        boolean ignoreEarly = leave.half() && "AFTERNOON".equals(leave.part());

        // 5-6: late/early per session, scheduled and short
        int late = 0;
        int early = 0;
        int scheduled = 0;
        if (workday && !leave.full()) {
            scheduled = shift.scheduledMinutes();
            if (leave.half()) {
                scheduled = scheduled / 2;
            }
            if (!shift.isFlexible() && !arrivals.isEmpty()) {
                List<TimedSession> sessions = shift.sessionsOn(in.date());
                for (int i = 0; i < sessions.size(); i++) {
                    TimedSession s = sessions.get(i);
                    LocalDateTime segStart = i == 0 ? LocalDateTime.MIN : midpoint(sessions.get(i - 1).end(), s.start());
                    LocalDateTime segEnd = i == sessions.size() - 1 ? LocalDateTime.MAX
                            : midpoint(s.end(), sessions.get(i + 1).start());
                    List<Pair> segArrivals = arrivals.stream()
                            .filter(p -> !p.start().isBefore(segStart) && p.start().isBefore(segEnd))
                            .sorted(Comparator.comparing(Pair::start)).toList();
                    if (segArrivals.isEmpty()) {
                        continue;
                    }
                    LocalDateTime segIn = segArrivals.get(0).start();
                    if (!ignoreLate && segIn.isAfter(s.start().plusMinutes(shift.lateGraceMinutes()))) {
                        late += (int) Duration.between(s.start(), segIn).toMinutes();
                    }
                    LocalDateTime segOut = pairs.stream()
                            .filter(p -> !p.start().isBefore(segStart) && p.start().isBefore(segEnd))
                            .map(Pair::end).max(Comparator.naturalOrder()).orElse(null);
                    if (segOut != null && !ignoreEarly && segOut.isBefore(s.end().minusMinutes(shift.earlyGraceMinutes()))) {
                        early += (int) Duration.between(segOut, s.end()).toMinutes();
                    }
                }
            }
        }

        // 8: status and absent fraction
        DayStatus status;
        BigDecimal absent = BigDecimal.ZERO;
        if (leave.full()) {
            status = DayStatus.ON_LEAVE;
        } else if (today.dayType() == DayType.WORKDAY && shift == null) {
            status = DayStatus.UNSCHEDULED;
        } else if (!hasPunches) {
            if (today.dayType() == DayType.HOLIDAY) {
                status = DayStatus.HOLIDAY;
            } else if (today.dayType() == DayType.WEEKLY_OFF) {
                status = DayStatus.WEEKLY_OFF;
            } else if (leave.half()) {
                status = DayStatus.HALF_DAY_LEAVE;
                absent = HALF;
            } else {
                status = DayStatus.ABSENT;
                absent = BigDecimal.ONE;
            }
        } else if (incomplete) {
            status = DayStatus.INCOMPLETE;
        } else if (leave.half()) {
            status = DayStatus.HALF_DAY_LEAVE;
        } else if (workday && shift.halfDayThresholdMinutes() != null && worked < shift.halfDayThresholdMinutes()) {
            status = DayStatus.HALF_DAY;
            absent = HALF;
        } else {
            status = DayStatus.PRESENT;
        }
        int shortMin = workday && !leave.full() && status != DayStatus.ABSENT && status != DayStatus.HALF_DAY
                && hasPunches ? Math.max(0, scheduled - worked) : 0;

        // 7: overtime / extra hours
        int raw = 0;
        if (!pairs.isEmpty()) {
            if (today.dayType() == DayType.WEEKLY_OFF || today.dayType() == DayType.HOLIDAY) {
                raw = worked;
            } else if (workday && shift.isFlexible()) {
                raw = Math.max(0, worked - (leave.half() ? scheduled : shift.scheduledMinutes()));
            } else if (workday) {
                List<TimedSession> sessions = shift.sessionsOn(in.date());
                LocalDateTime shiftStart = sessions.get(0).start();
                LocalDateTime shiftEnd = sessions.get(sessions.size() - 1).end();
                long post = Math.max(0, Duration.between(shiftEnd, lastOut).toMinutes());
                OtPolicyDefinition policy = policy(shift, in);
                long pre = policy != null && policy.allowPreShiftOt()
                        ? Math.max(0, Duration.between(firstIn, shiftStart).toMinutes()) : 0;
                raw = (int) (post + pre);
            }
        }
        OtPolicyDefinition policy = policy(shift, in);
        int ot = 0;
        int extra = 0;
        if (policy != null && raw > 0) {
            if (in.otEligible()) {
                ot = policy.apply(raw);
            } else {
                extra = raw;
            }
        }
        int otWorkday = today.dayType() == DayType.WORKDAY ? ot : 0;
        int otOffday = today.dayType() == DayType.WEEKLY_OFF ? ot : 0;
        int otHoliday = today.dayType() == DayType.HOLIDAY ? ot : 0;
        boolean otPending = ot > 0 && policy.requiresApproval();

        // 9: exception flags
        List<String> exceptions = new ArrayList<>();
        if (status == DayStatus.INCOMPLETE) {
            exceptions.add("INCOMPLETE");
        }
        if (status == DayStatus.ABSENT) {
            exceptions.add("ABSENT");
        }
        if (late > 0) {
            exceptions.add("LATE");
        }
        if (early > 0) {
            exceptions.add("EARLY");
        }
        if (status == DayStatus.UNSCHEDULED) {
            exceptions.add("UNSCHEDULED");
        }
        if (otPending) {
            exceptions.add("OT_PENDING");
        }

        return new Result(shift == null ? null : shift.id(), today.dayType(), status, firstIn, lastOut, scheduled,
                worked, late, early, shortMin, otWorkday, otOffday, otHoliday, extra, otPending, absent,
                leave.fraction(), leave.paid(), leave.requestId(), in.override(), List.copyOf(exceptions));
    }

    private static OtPolicyDefinition policy(ShiftDefinition shift, CalcInput in) {
        if (shift != null && shift.otPolicy() != null) {
            return shift.otPolicy();
        }
        return in.fallbackOtPolicy();
    }

    /** Pairs punches; returns the start of a pair left open (incomplete), or null. */
    static LocalDateTime pair(List<Punch> punches, List<Pair> out) {
        boolean anyDirection = punches.stream().anyMatch(p -> p.direction() != EventDirection.UNKNOWN);
        LocalDateTime open = null;
        for (Punch p : punches) {
            EventDirection d = p.direction();
            boolean isIn = anyDirection ? (d == EventDirection.IN || (d == EventDirection.UNKNOWN && open == null))
                    : open == null;
            if (isIn) {
                if (open == null) {
                    open = p.time();
                }
                // consecutive IN: keep the first
            } else if (open != null) {
                if (p.time().isAfter(open)) {
                    out.add(new Pair(open, p.time()));
                }
                open = null;
            }
            // OUT without an open pair: stray, ignored
        }
        return open;
    }

    /**
     * The date that owns a punch: among the neighbouring dates whose punch window contains it, dates with a shift
     * win over dates without; between shifted dates the one whose shift span is nearest wins (ties → earlier date).
     */
    static LocalDate owner(LocalDateTime t, CalcInput in) {
        LocalDate best = null;
        long bestDistance = Long.MAX_VALUE;
        boolean bestHasShift = false;
        for (DaySchedule d : List.of(in.yesterday(), in.today(), in.tomorrow())) {
            if (d == null) {
                continue;
            }
            ShiftDefinition s = d.shift();
            if (s == null) {
                LocalDateTime from = d.date().atStartOfDay();
                if (!t.isBefore(from) && t.isBefore(from.plusDays(1)) && best == null) {
                    best = d.date();
                }
                continue;
            }
            List<TimedSession> sessions = s.sessionsOn(d.date());
            LocalDateTime start = sessions.get(0).start();
            LocalDateTime end = sessions.get(sessions.size() - 1).end();
            int before = s.windowBeforeMinutes() != null ? s.windowBeforeMinutes() : in.defaultWindowBefore();
            int after = s.windowAfterMinutes() != null ? s.windowAfterMinutes() : in.defaultWindowAfter();
            if (t.isBefore(start.minusMinutes(before)) || t.isAfter(end.plusMinutes(after))) {
                continue;
            }
            long distance = t.isBefore(start) ? Duration.between(t, start).toMinutes()
                    : t.isAfter(end) ? Duration.between(end, t).toMinutes() : 0;
            if (!bestHasShift || distance < bestDistance) {
                best = d.date();
                bestDistance = distance;
                bestHasShift = true;
            }
        }
        return best == null ? LocalDate.MIN : best;
    }

    private static LocalDateTime midpoint(LocalDateTime a, LocalDateTime b) {
        return a.plusSeconds(Duration.between(a, b).getSeconds() / 2);
    }
}
