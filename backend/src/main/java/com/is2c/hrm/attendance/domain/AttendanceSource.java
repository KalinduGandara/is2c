package com.is2c.hrm.attendance.domain;

import java.util.UUID;

import com.is2c.hrm.attendance.adapter.AdapterType;
import com.is2c.hrm.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** A biometric middleware/device integration or the company's CSV import channel (FR-045). */
@Entity
@Table(name = "attendance_source")
public class AttendanceSource extends BaseEntity {

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "adapter_type", nullable = false)
    private AdapterType adapterType;

    @Column(name = "api_key_prefix")
    private String apiKeyPrefix;

    @Column(name = "api_key_hash")
    private String apiKeyHash;

    @Column(name = "system_source", nullable = false)
    private boolean systemSource;

    @Column(nullable = false)
    private boolean active = true;

    protected AttendanceSource() {
    }

    public AttendanceSource(UUID companyId, String name, AdapterType adapterType, boolean systemSource) {
        this.companyId = companyId;
        this.name = name;
        this.adapterType = adapterType;
        this.systemSource = systemSource;
    }

    public void setKey(String prefix, String hash) {
        this.apiKeyPrefix = prefix;
        this.apiKeyHash = hash;
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

    public AdapterType getAdapterType() {
        return adapterType;
    }

    public String getApiKeyPrefix() {
        return apiKeyPrefix;
    }

    public String getApiKeyHash() {
        return apiKeyHash;
    }

    public boolean isSystemSource() {
        return systemSource;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
