package com.is2c.hrm.employee.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.Repository;

public interface EmploymentHistoryRepository extends Repository<EmploymentHistory, UUID> {

    EmploymentHistory save(EmploymentHistory history);

    List<EmploymentHistory> findByEmployeeIdOrderByEffectiveDateDescRecordedAtDesc(UUID employeeId);
}
