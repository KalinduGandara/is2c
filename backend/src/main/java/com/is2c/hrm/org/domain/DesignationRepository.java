package com.is2c.hrm.org.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DesignationRepository extends JpaRepository<Designation, UUID> {

    List<Designation> findByCompanyIdOrderByCode(UUID companyId);

    Optional<Designation> findByCompanyIdAndCodeIgnoreCase(UUID companyId, String code);
}
