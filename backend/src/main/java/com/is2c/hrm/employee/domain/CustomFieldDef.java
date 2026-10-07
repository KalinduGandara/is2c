package com.is2c.hrm.employee.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.is2c.hrm.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Customer-specific employee field (FR-019). */
@Entity
@Table(name = "custom_field_def")
public class CustomFieldDef extends BaseEntity {

    public enum FieldType { TEXT, NUMBER, DATE, BOOLEAN, SELECT }

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "field_key", nullable = false, updatable = false)
    private String key;

    @Column(nullable = false)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "field_type", nullable = false, updatable = false)
    private FieldType type;

    @Column(name = "options_csv")
    private String optionsCsv;

    @Column(nullable = false)
    private boolean required;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    protected CustomFieldDef() {
    }

    public CustomFieldDef(UUID companyId, String key, FieldType type) {
        this.companyId = companyId;
        this.key = key;
        this.type = type;
    }

    public List<String> options() {
        if (optionsCsv == null || optionsCsv.isBlank()) {
            return List.of();
        }
        return new ArrayList<>(Arrays.stream(optionsCsv.split("\\|")).map(String::trim).filter(s -> !s.isEmpty()).toList());
    }

    public void setOptions(List<String> options) {
        this.optionsCsv = options == null ? null : String.join("|", options);
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public String getKey() {
        return key;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public FieldType getType() {
        return type;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }
}
