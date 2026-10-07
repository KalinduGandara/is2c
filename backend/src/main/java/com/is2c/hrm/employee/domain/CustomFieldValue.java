package com.is2c.hrm.employee.domain;

import java.io.Serializable;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "custom_field_value")
public class CustomFieldValue {

    @Embeddable
    public record Key(@Column(name = "employee_id") UUID employeeId,
            @Column(name = "field_def_id") UUID fieldDefId) implements Serializable {
    }

    @EmbeddedId
    private Key id;

    @Column(name = "value_text", nullable = false)
    private String value;

    protected CustomFieldValue() {
    }

    public CustomFieldValue(UUID employeeId, UUID fieldDefId, String value) {
        this.id = new Key(employeeId, fieldDefId);
        this.value = value;
    }

    public Key getId() {
        return id;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
