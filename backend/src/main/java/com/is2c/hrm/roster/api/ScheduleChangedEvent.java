package com.is2c.hrm.roster.api;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/** Published when expected schedules change (roster publish/edits) so attendance can reprocess (FR-039). */
public record ScheduleChangedEvent(UUID companyId, Set<UUID> employeeIds, LocalDate from, LocalDate to) {
}
