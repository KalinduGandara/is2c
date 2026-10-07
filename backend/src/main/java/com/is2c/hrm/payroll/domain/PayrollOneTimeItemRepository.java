package com.is2c.hrm.payroll.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollOneTimeItemRepository extends JpaRepository<PayrollOneTimeItem, UUID> {

    List<PayrollOneTimeItem> findByPeriodId(UUID periodId);
}
