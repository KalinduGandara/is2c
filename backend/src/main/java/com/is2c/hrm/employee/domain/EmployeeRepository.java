package com.is2c.hrm.employee.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

public interface EmployeeRepository extends JpaRepository<Employee, UUID>, JpaSpecificationExecutor<Employee> {

    Optional<Employee> findByCompanyIdAndEmployeeNumberIgnoreCase(UUID companyId, String employeeNumber);

    Optional<Employee> findByCompanyIdAndBadgeIdIgnoreCase(UUID companyId, String badgeId);

    Optional<Employee> findByCompanyIdAndNicIgnoreCase(UUID companyId, String nic);

    List<Employee> findByCompanyId(UUID companyId);

    List<Employee> findByIdIn(Collection<UUID> ids);

    @Query("""
            select count(e) from Employee e where e.status = com.is2c.hrm.employee.domain.EmployeeStatus.ACTIVE
            and (e.branchId = ?1 or e.departmentId = ?1 or e.designationId = ?1 or e.gradeId = ?1 or e.categoryId = ?1)
            """)
    long countActiveReferencing(UUID orgRecordId);
}
