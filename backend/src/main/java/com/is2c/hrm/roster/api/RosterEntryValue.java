package com.is2c.hrm.roster.api;

import java.util.UUID;

/** A published roster cell: a shift, or OFF when {@code shiftId} is null. */
public record RosterEntryValue(UUID shiftId) {

    public boolean isOff() {
        return shiftId == null;
    }
}
