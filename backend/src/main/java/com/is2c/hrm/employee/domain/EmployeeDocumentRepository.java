package com.is2c.hrm.employee.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeDocumentRepository extends JpaRepository<EmployeeDocument, UUID> {

    List<EmployeeDocument> findByEmployeeIdOrderByUploadedAtDesc(UUID employeeId);
}
