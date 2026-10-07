package com.is2c.hrm.attendance.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.is2c.hrm.attendance.service.AttendanceCalculator.DayStatus;
import com.is2c.hrm.attendance.service.AttendanceCalculator.Result;
import com.is2c.hrm.common.domain.Ids;
import com.is2c.hrm.roster.api.DaySchedule.DayType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Processed attendance of one employee on one date (FR-048). Times are company-local. */
@Entity
@Table(name = "attendance_daily")
public class AttendanceDaily {

    @Id
    private UUID id = Ids.newId();

    @Column(name = "employee_id", nullable = false, updatable = false)
    private UUID employeeId;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "work_date", nullable = false, updatable = false)
    private LocalDate workDate;

    @Column(name = "shift_id")
    private UUID shiftId;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_type", nullable = false)
    private DayType dayType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DayStatus status;

    @Column(name = "first_in")
    private LocalDateTime firstIn;

    @Column(name = "last_out")
    private LocalDateTime lastOut;

    @Column(name = "scheduled_min", nullable = false)
    private int scheduledMinutes;

    @Column(name = "worked_min", nullable = false)
    private int workedMinutes;

    @Column(name = "late_min", nullable = false)
    private int lateMinutes;

    @Column(name = "early_min", nullable = false)
    private int earlyMinutes;

    @Column(name = "short_min", nullable = false)
    private int shortMinutes;

    @Column(name = "ot_workday_min", nullable = false)
    private int otWorkdayMinutes;

    @Column(name = "ot_offday_min", nullable = false)
    private int otOffdayMinutes;

    @Column(name = "ot_holiday_min", nullable = false)
    private int otHolidayMinutes;

    @Column(name = "extra_min", nullable = false)
    private int extraMinutes;

    @Column(name = "ot_requires_approval", nullable = false)
    private boolean otRequiresApproval;

    @Column(name = "ot_approved", nullable = false)
    private boolean otApproved;

    @Column(name = "ot_rejected", nullable = false)
    private boolean otRejected;

    @Column(name = "absent_fraction", nullable = false)
    private BigDecimal absentFraction;

    @Column(name = "leave_fraction", nullable = false)
    private BigDecimal leaveFraction;

    @Column(name = "leave_paid", nullable = false)
    private boolean leavePaid;

    @Column(name = "leave_request_id")
    private UUID leaveRequestId;

    @Column(nullable = false)
    private boolean manual;

    @Column(nullable = false)
    private String exceptions;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected AttendanceDaily() {
    }

    public AttendanceDaily(UUID employeeId, UUID companyId, LocalDate workDate) {
        this.employeeId = employeeId;
        this.companyId = companyId;
        this.workDate = workDate;
    }

    /** Applies a calculation result; returns true when any payroll-relevant value changed. */
    public boolean apply(Result r, Instant now) {
        int previousOt = otWorkdayMinutes + otOffdayMinutes + otHolidayMinutes;
        boolean changed = !Objects.equals(status, r.status()) || workedMinutes != r.workedMinutes()
                || lateMinutes != r.lateMinutes() || earlyMinutes != r.earlyMinutes() || shortMinutes != r.shortMinutes()
                || otWorkdayMinutes != r.otWorkdayMinutes() || otOffdayMinutes != r.otOffdayMinutes()
                || otHolidayMinutes != r.otHolidayMinutes() || extraMinutes != r.extraMinutes()
                || absentFraction == null || absentFraction.compareTo(r.absentFraction()) != 0
                || leaveFraction == null || leaveFraction.compareTo(r.leaveFraction()) != 0 || leavePaid != r.leavePaid();
        shiftId = r.shiftId();
        dayType = r.dayType();
        status = r.status();
        firstIn = r.firstIn();
        lastOut = r.lastOut();
        scheduledMinutes = r.scheduledMinutes();
        workedMinutes = r.workedMinutes();
        lateMinutes = r.lateMinutes();
        earlyMinutes = r.earlyMinutes();
        shortMinutes = r.shortMinutes();
        otWorkdayMinutes = r.otWorkdayMinutes();
        otOffdayMinutes = r.otOffdayMinutes();
        otHolidayMinutes = r.otHolidayMinutes();
        extraMinutes = r.extraMinutes();
        otRequiresApproval = r.otRequiresApproval();
        if (previousOt != otWorkdayMinutes + otOffdayMinutes + otHolidayMinutes || !otRequiresApproval) {
            // A changed OT amount needs a fresh decision.
            otApproved = false;
            otRejected = false;
        }
        absentFraction = r.absentFraction();
        leaveFraction = r.leaveFraction();
        leavePaid = r.leavePaid();
        leaveRequestId = r.leaveRequestId();
        manual = r.manual();
        // an OT decision that still stands (same OT amount) is not pending any more
        exceptions = String.join(",", r.exceptions().stream()
                .filter(e -> !("OT_PENDING".equals(e) && (otApproved || otRejected))).toList());
        processedAt = now;
        return changed;
    }

    public List<String> exceptionList() {
        return exceptions == null || exceptions.isEmpty() ? List.of() : Arrays.asList(exceptions.split(","));
    }

    /** OT minutes payable by payroll: all OT unless approval is required and not granted (FR-056). */
    public boolean otPayable() {
        return !otRequiresApproval || (otApproved && !otRejected);
    }

    public void decideOt(boolean approve) {
        otApproved = approve;
        otRejected = !approve;
        if (approve || otRejected) {
            exceptions = String.join(",", exceptionList().stream().filter(e -> !"OT_PENDING".equals(e)).toList());
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public UUID getShiftId() {
        return shiftId;
    }

    public DayType getDayType() {
        return dayType;
    }

    public DayStatus getStatus() {
        return status;
    }

    public LocalDateTime getFirstIn() {
        return firstIn;
    }

    public LocalDateTime getLastOut() {
        return lastOut;
    }

    public int getScheduledMinutes() {
        return scheduledMinutes;
    }

    public int getWorkedMinutes() {
        return workedMinutes;
    }

    public int getLateMinutes() {
        return lateMinutes;
    }

    public int getEarlyMinutes() {
        return earlyMinutes;
    }

    public int getShortMinutes() {
        return shortMinutes;
    }

    public int getOtWorkdayMinutes() {
        return otWorkdayMinutes;
    }

    public int getOtOffdayMinutes() {
        return otOffdayMinutes;
    }

    public int getOtHolidayMinutes() {
        return otHolidayMinutes;
    }

    public int getExtraMinutes() {
        return extraMinutes;
    }

    public boolean isOtRequiresApproval() {
        return otRequiresApproval;
    }

    public boolean isOtApproved() {
        return otApproved;
    }

    public boolean isOtRejected() {
        return otRejected;
    }

    public BigDecimal getAbsentFraction() {
        return absentFraction;
    }

    public BigDecimal getLeaveFraction() {
        return leaveFraction;
    }

    public boolean isLeavePaid() {
        return leavePaid;
    }

    public UUID getLeaveRequestId() {
        return leaveRequestId;
    }

    public boolean isManual() {
        return manual;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
