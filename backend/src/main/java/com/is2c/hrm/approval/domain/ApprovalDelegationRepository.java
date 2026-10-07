package com.is2c.hrm.approval.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalDelegationRepository extends JpaRepository<ApprovalDelegation, UUID> {

    List<ApprovalDelegation> findByDelegatorUserIdAndActiveTrue(UUID delegatorUserId);

    List<ApprovalDelegation> findByDelegateUserIdAndActiveTrue(UUID delegateUserId);

    List<ApprovalDelegation> findByActiveTrue();
}
