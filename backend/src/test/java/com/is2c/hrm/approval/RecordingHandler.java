package com.is2c.hrm.approval;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import com.is2c.hrm.approval.api.ApprovalHandler;

/** Test handler for the "TEST" transaction type, recording callbacks. */
public class RecordingHandler implements ApprovalHandler {

    public final List<String> calls = Collections.synchronizedList(new ArrayList<>());

    @Override
    public String type() {
        return "TEST";
    }

    @Override
    public void onApproved(UUID referenceId) {
        calls.add("approved:" + referenceId);
    }

    @Override
    public void onRejected(UUID referenceId, String comment) {
        calls.add("rejected:" + referenceId + ":" + comment);
    }

    @Override
    public void onCancelled(UUID referenceId) {
        calls.add("cancelled:" + referenceId);
    }
}
