package com.is2c.hrm.attendance.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.is2c.hrm.attendance.adapter.AdapterType;
import com.is2c.hrm.attendance.adapter.GenericJsonAdapter;
import com.is2c.hrm.attendance.api.EventDirection;
import com.is2c.hrm.attendance.domain.AttendanceEvent;
import com.is2c.hrm.attendance.domain.AttendanceEventRepository;
import com.is2c.hrm.attendance.domain.AttendanceSource;
import com.is2c.hrm.attendance.service.AttendanceSourceService;
import com.is2c.hrm.attendance.service.IngestionService;
import com.is2c.hrm.common.web.PageResponse;
import com.is2c.hrm.org.service.DataScopeService;

@RestController
@RequestMapping("/api/v1")
public class AttendanceIngestionController {

    private final IngestionService ingestion;
    private final AttendanceSourceService sources;
    private final AttendanceEventRepository events;
    private final DataScopeService scope;

    public AttendanceIngestionController(IngestionService ingestion, AttendanceSourceService sources,
            AttendanceEventRepository events, DataScopeService scope) {
        this.ingestion = ingestion;
        this.sources = sources;
        this.events = events;
        this.scope = scope;
    }

    public record SourceDto(UUID id, UUID companyId, String name, AdapterType adapterType, String keyPrefix,
            boolean systemSource, boolean active, String apiKey) {
        static SourceDto of(AttendanceSource s, String apiKey) {
            return new SourceDto(s.getId(), s.getCompanyId(), s.getName(), s.getAdapterType(), s.getApiKeyPrefix(),
                    s.isSystemSource(), s.isActive(), apiKey);
        }
    }

    public record EventDto(UUID id, String employeeIdentifier, UUID employeeId, Instant eventTime,
            EventDirection direction, String deviceId, AttendanceEvent.Status status) {
        static EventDto of(AttendanceEvent e) {
            return new EventDto(e.getId(), e.getEmployeeIdentifier(), e.getEmployeeId(), e.getEventTime(),
                    e.getDirection(), e.getDeviceId(), e.getStatus());
        }
    }

    public record RematchResult(int matched) {
    }

    /** Device/middleware ingestion, authenticated by X-Api-Key (no user session). */
    @PostMapping("/integrations/attendance/events")
    public IngestionService.IngestResult ingest(@RequestHeader(value = "X-Api-Key", required = false) String apiKey,
            @RequestBody GenericJsonAdapter.JsonPayload payload) {
        return ingestion.ingestJson(apiKey, payload);
    }

    @PostMapping(value = "/attendance/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ATTENDANCE:ADD')")
    public IngestionService.IngestResult importCsv(@RequestParam UUID companyId, @RequestPart("file") MultipartFile file) {
        return ingestion.importCsv(companyId, file);
    }

    @PostMapping("/attendance/rematch")
    @PreAuthorize("hasAuthority('ATTENDANCE:EDIT')")
    public RematchResult rematch(@RequestParam UUID companyId) {
        return new RematchResult(ingestion.rematch(companyId));
    }

    @GetMapping("/attendance/unmatched-events")
    @PreAuthorize("hasAuthority('ATTENDANCE:VIEW')")
    public PageResponse<EventDto> unmatched(@RequestParam UUID companyId, Pageable pageable) {
        scope.assertCompanyReadable(companyId);
        return PageResponse.of(events.findByCompanyIdAndStatusOrderByEventTimeDesc(companyId,
                AttendanceEvent.Status.UNMATCHED, pageable), EventDto::of);
    }

    @GetMapping("/companies/{companyId}/attendance-sources")
    @PreAuthorize("hasAuthority('ATTENDANCE:VIEW')")
    public List<SourceDto> listSources(@PathVariable UUID companyId) {
        return sources.list(companyId).stream().map(s -> SourceDto.of(s, null)).toList();
    }

    @PostMapping("/companies/{companyId}/attendance-sources")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('ATTENDANCE:CONFIGURE')")
    public SourceDto createSource(@PathVariable UUID companyId, @RequestBody AttendanceSourceService.SourceData body) {
        AttendanceSourceService.SourceWithKey s = sources.create(companyId, body);
        return SourceDto.of(s.source(), s.apiKey());
    }

    @PostMapping("/attendance-sources/{id}/regenerate-key")
    @PreAuthorize("hasAuthority('ATTENDANCE:CONFIGURE')")
    public SourceDto regenerate(@PathVariable UUID id) {
        AttendanceSourceService.SourceWithKey s = sources.regenerateKey(id);
        return SourceDto.of(s.source(), s.apiKey());
    }

    @PostMapping("/attendance-sources/{id}/deactivate")
    @PreAuthorize("hasAuthority('ATTENDANCE:CONFIGURE')")
    public SourceDto deactivate(@PathVariable UUID id) {
        return SourceDto.of(sources.setActive(id, false), null);
    }

    @PostMapping("/attendance-sources/{id}/activate")
    @PreAuthorize("hasAuthority('ATTENDANCE:CONFIGURE')")
    public SourceDto activate(@PathVariable UUID id) {
        return SourceDto.of(sources.setActive(id, true), null);
    }
}
