package com.is2c.hrm.shift.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RotationAssignmentRepository extends JpaRepository<RotationAssignment, UUID> {

    List<RotationAssignment> findByEmployeeIdOrderByStartDate(UUID employeeId);

    long countByPatternId(UUID patternId);

    @Query("""
            select a from RotationAssignment a where a.employeeId in ?1
            and a.startDate <= ?3 and (a.endDate is null or a.endDate >= ?2)
            """)
    List<RotationAssignment> findActiveBetween(Collection<UUID> employeeIds, LocalDate from, LocalDate to);
}
