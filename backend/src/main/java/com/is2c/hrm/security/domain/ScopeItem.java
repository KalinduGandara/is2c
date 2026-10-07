package com.is2c.hrm.security.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public record ScopeItem(
        @Enumerated(EnumType.STRING) @Column(name = "item_type") ScopeItemType type,
        @Column(name = "item_id") UUID itemId) {

    public enum ScopeItemType { COMPANY, BRANCH, DEPARTMENT }
}
