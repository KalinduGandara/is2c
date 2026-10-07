package com.is2c.hrm.org.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BranchRepository extends JpaRepository<Branch, UUID> {

    List<Branch> findByCompanyIdOrderByCode(UUID companyId);

    List<Branch> findByIdIn(Collection<UUID> ids);

    Optional<Branch> findByCompanyIdAndCodeIgnoreCase(UUID companyId, String code);
}
