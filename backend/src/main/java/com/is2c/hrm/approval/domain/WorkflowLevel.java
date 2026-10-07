package com.is2c.hrm.approval.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

@Embeddable
public record WorkflowLevel(
        @Enumerated(EnumType.STRING) @Column(name = "approver_type", nullable = false) ApproverType approverType,
        @Column(name = "role_code") String roleCode,
        @Column(name = "user_id") UUID userId) {

    public enum ApproverType { REPORTING_MANAGER, ROLE, USER }
}
