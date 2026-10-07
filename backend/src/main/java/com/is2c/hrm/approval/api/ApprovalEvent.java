package com.is2c.hrm.approval.api;

import java.util.Set;
import java.util.UUID;

/** Published for notifications (FR-074). */
public record ApprovalEvent(Kind kind, UUID transactionId, String type, String summary, UUID requesterUserId,
        Set<UUID> recipientUserIds, String outcome) {

    public enum Kind { REQUESTED, COMPLETED }
}
