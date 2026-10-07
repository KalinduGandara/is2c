package com.is2c.hrm.employee.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record EmergencyContact(
        @Column(name = "name", nullable = false) String name,
        @Column(name = "relationship", nullable = false) String relationship,
        @Column(name = "phone", nullable = false) String phone) {
}
