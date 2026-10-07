package com.is2c.hrm.org.domain;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.is2c.hrm.common.domain.BaseEntity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

/** FR-025 holiday, applying to all branches of the company or to selected branches. */
@Entity
@Table(name = "holiday")
public class Holiday extends BaseEntity {

    public enum Type { PUBLIC, MERCANTILE, COMPANY, OTHER }

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "holiday_date", nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "holiday_type", nullable = false)
    private Type type;

    @Column(name = "all_branches", nullable = false)
    private boolean allBranches = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "holiday_branch", joinColumns = @JoinColumn(name = "holiday_id"))
    @Column(name = "branch_id")
    private Set<UUID> branchIds = new HashSet<>();

    protected Holiday() {
    }

    public Holiday(UUID companyId) {
        this.companyId = companyId;
    }

    /** True when this holiday applies to an employee in the given branch. */
    public boolean appliesTo(UUID branchId) {
        return allBranches || branchIds.contains(branchId);
    }

    /** True when both holidays could apply to the same branch. */
    public boolean overlaps(boolean otherAll, Set<UUID> otherBranches) {
        if (allBranches || otherAll) {
            return true;
        }
        return branchIds.stream().anyMatch(otherBranches::contains);
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public boolean isAllBranches() {
        return allBranches;
    }

    public void setAllBranches(boolean allBranches) {
        this.allBranches = allBranches;
    }

    public Set<UUID> getBranchIds() {
        return branchIds;
    }
}
