package com.is2c.hrm.payroll.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.is2c.hrm.payroll.api.CalcType;
import com.is2c.hrm.payroll.api.ComponentKind;
import com.is2c.hrm.payroll.api.PackInstaller;
import com.is2c.hrm.payroll.api.PackInstaller.ComponentSpec;
import com.is2c.hrm.payroll.api.PackInstaller.Slab;
import com.is2c.hrm.payroll.api.StatutoryPack;
import com.is2c.hrm.payroll.api.UnitMetric;

/**
 * Sri Lanka defaults (CL-B1, CL R2-1, R2-4): EPF 8% / 12%, ETF 3% on EPF-liable earnings ({@code BASE:STATUTORY}),
 * APIT monthly table effective 1 April 2025 on {@code BASE:TAXABLE}, OT at Basic/240 × 1.5 / 1.5 / 2.0, and no-pay
 * at Basic / company no-pay divisor. All values are editable data; customers must verify them against current law.
 */
@Component
public class SriLankaStatutoryPack implements StatutoryPack {

    static final Set<String> STATUTORY_TAXABLE = Set.of("STATUTORY", "TAXABLE");
    static final Set<String> TAXABLE = Set.of("TAXABLE");

    @Override
    public String code() {
        return "LK";
    }

    @Override
    public String description() {
        return "Sri Lanka — EPF, ETF, APIT (2025/26), OT and no-pay";
    }

    @Override
    public void install(PackInstaller p) {
        p.slabTable("APIT_MONTHLY", "APIT monthly (from 1 Apr 2025)", true, List.of(
                slab("0", "150000", "0"),
                slab("150000", "233333", "6"),
                slab("233333", "275000", "18"),
                slab("275000", "316667", "24"),
                slab("316667", "358333", "30"),
                slab("358333", null, "36")));

        p.component(fixed("ALLOWANCE", "Allowance", ComponentKind.EARNING, "RECURRING", 20, TAXABLE));
        p.component(fixed("BONUS", "Bonus", ComponentKind.EARNING, "ONE_TIME", 30, TAXABLE));
        p.component(fixed("INCENTIVE", "Incentive", ComponentKind.EARNING, "ONE_TIME", 31, TAXABLE));
        p.component(fixed("COMMISSION", "Commission", ComponentKind.EARNING, "ONE_TIME", 32, TAXABLE));
        p.component(perUnit("OT_WORKDAY", "Overtime (workday)", ComponentKind.EARNING, UnitMetric.OT_WORKDAY_HOURS, 40,
                "240", "1.5", TAXABLE));
        p.component(perUnit("OT_OFFDAY", "Overtime (weekly-off)", ComponentKind.EARNING, UnitMetric.OT_OFFDAY_HOURS, 41,
                "240", "1.5", TAXABLE));
        p.component(perUnit("OT_HOLIDAY", "Overtime (holiday)", ComponentKind.EARNING, UnitMetric.OT_HOLIDAY_HOURS, 42,
                "240", "2.0", TAXABLE));
        p.component(perUnit("EXTRA_HOURS", "Extra hours", ComponentKind.EARNING, UnitMetric.EXTRA_HOURS, 43,
                "240", "1.0", TAXABLE));
        p.component(perUnit("NO_PAY", "No-pay deduction", ComponentKind.DEDUCTION, UnitMetric.NO_PAY_DAYS, 50,
                null, "1", STATUTORY_TAXABLE));

        p.component(new ComponentSpec("EPF_EE", "EPF (employee 8%)", ComponentKind.DEDUCTION, "RECURRING", "AUTOMATIC",
                CalcType.PERCENT_OF, 100, Set.of(), "BASE:STATUTORY", new BigDecimal("8"), null, null, null, null, null,
                null, false, true, true));
        p.component(new ComponentSpec("APIT", "APIT (PAYE tax)", ComponentKind.DEDUCTION, "RECURRING", "AUTOMATIC",
                CalcType.SLAB, 110, Set.of(), "BASE:TAXABLE", null, null, null, null, null, null, "APIT_MONTHLY",
                false, true, true));
        p.component(new ComponentSpec("EPF_ER", "EPF (employer 12%)", ComponentKind.EMPLOYER_CONTRIBUTION, "RECURRING",
                "AUTOMATIC", CalcType.PERCENT_OF, 120, Set.of(), "BASE:STATUTORY", new BigDecimal("12"), null, null, null,
                null, null, null, false, true, true));
        p.component(new ComponentSpec("ETF", "ETF (employer 3%)", ComponentKind.EMPLOYER_CONTRIBUTION, "RECURRING",
                "AUTOMATIC", CalcType.PERCENT_OF, 121, Set.of(), "BASE:STATUTORY", new BigDecimal("3"), null, null, null,
                null, null, null, false, true, true));

        p.component(fixed("LOAN_DEDUCTION", "Loan deduction", ComponentKind.DEDUCTION, "RECURRING", 200, Set.of()));
        p.component(fixed("SALARY_ADVANCE", "Salary advance", ComponentKind.DEDUCTION, "ONE_TIME", 201, Set.of()));
    }

    private static Slab slab(String lower, String upper, String rate) {
        return new Slab(new BigDecimal(lower), upper == null ? null : new BigDecimal(upper), new BigDecimal(rate));
    }

    private static ComponentSpec fixed(String code, String name, ComponentKind kind, String frequency, int seq,
            Set<String> bases) {
        return new ComponentSpec(code, name, kind, frequency, "PER_EMPLOYEE", CalcType.FIXED, seq, bases, null, null,
                null, null, null, null, null, null, false, true, false);
    }

    private static ComponentSpec perUnit(String code, String name, ComponentKind kind, UnitMetric metric, int seq,
            String divisor, String multiplier, Set<String> bases) {
        return new ComponentSpec(code, name, kind, "RECURRING", "AUTOMATIC", CalcType.PER_UNIT, seq, bases,
                "COMPONENT:BASIC", null, metric, "BASIS", null, divisor == null ? null : new BigDecimal(divisor),
                new BigDecimal(multiplier), null, false, true, false);
    }
}
