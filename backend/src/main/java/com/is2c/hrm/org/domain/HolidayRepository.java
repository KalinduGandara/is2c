package com.is2c.hrm.org.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HolidayRepository extends JpaRepository<Holiday, UUID> {

    List<Holiday> findByCompanyIdAndDateBetweenOrderByDate(UUID companyId, LocalDate from, LocalDate to);

    List<Holiday> findByCompanyIdInAndDateBetween(Collection<UUID> companyIds, LocalDate from, LocalDate to);

    List<Holiday> findByCompanyIdAndDate(UUID companyId, LocalDate date);
}
