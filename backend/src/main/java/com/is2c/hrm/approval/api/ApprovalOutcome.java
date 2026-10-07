package com.is2c.hrm.approval.api;

import java.util.UUID;

/** Result of a submission: the transaction id and whether it was auto-approved (no workflow applies). */
public record ApprovalOutcome(UUID transactionId, boolean autoApproved) {
}
