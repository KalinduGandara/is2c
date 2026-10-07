package com.is2c.hrm.org.domain;

import java.util.UUID;

import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "employment_category")
@SQLRestriction("deleted_at IS NULL")
public class EmploymentCategory extends CompanyLookup {

    @Column(name = "ot_eligible", nullable = false)
    private boolean otEligible = true;

    protected EmploymentCategory() {
    }

    public boolean isOtEligible() {
        return otEligible;
    }

    public void setOtEligible(boolean otEligible) {
        this.otEligible = otEligible;
    }

    public EmploymentCategory(UUID companyId) {
        super(companyId);
    }
}
