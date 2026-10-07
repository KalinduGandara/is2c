package com.is2c.hrm.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import com.is2c.hrm.attendance.api.AttendanceLockPolicy;
import com.is2c.hrm.security.domain.AppUser;
import com.is2c.testsupport.ApiTestBase;
import com.is2c.testsupport.OrgFixtures.Structure;

@Import(AdjustmentApiTest.Config.class)
class AdjustmentApiTest extends ApiTestBase {

    static class TestLock implements AttendanceLockPolicy {
        final Set<LocalDate> locked = new HashSet<>();
        final List<LocalDate> changed = new CopyOnWriteArrayList<>();

        @Override
        public boolean isLocked(UUID companyId, LocalDate date) {
            return locked.contains(date);
        }

        @Override
        public void attendanceChanged(UUID companyId, Collection<LocalDate> dates) {
            changed.addAll(dates);
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        TestLock testLock() {
            return new TestLock();
        }
    }

    @Autowired
    TestLock lock;

    @Autowired
    JdbcTemplate jdbc;

    String admin;
    String hr;
    AppUser hrUser;
    Structure acme;
    String otPolicy;
    UUID worker;
    String apiKey;

    @BeforeEach
    void setUp() throws Exception {
        lock.locked.clear();
        lock.changed.clear();
        clock.set(Instant.parse("2026-03-20T12:00:00Z"));
        admin = data.bearerFor("root", "SUPER_ADMIN");
        hrUser = data.user("hr", "HR_MANAGER");
        hr = data.bearer(hrUser);
        acme = orgs.structure(admin, "ACME");
        otPolicy = body(postAs(admin, "/api/v1/companies/{c}/ot-policies", Map.of("name", "Approval", "requiresApproval", true),
                acme.companyId())).get("id").asString();
        String day = body(postAs(admin, "/api/v1/companies/{c}/shifts", Map.of("code", "DAY", "name", "Day", "type", "FIXED",
                "s1Start", "08:30", "s1End", "17:30", "breakMinutes", 60, "breakThresholdMinutes", 300,
                "otPolicyId", otPolicy), acme.companyId())).get("id").asString();
        Map<String, Object> body = orgs.employeeBody(acme, "Day", "Worker");
        body.put("badgeId", "B1");
        body.put("defaultShiftId", day);
        worker = orgs.employee(hr, body);
        apiKey = body(postAs(admin, "/api/v1/companies/{c}/attendance-sources", Map.of("name", "dev"), acme.companyId()))
                .get("apiKey").asString();
    }

    private void punch(String id, String ts, String dir) throws Exception {
        mvc.perform(post("/api/v1/integrations/attendance/events").header("X-Api-Key", apiKey)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("events", List.of(Map.of("externalId", id,
                        "employeeIdentifier", "B1", "timestamp", ts, "direction", dir)))))).andExpect(status().isOk());
    }

    private Map<String, Object> adjustment(String date, String in, String out, String reason) {
        Map<String, Object> m = new HashMap<>(Map.of("employeeId", worker, "date", date));
        m.put("in", in);
        m.put("out", out);
        m.put("reason", reason);
        return m;
    }

    @Test
    @DisplayName("FR-050 manual attendance requires a reason; with no workflow it applies immediately")
    void manualCreate() throws Exception {
        postAs(hr, "/api/v1/attendance/adjustments", adjustment("2026-03-16", "2026-03-16T08:30:00", "2026-03-16T17:30:00", " "))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].field").value("reason"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM attendance_adjustment", Integer.class)).isZero();
        postAs(hr, "/api/v1/attendance/adjustments", adjustment("2026-03-16", "2026-03-16T08:30:00", "2026-03-16T17:30:00", "device down"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.type").value("CREATE"))
                .andExpect(jsonPath("$.status").value("APPLIED"));
        getAs(hr, "/api/v1/attendance/daily/{e}/2026-03-16", worker)
                .andExpect(jsonPath("$.daily.status").value("PRESENT"))
                .andExpect(jsonPath("$.daily.manual").value(true))
                .andExpect(jsonPath("$.daily.workedMinutes").value(480))
                .andExpect(jsonPath("$.adjustments", hasSize(1)));
    }

    @Test
    @DisplayName("FR-051 editing IN keeps raw events, stores before/after and writes an audit record")
    void editIn() throws Exception {
        punch("p1", "2026-03-16T09:00:00", "IN");
        punch("p2", "2026-03-16T17:30:00", "OUT");
        getAs(hr, "/api/v1/attendance/daily/{e}/2026-03-16", worker).andExpect(jsonPath("$.daily.lateMinutes").value(30));
        postAs(hr, "/api/v1/attendance/adjustments", adjustment("2026-03-16", "2026-03-16T08:30:00", null, "forgot to punch"))
                .andExpect(jsonPath("$.type").value("EDIT"))
                .andExpect(jsonPath("$.beforeIn").value("2026-03-16T09:00:00"))
                .andExpect(jsonPath("$.newOut").value("2026-03-16T17:30:00"));
        getAs(hr, "/api/v1/attendance/daily/{e}/2026-03-16", worker)
                .andExpect(jsonPath("$.daily.lateMinutes").value(0))
                .andExpect(jsonPath("$.events", hasSize(2)))
                .andExpect(jsonPath("$.events[0].time").value("2026-03-16T09:00:00"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM attendance_event", Integer.class)).isEqualTo(2);
        getAs(data.bearerFor("aud", "AUDITOR"), "/api/v1/audit?entityType=ATTENDANCE_ADJUSTMENT")
                .andExpect(jsonPath("$.content[0].action").value("MANUAL_EDIT"))
                .andExpect(jsonPath("$.content[0].reason").value("forgot to punch"))
                .andExpect(jsonPath("$.content[0].before").value(org.hamcrest.Matchers.containsString("09:00")))
                .andExpect(jsonPath("$.content[0].after").value(org.hamcrest.Matchers.containsString("08:30")));
    }

    @Test
    @DisplayName("FR-050/051 validation: OUT after IN, no future dates, permission per create/edit")
    void validation() throws Exception {
        postAs(hr, "/api/v1/attendance/adjustments", adjustment("2026-03-16", "2026-03-16T17:00:00", "2026-03-16T08:00:00", "x"))
                .andExpect(jsonPath("$.errors[0].field").value("out"));
        postAs(hr, "/api/v1/attendance/adjustments", adjustment("2026-03-25", "2026-03-25T08:00:00", "2026-03-25T17:00:00", "x"))
                .andExpect(status().isUnprocessableContent());
        postAs(admin, "/api/v1/admin/roles", Map.of("code", "EDITOR", "name", "Editor",
                "permissions", List.of("ATTENDANCE:VIEW", "ATTENDANCE:EDIT", "EMPLOYEE:VIEW"))).andExpect(status().isCreated());
        postAs(data.bearerFor("editor", "EDITOR"), "/api/v1/attendance/adjustments",
                adjustment("2026-03-17", "2026-03-17T08:00:00", "2026-03-17T17:00:00", "x")).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("FR-052 with an enabled workflow the correction waits for approval; one pending per day")
    void approvalFlow() throws Exception {
        var wf = body(getAs(admin, "/api/v1/approval/workflows?companyId={c}", acme.companyId()));
        String wfId = null;
        long version = 0;
        for (var w : wf) {
            if (w.get("txnType").asString().equals("ATTENDANCE_CORRECTION")) {
                wfId = w.get("id").asString();
                version = w.get("version").asLong();
            }
        }
        putAs(admin, "/api/v1/approval/workflows/{id}", Map.of("txnType", "ATTENDANCE_CORRECTION", "enabled", true,
                "levels", List.of(Map.of("approverType", "ROLE", "roleCode", "HR_MANAGER")), "version", version), wfId)
                .andExpect(status().isOk());
        punch("p1", "2026-03-16T09:00:00", "IN");
        punch("p2", "2026-03-16T17:30:00", "OUT");
        var adj = body(postAs(hr, "/api/v1/attendance/adjustments", adjustment("2026-03-16", "2026-03-16T08:30:00", null, "late bus"))
                .andExpect(jsonPath("$.status").value("PENDING")));
        getAs(hr, "/api/v1/attendance/daily/{e}/2026-03-16", worker).andExpect(jsonPath("$.daily.lateMinutes").value(30));
        postAs(hr, "/api/v1/attendance/adjustments", adjustment("2026-03-16", "2026-03-16T08:45:00", null, "again"))
                .andExpect(status().isUnprocessableContent());

        String hr2 = data.bearerFor("hr2", "HR_MANAGER");
        var inbox = body(getAs(hr2, "/api/v1/approval/inbox").andExpect(jsonPath("$", hasSize(1))));
        postAs(hr, "/api/v1/approval/transactions/{id}/approve", Map.of(), inbox.get(0).get("id").asString())
                .andExpect(status().isForbidden());
        postAs(hr2, "/api/v1/approval/transactions/{id}/approve", Map.of(), inbox.get(0).get("id").asString())
                .andExpect(status().isOk());
        getAs(hr, "/api/v1/attendance/daily/{e}/2026-03-16", worker).andExpect(jsonPath("$.daily.lateMinutes").value(0))
                .andExpect(jsonPath("$.adjustments[0].status").value("APPLIED"));
        assertThat(adj.get("id").asString()).isNotBlank();
    }

    @Test
    @DisplayName("FR-053 locked dates reject changes; ATTENDANCE:CONFIGURE may override with audit")
    void lockAndOverride() throws Exception {
        lock.locked.add(LocalDate.parse("2026-03-16"));
        postAs(hr, "/api/v1/attendance/adjustments", adjustment("2026-03-16", "2026-03-16T08:30:00", "2026-03-16T17:30:00", "x"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("ATTENDANCE_LOCKED"));
        Map<String, Object> override = adjustment("2026-03-16", "2026-03-16T08:30:00", "2026-03-16T17:30:00", "audit fix");
        override.put("overrideLock", true);
        postAs(admin, "/api/v1/admin/roles", Map.of("code", "CLERK", "name", "Clerk",
                "permissions", List.of("ATTENDANCE:ADD", "ATTENDANCE:EDIT", "EMPLOYEE:VIEW"))).andExpect(status().isCreated());
        postAs(data.bearerFor("clerk", "CLERK"), "/api/v1/attendance/adjustments", override).andExpect(status().isForbidden());
        postAs(hr, "/api/v1/attendance/adjustments", override).andExpect(status().isOk())
                .andExpect(jsonPath("$.overrideLock").value(true)).andExpect(jsonPath("$.status").value("APPLIED"));
        getAs(hr, "/api/v1/attendance/daily/{e}/2026-03-16", worker).andExpect(jsonPath("$.daily.manual").value(true));
        getAs(data.bearerFor("aud", "AUDITOR"), "/api/v1/audit?action=LOCK_OVERRIDE").andExpect(jsonPath("$.content", hasSize(1)));
        // Normal processing still skips the locked date.
        postAs(hr, "/api/v1/attendance/process", Map.of("companyId", acme.companyId(), "from", "2026-03-16", "to", "2026-03-16"))
                .andExpect(jsonPath("$.skippedLocked").value(1));
    }

    @Test
    @DisplayName("FR-053 attendance changes notify the lock policy so draft payroll becomes stale")
    void staleNotification() throws Exception {
        punch("p1", "2026-03-17T08:30:00", "IN");
        assertThat(lock.changed).contains(LocalDate.parse("2026-03-17"));
    }

    @Test
    @DisplayName("FR-056 OT needing approval is pending until approved; rejection is recorded")
    void otApproval() throws Exception {
        punch("p1", "2026-03-16T08:30:00", "IN");
        punch("p2", "2026-03-16T19:30:00", "OUT");
        punch("p3", "2026-03-17T08:30:00", "IN");
        punch("p4", "2026-03-17T19:30:00", "OUT");
        var rows = body(getAs(hr, "/api/v1/attendance/exceptions?companyId={c}&from=2026-03-16&to=2026-03-17&type=OT_PENDING",
                acme.companyId()).andExpect(jsonPath("$.content", hasSize(2))));
        String d16 = rows.get("content").get(0).get("id").asString();
        String d17 = rows.get("content").get(1).get("id").asString();
        postAs(hr, "/api/v1/attendance/ot/approve", Map.of("dailyIds", List.of(d16))).andExpect(jsonPath("$.updated").value(1));
        postAs(hr, "/api/v1/attendance/ot/reject", Map.of("dailyIds", List.of(d17), "comment", "not authorised"))
                .andExpect(status().isOk());
        getAs(hr, "/api/v1/attendance/daily/{e}/2026-03-16", worker)
                .andExpect(jsonPath("$.daily.otApproved").value(true))
                .andExpect(jsonPath("$.daily.exceptions", org.hamcrest.Matchers.not(hasItem("OT_PENDING"))));
        getAs(hr, "/api/v1/attendance/daily/{e}/2026-03-17", worker).andExpect(jsonPath("$.daily.otRejected").value(true));
        // decided OT stays decided after reprocessing (no OT_PENDING again)
        postAs(hr, "/api/v1/attendance/process", Map.of("companyId", acme.companyId(), "from", "2026-03-16", "to", "2026-03-17"))
                .andExpect(status().isOk());
        getAs(hr, "/api/v1/attendance/exceptions?companyId={c}&from=2026-03-16&to=2026-03-17&type=OT_PENDING", acme.companyId())
                .andExpect(jsonPath("$.content", hasSize(0)));
        postAs(data.bearerFor("sup", "SUPERVISOR"), "/api/v1/attendance/ot/approve", Map.of("dailyIds", List.of(d16)))
                .andExpect(status().isForbidden());
        // Days without approval-requiring OT cannot be "approved".
        process();
        String absent = body(getAs(hr, "/api/v1/attendance/exceptions?companyId={c}&from=2026-03-18&to=2026-03-18&type=ABSENT",
                acme.companyId())).get("content").get(0).get("id").asString();
        postAs(hr, "/api/v1/attendance/ot/approve", Map.of("dailyIds", List.of(absent))).andExpect(status().isUnprocessableContent());
    }

    private void process() throws Exception {
        postAs(hr, "/api/v1/attendance/process", Map.of("companyId", acme.companyId(), "from", "2026-03-18", "to", "2026-03-18"));
    }

    @Test
    @DisplayName("FR-054 exceptions list returns only days with exceptions")
    void exceptionsList() throws Exception {
        punch("p1", "2026-03-16T08:30:00", "IN");
        punch("p2", "2026-03-16T17:30:00", "OUT");
        postAs(hr, "/api/v1/attendance/process", Map.of("companyId", acme.companyId(), "from", "2026-03-16", "to", "2026-03-17"));
        List<String> statuses = new ArrayList<>();
        body(getAs(hr, "/api/v1/attendance/exceptions?companyId={c}&from=2026-03-16&to=2026-03-17", acme.companyId()))
                .get("content").forEach(n -> statuses.add(n.get("status").asString()));
        assertThat(statuses).containsExactly("ABSENT");
    }
}
