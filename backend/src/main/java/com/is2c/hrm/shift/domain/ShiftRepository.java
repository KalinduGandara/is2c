package com.is2c.hrm.shift.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ShiftRepository extends JpaRepository<Shift, UUID> {

    List<Shift> findByCompanyIdOrderByCode(UUID companyId);

    Optional<Shift> findByCompanyIdAndCodeIgnoreCase(UUID companyId, String code);

    long countByOtPolicyId(UUID otPolicyId);
}
