package com.is2c.hrm.leave.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID>, JpaSpecificationExecutor<LeaveRequest> {

    @Query("""
            select r from LeaveRequest r where r.employeeId = ?1 and r.fromDate <= ?3 and r.toDate >= ?2
            and r.status in (com.is2c.hrm.leave.domain.LeaveRequest.Status.PENDING, com.is2c.hrm.leave.domain.LeaveRequest.Status.APPROVED)
            """)
    List<LeaveRequest> findActiveOverlapping(UUID employeeId, LocalDate from, LocalDate to);

    @Query("""
            select r from LeaveRequest r where r.employeeId in ?1 and r.fromDate <= ?3 and r.toDate >= ?2
            and r.status = com.is2c.hrm.leave.domain.LeaveRequest.Status.APPROVED
            """)
    List<LeaveRequest> findApproved(Collection<UUID> employeeIds, LocalDate from, LocalDate to);

    List<LeaveRequest> findByEmployeeIdOrderByFromDateDesc(UUID employeeId);
}
