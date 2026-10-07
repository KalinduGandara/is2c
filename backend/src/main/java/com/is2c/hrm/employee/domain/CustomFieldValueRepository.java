package com.is2c.hrm.employee.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomFieldValueRepository extends JpaRepository<CustomFieldValue, CustomFieldValue.Key> {

    List<CustomFieldValue> findByIdEmployeeId(UUID employeeId);
}
