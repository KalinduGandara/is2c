package com.is2c.hrm.attendance.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AttendanceAdjustmentRepository extends JpaRepository<AttendanceAdjustment, UUID> {

    @Query("""
            select a from AttendanceAdjustment a where a.employeeId in ?1 and a.workDate between ?2 and ?3
            and a.status = com.is2c.hrm.attendance.domain.AttendanceAdjustment.Status.APPLIED
            order by a.requestedAt
            """)
    List<AttendanceAdjustment> findApplied(Collection<UUID> employeeIds, LocalDate from, LocalDate to);

    List<AttendanceAdjustment> findByEmployeeIdAndWorkDateOrderByRequestedAtDesc(UUID employeeId, LocalDate date);

    List<AttendanceAdjustment> findByCompanyIdAndWorkDateBetweenOrderByRequestedAtDesc(UUID companyId, LocalDate from,
            LocalDate to);

    boolean existsByEmployeeIdAndWorkDateAndStatus(UUID employeeId, LocalDate date, AttendanceAdjustment.Status status);
}
