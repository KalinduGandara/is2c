package com.is2c.hrm.audit.domain;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

/** Insert and read only — no update or delete methods are exposed (FR-008). */
public interface AuditLogRepository extends Repository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    AuditLog save(AuditLog log);

    long count();
}
