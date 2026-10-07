package com.is2c.hrm.platform.db;

import java.util.UUID;

/**
 * The only seam for vendor-specific database behaviour (NFR-005, CL-A6). Domain modules use JPA repositories;
 * anything that needs vendor SQL goes here. To add a vendor: implement this interface, activate it with
 * {@code hrm.db.vendor=<vendor>}, and add migrations under {@code db/migration/<vendor>}.
 */
public interface DatabasePlatform {

    /** Flyway vendor id, e.g. {@code postgresql}. */
    String vendor();

    /**
     * Atomically returns the next value of a named counter scoped to a company, starting at 1.
     * Must never return the same value twice, including under concurrent callers.
     */
    long nextSequenceValue(UUID companyId, String key);
}
