package com.is2c.hrm.roster.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.is2c.hrm.roster.domain.Roster;
import com.is2c.hrm.roster.domain.RosterRevision;
import com.is2c.hrm.roster.service.RosterService;

@RestController
@RequestMapping("/api/v1/rosters")
public class RosterController {

    private final RosterService rosters;

    public RosterController(RosterService rosters) {
        this.rosters = rosters;
    }

    public record RosterDto(UUID id, UUID companyId, UUID departmentId, String name, Roster.PeriodType periodType,
            LocalDate startDate, LocalDate endDate, Roster.Status status, int revision, Long version) {
        static RosterDto of(Roster r) {
            return new RosterDto(r.getId(), r.getCompanyId(), r.getDepartmentId(), r.getName(), r.getPeriodType(),
                    r.getStartDate(), r.getEndDate(), r.getStatus(), r.getRevision(), r.getVersion());
        }
    }

    public record EntryDto(UUID employeeId, LocalDate date, UUID shiftId, boolean off) {
    }

    public record EmployeeRef(UUID id, String employeeNumber, String name, UUID departmentId) {
    }

    public record RosterDetail(RosterDto roster, List<EntryDto> entries, List<EmployeeRef> employees) {
    }

    public record RevisionDto(UUID id, int revision, UUID employeeId, LocalDate date, String oldValue,
            String newValue, String reason, UUID changedBy, Instant changedAt) {
        static RevisionDto of(RosterRevision r) {
            return new RevisionDto(r.getId(), r.getRevision(), r.getEmployeeId(), r.getWorkDate(), r.getOldValue(),
                    r.getNewValue(), r.getReason(), r.getChangedBy(), r.getChangedAt());
        }
    }

    public record UnlockRequest(String reason) {
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROSTER:VIEW')")
    public List<RosterDto> list(@RequestParam UUID companyId, @RequestParam LocalDate from, @RequestParam LocalDate to) {
        return rosters.list(companyId, from, to).stream().map(RosterDto::of).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ROSTER:ADD')")
    public RosterDto create(@RequestBody RosterService.CreateData body) {
        return RosterDto.of(rosters.create(body));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ROSTER:VIEW')")
    public RosterDetail get(@PathVariable UUID id) {
        RosterService.RosterView v = rosters.view(id);
        return new RosterDetail(RosterDto.of(v.roster()),
                v.entries().stream().map(e -> new EntryDto(e.getEmployeeId(), e.getWorkDate(), e.getShiftId(),
                        e.getShiftId() == null)).toList(),
                v.employees().stream().map(e -> new EmployeeRef(e.getId(), e.getEmployeeNumber(), e.fullName(),
                        e.getDepartmentId())).toList());
    }

    @PutMapping("/{id}/entries")
    @PreAuthorize("hasAuthority('ROSTER:EDIT')")
    public RosterDto updateEntries(@PathVariable UUID id, @RequestBody RosterService.EntriesUpdate body) {
        return RosterDto.of(rosters.updateEntries(id, body));
    }

    @PostMapping("/{id}/bulk-assign")
    @PreAuthorize("hasAuthority('ROSTER:EDIT')")
    public RosterDto bulkAssign(@PathVariable UUID id, @RequestBody RosterService.BulkAssign body) {
        return RosterDto.of(rosters.bulkAssign(id, body));
    }

    @PostMapping("/{id}/copy-from/{sourceId}")
    @PreAuthorize("hasAuthority('ROSTER:EDIT')")
    public RosterService.CopyResult copy(@PathVariable UUID id, @PathVariable UUID sourceId) {
        return rosters.copyFrom(id, sourceId);
    }

    @PostMapping("/{id}/validate")
    @PreAuthorize("hasAuthority('ROSTER:VIEW')")
    public List<RosterService.Conflict> validate(@PathVariable UUID id) {
        return rosters.validate(id);
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('ROSTER:APPROVE')")
    public RosterDto publish(@PathVariable UUID id) {
        return RosterDto.of(rosters.publish(id));
    }

    @PostMapping("/{id}/lock")
    @PreAuthorize("hasAuthority('ROSTER:APPROVE')")
    public RosterDto lock(@PathVariable UUID id) {
        return RosterDto.of(rosters.lock(id));
    }

    @PostMapping("/{id}/unlock")
    @PreAuthorize("hasAuthority('ROSTER:CONFIGURE')")
    public RosterDto unlock(@PathVariable UUID id, @RequestBody UnlockRequest body) {
        return RosterDto.of(rosters.unlock(id, body.reason()));
    }

    @GetMapping("/{id}/revisions")
    @PreAuthorize("hasAuthority('ROSTER:VIEW')")
    public List<RevisionDto> revisions(@PathVariable UUID id) {
        return rosters.revisions(id).stream().map(RevisionDto::of).toList();
    }
}
