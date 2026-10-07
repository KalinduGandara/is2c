package com.is2c.hrm.roster.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RosterRepository extends JpaRepository<Roster, UUID> {

    @Query("""
            select r from Roster r where r.companyId = ?1 and r.startDate <= ?3 and r.endDate >= ?2
            order by r.startDate desc, r.name
            """)
    List<Roster> findOverlapping(UUID companyId, LocalDate from, LocalDate to);
}
