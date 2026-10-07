package com.is2c.hrm.common.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Money arithmetic rules (NFR-013): scale 2, HALF_UP. */
public final class Money {

    public static final int SCALE = 2;
    public static final BigDecimal ZERO = BigDecimal.ZERO.setScale(SCALE);

    private Money() {
    }

    public static BigDecimal round(BigDecimal value) {
        return value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal nonNegative(BigDecimal value) {
        return value.signum() < 0 ? ZERO : value;
    }
}
