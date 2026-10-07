package com.is2c.hrm.org.api;

import java.util.UUID;

/** Implemented by modules that reference org records, to block deletion of records in use (FR-011..013). */
public interface OrgReferenceChecker {

    enum RefType { BRANCH, DEPARTMENT, DESIGNATION, GRADE, EMPLOYMENT_CATEGORY }

    /** Number of active employees (or other live records) referencing the org record. */
    long countActiveReferences(RefType type, UUID id);
}
