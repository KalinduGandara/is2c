package com.is2c.hrm.payroll.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollComponentRepository extends JpaRepository<PayrollComponent, UUID> {

    List<PayrollComponent> findByCompanyIdOrderBySequenceAscCodeAsc(UUID companyId);

    Optional<PayrollComponent> findByCompanyIdAndCode(UUID companyId, String code);
}
