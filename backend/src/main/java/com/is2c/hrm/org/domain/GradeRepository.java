package com.is2c.hrm.org.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GradeRepository extends JpaRepository<Grade, UUID> {

    List<Grade> findByCompanyIdOrderByCode(UUID companyId);

    Optional<Grade> findByCompanyIdAndCodeIgnoreCase(UUID companyId, String code);
}
