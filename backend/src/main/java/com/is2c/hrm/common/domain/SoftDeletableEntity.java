package com.is2c.hrm.common.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

/**
 * Master record that is never physically deleted (NFR-014). Concrete entities add
 * {@code @SQLRestriction("deleted_at IS NULL")} so deleted rows are invisible to normal queries.
 */
@MappedSuperclass
public abstract class SoftDeletableEntity extends BaseEntity {

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void markDeleted(Instant when) {
        this.deletedAt = when;
    }
}
