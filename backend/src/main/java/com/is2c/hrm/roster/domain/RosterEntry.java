package com.is2c.hrm.roster.domain;

import java.time.LocalDate;
import java.util.UUID;

import com.is2c.hrm.common.domain.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One roster cell: a shift, or OFF when {@code shiftId} is null (FR-036). */
@Entity
@Table(name = "roster_entry")
public class RosterEntry {

    @Id
    private UUID id = Ids.newId();

    @Column(name = "roster_id", nullable = false, updatable = false)
    private UUID rosterId;

    @Column(name = "employee_id", nullable = false, updatable = false)
    private UUID employeeId;

    @Column(name = "work_date", nullable = false, updatable = false)
    private LocalDate workDate;

    @Column(name = "shift_id")
    private UUID shiftId;

    protected RosterEntry() {
    }

    public RosterEntry(UUID rosterId, UUID employeeId, LocalDate workDate, UUID shiftId) {
        this.rosterId = rosterId;
        this.employeeId = employeeId;
        this.workDate = workDate;
        this.shiftId = shiftId;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRosterId() {
        return rosterId;
    }

    public UUID getEmployeeId() {
        return employeeId;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public UUID getShiftId() {
        return shiftId;
    }

    public void setShiftId(UUID shiftId) {
        this.shiftId = shiftId;
    }
}
