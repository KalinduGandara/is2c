package com.is2c.hrm.payroll.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PayrollResultRepository extends JpaRepository<PayrollResult, UUID> {

    List<PayrollResult> findByPeriodIdOrderByEmployeeNumber(UUID periodId);

    Optional<PayrollResult> findByPeriodIdAndEmployeeId(UUID periodId, UUID employeeId);

    List<PayrollResult> findByEmployeeIdAndLockedTrue(UUID employeeId);

    @Modifying
    @Query("delete from PayrollResult r where r.periodId = ?1")
    int deleteByPeriod(UUID periodId);

    boolean existsByEmployeeId(UUID employeeId);
}
