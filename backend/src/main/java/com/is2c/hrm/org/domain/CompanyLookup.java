package com.is2c.hrm.org.domain;

import java.util.UUID;

import com.is2c.hrm.common.domain.SoftDeletableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

/** Per-company code/name lookup (FR-013). */
@MappedSuperclass
public abstract class CompanyLookup extends SoftDeletableEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    protected CompanyLookup() {
    }

    protected CompanyLookup(UUID companyId) {
        this.companyId = companyId;
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
}
