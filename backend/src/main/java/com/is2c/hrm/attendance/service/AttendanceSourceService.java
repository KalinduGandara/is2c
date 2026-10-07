package com.is2c.hrm.attendance.service;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.is2c.hrm.attendance.adapter.AdapterType;
import com.is2c.hrm.attendance.domain.AttendanceSource;
import com.is2c.hrm.attendance.domain.AttendanceSourceRepository;
import com.is2c.hrm.audit.service.AuditEntry;
import com.is2c.hrm.audit.service.AuditService;
import com.is2c.hrm.common.error.HrmException;
import com.is2c.hrm.common.error.NotFoundException;
import com.is2c.hrm.common.error.ValidationException;
import com.is2c.hrm.org.service.DataScopeService;
import com.is2c.hrm.security.service.TokenHashing;

/** FR-045 attendance sources and their API keys (shown once, stored hashed). */
@Service
public class AttendanceSourceService {

    static final String KEY_PREFIX = "hrm_";

    private final AttendanceSourceRepository sources;
    private final DataScopeService scope;
    private final AuditService audit;

    public AttendanceSourceService(AttendanceSourceRepository sources, DataScopeService scope, AuditService audit) {
        this.sources = sources;
        this.scope = scope;
        this.audit = audit;
    }

    public record SourceData(String name, AdapterType adapterType) {
    }

    /** A source together with a newly generated key (only returned at creation/regeneration). */
    public record SourceWithKey(AttendanceSource source, String apiKey) {
    }

    @Transactional(readOnly = true)
    public List<AttendanceSource> list(UUID companyId) {
        scope.assertCompanyReadable(companyId);
        return sources.findByCompanyIdOrderByName(companyId);
    }

    @Transactional
    public SourceWithKey create(UUID companyId, SourceData d) {
        scope.assertCompanyWritable(companyId);
        if (d.name() == null || d.name().isBlank()) {
            throw new ValidationException("name", "is required");
        }
        AttendanceSource s = new AttendanceSource(companyId, d.name().trim(),
                d.adapterType() == null ? AdapterType.GENERIC_JSON : d.adapterType(), false);
        String key = assignKey(s);
        sources.save(s);
        audit.record(AuditEntry.of("CREATE", "ATTENDANCE_SOURCE", s.getId()).company(companyId).after(d));
        return new SourceWithKey(s, key);
    }

    @Transactional
    public SourceWithKey regenerateKey(UUID id) {
        AttendanceSource s = sources.findById(id).orElseThrow(() -> new NotFoundException("Attendance source", id));
        scope.assertCompanyWritable(s.getCompanyId());
        if (s.isSystemSource()) {
            throw new ValidationException("The CSV import channel has no API key");
        }
        String key = assignKey(s);
        audit.record(AuditEntry.of("REGENERATE_KEY", "ATTENDANCE_SOURCE", id).company(s.getCompanyId()));
        return new SourceWithKey(s, key);
    }

    @Transactional
    public AttendanceSource setActive(UUID id, boolean active) {
        AttendanceSource s = sources.findById(id).orElseThrow(() -> new NotFoundException("Attendance source", id));
        scope.assertCompanyWritable(s.getCompanyId());
        s.setActive(active);
        audit.record(AuditEntry.of(active ? "ACTIVATE" : "DEACTIVATE", "ATTENDANCE_SOURCE", id).company(s.getCompanyId()));
        return s;
    }

    /** Resolves an API key to an active source; 401 otherwise. Constant-time hash comparison. */
    @Transactional(readOnly = true)
    public AttendanceSource authenticate(String rawKey) {
        HrmException invalid = new HrmException(HttpStatus.UNAUTHORIZED, "INVALID_API_KEY", "Invalid or inactive API key");
        if (rawKey == null || !rawKey.startsWith(KEY_PREFIX)) {
            throw invalid;
        }
        String[] parts = rawKey.substring(KEY_PREFIX.length()).split("_", 2);
        if (parts.length != 2) {
            throw invalid;
        }
        AttendanceSource s = sources.findByApiKeyPrefix(parts[0]).orElseThrow(() -> invalid);
        if (!s.isActive() || !TokenHashing.constantTimeEquals(s.getApiKeyHash(), TokenHashing.sha256(rawKey))) {
            throw invalid;
        }
        return s;
    }

    /** The company's built-in CSV import channel, created on first use. */
    @Transactional(propagation = Propagation.REQUIRED)
    public AttendanceSource csvChannel(UUID companyId) {
        return sources.findByCompanyIdAndSystemSourceTrue(companyId)
                .orElseGet(() -> sources.save(new AttendanceSource(companyId, "CSV import", AdapterType.CSV, true)));
    }

    private static String assignKey(AttendanceSource s) {
        String prefix = TokenHashing.randomToken(9).replace('-', 'x').replace('_', 'y');
        String key = KEY_PREFIX + prefix + "_" + TokenHashing.randomToken(32);
        s.setKey(prefix, TokenHashing.sha256(key));
        return key;
    }
}
