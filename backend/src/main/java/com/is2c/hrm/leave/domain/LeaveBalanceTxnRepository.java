package com.is2c.hrm.leave.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.Repository;

public interface LeaveBalanceTxnRepository extends Repository<LeaveBalanceTxn, UUID> {

    LeaveBalanceTxn save(LeaveBalanceTxn txn);

    boolean existsByBalanceIdAndTypeAndReference(UUID balanceId, LeaveBalanceTxn.Type type, String reference);

    List<LeaveBalanceTxn> findByBalanceIdOrderByCreatedAt(UUID balanceId);
}
