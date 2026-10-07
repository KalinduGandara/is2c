package com.is2c.hrm.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

class ReportApiTest extends ReportTestBase {

    String roster;

    @BeforeEach
    void setUp() throws Exception {
        clock.set(Instant.parse("2026-03-20T06:00:00Z"));
        baseSetUp();
        punch("1", "B1", "2026-03-16T08:45:00", "IN");
        punch("2", "B1", "2026-03-16T17:30:00", "OUT");
        postAs(hr, "/api/v1/attendance/process", Map.of("companyId", acme.companyId(), "from", "2026-03-16", "to", "2026-03-16"))
                .andExpect(status().isOk());
        postAs(hr, "/api/v1/attendance/adjustments", Map.of("employeeId", e2, "date", "2026-03-13",
                "in", "2026-03-13T08:30:00", "out", "2026-03-13T17:30:00", "reason", "forgot card")).andExpect(status().isOk());
        disableWorkflow("LEAVE_REQUEST");
        postAs(hr, "/api/v1/leave/requests", Map.of("employeeId", e1, "leaveTypeId", leaveType("ANNUAL"),
                "fromDate", "2026-03-18", "toDate", "2026-03-18")).andExpect(status().isCreated());
        roster = body(postAs(hr, "/api/v1/rosters", Map.of("companyId", acme.companyId(), "name", "W12",
                "periodType", "WEEKLY", "startDate", "2026-03-16")).andExpect(status().isCreated())).get("id").asString();
        postAs(hr, "/api/v1/rosters/{id}/bulk-assign", Map.of("employeeIds", List.of(e1, e2), "from", "2026-03-16",
                "to", "2026-03-20", "shiftId", day), roster).andExpect(status().isOk());
    }

    private JsonNode run(String bearer, String key, String query) throws Exception {
        return body(getAs(bearer, "/api/v1/reports/" + key + "?" + query).andExpect(status().isOk()));
    }

    private String company() {
        return "companyId=" + acme.companyId();
    }

    private static Map<String, String> rowOf(List<Map<String, String>> rows, String name) {
        return rows.stream().filter(r -> name.equals(r.get("Name"))).findFirst().orElseThrow(() -> new AssertionError(name + " in " + rows));
    }

    private static double num(String s) {
        return Double.parseDouble(s);
    }

    @Test
    @DisplayName("FR-095 the catalog lists only reports the user may view")
    void catalog() throws Exception {
        List<String> hrKeys = keys(hr);
        assertThat(hrKeys).contains("employee-list", "daily-attendance", "attendance-summary", "attendance-edits",
                "leave-balances", "leave-usage", "roster", "payroll-register", "payroll-department-summary")
                .doesNotContain("audit-log");
        String auditor = data.bearerFor("aud", "AUDITOR");
        assertThat(keys(auditor)).contains("audit-log", "employee-list").doesNotContain("payroll-register");
        getAs(data.bearerFor("emp", "EMPLOYEE"), "/api/v1/reports").andExpect(status().isForbidden());
        getAs(auditor, "/api/v1/reports/payroll-register?periodId={p}", UUID.randomUUID()).andExpect(status().isForbidden());
        JsonNode daily = null;
        for (JsonNode r : body(getAs(hr, "/api/v1/reports"))) {
            if (r.get("key").asString().equals("daily-attendance")) {
                daily = r;
            }
        }
        assertThat(daily.get("params").toString()).contains("\"name\":\"date\"", "\"required\":true");
    }

    private List<String> keys(String bearer) throws Exception {
        List<String> keys = new java.util.ArrayList<>();
        body(getAs(bearer, "/api/v1/reports")).forEach(r -> keys.add(r.get("key").asString()));
        return keys;
    }

    @Test
    @DisplayName("FR-095 employee list honours employee search filters")
    void employeeList() throws Exception {
        List<Map<String, String>> all = rows(run(hr, "employee-list", company()));
        assertThat(all).extracting(r -> r.get("Name")).containsExactlyInAnyOrder("One Person", "Two Person");
        List<Map<String, String>> fin = rows(run(hr, "employee-list", company() + "&departmentId=" + finance));
        assertThat(fin).extracting(r -> r.get("Name")).containsExactly("Two Person");
        assertThat(fin.get(0).get("Department")).isEqualTo("FIN dept");
        assertThat(fin.get(0).get("Status")).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("FR-095 daily attendance and attendance summary")
    void attendance() throws Exception {
        JsonNode daily = run(hr, "daily-attendance", company() + "&date=2026-03-16");
        assertThat(daily.get("title").asString()).isEqualTo("Daily attendance");
        assertThat(daily.get("headerLines").toString()).contains("2026-03-16");
        Map<String, String> one = rowOf(rows(daily), "One Person");
        assertThat(one.get("Status")).isEqualTo("PRESENT");
        assertThat(one.get("Shift")).isEqualTo("DAY");
        assertThat(one.get("IN")).isEqualTo("08:45");
        assertThat(num(one.get("Late (min)"))).isEqualTo(15);
        assertThat(rowOf(rows(daily), "Two Person").get("Status")).isEqualTo("ABSENT");

        List<Map<String, String>> summary = rows(run(hr, "attendance-summary", company() + "&from=2026-03-16&to=2026-03-16"));
        Map<String, String> s1 = rowOf(summary, "One Person");
        assertThat(num(s1.get("Present"))).isEqualTo(1);
        assertThat(num(s1.get("Late count"))).isEqualTo(1);
        assertThat(num(s1.get("Late (min)"))).isEqualTo(15);
        assertThat(num(rowOf(summary, "Two Person").get("Absent"))).isEqualTo(1);
    }

    @Test
    @DisplayName("FR-095 attendance edits show before/after values, reason and user")
    void attendanceEdits() throws Exception {
        List<Map<String, String>> edits = rows(run(hr, "attendance-edits", company() + "&from=2026-03-13&to=2026-03-13"));
        assertThat(edits).hasSize(1);
        Map<String, String> e = edits.get(0);
        assertThat(e.get("Name")).isEqualTo("Two Person");
        assertThat(e.get("Reason")).isEqualTo("forgot card");
        assertThat(e.get("After IN")).isEqualTo("2026-03-13 08:30");
        assertThat(e.get("Requested by")).isEqualTo("hr");
    }

    @Test
    @DisplayName("FR-095 leave balances and leave usage")
    void leave() throws Exception {
        List<Map<String, String>> balances = rows(run(hr, "leave-balances", company() + "&year=2026&leaveTypeId=" + leaveType("ANNUAL")));
        assertThat(balances).hasSize(2);
        Map<String, String> one = rowOf(balances, "One Person");
        assertThat(one.get("Leave type")).isEqualTo("Annual leave");
        assertThat(num(one.get("Taken"))).isEqualTo(1);
        List<Map<String, String>> usage = rows(run(hr, "leave-usage", company() + "&from=2026-03-01&to=2026-03-31"));
        assertThat(usage).hasSize(1);
        assertThat(usage.get(0).get("From")).isEqualTo("2026-03-18");
        assertThat(num(usage.get(0).get("Days"))).isEqualTo(1);
    }

    @Test
    @DisplayName("FR-095 roster report is an employee × date grid of shift codes")
    void rosterGrid() throws Exception {
        JsonNode report = run(hr, "roster", "rosterId=" + roster);
        assertThat(report.get("columns").toString()).contains("2026-03-16", "2026-03-22");
        Map<String, String> one = rowOf(rows(report), "One Person");
        assertThat(one).containsEntry("2026-03-16", "DAY").containsEntry("2026-03-20", "DAY");
        assertThat(one.get("2026-03-21")).isNullOrEmpty();
        assertThat(report.get("landscape").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("FR-095 exports need REPORT:EXPORT; required parameters are validated")
    void exports() throws Exception {
        getAs(hr, "/api/v1/reports/daily-attendance/export?format=xlsx&" + company() + "&date=2026-03-16")
                .andExpect(status().isOk()).andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("daily-attendance-")));
        byte[] xlsx = getAs(hr, "/api/v1/reports/daily-attendance/export?format=xlsx&" + company() + "&date=2026-03-16")
                .andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            assertThat(wb.getSheetAt(0).getRow(0).getCell(0).getStringCellValue()).isEqualTo("Daily attendance");
        }
        String csv = new String(getAs(hr, "/api/v1/reports/employee-list/export?format=csv&" + company())
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(csv).contains("Employee No", "One Person", "Two Person");
        getAs(hr, "/api/v1/reports/daily-attendance?" + company()).andExpect(status().isUnprocessableContent());
        getAs(hr, "/api/v1/reports/daily-attendance?" + company() + "&date=yesterday").andExpect(status().isUnprocessableContent());
        getAs(hr, "/api/v1/reports/nope?" + company()).andExpect(status().isNotFound());
        String dm = data.bearerFor("dm", "DEPARTMENT_MANAGER");
        getAs(dm, "/api/v1/reports/employee-list/export?format=csv&" + company()).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("FR-008 audit log report and export (AUDIT:EXPORT)")
    void audit() throws Exception {
        String auditor = data.bearerFor("aud", "AUDITOR");
        List<Map<String, String>> rows = rows(run(auditor, "audit-log", "from=2026-03-01&to=2026-03-31&entityType=ATTENDANCE_ADJUSTMENT"));
        assertThat(rows).isNotEmpty().allMatch(r -> "ATTENDANCE_ADJUSTMENT".equals(r.get("Entity")));
        assertThat(rows.get(0).get("User")).isEqualTo("hr");
        String csv = new String(getAs(auditor, "/api/v1/audit/export?format=csv&entityType=ATTENDANCE_ADJUSTMENT")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        assertThat(csv).contains("ATTENDANCE_ADJUSTMENT", "forgot card");
        getAs(auditor, "/api/v1/audit/export?format=pdf").andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));
        getAs(data.bearerFor("ca", "COMPANY_ADMIN"), "/api/v1/audit/export?format=csv").andExpect(status().isForbidden());
    }
}
