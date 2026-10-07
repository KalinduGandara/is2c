package com.is2c.hrm.shift.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OtPolicyRepository extends JpaRepository<OtPolicy, UUID> {

    List<OtPolicy> findByCompanyIdOrderByName(UUID companyId);
}
