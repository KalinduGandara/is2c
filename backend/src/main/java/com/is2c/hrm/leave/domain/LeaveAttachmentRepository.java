package com.is2c.hrm.leave.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveAttachmentRepository extends JpaRepository<LeaveAttachment, UUID> {

    List<LeaveAttachment> findByRequestId(UUID requestId);
}
