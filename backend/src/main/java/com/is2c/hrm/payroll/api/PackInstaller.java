package com.is2c.hrm.payroll.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/** Fluent helper given to statutory packs to create a company's components and slab tables. */
public interface PackInstaller {

    record Slab(BigDecimal lowerBound, BigDecimal upperBound, BigDecimal ratePercent) {
    }

    void slabTable(String code, String name, boolean statutory, List<Slab> rows);

    /** Creates a component; {@code spec} uses the same fields as the admin API. */
    void component(ComponentSpec spec);

    record ComponentSpec(String code, String name, ComponentKind kind, String frequency, String assignment,
            CalcType calcType, int sequence, Set<String> bases, String basisRef, BigDecimal ratePercent,
            UnitMetric unitMetric, String unitRateMode, BigDecimal unitFixedAmount, BigDecimal divisor,
            BigDecimal multiplier, String slabTableCode, boolean prorate, boolean showOnPayslip, boolean statutory) {
    }
}
