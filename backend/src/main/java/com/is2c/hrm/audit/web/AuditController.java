package com.is2c.hrm.audit.web;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.is2c.hrm.audit.domain.AuditLog;
import com.is2c.hrm.audit.service.AuditQueryService;
import com.is2c.hrm.platform.export.ExportFormat;
import com.is2c.hrm.platform.export.TableExporter;
import com.is2c.hrm.common.web.PageResponse;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final AuditQueryService query;
    private final TableExporter exporter;

    public AuditController(AuditQueryService query, TableExporter exporter) {
        this.query = query;
        this.exporter = exporter;
    }

    public record AuditLogDto(UUID id, Instant occurredAt, UUID userId, String username, UUID onBehalfOf,
            String action, String entityType, String entityId, UUID companyId, String before, String after,
            String reason, String ip, String correlationId) {
        static AuditLogDto of(AuditLog a) {
            return new AuditLogDto(a.getId(), a.getOccurredAt(), a.getUserId(), a.getUsername(), a.getOnBehalfOf(),
                    a.getAction(), a.getEntityType(), a.getEntityId(), a.getCompanyId(), a.getBeforeJson(),
                    a.getAfterJson(), a.getReason(), a.getIp(), a.getCorrelationId());
        }
    }

    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT:VIEW')")
    public PageResponse<AuditLogDto> search(@RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to, @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String entityType, @RequestParam(required = false) String entityId,
            @RequestParam(required = false) String action, @RequestParam(required = false) UUID companyId,
            Pageable pageable) {
        return PageResponse.of(query.search(
                new AuditQueryService.Filter(from, to, userId, entityType, entityId, action, companyId), pageable),
                AuditLogDto::of);
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('AUDIT:EXPORT')")
    public ResponseEntity<byte[]> export(@RequestParam String format, @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to, @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String entityType, @RequestParam(required = false) String entityId,
            @RequestParam(required = false) String action, @RequestParam(required = false) UUID companyId) {
        ExportFormat f = ExportFormat.parse(format);
        byte[] content = exporter.export(query.table(
                new AuditQueryService.Filter(from, to, userId, entityType, entityId, action, companyId)), f);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("audit-log." + f.extension()).build().toString())
                .contentType(MediaType.parseMediaType(f.contentType())).body(content);
    }
}
