package com.is2c.hrm.leave.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;

/** Pure leave-entitlement arithmetic (FR-062). */
public final class LeaveRules {

    private static final BigDecimal TWO = BigDecimal.valueOf(2);
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

    private LeaveRules() {
    }

    /** Annual grant for a year; pro-rated by remaining months (incl. join month) and rounded down to 0.5. */
    public static BigDecimal upfrontGrant(BigDecimal annual, boolean proRata, LocalDate joinDate, int year) {
        if (!proRata || joinDate.getYear() < year) {
            return annual;
        }
        if (joinDate.getYear() > year) {
            return BigDecimal.ZERO;
        }
        int months = 12 - joinDate.getMonthValue() + 1;
        BigDecimal raw = annual.multiply(BigDecimal.valueOf(months)).divide(TWELVE, 4, RoundingMode.HALF_UP);
        return halfDown(raw);
    }

    /** Monthly credit: annual / 12 rounded to 2 decimals. */
    public static BigDecimal monthlyCredit(BigDecimal annual) {
        return annual.divide(TWELVE, 2, RoundingMode.HALF_UP);
    }

    /** Last day on which carried-forward days can be used: end of month {@code months} of the year. */
    public static LocalDate carryForwardExpiry(int year, int months) {
        return YearMonth.of(year, Math.min(12, Math.max(1, months))).atEndOfMonth();
    }

    /** Carried-forward days still unused at expiry: taken days consume carried-forward first. */
    public static BigDecimal unusedCarryForward(BigDecimal carriedForward, BigDecimal taken) {
        BigDecimal unused = carriedForward.subtract(taken);
        return unused.signum() > 0 ? unused : BigDecimal.ZERO;
    }

    static BigDecimal halfDown(BigDecimal value) {
        return value.multiply(TWO).setScale(0, RoundingMode.FLOOR).divide(TWO, 1, RoundingMode.UNNECESSARY)
                .setScale(2, RoundingMode.UNNECESSARY);
    }
}
