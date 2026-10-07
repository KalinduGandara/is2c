package com.is2c.hrm.shift.api;

import java.util.UUID;

/** Overtime rules applied by attendance calculation (FR-032). */
public record OtPolicyDefinition(UUID id, int minOtMinutes, int roundingMinutes, boolean allowPreShiftOt,
        Integer maxOtMinutes, boolean requiresApproval) {

    /** Applies minimum, round-down and daily cap to raw overtime minutes. */
    public int apply(int rawMinutes) {
        if (rawMinutes <= 0 || rawMinutes < minOtMinutes) {
            return 0;
        }
        int rounded = roundingMinutes > 1 ? (rawMinutes / roundingMinutes) * roundingMinutes : rawMinutes;
        return maxOtMinutes != null ? Math.min(rounded, maxOtMinutes) : rounded;
    }
}
