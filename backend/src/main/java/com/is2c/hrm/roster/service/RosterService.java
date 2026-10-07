package com.is2c.hrm.roster.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.is2c.hrm.audit.service.AuditEntry;
import com.is2c.hrm.audit.service.AuditService;
import com.is2c.hrm.common.error.FieldError;
import com.is2c.hrm.common.error.InvalidStateException;
import com.is2c.hrm.common.error.NotFoundException;
import com.is2c.hrm.common.error.ValidationException;
import com.is2c.hrm.employee.domain.Employee;
import com.is2c.hrm.employee.service.EmployeeService;
import com.is2c.hrm.org.service.DataScopeService;
import com.is2c.hrm.org.service.OrgDirectory;
import com.is2c.hrm.roster.api.RosterEntryValue;
import com.is2c.hrm.roster.api.ScheduleChangedEvent;
import com.is2c.hrm.roster.domain.Roster;
import com.is2c.hrm.roster.domain.Roster.PeriodType;
import com.is2c.hrm.roster.domain.Roster.Status;
import com.is2c.hrm.roster.domain.RosterEntry;
import com.is2c.hrm.roster.domain.RosterEntryRepository;
import com.is2c.hrm.roster.domain.RosterRepository;
import com.is2c.hrm.roster.domain.RosterRevision;
import com.is2c.hrm.roster.domain.RosterRevisionRepository;
import com.is2c.hrm.security.api.CurrentUser;
import com.is2c.hrm.shift.api.ShiftDefinition;
import com.is2c.hrm.shift.api.ShiftDefinition.TimedSession;
import com.is2c.hrm.shift.domain.Shift;
import com.is2c.hrm.shift.service.ShiftService;

/** FR-035..039 roster planning. */
@Service
public class RosterService implements PublishedRosterEntries {

    private static final UUID NO_ROSTER = new UUID(0, 0);

    private final RosterRepository rosters;
    private final RosterEntryRepository entries;
    private final RosterRevisionRepository revisions;
    private final EmployeeService employees;
    private final ShiftService shifts;
    private final OrgDirectory org;
    private final DataScopeService scope;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public RosterService(RosterRepository rosters, RosterEntryRepository entries, RosterRevisionRepository revisions,
            EmployeeService employees, ShiftService shifts, OrgDirectory org, DataScopeService scope,
            AuditService audit, ApplicationEventPublisher events, Clock clock) {
        this.rosters = rosters;
        this.entries = entries;
        this.revisions = revisions;
        this.employees = employees;
        this.shifts = shifts;
        this.org = org;
        this.scope = scope;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
    }

    public record CreateData(UUID companyId, UUID departmentId, String name, PeriodType periodType,
            LocalDate startDate) {
    }

    /** Set a cell to a shift, OFF ({@code off=true}) or remove it ({@code clear=true}). */
    public record CellChange(UUID employeeId, LocalDate date, UUID shiftId, boolean off, boolean clear) {
    }

    public record EntriesUpdate(List<CellChange> entries, String reason) {
    }

    public record BulkAssign(List<UUID> employeeIds, LocalDate from, LocalDate to, Set<DayOfWeek> weekdays,
            UUID shiftId, boolean off, String reason) {
    }

    public record Conflict(UUID employeeId, String employeeNumber, LocalDate date, String message) {
    }

    public record CopyResult(int copied, int skipped) {
    }

    public record RosterView(Roster roster, List<RosterEntry> entries, List<Employee> employees) {
    }

    // ---------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public List<Roster> list(UUID companyId, LocalDate from, LocalDate to) {
        scope.assertCompanyReadable(companyId);
        return rosters.findOverlapping(companyId, from, to);
    }

    @Transactional(readOnly = true)
    public RosterView view(UUID id) {
        Roster r = get(id);
        List<Employee> visible = eligibleEmployees(r);
        Set<UUID> ids = visible.stream().map(Employee::getId).collect(java.util.stream.Collectors.toSet());
        return new RosterView(r, entries.findByRosterId(id).stream().filter(e -> ids.contains(e.getEmployeeId())).toList(),
                visible);
    }

    @Transactional(readOnly = true)
    public List<RosterRevision> revisions(UUID id) {
        get(id);
        return revisions.findByRosterIdOrderByRevisionDescChangedAtDesc(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Key, RosterEntryValue> find(Collection<UUID> employeeIds, LocalDate from, LocalDate to) {
        Map<Key, RosterEntryValue> result = new HashMap<>();
        if (!employeeIds.isEmpty()) {
            entries.findPublished(employeeIds, from, to, NO_ROSTER).forEach(e ->
                    result.put(new Key(e.getEmployeeId(), e.getWorkDate()), new RosterEntryValue(e.getShiftId())));
        }
        return result;
    }

    // ---------------------------------------------------------------- commands

    @Transactional
    public Roster create(CreateData d) {
        List<FieldError> errors = new ArrayList<>();
        if (d.companyId() == null) {
            throw new ValidationException("companyId", "is required");
        }
        scope.assertCompanyReadable(d.companyId());
        scope.assertDepartmentManageable(d.companyId(), d.departmentId());
        if (d.name() == null || d.name().isBlank()) {
            errors.add(new FieldError("name", "is required"));
        }
        if (d.periodType() == null) {
            errors.add(new FieldError("periodType", "is required"));
        }
        if (d.startDate() == null) {
            errors.add(new FieldError("startDate", "is required"));
        }
        if (d.departmentId() != null && org.department(d.departmentId())
                .filter(x -> x.getCompanyId().equals(d.companyId())).isEmpty()) {
            errors.add(new FieldError("departmentId", "department must belong to the company"));
        }
        if (errors.isEmpty()) {
            if (d.periodType() == PeriodType.WEEKLY
                    && d.startDate().getDayOfWeek() != org.settings(d.companyId()).weekStartDay()) {
                errors.add(new FieldError("startDate", "a weekly roster must start on "
                        + org.settings(d.companyId()).weekStartDay()));
            }
            if (d.periodType() == PeriodType.MONTHLY && d.startDate().getDayOfMonth() != 1) {
                errors.add(new FieldError("startDate", "a monthly roster must start on the 1st"));
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("Validation failed", errors);
        }
        LocalDate end = d.periodType() == PeriodType.WEEKLY ? d.startDate().plusDays(6)
                : d.startDate().withDayOfMonth(d.startDate().lengthOfMonth());
        Roster r = rosters.save(new Roster(d.companyId(), d.departmentId(), d.name().trim(), d.periodType(),
                d.startDate(), end));
        audit.record(AuditEntry.of("CREATE", "ROSTER", r.getId()).company(r.getCompanyId()).after(d));
        return r;
    }

    @Transactional
    public Roster updateEntries(UUID id, EntriesUpdate update) {
        Roster r = get(id);
        apply(r, update.entries(), update.reason());
        return r;
    }

    @Transactional
    public Roster bulkAssign(UUID id, BulkAssign b) {
        Roster r = get(id);
        List<FieldError> errors = new ArrayList<>();
        if (b.employeeIds() == null || b.employeeIds().isEmpty()) {
            errors.add(new FieldError("employeeIds", "select at least one employee"));
        }
        if (b.from() == null || b.to() == null || b.to().isBefore(b.from())) {
            errors.add(new FieldError("to", "a valid date range is required"));
        }
        if (!b.off() && b.shiftId() == null) {
            errors.add(new FieldError("shiftId", "choose a shift or OFF"));
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("Validation failed", errors);
        }
        List<CellChange> changes = new ArrayList<>();
        for (UUID employeeId : b.employeeIds()) {
            for (LocalDate d = b.from(); !d.isAfter(b.to()); d = d.plusDays(1)) {
                if (b.weekdays() == null || b.weekdays().isEmpty() || b.weekdays().contains(d.getDayOfWeek())) {
                    changes.add(new CellChange(employeeId, d, b.off() ? null : b.shiftId(), b.off(), false));
                }
            }
        }
        apply(r, changes, b.reason());
        return r;
    }

    @Transactional
    public CopyResult copyFrom(UUID targetId, UUID sourceId) {
        Roster target = get(targetId);
        Roster source = get(sourceId);
        if (target.getStatus() != Status.DRAFT) {
            throw new InvalidStateException("Entries can only be copied into a DRAFT roster");
        }
        if (source.getPeriodType() != target.getPeriodType() || !source.getCompanyId().equals(target.getCompanyId())) {
            throw new ValidationException("sourceId", "the source roster must be of the same company and period type");
        }
        Set<UUID> eligible = eligibleEmployees(target).stream().filter(e -> e.getStatus()
                == com.is2c.hrm.employee.domain.EmployeeStatus.ACTIVE).map(Employee::getId).collect(Collectors.toSet());
        Map<UUID, Shift> shiftById = shiftsOf(target.getCompanyId());
        List<CellChange> changes = new ArrayList<>();
        int skipped = 0;
        for (RosterEntry e : entries.findByRosterId(sourceId)) {
            long offset = java.time.temporal.ChronoUnit.DAYS.between(source.getStartDate(), e.getWorkDate());
            LocalDate date = target.getStartDate().plusDays(offset);
            boolean shiftOk = e.getShiftId() == null || (shiftById.containsKey(e.getShiftId())
                    && shiftById.get(e.getShiftId()).isActive());
            if (!target.contains(date) || !eligible.contains(e.getEmployeeId()) || !shiftOk) {
                skipped++;
                continue;
            }
            changes.add(new CellChange(e.getEmployeeId(), date, e.getShiftId(), e.getShiftId() == null, false));
        }
        apply(target, changes, null);
        return new CopyResult(changes.size(), skipped);
    }

    @Transactional(readOnly = true)
    public List<Conflict> validate(UUID id) {
        Roster r = get(id);
        return conflicts(r, cells(entries.findByRosterId(id)), null);
    }

    @Transactional
    public Roster publish(UUID id) {
        Roster r = get(id);
        scope.assertDepartmentManageable(r.getCompanyId(), r.getDepartmentId());
        if (r.getStatus() != Status.DRAFT) {
            throw new InvalidStateException("Only DRAFT rosters can be published");
        }
        List<RosterEntry> current = entries.findByRosterId(id);
        throwIfConflicts(conflicts(r, cells(current), null));
        r.publish();
        audit.record(AuditEntry.of("PUBLISH", "ROSTER", id).company(r.getCompanyId()));
        scheduleChanged(r, current.stream().map(RosterEntry::getEmployeeId).collect(Collectors.toSet()));
        return r;
    }

    @Transactional
    public Roster lock(UUID id) {
        Roster r = get(id);
        scope.assertDepartmentManageable(r.getCompanyId(), r.getDepartmentId());
        if (r.getStatus() != Status.PUBLISHED) {
            throw new InvalidStateException("Only PUBLISHED rosters can be locked");
        }
        r.setStatus(Status.LOCKED);
        audit.record(AuditEntry.of("LOCK", "ROSTER", id).company(r.getCompanyId()));
        return r;
    }

    @Transactional
    public Roster unlock(UUID id, String reason) {
        Roster r = get(id);
        scope.assertDepartmentManageable(r.getCompanyId(), r.getDepartmentId());
        if (r.getStatus() != Status.LOCKED) {
            throw new InvalidStateException("Only LOCKED rosters can be unlocked");
        }
        if (reason == null || reason.isBlank()) {
            throw new ValidationException("reason", "a reason is required to unlock a roster");
        }
        r.setStatus(Status.PUBLISHED);
        audit.record(AuditEntry.of("UNLOCK", "ROSTER", id).company(r.getCompanyId()).reason(reason));
        return r;
    }

    // ---------------------------------------------------------------- core

    private void apply(Roster r, List<CellChange> changes, String reason) {
        if (r.getStatus() == Status.LOCKED) {
            throw new ValidationException("The roster is locked and cannot be changed");
        }
        boolean published = r.getStatus() == Status.PUBLISHED;
        if (published && (reason == null || reason.isBlank())) {
            throw new ValidationException("reason", "a reason is required to change a published roster");
        }
        Map<UUID, Employee> eligible = eligibleEmployees(r).stream()
                .collect(Collectors.toMap(Employee::getId, Function.identity()));
        Map<UUID, Shift> shiftById = shiftsOf(r.getCompanyId());
        List<FieldError> errors = new ArrayList<>();
        for (CellChange c : changes) {
            if (c.employeeId() == null || !eligible.containsKey(c.employeeId())) {
                errors.add(new FieldError("entries", "employee " + c.employeeId() + " is not part of this roster"));
            } else if (c.date() == null || !r.contains(c.date())) {
                errors.add(new FieldError("entries", "date " + c.date() + " is outside the roster period"));
            } else if (!c.clear() && !c.off() && (c.shiftId() == null || !shiftById.containsKey(c.shiftId()))) {
                errors.add(new FieldError("entries", "unknown shift for " + c.date()));
            } else if (!c.clear() && !c.off() && !shiftById.get(c.shiftId()).isActive()) {
                errors.add(new FieldError("entries", "shift " + shiftById.get(c.shiftId()).getCode() + " is inactive"));
            }
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("Validation failed", errors.stream().distinct().limit(50).toList());
        }

        Map<Cell, RosterEntry> existing = new HashMap<>();
        entries.findByRosterId(r.getId()).forEach(e -> existing.put(new Cell(e.getEmployeeId(), e.getWorkDate()), e));
        Map<Cell, Change> diffs = new LinkedHashMap<>();
        for (CellChange c : changes) {
            Cell cell = new Cell(c.employeeId(), c.date());
            RosterEntry before = existing.get(cell);
            String oldValue = before == null ? null : label(before.getShiftId(), shiftById);
            UUID newShift = c.off() ? null : c.shiftId();
            if (c.clear()) {
                if (before != null) {
                    entries.delete(before);
                    existing.remove(cell);
                    diffs.put(cell, new Change(oldValue, null));
                }
            } else if (before == null) {
                RosterEntry created = entries.save(new RosterEntry(r.getId(), c.employeeId(), c.date(), newShift));
                existing.put(cell, created);
                diffs.put(cell, new Change(null, label(newShift, shiftById)));
            } else if (!Objects.equals(before.getShiftId(), newShift)) {
                before.setShiftId(newShift);
                diffs.put(cell, new Change(oldValue, label(newShift, shiftById)));
            }
        }
        if (diffs.isEmpty()) {
            return;
        }
        Set<UUID> affected = diffs.keySet().stream().map(Cell::employeeId).collect(Collectors.toSet());
        Map<Cell, UUID> state = new HashMap<>();
        existing.forEach((cell, e) -> state.put(cell, e.getShiftId()));
        throwIfConflicts(conflicts(r, state, affected));

        if (published) {
            int revision = r.nextRevision();
            UUID user = CurrentUser.get().id();
            diffs.forEach((cell, change) -> revisions.save(new RosterRevision(r.getId(), revision, cell.employeeId(),
                    cell.date(), change.oldValue(), change.newValue(), reason.trim(), user, clock.instant())));
            scheduleChanged(r, affected);
        }
        audit.record(AuditEntry.of(published ? "REVISE" : "EDIT", "ROSTER", r.getId()).company(r.getCompanyId())
                .after(java.util.Map.of("changedCells", diffs.size(), "revision", r.getRevision())).reason(reason));
    }

    private List<Conflict> conflicts(Roster r, Map<Cell, UUID> state, Set<UUID> onlyEmployees) {
        Set<UUID> employeeIds = new HashSet<>();
        state.keySet().forEach(c -> employeeIds.add(c.employeeId()));
        if (onlyEmployees != null) {
            employeeIds.retainAll(onlyEmployees);
        }
        if (employeeIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, Shift> shiftById = shiftsOf(r.getCompanyId());
        Map<UUID, ShiftDefinition> defs = shifts.definitions(shiftById.keySet());
        Map<UUID, String> numbers = employees.employedInScope(r.getCompanyId(), null, null, r.getStartDate(),
                r.getEndDate()).stream().collect(Collectors.toMap(Employee::getId, Employee::getEmployeeNumber));

        Map<Cell, RosterEntry> others = new HashMap<>();
        entries.findPublished(employeeIds, r.getStartDate().minusDays(1), r.getEndDate().plusDays(1), r.getId())
                .forEach(e -> others.put(new Cell(e.getEmployeeId(), e.getWorkDate()), e));

        List<Conflict> conflicts = new ArrayList<>();
        for (UUID employeeId : employeeIds) {
            String number = numbers.getOrDefault(employeeId, employeeId.toString());
            for (LocalDate d = r.getStartDate().minusDays(1); !d.isAfter(r.getEndDate()); d = d.plusDays(1)) {
                Cell cell = new Cell(employeeId, d);
                boolean inThis = state.containsKey(cell);
                if (inThis && others.containsKey(cell)) {
                    conflicts.add(new Conflict(employeeId, number, d, "also rostered in another published roster"));
                }
                if (inThis && state.get(cell) != null && !shiftById.get(state.get(cell)).isActive()) {
                    conflicts.add(new Conflict(employeeId, number, d, "uses inactive shift "
                            + shiftById.get(state.get(cell)).getCode()));
                }
                UUID today = effective(cell, state, others);
                UUID tomorrow = effective(new Cell(employeeId, d.plusDays(1)), state, others);
                boolean involvesThis = inThis || state.containsKey(new Cell(employeeId, d.plusDays(1)));
                if (involvesThis && today != null && tomorrow != null && overlaps(defs.get(today), defs.get(tomorrow), d)) {
                    conflicts.add(new Conflict(employeeId, number, d.plusDays(1), defs.get(today).code() + " on " + d
                            + " overlaps " + defs.get(tomorrow).code() + " on " + d.plusDays(1)));
                }
            }
        }
        return conflicts;
    }

    private static UUID effective(Cell cell, Map<Cell, UUID> state, Map<Cell, RosterEntry> others) {
        if (state.containsKey(cell)) {
            return state.get(cell);
        }
        RosterEntry other = others.get(cell);
        return other == null ? null : other.getShiftId();
    }

    private static boolean overlaps(ShiftDefinition first, ShiftDefinition next, LocalDate day) {
        List<TimedSession> a = first.sessionsOn(day);
        List<TimedSession> b = next.sessionsOn(day.plusDays(1));
        LocalDateTime firstEnds = a.get(a.size() - 1).end();
        LocalDateTime nextStarts = b.get(0).start();
        return firstEnds.isAfter(nextStarts);
    }

    private static void throwIfConflicts(List<Conflict> conflicts) {
        if (!conflicts.isEmpty()) {
            throw new ValidationException("The roster has conflicts", conflicts.stream().limit(100)
                    .map(c -> new FieldError("entries", c.employeeNumber() + " " + c.date() + ": " + c.message()))
                    .toList());
        }
    }

    private void scheduleChanged(Roster r, Set<UUID> employeeIds) {
        if (!employeeIds.isEmpty()) {
            events.publishEvent(new ScheduleChangedEvent(r.getCompanyId(), Set.copyOf(employeeIds), r.getStartDate(),
                    r.getEndDate()));
        }
    }

    private Roster get(UUID id) {
        Roster r = rosters.findById(id).orElseThrow(() -> new NotFoundException("Roster", id));
        scope.assertCompanyReadable(r.getCompanyId());
        return r;
    }

    /** Employees covered by the roster (company/department subtree) within the user's scope. */
    private List<Employee> eligibleEmployees(Roster r) {
        Set<UUID> departments = r.getDepartmentId() == null ? null : org.departmentWithDescendants(r.getDepartmentId());
        return employees.employedInScope(r.getCompanyId(), departments, null, r.getStartDate(), r.getEndDate());
    }

    private Map<UUID, Shift> shiftsOf(UUID companyId) {
        return shifts.forCompany(companyId).stream().collect(Collectors.toMap(Shift::getId, Function.identity()));
    }

    private static Map<Cell, UUID> cells(List<RosterEntry> list) {
        Map<Cell, UUID> map = new HashMap<>();
        list.forEach(e -> map.put(new Cell(e.getEmployeeId(), e.getWorkDate()), e.getShiftId()));
        return map;
    }

    private static String label(UUID shiftId, Map<UUID, Shift> shiftById) {
        return shiftId == null ? "OFF" : shiftById.get(shiftId).getCode();
    }

    private record Cell(UUID employeeId, LocalDate date) {
    }

    private record Change(String oldValue, String newValue) {
    }
}
