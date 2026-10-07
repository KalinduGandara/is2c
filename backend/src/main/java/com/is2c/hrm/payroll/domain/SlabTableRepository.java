package com.is2c.hrm.payroll.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SlabTableRepository extends JpaRepository<SlabTable, UUID> {

    List<SlabTable> findByCompanyIdOrderByCode(UUID companyId);

    Optional<SlabTable> findByCompanyIdAndCode(UUID companyId, String code);
}
