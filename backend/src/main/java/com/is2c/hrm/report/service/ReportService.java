package com.is2c.hrm.report.service;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.is2c.hrm.common.error.ForbiddenException;
import com.is2c.hrm.common.error.NotFoundException;
import com.is2c.hrm.platform.export.TableData;
import com.is2c.hrm.security.api.AuthenticatedUser;
import com.is2c.hrm.security.api.CurrentUser;

/** FR-095 report catalog and execution. */
@Service
public class ReportService {

    private final Map<String, ReportDefinition> definitions = new LinkedHashMap<>();
    private final Clock clock;

    public ReportService(List<ReportProvider> providers, Clock clock) {
        this.clock = clock;
        providers.stream().flatMap(p -> p.reports().stream()).forEach(d -> {
            if (definitions.putIfAbsent(d.key(), d) != null) {
                throw new IllegalStateException("Duplicate report key " + d.key());
            }
        });
    }

    /** Reports the current user may run. */
    public List<ReportDefinition> catalog() {
        AuthenticatedUser user = CurrentUser.get();
        return definitions.values().stream().filter(d -> allowed(d, user)).toList();
    }

    /** Runs a report; read-write because some sources create derived rows lazily (e.g. leave balances). */
    @Transactional
    public TableData run(String key, Map<String, String> params) {
        ReportDefinition d = definitions.get(key);
        if (d == null) {
            throw new NotFoundException("Report", key);
        }
        if (!allowed(d, CurrentUser.get())) {
            throw new ForbiddenException("You may not view this report");
        }
        return d.builder().apply(new ReportParams(d, params, clock));
    }

    private static boolean allowed(ReportDefinition d, AuthenticatedUser user) {
        return d.requiresAny().isEmpty() || d.requiresAny().stream().anyMatch(user::hasPermission);
    }
}
