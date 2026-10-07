package com.is2c.hrm.payroll.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PayrollPeriodRepository extends JpaRepository<PayrollPeriod, UUID> {

    List<PayrollPeriod> findByCompanyIdOrderByYearDescMonthDesc(UUID companyId);

    Optional<PayrollPeriod> findByCompanyIdAndYearAndMonth(UUID companyId, int year, int month);

    @Query("select p from PayrollPeriod p where p.companyId = ?1 and p.startDate <= ?3 and p.endDate >= ?2")
    List<PayrollPeriod> findOverlapping(UUID companyId, LocalDate from, LocalDate to);
}
