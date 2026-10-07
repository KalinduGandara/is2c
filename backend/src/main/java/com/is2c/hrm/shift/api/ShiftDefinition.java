package com.is2c.hrm.shift.api;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Immutable timing rules of a shift (FR-030), independent of persistence so attendance calculation can be
 * unit-tested. Session ends at or before their start time run into the next day.
 */
public record ShiftDefinition(
        UUID id,
        String code,
        ShiftType type,
        List<Session> sessions,
        LocalTime flexEarliestStart,
        LocalTime flexLatestEnd,
        Integer requiredMinutes,
        int lateGraceMinutes,
        int earlyGraceMinutes,
        BreakMode breakMode,
        int breakMinutes,
        int breakThresholdMinutes,
        Integer halfDayThresholdMinutes,
        OtPolicyDefinition otPolicy,
        Integer windowBeforeMinutes,
        Integer windowAfterMinutes) {

    public enum ShiftType { FIXED, FLEXIBLE, NIGHT, SPLIT, CUSTOM }

    public enum BreakMode { FIXED, PUNCHED }

    public record Session(LocalTime start, LocalTime end) {

        public boolean crossesMidnight() {
            return !end.isAfter(start);
        }

        public int minutes() {
            long m = Duration.between(start, end).toMinutes();
            return (int) (m <= 0 ? m + 24 * 60 : m);
        }
    }

    /** A session placed on a calendar date. */
    public record TimedSession(LocalDateTime start, LocalDateTime end) {

        public long minutes() {
            return Duration.between(start, end).toMinutes();
        }
    }

    public boolean isFlexible() {
        return type == ShiftType.FLEXIBLE;
    }

    /** Sessions of this shift when worked on the given date. FLEXIBLE shifts span their allowed window. */
    public List<TimedSession> sessionsOn(LocalDate date) {
        List<Session> source = isFlexible() ? List.of(new Session(flexEarliestStart, flexLatestEnd)) : sessions;
        List<TimedSession> result = new ArrayList<>();
        for (Session s : source) {
            LocalDateTime start = date.atTime(s.start());
            LocalDateTime end = s.crossesMidnight() ? date.plusDays(1).atTime(s.end()) : date.atTime(s.end());
            result.add(new TimedSession(start, end));
        }
        return result;
    }

    /** Expected working minutes after any fixed break (FLEXIBLE: required minutes). */
    public int scheduledMinutes() {
        if (isFlexible()) {
            return requiredMinutes == null ? 0 : requiredMinutes;
        }
        int total = sessions.stream().mapToInt(Session::minutes).sum();
        return breakMode == BreakMode.FIXED ? Math.max(0, total - breakMinutes) : total;
    }
}
