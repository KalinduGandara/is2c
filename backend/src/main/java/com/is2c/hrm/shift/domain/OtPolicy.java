package com.is2c.hrm.shift.domain;

import java.util.UUID;

import com.is2c.hrm.common.domain.BaseEntity;
import com.is2c.hrm.shift.api.OtPolicyDefinition;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "ot_policy")
public class OtPolicy extends BaseEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(nullable = false)
    private String name;

    @Column(name = "min_ot_minutes", nullable = false)
    private int minOtMinutes = 30;

    @Column(name = "rounding_minutes", nullable = false)
    private int roundingMinutes = 15;

    @Column(name = "allow_pre_shift_ot", nullable = false)
    private boolean allowPreShiftOt;

    @Column(name = "max_ot_minutes")
    private Integer maxOtMinutes;

    @Column(name = "requires_approval", nullable = false)
    private boolean requiresApproval;

    protected OtPolicy() {
    }

    public OtPolicy(UUID companyId) {
        this.companyId = companyId;
    }

    public OtPolicyDefinition toDefinition() {
        return new OtPolicyDefinition(getId(), minOtMinutes, roundingMinutes, allowPreShiftOt, maxOtMinutes,
                requiresApproval);
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getMinOtMinutes() {
        return minOtMinutes;
    }

    public void setMinOtMinutes(int minOtMinutes) {
        this.minOtMinutes = minOtMinutes;
    }

    public int getRoundingMinutes() {
        return roundingMinutes;
    }

    public void setRoundingMinutes(int roundingMinutes) {
        this.roundingMinutes = roundingMinutes;
    }

    public boolean isAllowPreShiftOt() {
        return allowPreShiftOt;
    }

    public void setAllowPreShiftOt(boolean allowPreShiftOt) {
        this.allowPreShiftOt = allowPreShiftOt;
    }

    public Integer getMaxOtMinutes() {
        return maxOtMinutes;
    }

    public void setMaxOtMinutes(Integer maxOtMinutes) {
        this.maxOtMinutes = maxOtMinutes;
    }

    public boolean isRequiresApproval() {
        return requiresApproval;
    }

    public void setRequiresApproval(boolean requiresApproval) {
        this.requiresApproval = requiresApproval;
    }
}
