package com.is2c.hrm.payroll.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.is2c.hrm.common.error.FieldError;
import com.is2c.hrm.payroll.api.CalcType;
import com.is2c.hrm.payroll.domain.PayrollComponent;
import com.is2c.hrm.payroll.domain.PayrollComponent.UnitRateMode;

/**
 * Validates the whole component configuration of a company (FR-080): each component's fields for its calculation
 * type, and that every basis only depends on components evaluated earlier (no forward references or cycles).
 */
public final class ComponentGraphValidator {

    private ComponentGraphValidator() {
    }

    public static List<FieldError> validate(List<PayrollComponent> components, Set<UUID> slabTableIds) {
        List<FieldError> errors = new ArrayList<>();
        Map<String, PayrollComponent> byCode = new HashMap<>();
        for (PayrollComponent c : components) {
            if (byCode.put(c.getCode(), c) != null) {
                errors.add(new FieldError("code", "duplicate component code " + c.getCode()));
            }
        }
        for (PayrollComponent c : components) {
            if (!c.isActive()) {
                continue;
            }
            String p = c.getCode() + ": ";
            if (c.getCalcType() == null || c.getKind() == null) {
                errors.add(new FieldError("calcType", p + "kind and calculation type are required"));
                continue;
            }
            boolean needsBasis = c.getCalcType() == CalcType.PERCENT_OF || c.getCalcType() == CalcType.SLAB
                    || (c.getCalcType() == CalcType.PER_UNIT && c.getUnitRateMode() == UnitRateMode.BASIS);
            switch (c.getCalcType()) {
                case FIXED -> {
                }
                case PERCENT_OF -> nonNegative(errors, "ratePercent", p, c.getRatePercent(), true);
                case PER_UNIT -> {
                    if (c.getUnitMetric() == null) {
                        errors.add(new FieldError("unitMetric", p + "a unit metric is required"));
                    }
                    if (c.getUnitRateMode() == null) {
                        errors.add(new FieldError("unitRateMode", p + "a unit rate mode is required"));
                    } else if (c.getUnitRateMode() == UnitRateMode.FIXED_AMOUNT) {
                        nonNegative(errors, "unitFixedAmount", p, c.getUnitFixedAmount(), true);
                    } else {
                        if (c.getDivisor() != null && c.getDivisor().signum() <= 0) {
                            errors.add(new FieldError("divisor", p + "divisor must be greater than 0"));
                        }
                        nonNegative(errors, "multiplier", p, c.getMultiplier(), true);
                    }
                }
                case SLAB -> {
                    if (c.getSlabTableId() == null || !slabTableIds.contains(c.getSlabTableId())) {
                        errors.add(new FieldError("slabTableId", p + "a slab table of the company is required"));
                    }
                }
            }
            if (needsBasis) {
                checkBasis(c, byCode, components, errors);
            }
        }
        return errors;
    }

    private static void checkBasis(PayrollComponent c, Map<String, PayrollComponent> byCode,
            List<PayrollComponent> all, List<FieldError> errors) {
        String p = c.getCode() + ": ";
        String ref = c.getBasisRef();
        if (ref == null || ref.isBlank()) {
            errors.add(new FieldError("basisRef", p + "a basis is required"));
            return;
        }
        if (ref.equals("GROSS")) {
            return;
        }
        if (ref.startsWith("COMPONENT:")) {
            PayrollComponent target = byCode.get(ref.substring("COMPONENT:".length()));
            if (target == null || !target.isActive()) {
                errors.add(new FieldError("basisRef", p + "unknown or inactive component in " + ref));
            } else if (target.getSequence() >= c.getSequence()) {
                errors.add(new FieldError("basisRef", p + ref + " must be evaluated earlier (lower sequence)"));
            }
            return;
        }
        if (ref.startsWith("BASE:")) {
            String base = ref.substring("BASE:".length());
            for (PayrollComponent m : all) {
                if (m.isActive() && m != c && m.baseSet().contains(base) && m.getSequence() >= c.getSequence()) {
                    errors.add(new FieldError("basisRef", p + "base " + base + " includes " + m.getCode()
                            + " which is evaluated later"));
                }
            }
            if (c.baseSet().contains(base)) {
                errors.add(new FieldError("bases", p + "a component cannot belong to the base it is calculated from"));
            }
            return;
        }
        errors.add(new FieldError("basisRef", p + "basis must be GROSS, COMPONENT:<code> or BASE:<name>"));
    }

    private static void nonNegative(List<FieldError> errors, String field, String prefix, BigDecimal v, boolean required) {
        if (v == null) {
            if (required) {
                errors.add(new FieldError(field, prefix + field + " is required"));
            }
        } else if (v.signum() < 0) {
            errors.add(new FieldError(field, prefix + field + " must not be negative"));
        }
    }
}
