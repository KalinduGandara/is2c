package com.is2c.hrm.attendance.adapter;

import java.time.Instant;
import java.time.LocalDateTime;

import com.is2c.hrm.attendance.api.EventDirection;

/**
 * Standard attendance event produced by an adapter (FR-047). Exactly one of {@code instant} (timestamp with offset)
 * or {@code localTime} (to be interpreted in the company time zone) is set. {@code error} marks a rejected row.
 */
public record ParsedEvent(int row, String externalId, String deviceId, String employeeIdentifier, Instant instant,
        LocalDateTime localTime, EventDirection direction, String error) {

    public static ParsedEvent rejected(int row, String error) {
        return new ParsedEvent(row, null, null, null, null, null, null, error);
    }

    public boolean isRejected() {
        return error != null;
    }
}
