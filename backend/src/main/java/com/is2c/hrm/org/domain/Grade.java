package com.is2c.hrm.org.domain;

import java.util.UUID;

import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "grade")
@SQLRestriction("deleted_at IS NULL")
public class Grade extends CompanyLookup {

    @Column(name = "rank_no", nullable = false)
    private int rank;

    protected Grade() {
    }

    public int getRank() {
        return rank;
    }

    public void setRank(int rank) {
        this.rank = rank;
    }

    public Grade(UUID companyId) {
        super(companyId);
    }
}
