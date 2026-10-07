package com.is2c.hrm.org.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, UUID> {

    List<Department> findByCompanyIdOrderByCode(UUID companyId);

    List<Department> findByCompanyIdIn(Collection<UUID> companyIds);

    List<Department> findByIdIn(Collection<UUID> ids);

    List<Department> findByParentId(UUID parentId);

    Optional<Department> findByCompanyIdAndCodeIgnoreCase(UUID companyId, String code);
}
