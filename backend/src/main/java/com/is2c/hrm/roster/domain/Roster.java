package com.is2c.hrm.roster.domain;

import java.time.LocalDate;
import java.util.UUID;

import com.is2c.hrm.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "roster")
public class Roster extends BaseEntity {

    public enum PeriodType { WEEKLY, MONTHLY }

    public enum Status { DRAFT, PUBLISHED, LOCKED }

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "department_id", updatable = false)
    private UUID departmentId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, updatable = false)
    private PeriodType periodType;

    @Column(name = "start_date", nullable = false, updatable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false, updatable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.DRAFT;

    @Column(nullable = false)
    private int revision;

    protected Roster() {
    }

    public Roster(UUID companyId, UUID departmentId, String name, PeriodType periodType, LocalDate startDate,
            LocalDate endDate) {
        this.companyId = companyId;
        this.departmentId = departmentId;
        this.name = name;
        this.periodType = periodType;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    public int dayCount() {
        return (int) java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    public void publish() {
        status = Status.PUBLISHED;
        revision = 1;
    }

    public int nextRevision() {
        return ++revision;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public PeriodType getPeriodType() {
        return periodType;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public int getRevision() {
        return revision;
    }
}
