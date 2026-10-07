package com.is2c.hrm.attendance.adapter;

import java.util.List;

/**
 * Converts a vendor payload into standard events (FR-047). New biometric vendors are supported by adding an
 * implementation; nothing else in the module is vendor-specific.
 */
public interface AttendanceSourceAdapter {

    AdapterType type();

    /** Parses a payload; invalid rows are returned as rejected events rather than failing the batch. */
    List<ParsedEvent> parse(Object payload);
}
