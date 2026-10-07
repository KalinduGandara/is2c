package com.is2c.hrm.shift.domain;

import java.time.LocalDate;
import java.util.UUID;

import com.is2c.hrm.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** An employee following a rotation pattern from a start date (cycle day 1) (FR-031). */
@Entity
@Table(name = "rotation_assignment")
public class RotationAssignment extends BaseEntity {

    @Column(name = "employee_id", nullable = false, updatable = false)
    private UUID employeeId;

    @Column(name = "pattern_id", nullable = false, updatable = false)
    private UUID patternId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    protected RotationAssignment() {
    }

    public RotationAssignment(UUID employeeId, UUID patternId, LocalDate startDate, LocalDate endDate) {
        this.employeeId = employeeId;
        this.patternId = patternId;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public boolean covers(LocalDate date) {
        return !date.isBefore(startDate) && (endDate == null || !date.isAfter(endDate));
    }

    public boolean overlaps(LocalDate otherStart, LocalDate otherEnd) {
        boolean startsBeforeOtherEnds = otherEnd == null || !startDate.isAfter(otherEnd);
        boolean endsAfterOtherStarts = endDate == null || !endDate.isBefore(otherStart);
        return startsBeforeOtherEnds && endsAfterOtherStarts;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public UUID getPatternId() {
        return patternId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }
}
