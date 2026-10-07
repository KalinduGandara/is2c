package com.is2c.hrm.roster.service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

import com.is2c.hrm.roster.api.RosterEntryValue;

/** Source of PUBLISHED/LOCKED roster cells for schedule resolution. */
public interface PublishedRosterEntries {

    /** Key: employee id + date. */
    record Key(UUID employeeId, LocalDate date) {
    }

    Map<Key, RosterEntryValue> find(Collection<UUID> employeeIds, LocalDate from, LocalDate to);
}
