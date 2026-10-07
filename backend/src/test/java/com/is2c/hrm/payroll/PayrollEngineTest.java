package com.is2c.hrm.payroll;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.is2c.hrm.payroll.api.CalcType;
import com.is2c.hrm.payroll.api.ComponentKind;
import com.is2c.hrm.payroll.api.UnitMetric;
import com.is2c.hrm.payroll.domain.PayrollComponent.Assignment;
import com.is2c.hrm.payroll.domain.PayrollComponent.Frequency;
import com.is2c.hrm.payroll.domain.PayrollComponent.UnitRateMode;
import com.is2c.hrm.payroll.engine.PayrollEngine;
import com.is2c.hrm.payroll.engine.PayrollEngine.ComponentDef;
import com.is2c.hrm.payroll.engine.PayrollEngine.EmployeeInput;
import com.is2c.hrm.payroll.engine.PayrollEngine.LineInput;
import com.is2c.hrm.payroll.engine.PayrollEngine.Result;
import com.is2c.hrm.payroll.engine.PayrollEngine.SlabBand;

/** FR-085 payroll calculation with Sri Lanka worked examples (FR-081 values supplied as data). */
class PayrollEngineTest {

    static BigDecimal d(String v) {
        return new BigDecimal(v);
    }

    static final Set<String> ST_TX = Set.of("STATUTORY", "TAXABLE");
    static final List<SlabBand> APIT = List.of(
            new SlabBand(d("0"), d("150000"), d("0")),
            new SlabBand(d("150000"), d("233333"), d("6")),
            new SlabBand(d("233333"), d("275000"), d("18")),
            new SlabBand(d("275000"), d("316667"), d("24")),
            new SlabBand(d("316667"), d("358333"), d("30")),
            new SlabBand(d("358333"), null, d("36")));

    static ComponentDef basic() {
        return new ComponentDef("BASIC", "Basic salary", ComponentKind.EARNING, Frequency.RECURRING, Assignment.PER_EMPLOYEE,
                CalcType.FIXED, 10, ST_TX, null, null, null, null, null, null, null, List.of(), true, true, true);
    }

    static ComponentDef fixed(String code, ComponentKind kind, Frequency f, int seq, Set<String> bases, boolean prorate) {
        return new ComponentDef(code, code, kind, f, Assignment.PER_EMPLOYEE, CalcType.FIXED, seq, bases, null, null, null,
                null, null, null, null, List.of(), prorate, true, false);
    }

    static ComponentDef perUnit(String code, ComponentKind kind, UnitMetric metric, int seq, String divisor, String mult,
            Set<String> bases) {
        return new ComponentDef(code, code, kind, Frequency.RECURRING, Assignment.AUTOMATIC, CalcType.PER_UNIT, seq, bases,
                "COMPONENT:BASIC", null, metric, UnitRateMode.BASIS, null, divisor == null ? null : d(divisor), d(mult),
                List.of(), false, true, false);
    }

    static ComponentDef percent(String code, ComponentKind kind, int seq, String basis, String rate) {
        return new ComponentDef(code, code, kind, Frequency.RECURRING, Assignment.AUTOMATIC, CalcType.PERCENT_OF, seq,
                Set.of(), basis, d(rate), null, null, null, null, null, List.of(), false, true, false);
    }

    static ComponentDef slab(String code, int seq, String basis, List<SlabBand> bands) {
        return new ComponentDef(code, code, ComponentKind.DEDUCTION, Frequency.RECURRING, Assignment.AUTOMATIC,
                CalcType.SLAB, seq, Set.of(), basis, null, null, null, null, null, null, bands, false, true, false);
    }

    /** The seeded LK configuration expressed as engine definitions. */
    static List<ComponentDef> sriLanka(String epfEmployeeRate) {
        return List.of(
                basic(),
                fixed("ALLOWANCE", ComponentKind.EARNING, Frequency.RECURRING, 20, Set.of("TAXABLE"), false),
                fixed("BONUS", ComponentKind.EARNING, Frequency.ONE_TIME, 30, Set.of("TAXABLE"), false),
                perUnit("OT_WORKDAY", ComponentKind.EARNING, UnitMetric.OT_WORKDAY_HOURS, 40, "240", "1.5", Set.of("TAXABLE")),
                perUnit("OT_HOLIDAY", ComponentKind.EARNING, UnitMetric.OT_HOLIDAY_HOURS, 42, "240", "2.0", Set.of("TAXABLE")),
                perUnit("NO_PAY", ComponentKind.DEDUCTION, UnitMetric.NO_PAY_DAYS, 50, null, "1", ST_TX),
                percent("EPF_EE", ComponentKind.DEDUCTION, 100, "BASE:STATUTORY", epfEmployeeRate),
                slab("APIT", 110, "BASE:TAXABLE", APIT),
                percent("EPF_ER", ComponentKind.EMPLOYER_CONTRIBUTION, 120, "BASE:STATUTORY", "12"),
                percent("ETF", ComponentKind.EMPLOYER_CONTRIBUTION, 121, "BASE:STATUTORY", "3"),
                fixed("LOAN", ComponentKind.DEDUCTION, Frequency.RECURRING, 200, Set.of(), false));
    }

    static EmployeeInput input(String basic, List<LineInput> lines, Map<UnitMetric, BigDecimal> metrics) {
        return new EmployeeInput(d(basic), lines, List.of(), metrics, BigDecimal.ONE, d("30"));
    }

    static BigDecimal amount(Result r, String code) {
        return r.lines().stream().filter(l -> l.componentCode().equals(code)).map(PayrollEngine.Line::amount)
                .findFirst().orElse(null);
    }

    @Test
    @DisplayName("LK: basic 100,000 + allowance 20,000 → EPF 8,000; no APIT below 150,000; EPF_ER 12,000; ETF 3,000")
    void belowTaxThreshold() {
        Result r = PayrollEngine.calculate(sriLanka("8"),
                input("100000", List.of(new LineInput("ALLOWANCE", d("20000"), null, null, false)), Map.of()));
        assertThat(amount(r, "BASIC")).isEqualByComparingTo("100000.00");
        assertThat(amount(r, "EPF_EE")).isEqualByComparingTo("8000.00");
        assertThat(amount(r, "APIT")).isNull();
        assertThat(amount(r, "EPF_ER")).isEqualByComparingTo("12000.00");
        assertThat(amount(r, "ETF")).isEqualByComparingTo("3000.00");
        assertThat(r.gross()).isEqualByComparingTo("120000.00");
        assertThat(r.deductions()).isEqualByComparingTo("8000.00");
        assertThat(r.net()).isEqualByComparingTo("112000.00");
        assertThat(r.employerContributions()).isEqualByComparingTo("15000.00");
    }

    @Test
    @DisplayName("LK: OT at Basic/240×1.5 is taxable not EPF-liable; APIT is progressive across bands")
    void overtimeAndApit() {
        Result r = PayrollEngine.calculate(sriLanka("8"), input("250000",
                List.of(new LineInput("ALLOWANCE", d("50000"), null, null, false)),
                Map.of(UnitMetric.OT_WORKDAY_HOURS, d("10"))));
        PayrollEngine.Line ot = r.lines().stream().filter(l -> l.componentCode().equals("OT_WORKDAY")).findFirst().orElseThrow();
        assertThat(ot.units()).isEqualByComparingTo("10");
        assertThat(ot.rate()).isEqualByComparingTo("1562.5");
        assertThat(ot.amount()).isEqualByComparingTo("15625.00");
        // Taxable 315,625: 83,333×6% + 41,667×18% + 40,625×24% = 4,999.98 + 7,500.06 + 9,750.00
        assertThat(amount(r, "APIT")).isEqualByComparingTo("22250.04");
        assertThat(amount(r, "EPF_EE")).isEqualByComparingTo("20000.00");
        assertThat(r.gross()).isEqualByComparingTo("315625.00");
        assertThat(r.net()).isEqualByComparingTo("273374.96");
        assertThat(r.employerContributions()).isEqualByComparingTo("37500.00");
    }

    @Test
    @DisplayName("LK: no-pay days use the company divisor and reduce the EPF base")
    void noPay() {
        Result r = PayrollEngine.calculate(sriLanka("8"), input("90000", List.of(), Map.of(UnitMetric.NO_PAY_DAYS, d("2"))));
        assertThat(amount(r, "NO_PAY")).isEqualByComparingTo("6000.00");
        assertThat(amount(r, "EPF_EE")).isEqualByComparingTo("6720.00");
        assertThat(r.net()).isEqualByComparingTo("77280.00");
    }

    @Test
    @DisplayName("FR-085 proration applies to prorated recurring fixed components only")
    void proration() {
        EmployeeInput in = new EmployeeInput(d("100000"), List.of(new LineInput("ALLOWANCE", d("10000"), null, null, false)),
                List.of(), Map.of(), d("0.5"), d("30"));
        Result r = PayrollEngine.calculate(sriLanka("8"), in);
        assertThat(amount(r, "BASIC")).isEqualByComparingTo("50000.00");
        assertThat(amount(r, "ALLOWANCE")).isEqualByComparingTo("10000.00");
        assertThat(amount(r, "EPF_EE")).isEqualByComparingTo("4000.00");
    }

    @Test
    @DisplayName("FR-082 excluded automatic components and per-employee components not on the structure are skipped")
    void applicability() {
        Result r = PayrollEngine.calculate(sriLanka("8"), input("100000",
                List.of(new LineInput("EPF_EE", null, null, null, true)), Map.of()));
        assertThat(amount(r, "EPF_EE")).isNull();
        assertThat(amount(r, "EPF_ER")).isEqualByComparingTo("12000.00");
        assertThat(amount(r, "ALLOWANCE")).isNull();
        assertThat(amount(r, "LOAN")).isNull();
    }

    @Test
    @DisplayName("FR-083 one-time items are added once (not prorated) and count towards tax")
    void oneTime() {
        EmployeeInput in = new EmployeeInput(d("140000"), List.of(),
                List.of(new PayrollEngine.OneTime("BONUS", d("30000")), new PayrollEngine.OneTime("BONUS", d("5000"))),
                Map.of(), d("0.5"), d("30"));
        Result r = PayrollEngine.calculate(sriLanka("8"), in);
        assertThat(amount(r, "BONUS")).isEqualByComparingTo("35000.00");
        // Taxable 70,000 + 35,000 = 105,000 → still below the threshold
        assertThat(amount(r, "APIT")).isNull();
    }

    @Test
    @DisplayName("FR-082 structure lines can override rates and multipliers")
    void overrides() {
        Result r = PayrollEngine.calculate(sriLanka("8"), input("240000",
                List.of(new LineInput("EPF_EE", null, d("10"), null, false), new LineInput("OT_WORKDAY", null, null, d("2"), false)),
                Map.of(UnitMetric.OT_WORKDAY_HOURS, d("1"))));
        assertThat(amount(r, "EPF_EE")).isEqualByComparingTo("24000.00");
        assertThat(amount(r, "OT_WORKDAY")).isEqualByComparingTo("2000.00");
    }

    @Test
    @DisplayName("FR-081 changing the configured statutory rate changes the result (no hard-coded values)")
    void configurableRate() {
        Result r = PayrollEngine.calculate(sriLanka("10"), input("100000", List.of(), Map.of()));
        assertThat(amount(r, "EPF_EE")).isEqualByComparingTo("10000.00");
    }

    @Test
    @DisplayName("FR-085 deductions larger than earnings give a negative net, reported for review")
    void negativeNet() {
        Result r = PayrollEngine.calculate(sriLanka("8"), input("50000",
                List.of(new LineInput("LOAN", d("60000"), null, null, false)), Map.of()));
        assertThat(r.net()).isNegative();
    }

    @Test
    @DisplayName("FR-085 amounts round HALF_UP to 2 decimals and the calculation is deterministic")
    void rounding() {
        // 1,000.50 × 1% = 10.005 → 10.01 with HALF_UP (banker's rounding would give 10.00)
        List<ComponentDef> defs = List.of(basic(), percent("ODD", ComponentKind.DEDUCTION, 100, "COMPONENT:BASIC", "1"));
        Result r = PayrollEngine.calculate(defs, input("1000.50", List.of(), Map.of()));
        assertThat(amount(r, "ODD")).isEqualByComparingTo("10.01");
        assertThat(PayrollEngine.calculate(defs, input("1000.50", List.of(), Map.of()))).isEqualTo(r);
    }

    @Test
    @DisplayName("FR-080 GROSS basis and fixed-amount unit rates")
    void grossAndFixedUnit() {
        List<ComponentDef> defs = new ArrayList<>(List.of(basic(),
                new ComponentDef("MEAL", "Meal", ComponentKind.EARNING, Frequency.RECURRING, Assignment.AUTOMATIC,
                        CalcType.PER_UNIT, 20, Set.of(), null, null, UnitMetric.WORKED_DAYS, UnitRateMode.FIXED_AMOUNT,
                        d("250"), null, null, List.of(), false, true, false),
                percent("WELFARE", ComponentKind.DEDUCTION, 100, "GROSS", "1")));
        Result r = PayrollEngine.calculate(defs, input("50000", List.of(), Map.of(UnitMetric.WORKED_DAYS, d("20"))));
        assertThat(amount(r, "MEAL")).isEqualByComparingTo("5000.00");
        assertThat(amount(r, "WELFARE")).isEqualByComparingTo("550.00");
    }
}
