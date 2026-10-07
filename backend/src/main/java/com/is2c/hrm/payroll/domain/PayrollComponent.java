package com.is2c.hrm.payroll.domain;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.is2c.hrm.common.domain.BaseEntity;
import com.is2c.hrm.payroll.api.CalcType;
import com.is2c.hrm.payroll.api.ComponentKind;
import com.is2c.hrm.payroll.api.UnitMetric;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Configurable earning/deduction/contribution (FR-080). Statutory components are ordinary data (FR-081). */
@Entity
@Table(name = "payroll_component")
public class PayrollComponent extends BaseEntity {

    public static final String BASIC = "BASIC";

    public enum Frequency { RECURRING, ONE_TIME }

    public enum Assignment { PER_EMPLOYEE, AUTOMATIC }

    public enum UnitRateMode { FIXED_AMOUNT, BASIS }

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ComponentKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Frequency frequency = Frequency.RECURRING;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Assignment assignment = Assignment.PER_EMPLOYEE;

    @Enumerated(EnumType.STRING)
    @Column(name = "calc_type", nullable = false)
    private CalcType calcType;

    @Column(nullable = false)
    private int sequence;

    @Column(nullable = false)
    private String bases = "";

    @Column(name = "basis_ref")
    private String basisRef;

    @Column(name = "rate_percent")
    private BigDecimal ratePercent;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_metric")
    private UnitMetric unitMetric;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_rate_mode")
    private UnitRateMode unitRateMode;

    @Column(name = "unit_fixed_amount")
    private BigDecimal unitFixedAmount;

    private BigDecimal divisor;

    private BigDecimal multiplier;

    @Column(name = "slab_table_id")
    private UUID slabTableId;

    @Column(nullable = false)
    private boolean prorate;

    @Column(name = "show_on_payslip", nullable = false)
    private boolean showOnPayslip = true;

    @Column(nullable = false)
    private boolean statutory;

    @Column(name = "system_component", nullable = false)
    private boolean systemComponent;

    @Column(nullable = false)
    private boolean active = true;

    protected PayrollComponent() {
    }

    public PayrollComponent(UUID companyId, String code) {
        this.companyId = companyId;
        this.code = code;
    }

    public Set<String> baseSet() {
        return bases == null || bases.isBlank() ? Set.of()
                : Arrays.stream(bases.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                        .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public void setBaseSet(Set<String> set) {
        this.bases = set == null ? "" : set.stream().map(String::trim).map(String::toUpperCase).filter(s -> !s.isEmpty())
                .sorted().collect(Collectors.joining(","));
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ComponentKind getKind() {
        return kind;
    }

    public void setKind(ComponentKind kind) {
        this.kind = kind;
    }

    public Frequency getFrequency() {
        return frequency;
    }

    public void setFrequency(Frequency frequency) {
        this.frequency = frequency;
    }

    public Assignment getAssignment() {
        return assignment;
    }

    public void setAssignment(Assignment assignment) {
        this.assignment = assignment;
    }

    public CalcType getCalcType() {
        return calcType;
    }

    public void setCalcType(CalcType calcType) {
        this.calcType = calcType;
    }

    public int getSequence() {
        return sequence;
    }

    public void setSequence(int sequence) {
        this.sequence = sequence;
    }

    public String getBasisRef() {
        return basisRef;
    }

    public void setBasisRef(String basisRef) {
        this.basisRef = basisRef;
    }

    public BigDecimal getRatePercent() {
        return ratePercent;
    }

    public void setRatePercent(BigDecimal ratePercent) {
        this.ratePercent = ratePercent;
    }

    public UnitMetric getUnitMetric() {
        return unitMetric;
    }

    public void setUnitMetric(UnitMetric unitMetric) {
        this.unitMetric = unitMetric;
    }

    public UnitRateMode getUnitRateMode() {
        return unitRateMode;
    }

    public void setUnitRateMode(UnitRateMode unitRateMode) {
        this.unitRateMode = unitRateMode;
    }

    public BigDecimal getUnitFixedAmount() {
        return unitFixedAmount;
    }

    public void setUnitFixedAmount(BigDecimal unitFixedAmount) {
        this.unitFixedAmount = unitFixedAmount;
    }

    public BigDecimal getDivisor() {
        return divisor;
    }

    public void setDivisor(BigDecimal divisor) {
        this.divisor = divisor;
    }

    public BigDecimal getMultiplier() {
        return multiplier;
    }

    public void setMultiplier(BigDecimal multiplier) {
        this.multiplier = multiplier;
    }

    public UUID getSlabTableId() {
        return slabTableId;
    }

    public void setSlabTableId(UUID slabTableId) {
        this.slabTableId = slabTableId;
    }

    public boolean isProrate() {
        return prorate;
    }

    public void setProrate(boolean prorate) {
        this.prorate = prorate;
    }

    public boolean isShowOnPayslip() {
        return showOnPayslip;
    }

    public void setShowOnPayslip(boolean showOnPayslip) {
        this.showOnPayslip = showOnPayslip;
    }

    public boolean isStatutory() {
        return statutory;
    }

    public void setStatutory(boolean statutory) {
        this.statutory = statutory;
    }

    public boolean isSystemComponent() {
        return systemComponent;
    }

    public void setSystemComponent(boolean systemComponent) {
        this.systemComponent = systemComponent;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
