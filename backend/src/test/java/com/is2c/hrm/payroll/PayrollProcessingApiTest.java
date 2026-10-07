package com.is2c.hrm.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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

import com.is2c.testsupport.ApiTestBase;
import com.is2c.testsupport.OrgFixtures.Structure;

import tools.jackson.databind.JsonNode;

class PayrollProcessingApiTest extends ApiTestBase {

    @Autowired
    JdbcTemplate jdbc;

    String admin;
    String hr;
    String po;
    Structure acme;
    String day;
    UUID e1;
    UUID joiner;
    UUID noStructure;
    UUID indebted;
    Map<String, String> comps = new HashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        clock.set(Instant.parse("2026-04-05T06:00:00Z"));
        admin = data.bearerFor("root", "SUPER_ADMIN");
        hr = data.bearerFor("hr", "HR_MANAGER");
        po = data.bearerFor("po", "PAYROLL_OFFICER");
        acme = orgs.structure(admin, "ACME");
        settings(false, 1);
        String ot = body(postAs(admin, "/api/v1/companies/{c}/ot-policies", Map.of("name", "Std"), acme.companyId())).get("id").asString();
        day = body(postAs(admin, "/api/v1/companies/{c}/shifts", Map.of("code", "DAY", "name", "Day", "type", "FIXED",
                "s1Start", "08:30", "s1End", "17:30", "breakMinutes", 60, "breakThresholdMinutes", 300, "otPolicyId", ot),
                acme.companyId())).get("id").asString();
        for (JsonNode c : body(getAs(po, "/api/v1/companies/{c}/payroll-components", acme.companyId()))) {
            comps.put(c.get("code").asString(), c.get("id").asString());
        }
        e1 = employee("Full", "Month", "2025-01-01", "B1");
        joiner = employee("New", "Joiner", "2026-03-16", "B2");
        noStructure = employee("No", "Structure", "2025-01-01", "B3");
        indebted = employee("In", "Debt", "2025-01-01", "B4");
        structure(e1, 100000, List.of(line("ALLOWANCE", 20000)));
        structure(joiner, 90000, List.of());
        structure(indebted, 50000, List.of(line("LOAN_DEDUCTION", 60000)));
    }

    private void settings(boolean absentAsNoPay, int startDay) throws Exception {
        JsonNode s = body(getAs(admin, "/api/v1/companies/{c}/settings", acme.companyId()));
        putAs(admin, "/api/v1/companies/{c}/settings", Map.of("weeklyOffDays", List.of("SUNDAY"),
                "employeeNumberFormat", "{COMPANY}{SEQ:5}", "payrollStartDay", startDay, "absentAsNoPay", absentAsNoPay,
                "noPayDivisor", 30, "punchWindowBeforeMinutes", 240, "punchWindowAfterMinutes", 240,
                "weekStartDay", "MONDAY", "version", s.get("version").asLong()), acme.companyId()).andExpect(status().isOk());
    }

    private UUID employee(String first, String last, String joined, String badge) throws Exception {
        Map<String, Object> b = orgs.employeeBody(acme, first, last);
        b.put("joinDate", joined);
        b.put("defaultShiftId", day);
        b.put("badgeId", badge);
        return orgs.employee(hr, b);
    }

    private Map<String, Object> line(String code, int amount) {
        Map<String, Object> m = new HashMap<>(Map.of("componentId", comps.get(code), "amount", amount));
        m.put("excluded", false);
        return m;
    }

    private void structure(UUID employee, int basic, List<Map<String, Object>> lines) throws Exception {
        postAs(po, "/api/v1/employees/{e}/salary-structures", Map.of("effectiveFrom", "2025-01-01", "basicSalary", basic,
                "lines", lines), employee).andExpect(status().isCreated());
    }

    private String openMarch() throws Exception {
        return body(postAs(po, "/api/v1/payroll/periods", Map.of("companyId", acme.companyId(), "year", 2026, "month", 3))
                .andExpect(status().isCreated())).get("id").asString();
    }

    private JsonNode result(String period, UUID employee) throws Exception {
        return body(getAs(po, "/api/v1/payroll/periods/{p}/results/{e}", period, employee));
    }

    private static double amount(JsonNode detail, String code) {
        for (JsonNode l : detail.get("lines")) {
            if (l.get("componentCode").asString().equals(code)) {
                return l.get("amount").asDouble();
            }
        }
        return 0;
    }

    @Test
    @DisplayName("FR-084 periods follow the start day and cannot overlap or repeat")
    void periods() throws Exception {
        String march = openMarch();
        getAs(po, "/api/v1/payroll/periods/{id}", march).andExpect(jsonPath("$.startDate").value("2026-03-01"))
                .andExpect(jsonPath("$.endDate").value("2026-03-31")).andExpect(jsonPath("$.status").value("OPEN"));
        postAs(po, "/api/v1/payroll/periods", Map.of("companyId", acme.companyId(), "year", 2026, "month", 3))
                .andExpect(status().isUnprocessableContent());
        settings(false, 21);
        postAs(po, "/api/v1/payroll/periods", Map.of("companyId", acme.companyId(), "year", 2026, "month", 4))
                .andExpect(status().isUnprocessableContent());
        postAs(po, "/api/v1/payroll/periods", Map.of("companyId", acme.companyId(), "year", 2026, "month", 5))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.startDate").value("2026-04-21"))
                .andExpect(jsonPath("$.endDate").value("2026-05-20"));
    }

    @Test
    @DisplayName("FR-085 processing computes LK payroll, prorates joiners and records exceptions")
    void process() throws Exception {
        String march = openMarch();
        postAs(po, "/api/v1/payroll/periods/{id}/process", null, march).andExpect(status().isOk())
                .andExpect(jsonPath("$.processed").value(3)).andExpect(jsonPath("$.skipped").value(1))
                .andExpect(jsonPath("$.blocking").value(1));
        getAs(po, "/api/v1/payroll/periods/{id}", march).andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.stale").value(false));
        JsonNode full = result(march, e1);
        assertThat(amount(full, "BASIC")).isEqualTo(100000.0);
        assertThat(amount(full, "ALLOWANCE")).isEqualTo(20000.0);
        assertThat(amount(full, "EPF_EE")).isEqualTo(8000.0);
        assertThat(amount(full, "EPF_ER")).isEqualTo(12000.0);
        assertThat(full.get("result").get("net").asDouble()).isEqualTo(112000.0);
        // 16 of 31 days → 90,000 × 16 / 31
        assertThat(amount(result(march, joiner), "BASIC")).isEqualTo(46451.61);
        getAs(po, "/api/v1/payroll/periods/{id}/exceptions", march)
                .andExpect(jsonPath("$[?(@.code == 'NO_SALARY_STRUCTURE')].employeeId").value(noStructure.toString()))
                .andExpect(jsonPath("$[?(@.code == 'NEGATIVE_NET')].blocking").value(true));
        getAs(po, "/api/v1/payroll/periods/{id}/results", march).andExpect(jsonPath("$", hasSize(3)));
        JsonNode totals = body(getAs(po, "/api/v1/payroll/periods/{id}/totals", march));
        assertThat(totals.get("employees").asInt()).isEqualTo(3);
        assertThat(totals.get("byComponent").get("BASIC").asDouble()).isEqualTo(100000 + 46451.61 + 50000);
    }

    @Test
    @DisplayName("FR-085 overtime from processed attendance is paid at Basic/240×1.5")
    void overtime() throws Exception {
        String key = body(postAs(admin, "/api/v1/companies/{c}/attendance-sources", Map.of("name", "dev"), acme.companyId()))
                .get("apiKey").asString();
        mvc.perform(post("/api/v1/integrations/attendance/events").header("X-Api-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content(toJson(Map.of("events", List.of(
                        Map.of("externalId", "1", "employeeIdentifier", "B1", "timestamp", "2026-03-10T08:30:00", "direction", "IN"),
                        Map.of("externalId", "2", "employeeIdentifier", "B1", "timestamp", "2026-03-10T19:30:00", "direction", "OUT"))))))
                .andExpect(status().isOk());
        String march = openMarch();
        postAs(po, "/api/v1/payroll/periods/{id}/process", null, march).andExpect(status().isOk());
        JsonNode r = result(march, e1);
        assertThat(amount(r, "OT_WORKDAY")).isEqualTo(1250.0);
        assertThat(r.get("result").get("otHours").asDouble()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("FR-083/086 one-time items mark a draft stale until reprocessed")
    void oneTimeAndStale() throws Exception {
        String march = openMarch();
        postAs(po, "/api/v1/payroll/periods/{id}/process", null, march);
        postAs(po, "/api/v1/payroll/periods/{id}/items", Map.of("employeeId", e1, "componentId", comps.get("ALLOWANCE"),
                "amount", 100), march).andExpect(status().isUnprocessableContent());
        String item = body(postAs(po, "/api/v1/payroll/periods/{id}/items", Map.of("employeeId", e1,
                "componentId", comps.get("BONUS"), "amount", 10000, "note", "Q1 bonus"), march).andExpect(status().isCreated()))
                .get("id").asString();
        getAs(po, "/api/v1/payroll/periods/{id}", march).andExpect(jsonPath("$.stale").value(true));
        postAs(po, "/api/v1/payroll/periods/{id}/process", null, march);
        assertThat(amount(result(march, e1), "BONUS")).isEqualTo(10000.0);
        getAs(po, "/api/v1/payroll/periods/{id}", march).andExpect(jsonPath("$.stale").value(false));
        deleteAs(po, "/api/v1/payroll/items/{id}", item).andExpect(status().isNoContent());
        getAs(po, "/api/v1/payroll/periods/{id}", march).andExpect(jsonPath("$.stale").value(true));
    }

    @Test
    @DisplayName("FR-066 approved unpaid leave becomes no-pay days")
    void unpaidLeave() throws Exception {
        JsonNode wf = null;
        for (JsonNode w : body(getAs(admin, "/api/v1/approval/workflows?companyId={c}", acme.companyId()))) {
            if (w.get("txnType").asString().equals("LEAVE_REQUEST")) {
                wf = w;
            }
        }
        putAs(admin, "/api/v1/approval/workflows/{id}", Map.of("txnType", "LEAVE_REQUEST", "enabled", false,
                "levels", List.of(Map.of("approverType", "REPORTING_MANAGER")), "version", wf.get("version").asLong()),
                wf.get("id").asString()).andExpect(status().isOk());
        String nopay = null;
        for (JsonNode t : body(getAs(hr, "/api/v1/companies/{c}/leave-types", acme.companyId()))) {
            if (t.get("code").asString().equals("NOPAY")) {
                nopay = t.get("id").asString();
            }
        }
        postAs(hr, "/api/v1/leave/requests", Map.of("employeeId", e1, "leaveTypeId", nopay, "fromDate", "2026-03-10",
                "toDate", "2026-03-11")).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("APPROVED"));
        String march = openMarch();
        postAs(po, "/api/v1/payroll/periods/{id}/process", null, march);
        JsonNode r = result(march, e1);
        assertThat(r.get("result").get("noPayDays").asDouble()).isEqualTo(2.0);
        assertThat(amount(r, "NO_PAY")).isEqualTo(6666.67);
    }

    @Test
    @DisplayName("FR-053 dates in periods under review are locked; FR-084 locked periods cannot be reprocessed")
    void lockAndState() throws Exception {
        String march = openMarch();
        postAs(po, "/api/v1/payroll/periods/{id}/process", null, march);
        jdbc.update("UPDATE payroll_period SET status = 'IN_REVIEW' WHERE id = ?", UUID.fromString(march));
        postAs(hr, "/api/v1/attendance/adjustments", Map.of("employeeId", e1, "date", "2026-03-10",
                "in", "2026-03-10T08:30:00", "out", "2026-03-10T17:30:00", "reason", "x"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("ATTENDANCE_LOCKED"));
        postAs(po, "/api/v1/payroll/periods/{id}/process", null, march).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("FR-086 attendance changes inside a draft period make it stale")
    void staleOnAttendance() throws Exception {
        String march = openMarch();
        postAs(po, "/api/v1/payroll/periods/{id}/process", null, march);
        postAs(hr, "/api/v1/attendance/adjustments", Map.of("employeeId", e1, "date", "2026-03-10",
                "in", "2026-03-10T08:30:00", "out", "2026-03-10T17:30:00", "reason", "manual")).andExpect(status().isOk());
        getAs(po, "/api/v1/payroll/periods/{id}", march).andExpect(jsonPath("$.stale").value(true));
    }

    @Test
    @DisplayName("FR-007 HR can review payroll but only payroll officers process it")
    void permissions() throws Exception {
        String march = openMarch();
        postAs(hr, "/api/v1/payroll/periods/{id}/process", null, march).andExpect(status().isForbidden());
        postAs(po, "/api/v1/payroll/periods/{id}/process", null, march).andExpect(status().isOk());
        getAs(hr, "/api/v1/payroll/periods/{id}/results", march).andExpect(status().isOk());
        getAs(data.bearerFor("ca", "COMPANY_ADMIN"), "/api/v1/payroll/periods/{id}/results", march).andExpect(status().isForbidden());
    }
}
