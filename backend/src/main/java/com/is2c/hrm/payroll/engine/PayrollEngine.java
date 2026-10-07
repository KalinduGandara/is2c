package com.is2c.hrm.payroll.engine;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.is2c.hrm.common.domain.Money;
import com.is2c.hrm.payroll.api.CalcType;
import com.is2c.hrm.payroll.api.ComponentKind;
import com.is2c.hrm.payroll.api.UnitMetric;
import com.is2c.hrm.payroll.domain.PayrollComponent.Assignment;
import com.is2c.hrm.payroll.domain.PayrollComponent.Frequency;
import com.is2c.hrm.payroll.domain.PayrollComponent.UnitRateMode;

/**
 * Pure payroll calculation (FR-085, design §7.3). Components are evaluated in sequence; every rate and threshold
 * comes from the supplied definitions (FR-081). Lines are rounded HALF_UP to 2 decimals (NFR-013).
 */
public final class PayrollEngine {

    private static final MathContext MC = MathContext.DECIMAL64;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private PayrollEngine() {
    }

    public record SlabBand(BigDecimal lowerBound, BigDecimal upperBound, BigDecimal ratePercent) {
    }

    public record ComponentDef(String code, String name, ComponentKind kind, Frequency frequency,
            Assignment assignment, CalcType calcType, int sequence, Set<String> bases, String basisRef,
            BigDecimal ratePercent, UnitMetric unitMetric, UnitRateMode unitRateMode, BigDecimal unitFixedAmount,
            BigDecimal divisor, BigDecimal multiplier, List<SlabBand> slab, boolean prorate, boolean showOnPayslip,
            boolean basic) {
    }

    /** A salary-structure line (amount for FIXED, optional overrides, or an exclusion). */
    public record LineInput(String componentCode, BigDecimal amount, BigDecimal rateOverride,
            BigDecimal multiplierOverride, boolean excluded) {
    }

    public record OneTime(String componentCode, BigDecimal amount) {
    }

    /**
     * @param prorationFactor employed calendar days / period calendar days (1 for a full period)
     * @param defaultDivisor used by PER_UNIT components without their own divisor (company no-pay divisor)
     */
    public record EmployeeInput(BigDecimal basic, List<LineInput> structureLines, List<OneTime> oneTimeItems,
            Map<UnitMetric, BigDecimal> metrics, BigDecimal prorationFactor, BigDecimal defaultDivisor) {
    }

    public record Line(String componentCode, String componentName, ComponentKind kind, BigDecimal units,
            BigDecimal rate, BigDecimal amount, boolean showOnPayslip, int sequence) {
    }

    public record Result(List<Line> lines, BigDecimal gross, BigDecimal deductions, BigDecimal net,
            BigDecimal employerContributions) {
    }

    public static Result calculate(List<ComponentDef> definitions, EmployeeInput in) {
        Map<String, LineInput> structure = new HashMap<>();
        in.structureLines().forEach(l -> structure.put(l.componentCode(), l));
        Map<String, BigDecimal> oneTime = new HashMap<>();
        in.oneTimeItems().forEach(o -> oneTime.merge(o.componentCode(), o.amount(), BigDecimal::add));

        Map<String, BigDecimal> amounts = new HashMap<>();
        Map<String, BigDecimal> bases = new HashMap<>();
        BigDecimal gross = Money.ZERO;
        BigDecimal deductions = Money.ZERO;
        BigDecimal employer = Money.ZERO;
        List<Line> lines = new ArrayList<>();

        List<ComponentDef> ordered = definitions.stream()
                .sorted(Comparator.comparingInt(ComponentDef::sequence).thenComparing(ComponentDef::code)).toList();
        for (ComponentDef c : ordered) {
            LineInput line = structure.get(c.code());
            if (!applies(c, line, oneTime)) {
                continue;
            }
            BigDecimal units = null;
            BigDecimal rate = null;
            BigDecimal raw;
            switch (c.calcType()) {
                case FIXED -> {
                    if (c.basic()) {
                        raw = in.basic();
                    } else if (c.frequency() == Frequency.ONE_TIME) {
                        raw = oneTime.getOrDefault(c.code(), BigDecimal.ZERO);
                    } else {
                        raw = line != null && line.amount() != null ? line.amount() : BigDecimal.ZERO;
                    }
                    if (c.prorate() && c.frequency() == Frequency.RECURRING) {
                        raw = raw.multiply(in.prorationFactor(), MC);
                    }
                }
                case PERCENT_OF -> {
                    rate = line != null && line.rateOverride() != null ? line.rateOverride() : c.ratePercent();
                    raw = positive(basis(c, amounts, bases, gross)).multiply(rate, MC).divide(HUNDRED, MC);
                }
                case PER_UNIT -> {
                    units = in.metrics().getOrDefault(c.unitMetric(), BigDecimal.ZERO);
                    if (c.unitRateMode() == UnitRateMode.FIXED_AMOUNT) {
                        rate = c.unitFixedAmount();
                    } else {
                        BigDecimal divisor = c.divisor() != null ? c.divisor() : in.defaultDivisor();
                        BigDecimal multiplier = line != null && line.multiplierOverride() != null
                                ? line.multiplierOverride() : c.multiplier();
                        rate = positive(basis(c, amounts, bases, gross)).divide(divisor, MC).multiply(multiplier, MC);
                    }
                    raw = units.multiply(rate, MC);
                }
                case SLAB -> raw = slab(positive(basis(c, amounts, bases, gross)), c.slab());
                default -> throw new IllegalStateException("Unsupported calculation type " + c.calcType());
            }
            BigDecimal amount = Money.nonNegative(Money.round(raw));
            amounts.put(c.code(), amount);
            if (amount.signum() == 0) {
                continue;
            }
            switch (c.kind()) {
                case EARNING -> gross = gross.add(amount);
                case DEDUCTION -> deductions = deductions.add(amount);
                case EMPLOYER_CONTRIBUTION -> employer = employer.add(amount);
            }
            for (String base : c.bases()) {
                BigDecimal delta = c.kind() == ComponentKind.EARNING ? amount
                        : c.kind() == ComponentKind.DEDUCTION ? amount.negate() : BigDecimal.ZERO;
                bases.merge(base, delta, BigDecimal::add);
            }
            lines.add(new Line(c.code(), c.name(), c.kind(), units == null ? null : units.stripTrailingZeros(),
                    rate == null ? null : rate.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros(), amount,
                    c.showOnPayslip(), c.sequence()));
        }
        return new Result(List.copyOf(lines), gross, deductions, gross.subtract(deductions), employer);
    }

    private static boolean applies(ComponentDef c, LineInput line, Map<String, BigDecimal> oneTime) {
        if (c.basic()) {
            return true;
        }
        if (c.frequency() == Frequency.ONE_TIME) {
            return oneTime.containsKey(c.code());
        }
        if (c.assignment() == Assignment.AUTOMATIC) {
            return line == null || !line.excluded();
        }
        return line != null && !line.excluded();
    }

    private static BigDecimal basis(ComponentDef c, Map<String, BigDecimal> amounts, Map<String, BigDecimal> bases,
            BigDecimal gross) {
        String ref = c.basisRef();
        if (ref == null) {
            return BigDecimal.ZERO;
        }
        if (ref.equals("GROSS")) {
            return gross;
        }
        if (ref.startsWith("COMPONENT:")) {
            return amounts.getOrDefault(ref.substring("COMPONENT:".length()), BigDecimal.ZERO);
        }
        if (ref.startsWith("BASE:")) {
            return bases.getOrDefault(ref.substring("BASE:".length()), BigDecimal.ZERO);
        }
        throw new IllegalStateException("Unsupported basis " + ref);
    }

    /** Progressive tax: each band taxes only the part of the value that falls inside it. */
    static BigDecimal slab(BigDecimal value, List<SlabBand> bands) {
        BigDecimal total = BigDecimal.ZERO;
        for (SlabBand b : bands) {
            if (value.compareTo(b.lowerBound()) <= 0) {
                break;
            }
            BigDecimal top = b.upperBound() == null ? value : value.min(b.upperBound());
            total = total.add(top.subtract(b.lowerBound()).multiply(b.ratePercent(), MC).divide(HUNDRED, MC));
        }
        return total;
    }

    private static BigDecimal positive(BigDecimal v) {
        return v.signum() < 0 ? BigDecimal.ZERO : v;
    }
}
