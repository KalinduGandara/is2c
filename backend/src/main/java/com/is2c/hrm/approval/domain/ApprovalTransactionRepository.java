package com.is2c.hrm.approval.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalTransactionRepository extends JpaRepository<ApprovalTransaction, UUID> {

    List<ApprovalTransaction> findByStatusAndCompanyIdInOrderBySubmittedAt(ApprovalTransaction.Status status,
            Collection<UUID> companyIds);

    List<ApprovalTransaction> findByStatusOrderBySubmittedAt(ApprovalTransaction.Status status);

    List<ApprovalTransaction> findByRequesterUserIdOrderBySubmittedAtDesc(UUID userId);

    Optional<ApprovalTransaction> findFirstByTxnTypeAndReferenceIdAndStatus(String type, UUID referenceId,
            ApprovalTransaction.Status status);
}
