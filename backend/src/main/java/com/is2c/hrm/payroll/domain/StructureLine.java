package com.is2c.hrm.payroll.domain;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** A component on an employee's salary structure (FR-082). {@code excluded} opts out of an AUTOMATIC component. */
@Embeddable
public record StructureLine(@Column(name = "component_id", nullable = false) UUID componentId,
        @Column(name = "amount") BigDecimal amount,
        @Column(name = "rate_override") BigDecimal rateOverride,
        @Column(name = "multiplier_override") BigDecimal multiplierOverride,
        @Column(name = "excluded", nullable = false) boolean excluded) {
}
