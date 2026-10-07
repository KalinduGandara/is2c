package com.is2c.hrm.leave.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, UUID> {

    Optional<LeaveBalance> findByEmployeeIdAndLeaveTypeIdAndYear(UUID employeeId, UUID leaveTypeId, int year);

    List<LeaveBalance> findByEmployeeIdAndYear(UUID employeeId, int year);

    List<LeaveBalance> findByEmployeeIdInAndYear(Collection<UUID> employeeIds, int year);

    List<LeaveBalance> findByLeaveTypeIdAndYear(UUID leaveTypeId, int year);

    boolean existsByEmployeeId(UUID employeeId);
}
