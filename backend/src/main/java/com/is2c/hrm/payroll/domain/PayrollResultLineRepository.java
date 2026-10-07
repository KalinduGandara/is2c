package com.is2c.hrm.payroll.domain;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PayrollResultLineRepository extends JpaRepository<PayrollResultLine, UUID> {

    List<PayrollResultLine> findByResultIdOrderBySortOrder(UUID resultId);

    List<PayrollResultLine> findByResultIdIn(Collection<UUID> resultIds);

    @Modifying
    @Query("delete from PayrollResultLine l where l.resultId in (select r.id from PayrollResult r where r.periodId = ?1)")
    int deleteByPeriod(UUID periodId);

    @Query("select count(l) from PayrollResultLine l where l.componentCode = ?2 and l.resultId in "
            + "(select r.id from PayrollResult r, PayrollPeriod p where r.periodId = p.id and p.companyId = ?1)")
    long countUsage(UUID companyId, String componentCode);
}
