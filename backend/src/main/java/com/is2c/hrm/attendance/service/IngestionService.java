package com.is2c.hrm.attendance.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.is2c.hrm.attendance.adapter.AdapterType;
import com.is2c.hrm.attendance.adapter.AttendanceSourceAdapter;
import com.is2c.hrm.attendance.adapter.GenericJsonAdapter;
import com.is2c.hrm.attendance.adapter.ParsedEvent;
import com.is2c.hrm.attendance.api.AttendanceEventsIngested;
import com.is2c.hrm.attendance.domain.AttendanceEvent;
import com.is2c.hrm.attendance.domain.AttendanceEventRepository;
import com.is2c.hrm.attendance.domain.AttendanceSource;
import com.is2c.hrm.audit.service.AuditEntry;
import com.is2c.hrm.audit.service.AuditService;
import com.is2c.hrm.common.error.ValidationException;
import com.is2c.hrm.employee.service.EmployeeService;
import com.is2c.hrm.org.service.DataScopeService;
import com.is2c.hrm.org.service.OrgDirectory;

/** FR-045..047 event ingestion through adapters, with idempotency, matching and per-row reporting. */
@Service
public class IngestionService {

    public static final int MAX_BATCH = 5000;

    private final Map<AdapterType, AttendanceSourceAdapter> adapters = new HashMap<>();
    private final AttendanceSourceService sources;
    private final AttendanceEventRepository events;
    private final EmployeeService employees;
    private final OrgDirectory org;
    private final DataScopeService scope;
    private final AuditService audit;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    public IngestionService(List<AttendanceSourceAdapter> adapterList, AttendanceSourceService sources,
            AttendanceEventRepository events, EmployeeService employees, OrgDirectory org, DataScopeService scope,
            AuditService audit, ApplicationEventPublisher publisher, Clock clock) {
        adapterList.forEach(a -> adapters.put(a.type(), a));
        this.sources = sources;
        this.events = events;
        this.employees = employees;
        this.org = org;
        this.scope = scope;
        this.audit = audit;
        this.publisher = publisher;
        this.clock = clock;
    }

    public record RowError(int row, String reason) {
    }

    public record IngestResult(int accepted, int duplicates, int unmatched, int rejected, List<RowError> errors) {
    }

    @Transactional
    public IngestResult ingestJson(String apiKey, GenericJsonAdapter.JsonPayload payload) {
        AttendanceSource source = sources.authenticate(apiKey);
        if (payload.events() != null && payload.events().size() > MAX_BATCH) {
            throw new ValidationException("events", "a batch may contain at most " + MAX_BATCH + " events");
        }
        return ingest(source, adapters.get(AdapterType.GENERIC_JSON).parse(payload), null);
    }

    @Transactional
    public IngestResult importCsv(UUID companyId, MultipartFile file) {
        scope.assertCompanyReadable(companyId);
        if (file == null || file.isEmpty()) {
            throw new ValidationException("file", "a CSV file is required");
        }
        String text;
        try {
            text = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        List<ParsedEvent> parsed = adapters.get(AdapterType.CSV).parse(text);
        if (parsed.size() > MAX_BATCH * 10) {
            throw new ValidationException("file", "the file has too many rows");
        }
        IngestResult result = ingest(sources.csvChannel(companyId), parsed, employeesInScope(companyId));
        audit.record(AuditEntry.of("IMPORT_CSV", "ATTENDANCE_EVENT", companyId).company(companyId)
                .after(java.util.Map.of("file", String.valueOf(file.getOriginalFilename()), "accepted", result.accepted(),
                        "duplicates", result.duplicates(), "unmatched", result.unmatched(), "rejected", result.rejected())));
        return result;
    }

    /** Re-matches unmatched events of a company against current badges/numbers. */
    @Transactional
    public int rematch(UUID companyId) {
        scope.assertCompanyReadable(companyId);
        Map<String, UUID> ids = employees.identifierMap(companyId);
        Set<UUID> allowed = employeesInScope(companyId);
        ZoneId zone = org.settings(companyId).timeZone();
        Map<UUID, Set<LocalDate>> touched = new HashMap<>();
        int matched = 0;
        for (AttendanceEvent e : events.findByCompanyIdAndStatus(companyId, AttendanceEvent.Status.UNMATCHED)) {
            UUID employeeId = ids.get(e.getEmployeeIdentifier().toLowerCase(Locale.ROOT));
            if (employeeId != null && (allowed == null || allowed.contains(employeeId))) {
                e.match(employeeId);
                touched.computeIfAbsent(employeeId, k -> new HashSet<>()).add(e.getEventTime().atZone(zone).toLocalDate());
                matched++;
            }
        }
        if (!touched.isEmpty()) {
            publisher.publishEvent(new AttendanceEventsIngested(companyId, touched));
        }
        audit.record(AuditEntry.of("REMATCH", "ATTENDANCE_EVENT", companyId).company(companyId)
                .after(java.util.Map.of("matched", matched, "employees", touched.size())));
        return matched;
    }

    /** Employees the current user may touch, or null when they cover the whole company (FR-006). */
    private Set<UUID> employeesInScope(UUID companyId) {
        if (scope.current().coversWholeCompany(companyId)) {
            return null;
        }
        Set<UUID> ids = new HashSet<>();
        employees.employedInScope(companyId, null, null, LocalDate.of(1900, 1, 1), LocalDate.of(9999, 12, 31))
                .forEach(e -> ids.add(e.getId()));
        return ids;
    }

    /** {@code allowed}: employees the caller may record punches for (null = any matched employee). */
    private IngestResult ingest(AttendanceSource source, List<ParsedEvent> parsed, Set<UUID> allowed) {
        UUID companyId = source.getCompanyId();
        ZoneId zone = org.settings(companyId).timeZone();
        Map<String, UUID> ids = employees.identifierMap(companyId);
        List<RowError> errors = new ArrayList<>();
        List<ParsedEvent> valid = new ArrayList<>();
        for (ParsedEvent p : parsed) {
            UUID matchedId = p.isRejected() ? null : ids.get(p.employeeIdentifier().toLowerCase(Locale.ROOT));
            if (p.isRejected()) {
                errors.add(new RowError(p.row(), p.error()));
            } else if (allowed != null && (matchedId == null || !allowed.contains(matchedId))) {
                errors.add(new RowError(p.row(), "employee is not known or outside your scope"));
            } else {
                valid.add(p);
            }
        }
        Set<String> existing = new HashSet<>();
        List<String> externalIds = valid.stream().map(ParsedEvent::externalId).toList();
        for (int i = 0; i < externalIds.size(); i += 1000) {
            existing.addAll(events.findExistingExternalIds(source.getId(),
                    externalIds.subList(i, Math.min(i + 1000, externalIds.size()))));
        }
        Instant now = clock.instant();
        int accepted = 0;
        int duplicates = 0;
        int unmatched = 0;
        Map<UUID, Set<LocalDate>> touched = new HashMap<>();
        List<AttendanceEvent> toSave = new ArrayList<>();
        for (ParsedEvent p : valid) {
            if (!existing.add(p.externalId())) {
                duplicates++;
                continue;
            }
            Instant time = p.instant() != null ? p.instant() : p.localTime().atZone(zone).toInstant();
            UUID employeeId = ids.get(p.employeeIdentifier().toLowerCase(Locale.ROOT));
            toSave.add(new AttendanceEvent(source.getId(), companyId, p.externalId(), p.deviceId(),
                    p.employeeIdentifier(), employeeId, time, p.direction(), now));
            accepted++;
            if (employeeId == null) {
                unmatched++;
            } else {
                touched.computeIfAbsent(employeeId, k -> new HashSet<>()).add(time.atZone(zone).toLocalDate());
            }
        }
        events.saveAll(toSave);
        if (!touched.isEmpty()) {
            publisher.publishEvent(new AttendanceEventsIngested(companyId, touched));
        }
        return new IngestResult(accepted, duplicates, unmatched, errors.size(), errors);
    }
}
