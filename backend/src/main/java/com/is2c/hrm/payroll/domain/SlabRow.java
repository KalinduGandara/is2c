package com.is2c.hrm.payroll.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** One band of a progressive table: [lowerBound, upperBound) at ratePercent; null upper = open-ended. */
@Embeddable
public record SlabRow(@Column(name = "lower_bound", nullable = false) BigDecimal lowerBound,
        @Column(name = "upper_bound") BigDecimal upperBound,
        @Column(name = "rate_percent", nullable = false) BigDecimal ratePercent) {
}
