package com.is2c.hrm.attendance.adapter;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/** Parses ISO-8601 timestamps with an offset (absolute) or without (local to the company). */
final class Timestamps {

    record Parsed(Instant instant, LocalDateTime local) {
    }

    private Timestamps() {
    }

    static Parsed parse(String value) {
        String v = value.trim().replace(' ', 'T');
        try {
            return new Parsed(OffsetDateTime.parse(v).toInstant(), null);
        } catch (DateTimeParseException ignored) {
            // not an offset timestamp
        }
        try {
            return new Parsed(Instant.parse(v), null);
        } catch (DateTimeParseException ignored) {
            // not an instant
        }
        return new Parsed(null, LocalDateTime.parse(v));
    }
}
