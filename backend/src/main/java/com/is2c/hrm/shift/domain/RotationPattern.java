package com.is2c.hrm.shift.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.is2c.hrm.common.domain.BaseEntity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

/** Repeating cycle of shifts/OFF days (FR-031). A null entry is an OFF day. */
@Entity
@Table(name = "rotation_pattern")
public class RotationPattern extends BaseEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(nullable = false)
    private String name;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "rotation_pattern_day", joinColumns = @JoinColumn(name = "pattern_id"))
    @OrderColumn(name = "day_index")
    @Column(name = "shift_id")
    private List<UUID> days = new ArrayList<>();

    protected RotationPattern() {
    }

    public RotationPattern(UUID companyId) {
        this.companyId = companyId;
    }

    /** Shift id for a day offset from the cycle start (null = OFF). */
    public UUID shiftOnOffset(long daysSinceStart) {
        int n = days.size();
        return days.get((int) Math.floorMod(daysSinceStart, n));
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

    public List<UUID> getDays() {
        return days;
    }
}
