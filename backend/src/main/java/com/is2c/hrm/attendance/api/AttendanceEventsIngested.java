package com.is2c.hrm.attendance.api;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Raw events arrived for these employees on these (company-local) dates; days need reprocessing. */
public record AttendanceEventsIngested(UUID companyId, Map<UUID, Set<LocalDate>> datesByEmployee) {
}
