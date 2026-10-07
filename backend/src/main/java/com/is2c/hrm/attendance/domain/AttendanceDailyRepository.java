package com.is2c.hrm.attendance.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AttendanceDailyRepository extends JpaRepository<AttendanceDaily, UUID>,
        JpaSpecificationExecutor<AttendanceDaily> {

    List<AttendanceDaily> findByEmployeeIdInAndWorkDateBetween(Collection<UUID> employeeIds, LocalDate from, LocalDate to);

    Optional<AttendanceDaily> findByEmployeeIdAndWorkDate(UUID employeeId, LocalDate date);

    List<AttendanceDaily> findByEmployeeIdAndWorkDateBetweenOrderByWorkDate(UUID employeeId, LocalDate from, LocalDate to);

    List<AttendanceDaily> findByCompanyIdAndWorkDateBetween(UUID companyId, LocalDate from, LocalDate to);

    boolean existsByEmployeeId(UUID employeeId);
}
