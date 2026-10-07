package com.is2c.hrm.org.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmploymentCategoryRepository extends JpaRepository<EmploymentCategory, UUID> {

    List<EmploymentCategory> findByCompanyIdOrderByCode(UUID companyId);

    Optional<EmploymentCategory> findByCompanyIdAndCodeIgnoreCase(UUID companyId, String code);
}
