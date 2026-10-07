package com.is2c.hrm.leave;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.is2c.hrm.leave.service.LeaveRules;

class LeaveRulesTest {

    @Test
    @DisplayName("FR-062 upfront grant is pro-rated by remaining months and rounded down to 0.5")
    void proRata() {
        BigDecimal fourteen = BigDecimal.valueOf(14);
        assertThat(LeaveRules.upfrontGrant(fourteen, true, LocalDate.of(2020, 5, 1), 2026)).isEqualByComparingTo("14");
        // Joined April: 9 months → 14 × 9 / 12 = 10.5
        assertThat(LeaveRules.upfrontGrant(fourteen, true, LocalDate.of(2026, 4, 20), 2026)).isEqualByComparingTo("10.5");
        // Joined Oct: 3 months → 3.5
        assertThat(LeaveRules.upfrontGrant(fourteen, true, LocalDate.of(2026, 10, 1), 2026)).isEqualByComparingTo("3.5");
        // 7 × 5 / 12 = 2.916 → 2.5
        assertThat(LeaveRules.upfrontGrant(BigDecimal.valueOf(7), true, LocalDate.of(2026, 8, 1), 2026)).isEqualByComparingTo("2.5");
        assertThat(LeaveRules.upfrontGrant(fourteen, false, LocalDate.of(2026, 10, 1), 2026)).isEqualByComparingTo("14");
        assertThat(LeaveRules.upfrontGrant(fourteen, true, LocalDate.of(2027, 1, 1), 2026)).isEqualByComparingTo("0");
    }

    @Test
    @DisplayName("FR-062 monthly credit is annual/12 to 2 decimals; expiry at end of the configured month")
    void monthlyAndExpiry() {
        assertThat(LeaveRules.monthlyCredit(BigDecimal.valueOf(14))).isEqualByComparingTo("1.17");
        assertThat(LeaveRules.carryForwardExpiry(2026, 3)).isEqualTo(LocalDate.of(2026, 3, 31));
        assertThat(LeaveRules.unusedCarryForward(BigDecimal.valueOf(5), BigDecimal.valueOf(2))).isEqualByComparingTo("3");
        assertThat(LeaveRules.unusedCarryForward(BigDecimal.valueOf(5), BigDecimal.valueOf(8))).isEqualByComparingTo("0");
    }
}
