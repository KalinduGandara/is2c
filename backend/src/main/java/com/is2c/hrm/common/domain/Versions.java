package com.is2c.hrm.common.domain;

import java.util.Objects;

import com.is2c.hrm.common.error.ConflictException;

/** Optimistic-lock check for updates that carry the client's last-seen version (NFR-017). */
public final class Versions {

    private Versions() {
    }

    public static void check(BaseEntity entity, Long clientVersion) {
        if (clientVersion != null && !Objects.equals(entity.getVersion(), clientVersion)) {
            throw new ConflictException("OPTIMISTIC_LOCK", "The record was changed by another user. Reload and try again.");
        }
    }
}
