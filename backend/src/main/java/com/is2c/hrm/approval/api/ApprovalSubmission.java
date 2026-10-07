package com.is2c.hrm.approval.api;

import java.util.UUID;

/**
 * A request for approval. {@code subjectEmployeeId} (who the transaction is about) drives routing; it is null
 * for company-level transactions such as payroll.
 */
public record ApprovalSubmission(String type, UUID referenceId, UUID companyId, UUID subjectEmployeeId,
        String summary) {
}
