package com.is2c.hrm.approval.api;

import java.util.UUID;

/**
 * Implemented by a module that submits transactions of {@link #type()} to the approval engine (FR-071).
 * Callbacks run inside the approval transaction.
 */
public interface ApprovalHandler {

    /** Transaction type code, e.g. {@link ApprovalTypes#LEAVE_REQUEST}. */
    String type();

    void onApproved(UUID referenceId);

    void onRejected(UUID referenceId, String comment);

    void onCancelled(UUID referenceId);
}
