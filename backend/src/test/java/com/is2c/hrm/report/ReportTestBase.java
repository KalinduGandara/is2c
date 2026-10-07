package com.is2c.hrm.report;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.MediaType;

import com.is2c.testsupport.ApiTestBase;
import com.is2c.testsupport.OrgFixtures.Structure;

import tools.jackson.databind.JsonNode;

/** Company ACME with a day shift (08:30–17:30), e1 in OPS (badge B1) and e2 in FIN (badge B2). */
abstract class ReportTestBase extends ApiTestBase {

    String admin;
    String hr;
    Structure acme;
    UUID finance;
    String day;
    String apiKey;
    UUID e1;
    UUID e2;

    void baseSetUp() throws Exception {
        admin = data.bearerFor("root", "SUPER_ADMIN");
        hr = data.bearerFor("hr", "HR_MANAGER");
        acme = orgs.structure(admin, "ACME");
        finance = orgs.department(admin, acme.companyId(), "FIN", null);
        day = body(postAs(admin, "/api/v1/companies/{c}/shifts", Map.of("code", "DAY", "name", "Day", "type", "FIXED",
                "s1Start", "08:30", "s1End", "17:30", "breakMinutes", 60, "breakThresholdMinutes", 300), acme.companyId()))
                .get("id").asString();
        apiKey = body(postAs(admin, "/api/v1/companies/{c}/attendance-sources", Map.of("name", "dev"), acme.companyId()))
                .get("apiKey").asString();
        e1 = employee("One", null, "B1");
        e2 = employee("Two", finance, "B2");
    }

    UUID employee(String first, UUID department, String badge) throws Exception {
        Map<String, Object> b = orgs.employeeBody(acme, first, "Person");
        b.put("joinDate", "2025-01-01");
        b.put("defaultShiftId", day);
        b.put("badgeId", badge);
        if (department != null) {
            b.put("departmentId", department);
        }
        return orgs.employee(hr, b);
    }

    void punch(String id, String badge, String timestamp, String direction) throws Exception {
        mvc.perform(post("/api/v1/integrations/attendance/events").header("X-Api-Key", apiKey)
                .contentType(MediaType.APPLICATION_JSON).content(toJson(Map.of("events", List.of(Map.of("externalId", id,
                        "employeeIdentifier", badge, "timestamp", timestamp, "direction", direction))))))
                .andExpect(status().isOk());
    }

    void disableWorkflow(String type) throws Exception {
        for (JsonNode w : body(getAs(admin, "/api/v1/approval/workflows?companyId={c}", acme.companyId()))) {
            if (w.get("txnType").asString().equals(type)) {
                putAs(admin, "/api/v1/approval/workflows/{id}", Map.of("txnType", type, "enabled", false,
                        "levels", List.of(Map.of("approverType", "REPORTING_MANAGER")), "version", w.get("version").asLong()),
                        w.get("id").asString()).andExpect(status().isOk());
            }
        }
    }

    String leaveType(String code) throws Exception {
        for (JsonNode t : body(getAs(hr, "/api/v1/companies/{c}/leave-types", acme.companyId()))) {
            if (t.get("code").asString().equals(code)) {
                return t.get("id").asString();
            }
        }
        throw new IllegalStateException(code);
    }

    /** Report rows as lists of text keyed by column header. */
    static List<Map<String, String>> rows(JsonNode report) {
        List<String> headers = new ArrayList<>();
        report.get("columns").forEach(c -> headers.add(c.get("header").asString()));
        List<Map<String, String>> out = new ArrayList<>();
        for (JsonNode r : report.get("rows")) {
            Map<String, String> m = new java.util.LinkedHashMap<>();
            for (int i = 0; i < headers.size(); i++) {
                m.put(headers.get(i), r.get(i).isNull() ? null : r.get(i).asString());
            }
            out.add(m);
        }
        return out;
    }
}
