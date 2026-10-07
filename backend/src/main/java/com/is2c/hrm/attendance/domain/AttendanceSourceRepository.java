package com.is2c.hrm.attendance.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AttendanceSourceRepository extends JpaRepository<AttendanceSource, UUID> {

    List<AttendanceSource> findByCompanyIdOrderByName(UUID companyId);

    Optional<AttendanceSource> findByApiKeyPrefix(String prefix);

    Optional<AttendanceSource> findByCompanyIdAndSystemSourceTrue(UUID companyId);
}
