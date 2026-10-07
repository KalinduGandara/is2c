package com.is2c.hrm.leave.domain;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import com.is2c.hrm.common.domain.BaseEntity;
import com.is2c.hrm.common.domain.UuidSetConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Leave policy (FR-060). */
@Entity
@Table(name = "leave_type")
public class LeaveType extends BaseEntity {

    public enum AccrualMethod { ANNUAL_UPFRONT, MONTHLY }

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;
    @Column(name = "code")
    private String code;

    @Column(name = "name")
    private String name;

    @Column(name = "paid")
    private boolean paid;

    @Column(name = "annual_days")
    private BigDecimal annualDays = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "accrual_method")
    private AccrualMethod accrualMethod;

    @Column(name = "pro_rata")
    private boolean proRata;

    @Column(name = "cf_max_days")
    private BigDecimal cfMaxDays = BigDecimal.ZERO;

    @Column(name = "cf_expiry_months")
    private int cfExpiryMonths;

    @Column(name = "allow_half_day")
    private boolean allowHalfDay;

    @Column(name = "allow_negative")
    private boolean allowNegative;

    @Column(name = "unlimited")
    private boolean unlimited;

    @Column(name = "count_weekly_off")
    private boolean countWeeklyOff;

    @Column(name = "count_holiday")
    private boolean countHoliday;

    @Column(name = "attachment_above_days")
    private BigDecimal attachmentAboveDays;

    @Convert(converter = UuidSetConverter.class)
    @Column(name = "eligible_category_ids")
    private Set<UUID> eligibleCategoryIds = new LinkedHashSet<>();

    @Column(name = "color")
    private String color;

    @Column(name = "active")
    private boolean active = true;

    protected LeaveType() {
    }

    public LeaveType(UUID companyId) {
        this.companyId = companyId;
    }

    public boolean eligible(UUID categoryId) {
        return eligibleCategoryIds == null || eligibleCategoryIds.isEmpty() || eligibleCategoryIds.contains(categoryId);
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

    public boolean isPaid() {
        return paid;
    }

    public void setPaid(boolean paid) {
        this.paid = paid;
    }

    public BigDecimal getAnnualDays() {
        return annualDays;
    }

    public void setAnnualDays(BigDecimal annualDays) {
        this.annualDays = annualDays;
    }

    public AccrualMethod getAccrualMethod() {
        return accrualMethod;
    }

    public void setAccrualMethod(AccrualMethod accrualMethod) {
        this.accrualMethod = accrualMethod;
    }

    public boolean isProRata() {
        return proRata;
    }

    public void setProRata(boolean proRata) {
        this.proRata = proRata;
    }

    public BigDecimal getCfMaxDays() {
        return cfMaxDays;
    }

    public void setCfMaxDays(BigDecimal cfMaxDays) {
        this.cfMaxDays = cfMaxDays;
    }

    public int getCfExpiryMonths() {
        return cfExpiryMonths;
    }

    public void setCfExpiryMonths(int cfExpiryMonths) {
        this.cfExpiryMonths = cfExpiryMonths;
    }

    public boolean isAllowHalfDay() {
        return allowHalfDay;
    }

    public void setAllowHalfDay(boolean allowHalfDay) {
        this.allowHalfDay = allowHalfDay;
    }

    public boolean isAllowNegative() {
        return allowNegative;
    }

    public void setAllowNegative(boolean allowNegative) {
        this.allowNegative = allowNegative;
    }

    public boolean isUnlimited() {
        return unlimited;
    }

    public void setUnlimited(boolean unlimited) {
        this.unlimited = unlimited;
    }

    public boolean isCountWeeklyOff() {
        return countWeeklyOff;
    }

    public void setCountWeeklyOff(boolean countWeeklyOff) {
        this.countWeeklyOff = countWeeklyOff;
    }

    public boolean isCountHoliday() {
        return countHoliday;
    }

    public void setCountHoliday(boolean countHoliday) {
        this.countHoliday = countHoliday;
    }

    public BigDecimal getAttachmentAboveDays() {
        return attachmentAboveDays;
    }

    public void setAttachmentAboveDays(BigDecimal attachmentAboveDays) {
        this.attachmentAboveDays = attachmentAboveDays;
    }

    public Set<UUID> getEligibleCategoryIds() {
        return eligibleCategoryIds;
    }

    public void setEligibleCategoryIds(Set<UUID> eligibleCategoryIds) {
        this.eligibleCategoryIds = eligibleCategoryIds;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
