package com.is2c.hrm.approval.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalWorkflowRepository extends JpaRepository<ApprovalWorkflow, UUID> {

    List<ApprovalWorkflow> findByCompanyIdOrderByTxnType(UUID companyId);

    List<ApprovalWorkflow> findByCompanyIdAndTxnType(UUID companyId, String txnType);
}
