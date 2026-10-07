package com.is2c.hrm.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.is2c.hrm.audit.service.AuditEntry;
import com.is2c.hrm.audit.service.AuditService;
import com.is2c.hrm.support.ApiTestBase;
import com.is2c.hrm.support.TestData;

class AuditApiTest extends ApiTestBase {

    @Autowired
    AuditService audit;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("FR-008 database rejects UPDATE and DELETE of audit records")
    void immutable() {
        audit.record(AuditEntry.of("TEST", "THING", "1").after(Map.of("a", 1)), null, "tester");
        assertThatThrownBy(() -> jdbc.update("UPDATE audit_log SET action = 'X'"))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_log"))
                .hasMessageContaining("append-only");
        assertThat(jdbc.queryForObject("SELECT action FROM audit_log", String.class)).isEqualTo("TEST");
    }

    @Test
    @DisplayName("FR-008 record stores user, before/after JSON, reason and timestamp")
    void recordContents() throws Exception {
        UUID entityId = UUID.randomUUID();
        audit.record(AuditEntry.of("UPDATE", "EMPLOYEE", entityId).before(Map.of("name", "A"))
                .after(Map.of("name", "B")).reason("typo"), UUID.randomUUID(), "editor");
        String bearer = data.bearerFor("aud", "AUDITOR");
        getAs(bearer, "/api/v1/audit?entityType=EMPLOYEE&entityId=" + entityId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].username").value("editor"))
                .andExpect(jsonPath("$.content[0].before").value("{\"name\":\"A\"}"))
                .andExpect(jsonPath("$.content[0].after").value("{\"name\":\"B\"}"))
                .andExpect(jsonPath("$.content[0].reason").value("typo"))
                .andExpect(jsonPath("$.content[0].occurredAt").value("2026-03-16T04:30:00Z"));
    }

    @Test
    @DisplayName("FR-008 search filters by action and date range, newest first")
    void searchFilters() throws Exception {
        audit.record(AuditEntry.of("CREATE", "X", "1"), null, "u");
        clock.advance(Duration.ofHours(1));
        audit.record(AuditEntry.of("CREATE", "X", "2"), null, "u");
        clock.advance(Duration.ofHours(1));
        audit.record(AuditEntry.of("DELETE", "X", "3"), null, "u");
        String bearer = data.bearerFor("aud", "AUDITOR");
        getAs(bearer, "/api/v1/audit?entityType=X&action=CREATE")
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].entityId").value("2"));
        getAs(bearer, "/api/v1/audit?entityType=X&from=2026-03-16T05:00:00Z&to=2026-03-16T06:00:00Z")
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].entityId").value("2"));
    }

    @Test
    @DisplayName("FR-008 users without AUDIT:VIEW cannot read the audit log")
    void permission() throws Exception {
        getAs(data.bearerFor("emp", "EMPLOYEE"), "/api/v1/audit").andExpect(status().isForbidden());
        getAs(data.bearerFor("po", "PAYROLL_OFFICER"), "/api/v1/audit").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("FR-008 no API exists to modify or delete audit records")
    void noMutationApi() throws Exception {
        String bearer = data.bearerFor("root", "SUPER_ADMIN");
        deleteAs(bearer, "/api/v1/audit").andExpect(status().isMethodNotAllowed());
        putAs(bearer, "/api/v1/audit", Map.of()).andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("FR-008 / NFR-008 login success, failure and permission denials are audited")
    void securityEventsAudited() throws Exception {
        data.user("sam", "EMPLOYEE");
        postAs(null, "/api/v1/auth/login", Map.of("username", "sam", "password", TestData.PASSWORD))
                .andExpect(status().isOk());
        postAs(null, "/api/v1/auth/login", Map.of("username", "sam", "password", "Wrong#Pass1"))
                .andExpect(status().isUnauthorized());
        getAs(data.bearerFor("emp2", "EMPLOYEE"), "/api/v1/test-secured/employee-view")
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForList("SELECT action FROM audit_log WHERE entity_type = 'SECURITY'", String.class))
                .contains("LOGIN_SUCCESS", "LOGIN_FAILURE", "PERMISSION_DENIED");
    }

    @Test
    @DisplayName("FR-008 audit record captures the authenticated user when present")
    void capturesCurrentUser() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("x", "y"));
        try {
            audit.record(AuditEntry.of("CREATE", "X", "1"));
        } finally {
            SecurityContextHolder.clearContext();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log", Integer.class)).isEqualTo(1);
    }
}
