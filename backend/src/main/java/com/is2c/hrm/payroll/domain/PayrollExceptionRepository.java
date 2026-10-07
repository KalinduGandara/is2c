package com.is2c.hrm.payroll.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PayrollExceptionRepository extends JpaRepository<PayrollException, UUID> {

    List<PayrollException> findByPeriodId(UUID periodId);

    boolean existsByPeriodIdAndBlockingTrue(UUID periodId);

    @Modifying
    @Query("delete from PayrollException e where e.periodId = ?1")
    int deleteByPeriod(UUID periodId);
}
