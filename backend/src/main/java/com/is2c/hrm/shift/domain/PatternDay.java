package com.is2c.hrm.shift.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** One day of a rotation cycle. The explicit flag keeps OFF days persistable (no all-null embeddables). */
@Embeddable
public record PatternDay(@Column(name = "off_day", nullable = false) boolean off,
        @Column(name = "shift_id") UUID shiftId) {

    public static PatternDay of(UUID shiftId) {
        return new PatternDay(shiftId == null, shiftId);
    }
}
