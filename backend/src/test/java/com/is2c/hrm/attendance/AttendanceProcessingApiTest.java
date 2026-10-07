package com.is2c.hrm.attendance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import com.is2c.hrm.security.api.ScopeType;
import com.is2c.hrm.security.domain.ScopeItem;
import com.is2c.hrm.security.domain.ScopeItem.ScopeItemType;
import com.is2c.testsupport.ApiTestBase;
import com.is2c.testsupport.OrgFixtures.Structure;

class AttendanceProcessingApiTest extends ApiTestBase {

    @Autowired
    JdbcTemplate jdbc;

    String admin;
    String hr;
    com.is2c.hrm.security.domain.AppUser hrUser;
    Structure acme;
    String day;
    String night;
    UUID worker;
    String apiKey;

    @BeforeEach
    void setUp() throws Exception {
        clock.set(Instant.parse("2026-03-20T12:00:00Z")); // Friday afternoon in Colombo
        admin = data.bearerFor("root", "SUPER_ADMIN");
        hrUser = data.user("hr", "HR_MANAGER");
        hr = data.bearer(hrUser);
        acme = orgs.structure(admin, "ACME");
        String ot = body(postAs(admin, "/api/v1/companies/{c}/ot-policies", Map.of("name", "Std"), acme.companyId()))
                .get("id").asString();
        day = body(postAs(admin, "/api/v1/companies/{c}/shifts", Map.of("code", "DAY", "name", "Day", "type", "FIXED",
                "s1Start", "08:30", "s1End", "17:30", "breakMode", "FIXED", "breakMinutes", 60, "breakThresholdMinutes", 300,
                "lateGraceMinutes", 10, "otPolicyId", ot), acme.companyId())).get("id").asString();
        night = body(postAs(admin, "/api/v1/companies/{c}/shifts", Map.of("code", "NGT", "name", "Night", "type", "NIGHT",
                "s1Start", "22:00", "s1End", "06:00", "otPolicyId", ot), acme.companyId())).get("id").asString();
        worker = employee("Day", "Worker", "B1", day, "2025-01-01");
        apiKey = body(postAs(admin, "/api/v1/companies/{c}/attendance-sources", Map.of("name", "dev"), acme.companyId()))
                .get("apiKey").asString();
    }

    private UUID employee(String first, String last, String badge, String shift, String joined) throws Exception {
        Map<String, Object> body = orgs.employeeBody(acme, first, last);
        body.put("badgeId", badge);
        body.put("defaultShiftId", shift);
        body.put("joinDate", joined);
        return orgs.employee(hr, body);
    }

    private void punch(String id, String badge, String localTime, String dir) throws Exception {
        Map<String, Object> e = new HashMap<>(Map.of("externalId", id, "employeeIdentifier", badge, "timestamp", localTime));
        e.put("direction", dir);
        mvc.perform(post("/api/v1/integrations/attendance/events").header("X-Api-Key", apiKey)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("events", List.of(e)))))
                .andExpect(status().isOk());
    }

    private void process(String from, String to) throws Exception {
        postAs(hr, "/api/v1/attendance/process", Map.of("companyId", acme.companyId(), "from", from, "to", to))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("FR-048 ingested punches are processed into daily attendance with OT and break")
    void ingestedPunchesProcessed() throws Exception {
        punch("p1", "B1", "2026-03-16T08:25:00", "IN");
        punch("p2", "B1", "2026-03-16T18:44:00", "OUT");
        getAs(hr, "/api/v1/attendance/daily?companyId={c}&from=2026-03-16&to=2026-03-16", acme.companyId())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].status").value("PRESENT"))
                .andExpect(jsonPath("$.content[0].workedMinutes").value(619 - 60))
                .andExpect(jsonPath("$.content[0].otWorkdayMinutes").value(60))
                .andExpect(jsonPath("$.content[0].firstIn").value("2026-03-16T08:25:00"))
                .andExpect(jsonPath("$.content[0].employeeNumber").isString());
    }

    @Test
    @DisplayName("FR-048 on-demand processing marks absences, weekly-offs and holidays; never future dates")
    void absencesAndFuture() throws Exception {
        postAs(admin, "/api/v1/companies/{c}/holidays", Map.of("date", "2026-03-18", "name", "Poya", "type", "PUBLIC"),
                acme.companyId());
        process("2026-03-16", "2026-03-25");
        assertThat(jdbc.queryForObject("SELECT max(work_date) FROM attendance_daily", String.class)).isEqualTo("2026-03-20");
        getAs(hr, "/api/v1/attendance/daily?companyId={c}&from=2026-03-16&to=2026-03-22&size=50", acme.companyId())
                .andExpect(jsonPath("$.content", hasSize(5)))
                .andExpect(jsonPath("$.content[0].status").value("ABSENT"))
                .andExpect(jsonPath("$.content[2].status").value("HOLIDAY"));
        clock.set(Instant.parse("2026-03-23T12:00:00Z"));
        hr = data.bearer(hrUser); // tokens issued before the clock moved have expired
        process("2026-03-22", "2026-03-22");
        getAs(hr, "/api/v1/attendance/daily?companyId={c}&from=2026-03-22&to=2026-03-22", acme.companyId())
                .andExpect(jsonPath("$.content[0].status").value("WEEKLY_OFF"));
    }

    @Test
    @DisplayName("FR-048 processing is idempotent and respects join dates")
    void idempotentAndJoinDate() throws Exception {
        employee("New", "Joiner", "B9", day, "2026-03-19");
        process("2026-03-16", "2026-03-20");
        process("2026-03-16", "2026-03-20");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM attendance_daily", Integer.class)).isEqualTo(5 + 2);
    }

    @Test
    @DisplayName("FR-048 night shift: the morning punch is attributed to the start date (CL-C3)")
    void nightShift() throws Exception {
        employee("Night", "Owl", "N1", night, "2025-01-01");
        punch("n1", "N1", "2026-03-17T21:55:00", "IN");
        punch("n2", "N1", "2026-03-18T06:05:00", "OUT");
        getAs(hr, "/api/v1/attendance/daily?companyId={c}&from=2026-03-17&to=2026-03-18&status=PRESENT", acme.companyId())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].date").value("2026-03-17"))
                .andExpect(jsonPath("$.content[0].lastOut").value("2026-03-18T06:05:00"));
    }

    @Test
    @DisplayName("FR-039 publishing a roster change reprocesses affected days")
    void rosterChangeReprocesses() throws Exception {
        process("2026-03-16", "2026-03-20");
        getAs(hr, "/api/v1/attendance/daily?companyId={c}&from=2026-03-17&to=2026-03-17", acme.companyId())
                .andExpect(jsonPath("$.content[0].status").value("ABSENT"));
        String roster = body(postAs(hr, "/api/v1/rosters", Map.of("companyId", acme.companyId(), "name", "W12",
                "periodType", "WEEKLY", "startDate", "2026-03-16"))).get("id").asString();
        Map<String, Object> cell = new HashMap<>(Map.of("employeeId", worker, "date", "2026-03-17", "off", true));
        cell.put("shiftId", null);
        putAs(hr, "/api/v1/rosters/{id}/entries", Map.of("entries", List.of(cell)), roster).andExpect(status().isOk());
        postAs(hr, "/api/v1/rosters/{id}/publish", null, roster).andExpect(status().isOk());
        getAs(hr, "/api/v1/attendance/daily?companyId={c}&from=2026-03-17&to=2026-03-17", acme.companyId())
                .andExpect(jsonPath("$.content[0].status").value("WEEKLY_OFF"));
    }

    @Test
    @DisplayName("FR-055 day detail shows raw events; employees see only their own attendance via /me")
    void detailAndSelfService() throws Exception {
        punch("p1", "B1", "2026-03-16T08:25:00", "IN");
        getAs(hr, "/api/v1/attendance/daily/{e}/2026-03-16", worker)
                .andExpect(jsonPath("$.daily.status").value("INCOMPLETE"))
                .andExpect(jsonPath("$.events", hasSize(1)))
                .andExpect(jsonPath("$.events[0].time").value("2026-03-16T08:25:00"));
        var u = data.user("self", ScopeType.OWN, Set.of(), "EMPLOYEE");
        String self = data.bearer(data.linkEmployee(u, worker));
        getAs(self, "/api/v1/me/attendance?from=2026-03-16&to=2026-03-16").andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("INCOMPLETE"));
        getAs(self, "/api/v1/attendance/daily?companyId={c}&from=2026-03-16&to=2026-03-16", acme.companyId())
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("FR-006 attendance views are limited to the user's departments")
    void scope() throws Exception {
        UUID fin = orgs.department(admin, acme.companyId(), "FIN", null);
        Map<String, Object> body = orgs.employeeBody(acme, "Fin", "Person");
        body.put("departmentId", fin);
        body.put("defaultShiftId", day);
        UUID finEmp = orgs.employee(hr, body);
        process("2026-03-16", "2026-03-16");
        String mgr = data.bearer(data.user("dm", ScopeType.DEPARTMENT,
                Set.of(new ScopeItem(ScopeItemType.DEPARTMENT, fin)), "DEPARTMENT_MANAGER"));
        getAs(mgr, "/api/v1/attendance/daily?companyId={c}&from=2026-03-16&to=2026-03-16", acme.companyId())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].employeeId").value(finEmp.toString()));
        getAs(mgr, "/api/v1/attendance/daily/{e}/2026-03-16", worker).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("FR-054 daily view can filter by exception type")
    void exceptionFilter() throws Exception {
        punch("p1", "B1", "2026-03-16T09:00:00", "IN");
        punch("p2", "B1", "2026-03-16T17:30:00", "OUT");
        process("2026-03-16", "2026-03-17");
        getAs(hr, "/api/v1/attendance/daily?companyId={c}&from=2026-03-16&to=2026-03-17&exception=LATE", acme.companyId())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].lateMinutes").value(30));
        getAs(hr, "/api/v1/attendance/daily?companyId={c}&from=2026-03-16&to=2026-03-17&exception=ABSENT", acme.companyId())
                .andExpect(jsonPath("$.content[0].date").value("2026-03-17"));
    }
}
