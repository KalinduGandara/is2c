package com.is2c.hrm.org.domain;

import java.util.UUID;

import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "designation")
@SQLRestriction("deleted_at IS NULL")
public class Designation extends CompanyLookup {

    protected Designation() {
    }

    public Designation(UUID companyId) {
        super(companyId);
    }
}
