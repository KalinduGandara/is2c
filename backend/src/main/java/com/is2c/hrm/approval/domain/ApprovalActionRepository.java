package com.is2c.hrm.approval.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.Repository;

public interface ApprovalActionRepository extends Repository<ApprovalAction, UUID> {

    ApprovalAction save(ApprovalAction action);

    List<ApprovalAction> findByTransactionIdOrderByActedAt(UUID transactionId);
}
