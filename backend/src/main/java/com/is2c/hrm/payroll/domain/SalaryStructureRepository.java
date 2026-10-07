package com.is2c.hrm.payroll.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SalaryStructureRepository extends JpaRepository<SalaryStructure, UUID> {

    List<SalaryStructure> findByEmployeeIdOrderByEffectiveFromDesc(UUID employeeId);

    @Query("""
            select s from SalaryStructure s where s.employeeId in ?1 and s.effectiveFrom <= ?2
            and (s.effectiveTo is null or s.effectiveTo >= ?2)
            """)
    List<SalaryStructure> findEffectiveOn(Collection<UUID> employeeIds, LocalDate date);

    boolean existsByEmployeeId(UUID employeeId);
}
