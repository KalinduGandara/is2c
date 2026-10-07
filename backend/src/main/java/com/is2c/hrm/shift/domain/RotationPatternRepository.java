package com.is2c.hrm.shift.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RotationPatternRepository extends JpaRepository<RotationPattern, UUID> {

    List<RotationPattern> findByCompanyIdOrderByName(UUID companyId);
}
