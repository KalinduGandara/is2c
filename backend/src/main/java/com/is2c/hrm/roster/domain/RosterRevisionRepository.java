package com.is2c.hrm.roster.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.repository.Repository;

public interface RosterRevisionRepository extends Repository<RosterRevision, UUID> {

    RosterRevision save(RosterRevision revision);

    List<RosterRevision> findByRosterIdOrderByRevisionDescChangedAtDesc(UUID rosterId);
}
