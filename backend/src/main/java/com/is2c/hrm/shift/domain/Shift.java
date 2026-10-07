package com.is2c.hrm.shift.domain;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.is2c.hrm.common.domain.BaseEntity;
import com.is2c.hrm.shift.api.OtPolicyDefinition;
import com.is2c.hrm.shift.api.ShiftDefinition;
import com.is2c.hrm.shift.api.ShiftDefinition.BreakMode;
import com.is2c.hrm.shift.api.ShiftDefinition.Session;
import com.is2c.hrm.shift.api.ShiftDefinition.ShiftType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Shift template (FR-030). Never deleted once used; deactivated instead. */
@Entity
@Table(name = "shift")
public class Shift extends BaseEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;
    @Column(name = "code")
    private String code;

    @Column(name = "name")
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "shift_type")
    private ShiftType type;

    @Column(name = "color")
    private String color;

    @Column(name = "s1_start")
    private LocalTime s1Start;

    @Column(name = "s1_end")
    private LocalTime s1End;

    @Column(name = "s2_start")
    private LocalTime s2Start;

    @Column(name = "s2_end")
    private LocalTime s2End;

    @Column(name = "flex_earliest_start")
    private LocalTime flexEarliestStart;

    @Column(name = "flex_latest_end")
    private LocalTime flexLatestEnd;

    @Column(name = "required_minutes")
    private Integer requiredMinutes;

    @Column(name = "late_grace_min")
    private int lateGraceMinutes;

    @Column(name = "early_grace_min")
    private int earlyGraceMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "break_mode")
    private BreakMode breakMode;

    @Column(name = "break_minutes")
    private int breakMinutes;

    @Column(name = "break_threshold_min")
    private int breakThresholdMinutes;

    @Column(name = "half_day_threshold_min")
    private Integer halfDayThresholdMinutes;

    @Column(name = "ot_policy_id")
    private UUID otPolicyId;

    @Column(name = "window_before_min")
    private Integer windowBeforeMinutes;

    @Column(name = "window_after_min")
    private Integer windowAfterMinutes;

    @Column(name = "active")
    private boolean active = true;

    protected Shift() {
    }

    public Shift(UUID companyId) {
        this.companyId = companyId;
    }

    public List<Session> sessions() {
        List<Session> list = new ArrayList<>();
        if (s1Start != null && s1End != null) {
            list.add(new Session(s1Start, s1End));
        }
        if (s2Start != null && s2End != null) {
            list.add(new Session(s2Start, s2End));
        }
        return list;
    }

    public ShiftDefinition toDefinition(OtPolicyDefinition otPolicy) {
        return new ShiftDefinition(getId(), code, type, sessions(), flexEarliestStart, flexLatestEnd, requiredMinutes,
                lateGraceMinutes, earlyGraceMinutes, breakMode, breakMinutes, breakThresholdMinutes,
                halfDayThresholdMinutes, otPolicy, windowBeforeMinutes, windowAfterMinutes);
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

    public ShiftType getType() {
        return type;
    }

    public void setType(ShiftType type) {
        this.type = type;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public LocalTime getS1Start() {
        return s1Start;
    }

    public void setS1Start(LocalTime s1Start) {
        this.s1Start = s1Start;
    }

    public LocalTime getS1End() {
        return s1End;
    }

    public void setS1End(LocalTime s1End) {
        this.s1End = s1End;
    }

    public LocalTime getS2Start() {
        return s2Start;
    }

    public void setS2Start(LocalTime s2Start) {
        this.s2Start = s2Start;
    }

    public LocalTime getS2End() {
        return s2End;
    }

    public void setS2End(LocalTime s2End) {
        this.s2End = s2End;
    }

    public LocalTime getFlexEarliestStart() {
        return flexEarliestStart;
    }

    public void setFlexEarliestStart(LocalTime flexEarliestStart) {
        this.flexEarliestStart = flexEarliestStart;
    }

    public LocalTime getFlexLatestEnd() {
        return flexLatestEnd;
    }

    public void setFlexLatestEnd(LocalTime flexLatestEnd) {
        this.flexLatestEnd = flexLatestEnd;
    }

    public Integer getRequiredMinutes() {
        return requiredMinutes;
    }

    public void setRequiredMinutes(Integer requiredMinutes) {
        this.requiredMinutes = requiredMinutes;
    }

    public int getLateGraceMinutes() {
        return lateGraceMinutes;
    }

    public void setLateGraceMinutes(int lateGraceMinutes) {
        this.lateGraceMinutes = lateGraceMinutes;
    }

    public int getEarlyGraceMinutes() {
        return earlyGraceMinutes;
    }

    public void setEarlyGraceMinutes(int earlyGraceMinutes) {
        this.earlyGraceMinutes = earlyGraceMinutes;
    }

    public BreakMode getBreakMode() {
        return breakMode;
    }

    public void setBreakMode(BreakMode breakMode) {
        this.breakMode = breakMode;
    }

    public int getBreakMinutes() {
        return breakMinutes;
    }

    public void setBreakMinutes(int breakMinutes) {
        this.breakMinutes = breakMinutes;
    }

    public int getBreakThresholdMinutes() {
        return breakThresholdMinutes;
    }

    public void setBreakThresholdMinutes(int breakThresholdMinutes) {
        this.breakThresholdMinutes = breakThresholdMinutes;
    }

    public Integer getHalfDayThresholdMinutes() {
        return halfDayThresholdMinutes;
    }

    public void setHalfDayThresholdMinutes(Integer halfDayThresholdMinutes) {
        this.halfDayThresholdMinutes = halfDayThresholdMinutes;
    }

    public UUID getOtPolicyId() {
        return otPolicyId;
    }

    public void setOtPolicyId(UUID otPolicyId) {
        this.otPolicyId = otPolicyId;
    }

    public Integer getWindowBeforeMinutes() {
        return windowBeforeMinutes;
    }

    public void setWindowBeforeMinutes(Integer windowBeforeMinutes) {
        this.windowBeforeMinutes = windowBeforeMinutes;
    }

    public Integer getWindowAfterMinutes() {
        return windowAfterMinutes;
    }

    public void setWindowAfterMinutes(Integer windowAfterMinutes) {
        this.windowAfterMinutes = windowAfterMinutes;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
