package com.is2c.hrm.employee.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomFieldDefRepository extends JpaRepository<CustomFieldDef, UUID> {

    List<CustomFieldDef> findByCompanyIdOrderByDisplayOrderAscKeyAsc(UUID companyId);

    Optional<CustomFieldDef> findByCompanyIdAndKey(UUID companyId, String key);
}
