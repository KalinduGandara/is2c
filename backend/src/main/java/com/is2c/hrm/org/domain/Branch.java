package com.is2c.hrm.org.domain;

import java.util.UUID;

import org.hibernate.annotations.SQLRestriction;

import com.is2c.hrm.common.domain.SoftDeletableEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "branch")
@SQLRestriction("deleted_at IS NULL")
public class Branch extends SoftDeletableEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    private String address;
    private String phone;

    protected Branch() {
    }

    public Branch(UUID companyId, String code) {
        this.companyId = companyId;
        this.code = code;
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

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
