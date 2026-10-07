package com.is2c.hrm.audit.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.is2c.hrm.security.service.SecurityEvent;

/** Persists security events (login success/failure, lockout, permission denied, …) to the audit log. */
@Component
public class SecurityEventAuditor {

    private final AuditService audit;

    public SecurityEventAuditor(AuditService audit) {
        this.audit = audit;
    }

    @EventListener
    public void on(SecurityEvent event) {
        audit.record(AuditEntry.of(event.type().name(), "SECURITY", event.userId()).reason(event.detail()),
                event.userId(), event.username());
    }
}
