package com.is2c.hrm.roster.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RosterEntryRepository extends JpaRepository<RosterEntry, UUID> {

    List<RosterEntry> findByRosterId(UUID rosterId);

    /** Cells of PUBLISHED/LOCKED rosters (optionally excluding one roster). */
    @Query("""
            select e from RosterEntry e, Roster r where r.id = e.rosterId
            and r.status in (com.is2c.hrm.roster.domain.Roster.Status.PUBLISHED, com.is2c.hrm.roster.domain.Roster.Status.LOCKED)
            and e.employeeId in ?1 and e.workDate between ?2 and ?3 and r.id <> ?4
            """)
    List<RosterEntry> findPublished(Collection<UUID> employeeIds, LocalDate from, LocalDate to, UUID excludeRosterId);
}
