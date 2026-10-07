package com.is2c.hrm.attendance.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface AttendanceEventRepository extends JpaRepository<AttendanceEvent, UUID> {

    @Query("select e.externalId from AttendanceEvent e where e.sourceId = ?1 and e.externalId in ?2")
    List<String> findExistingExternalIds(UUID sourceId, Collection<String> externalIds);

    List<AttendanceEvent> findByCompanyIdAndStatus(UUID companyId, AttendanceEvent.Status status);

    Page<AttendanceEvent> findByCompanyIdAndStatusOrderByEventTimeDesc(UUID companyId, AttendanceEvent.Status status,
            Pageable pageable);

    @Query("""
            select e from AttendanceEvent e where e.employeeId in ?1 and e.eventTime >= ?2 and e.eventTime < ?3
            order by e.eventTime
            """)
    List<AttendanceEvent> findForEmployees(Collection<UUID> employeeIds, Instant from, Instant to);

    boolean existsByEmployeeId(UUID employeeId);
}
