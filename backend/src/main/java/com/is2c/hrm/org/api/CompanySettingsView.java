package com.is2c.hrm.org.api;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

/** Read-only company policy settings for other modules (FR-016). */
public record CompanySettingsView(UUID companyId, String companyCode, ZoneId timeZone, String currency,
        Set<DayOfWeek> weeklyOffDays, String employeeNumberFormat, int payrollStartDay, boolean absentAsNoPay,
        BigDecimal noPayDivisor, int punchWindowBeforeMinutes, int punchWindowAfterMinutes, DayOfWeek weekStartDay) {
}
