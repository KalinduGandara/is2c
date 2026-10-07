package com.is2c.hrm.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.ResultActions;

import com.is2c.hrm.attendance.api.AttendanceEventsIngested;
import com.is2c.testsupport.ApiTestBase;
import com.is2c.testsupport.OrgFixtures.Structure;

@RecordApplicationEvents
class IngestionApiTest extends ApiTestBase {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ApplicationEvents appEvents;

    String admin;
    String hr;
    Structure acme;
    UUID badgeHolder;
    String apiKey;
    String sourceId;

    @BeforeEach
    void setUp() throws Exception {
        admin = data.bearerFor("root", "SUPER_ADMIN");
        hr = data.bearerFor("hr", "HR_MANAGER");
        acme = orgs.structure(admin, "ACME");
        Map<String, Object> body = orgs.employeeBody(acme, "Badge", "Holder");
        body.put("badgeId", "B-100");
        badgeHolder = orgs.employee(hr, body);
        var source = body(postAs(admin, "/api/v1/companies/{c}/attendance-sources",
                Map.of("name", "ZK middleware", "adapterType", "GENERIC_JSON"), acme.companyId())
                .andExpect(status().isCreated()));
        apiKey = source.get("apiKey").asString();
        sourceId = source.get("id").asString();
    }

    private Map<String, Object> event(String id, String who, String ts, String dir) {
        Map<String, Object> m = new HashMap<>();
        m.put("externalId", id);
        m.put("employeeIdentifier", who);
        m.put("timestamp", ts);
        m.put("direction", dir);
        m.put("deviceId", "D1");
        return m;
    }

    private ResultActions push(String key, List<Map<String, Object>> events) throws Exception {
        var req = post("/api/v1/integrations/attendance/events").contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("events", events)));
        if (key != null) {
            req.header("X-Api-Key", key);
        }
        return mvc.perform(req);
    }

    @Test
    @DisplayName("FR-045 API key is returned once, stored hashed; invalid or inactive keys get 401")
    void apiKeys() throws Exception {
        assertThat(apiKey).startsWith("hrm_");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM attendance_source WHERE api_key_hash = ?", Integer.class, apiKey)).isZero();
        getAs(admin, "/api/v1/companies/{c}/attendance-sources", acme.companyId())
                .andExpect(jsonPath("$[0].apiKey").doesNotExist());
        push(null, List.of()).andExpect(status().isUnauthorized());
        push(apiKey + "x", List.of()).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("INVALID_API_KEY"));
        push(apiKey, List.of()).andExpect(status().isOk());
        postAs(admin, "/api/v1/attendance-sources/{id}/deactivate", null, sourceId);
        push(apiKey, List.of()).andExpect(status().isUnauthorized());
        postAs(admin, "/api/v1/attendance-sources/{id}/activate", null, sourceId);
        String newKey = body(postAs(admin, "/api/v1/attendance-sources/{id}/regenerate-key", null, sourceId)).get("apiKey").asString();
        push(apiKey, List.of()).andExpect(status().isUnauthorized());
        push(newKey, List.of()).andExpect(status().isOk());
    }

    @Test
    @DisplayName("FR-045 events are matched by badge or employee number, duplicates skipped, bad rows reported")
    void ingest() throws Exception {
        String number = jdbc.queryForObject("SELECT employee_number FROM employee WHERE id = ?", String.class, badgeHolder);
        push(apiKey, List.of(
                event("e1", "b-100", "2026-03-16T08:01:00+05:30", "IN"),
                event("e2", number, "2026-03-16T17:02:00", "OUT"),
                event("e3", "UNKNOWN-7", "2026-03-16T08:00:00", "IN"),
                event("e1", "b-100", "2026-03-16T08:01:00+05:30", "IN"),
                event("e4", "b-100", "garbage", "IN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(3))
                .andExpect(jsonPath("$.duplicates").value(1))
                .andExpect(jsonPath("$.unmatched").value(1))
                .andExpect(jsonPath("$.rejected").value(1))
                .andExpect(jsonPath("$.errors[0].row").value(5));
        push(apiKey, List.of(event("e1", "b-100", "2026-03-16T08:01:00+05:30", "IN")))
                .andExpect(jsonPath("$.accepted").value(0)).andExpect(jsonPath("$.duplicates").value(1));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM attendance_event", Integer.class)).isEqualTo(3);
        // Local timestamps are interpreted in the company time zone (Asia/Colombo, +05:30).
        assertThat(jdbc.queryForObject("SELECT event_time AT TIME ZONE 'UTC' FROM attendance_event WHERE external_id = 'e2'",
                String.class)).startsWith("2026-03-16 11:32");
        assertThat(appEvents.stream(AttendanceEventsIngested.class)).hasSize(1);
    }

    @Test
    @DisplayName("FR-045 batches above 5000 events are rejected")
    void batchLimit() throws Exception {
        List<Map<String, Object>> many = new ArrayList<>();
        for (int i = 0; i < 5001; i++) {
            many.add(event("x" + i, "b-100", "2026-03-16T08:00:00", "IN"));
        }
        push(apiKey, many).andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("FR-045 unmatched events are linked by rematch once the badge is assigned")
    void rematch() throws Exception {
        push(apiKey, List.of(event("u1", "NEWBADGE", "2026-03-16T08:00:00", "IN"))).andExpect(jsonPath("$.unmatched").value(1));
        getAs(hr, "/api/v1/attendance/unmatched-events?companyId={c}", acme.companyId()).andExpect(jsonPath("$.content", hasSize(1)));
        Map<String, Object> body = orgs.employeeBody(acme, "New", "Badge");
        body.put("badgeId", "newbadge");
        orgs.employee(hr, body);
        postAs(hr, "/api/v1/attendance/rematch?companyId={c}", null, acme.companyId()).andExpect(jsonPath("$.matched").value(1));
        getAs(hr, "/api/v1/attendance/unmatched-events?companyId={c}", acme.companyId()).andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    @DisplayName("FR-046 CSV import uses the same matching and is idempotent on re-import")
    void csvImport() throws Exception {
        byte[] csv = ("employee,timestamp,direction\nB-100,2026-03-16T08:00:00,IN\nB-100,2026-03-16T17:00:00,OUT\nB-100,bad\n")
                .getBytes(StandardCharsets.UTF_8);
        mvc.perform(multipart("/api/v1/attendance/import").file(new MockMultipartFile("file", "punches.csv", "text/csv", csv))
                        .param("companyId", acme.companyId().toString()).header("Authorization", hr))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accepted").value(2)).andExpect(jsonPath("$.rejected").value(1));
        mvc.perform(multipart("/api/v1/attendance/import").file(new MockMultipartFile("file", "punches.csv", "text/csv", csv))
                        .param("companyId", acme.companyId().toString()).header("Authorization", hr))
                .andExpect(jsonPath("$.accepted").value(0)).andExpect(jsonPath("$.duplicates").value(2));
        getAs(admin, "/api/v1/companies/{c}/attendance-sources", acme.companyId())
                .andExpect(jsonPath("$[?(@.systemSource == true)].name").value("CSV import"));
    }

    @Test
    @DisplayName("FR-017 employees with attendance events cannot be deleted")
    void usageBlocksDelete() throws Exception {
        push(apiKey, List.of(event("d1", "b-100", "2026-03-16T08:00:00", "IN")));
        deleteAs(hr, "/api/v1/employees/{id}", badgeHolder).andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("FR-045 only ATTENDANCE:CONFIGURE can manage sources")
    void permissions() throws Exception {
        postAs(data.bearerFor("sup", "SUPERVISOR"), "/api/v1/companies/{c}/attendance-sources", Map.of("name", "x"),
                acme.companyId()).andExpect(status().isForbidden());
    }
}
