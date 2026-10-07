package com.is2c.hrm.leave.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, UUID> {

    List<LeaveType> findByCompanyIdOrderByCode(UUID companyId);

    Optional<LeaveType> findByCompanyIdAndCodeIgnoreCase(UUID companyId, String code);
}
