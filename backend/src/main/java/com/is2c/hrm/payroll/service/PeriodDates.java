package com.is2c.hrm.payroll.service;

import java.time.LocalDate;
import java.time.YearMonth;

/** FR-084 period boundaries from the company's payroll start day (design §7.4). */
public final class PeriodDates {

    public record Range(LocalDate start, LocalDate end) {
    }

    private PeriodDates() {
    }

    public static Range of(int startDay, int year, int month) {
        YearMonth ym = YearMonth.of(year, month);
        if (startDay <= 1) {
            return new Range(ym.atDay(1), ym.atEndOfMonth());
        }
        YearMonth previous = ym.minusMonths(1);
        return new Range(previous.atDay(startDay), ym.atDay(startDay - 1));
    }
}
