package com.is2c.hrm.shift;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.is2c.testsupport.ApiTestBase;
import com.is2c.testsupport.OrgFixtures.Structure;

class ShiftApiTest extends ApiTestBase {

    String admin;
    String hr;
    Structure acme;

    @BeforeEach
    void setUp() throws Exception {
        admin = data.bearerFor("root", "SUPER_ADMIN");
        acme = org.structure(admin, "ACME");
        hr = data.bearerFor("hr", "HR_MANAGER");
    }

    private Map<String, Object> shift(String code, String type, String s1Start, String s1End) {
        Map<String, Object> m = new HashMap<>(Map.of("code", code, "name", code + " shift", "type", type));
        if (s1Start != null) {
            m.put("s1Start", s1Start);
            m.put("s1End", s1End);
        }
        return m;
    }

    private String createShift(Map<String, Object> body) throws Exception {
        return body(postAs(admin, "/api/v1/companies/{c}/shifts", body, acme.companyId()).andExpect(status().isCreated()))
                .get("id").asString();
    }

    @Test
    @DisplayName("FR-030 fixed shift with fixed break reports scheduled minutes")
    void fixedShift() throws Exception {
        Map<String, Object> body = shift("DAY", "FIXED", "08:30", "17:30");
        body.put("breakMode", "FIXED");
        body.put("breakMinutes", 60);
        body.put("lateGraceMinutes", 10);
        postAs(admin, "/api/v1/companies/{c}/shifts", body, acme.companyId()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.scheduledMinutes").value(480))
                .andExpect(jsonPath("$.crossesMidnight").value(false))
                .andExpect(jsonPath("$.lateGraceMinutes").value(10));
        postAs(admin, "/api/v1/companies/{c}/shifts", shift("day", "FIXED", "09:00", "17:00"), acme.companyId())
                .andExpect(jsonPath("$.errors[0].field").value("code"));
    }

    @Test
    @DisplayName("FR-030 night shifts must cross midnight; fixed shifts must not; custom may")
    void midnightRules() throws Exception {
        postAs(admin, "/api/v1/companies/{c}/shifts", shift("N1", "NIGHT", "08:00", "17:00"), acme.companyId())
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].field").value("s1End"));
        postAs(admin, "/api/v1/companies/{c}/shifts", shift("F1", "FIXED", "22:00", "06:00"), acme.companyId())
                .andExpect(status().isUnprocessableContent());
        postAs(admin, "/api/v1/companies/{c}/shifts", shift("N2", "NIGHT", "22:00", "06:00"), acme.companyId())
                .andExpect(status().isCreated()).andExpect(jsonPath("$.crossesMidnight").value(true));
        postAs(admin, "/api/v1/companies/{c}/shifts", shift("C1", "CUSTOM", "20:00", "04:00"), acme.companyId())
                .andExpect(status().isCreated());
        postAs(admin, "/api/v1/companies/{c}/shifts", shift("X1", "FIXED", null, null), acme.companyId())
                .andExpect(jsonPath("$.errors[0].field").value("s1Start"));
    }

    @Test
    @DisplayName("FR-030 split shifts need two ordered, same-day sessions")
    void splitRules() throws Exception {
        Map<String, Object> bad = shift("SP", "SPLIT", "07:00", "11:00");
        bad.put("s2Start", "10:00");
        bad.put("s2End", "14:00");
        postAs(admin, "/api/v1/companies/{c}/shifts", bad, acme.companyId()).andExpect(status().isUnprocessableContent());
        bad.put("s2Start", "16:00");
        bad.put("s2End", "20:00");
        bad.put("breakMode", "PUNCHED");
        postAs(admin, "/api/v1/companies/{c}/shifts", bad, acme.companyId()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.scheduledMinutes").value(480));
    }

    @Test
    @DisplayName("FR-030 flexible shifts need a window and required minutes that fit in it")
    void flexibleRules() throws Exception {
        Map<String, Object> flex = shift("FLEX", "FLEXIBLE", null, null);
        flex.put("flexEarliestStart", "07:00");
        flex.put("flexLatestEnd", "12:00");
        flex.put("requiredMinutes", 480);
        postAs(admin, "/api/v1/companies/{c}/shifts", flex, acme.companyId())
                .andExpect(jsonPath("$.errors[0].field").value("requiredMinutes"));
        flex.put("flexLatestEnd", "19:00");
        postAs(admin, "/api/v1/companies/{c}/shifts", flex, acme.companyId()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.scheduledMinutes").value(480))
                .andExpect(jsonPath("$.s1Start").doesNotExist());
    }

    @Test
    @DisplayName("FR-032 OT policies are configurable and cannot be deleted while used")
    void otPolicies() throws Exception {
        String policyId = body(postAs(admin, "/api/v1/companies/{c}/ot-policies", Map.of("name", "Standard"),
                acme.companyId()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.minOtMinutes").value(30))
                .andExpect(jsonPath("$.roundingMinutes").value(15))
                .andExpect(jsonPath("$.requiresApproval").value(false))).get("id").asString();
        postAs(admin, "/api/v1/companies/{c}/ot-policies", Map.of("name", "Bad", "roundingMinutes", 0), acme.companyId())
                .andExpect(status().isUnprocessableContent());
        Map<String, Object> s = shift("DAY", "FIXED", "08:00", "17:00");
        s.put("otPolicyId", policyId);
        createShift(s);
        deleteAs(admin, "/api/v1/ot-policies/{id}", policyId).andExpect(status().isUnprocessableContent());
        putAs(admin, "/api/v1/ot-policies/{id}", Map.of("name", "Standard", "requiresApproval", true, "version", 0), policyId)
                .andExpect(jsonPath("$.requiresApproval").value(true));
        postAs(hr, "/api/v1/companies/{c}/ot-policies", Map.of("name", "HR"), acme.companyId()).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("FR-030 shifts are deactivated, not deleted, and remain listed")
    void deactivate() throws Exception {
        String id = createShift(shift("DAY", "FIXED", "08:00", "17:00"));
        postAs(admin, "/api/v1/shifts/{id}/deactivate", null, id).andExpect(jsonPath("$.active").value(false));
        getAs(admin, "/api/v1/companies/{c}/shifts", acme.companyId()).andExpect(jsonPath("$", hasSize(1)));
        deleteAs(admin, "/api/v1/shifts/{id}", id).andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("FR-031 rotation patterns: 1..62 days of active shifts or OFF")
    void patterns() throws Exception {
        String a = createShift(shift("A", "FIXED", "06:00", "14:00"));
        String b = createShift(shift("B", "FIXED", "14:00", "22:00"));
        postAs(admin, "/api/v1/companies/{c}/rotation-patterns",
                Map.of("name", "AB-off", "days", Arrays.asList(a, a, b, b, null, null)), acme.companyId())
                .andExpect(status().isCreated()).andExpect(jsonPath("$.days", hasSize(6)))
                .andExpect(jsonPath("$.days[4]").doesNotExist());
        postAs(admin, "/api/v1/companies/{c}/rotation-patterns", Map.of("name", "empty", "days", List.of()), acme.companyId())
                .andExpect(status().isUnprocessableContent());
        postAs(admin, "/api/v1/shifts/{id}/deactivate", null, b);
        postAs(admin, "/api/v1/companies/{c}/rotation-patterns", Map.of("name", "uses-inactive", "days", List.of(b)),
                acme.companyId()).andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("FR-031 rotation assignments reject overlaps and other-company employees")
    void assignments() throws Exception {
        String a = createShift(shift("A", "FIXED", "06:00", "14:00"));
        String pattern = body(postAs(admin, "/api/v1/companies/{c}/rotation-patterns",
                Map.of("name", "A", "days", List.of(a)), acme.companyId())).get("id").asString();
        UUID emp = org.employee(hr, acme, "Rota", "Worker");
        postAs(hr, "/api/v1/rotation-patterns/{id}/assignments", Map.of("employeeIds", List.of(emp),
                "startDate", "2026-01-01", "endDate", "2026-06-30"), pattern).andExpect(status().isCreated());
        postAs(hr, "/api/v1/rotation-patterns/{id}/assignments", Map.of("employeeIds", List.of(emp),
                "startDate", "2026-06-01"), pattern).andExpect(status().isUnprocessableContent());
        String later = body(postAs(hr, "/api/v1/rotation-patterns/{id}/assignments", Map.of("employeeIds", List.of(emp),
                "startDate", "2026-07-01"), pattern).andExpect(status().isCreated())).get(0).get("id").asString();
        getAs(hr, "/api/v1/employees/{id}/rotation-assignments", emp).andExpect(jsonPath("$", hasSize(2)));
        deleteAs(hr, "/api/v1/rotation-assignments/{id}", later).andExpect(status().isNoContent());
        deleteAs(admin, "/api/v1/rotation-patterns/{id}", pattern).andExpect(status().isUnprocessableContent());

        Structure other = org.structure(admin, "OTHER");
        UUID foreign = org.employee(hr, other, "Other", "Co");
        postAs(hr, "/api/v1/rotation-patterns/{id}/assignments", Map.of("employeeIds", List.of(foreign),
                "startDate", "2026-01-01"), pattern).andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("FR-015 employee default shift must be an active shift of the same company")
    void defaultShift() throws Exception {
        String day = createShift(shift("DAY", "FIXED", "08:00", "17:00"));
        Map<String, Object> body = org.employeeBody(acme, "A", "B");
        body.put("defaultShiftId", day);
        org.employee(hr, body);
        Structure other = org.structure(admin, "OTHER");
        String foreign = body(postAs(admin, "/api/v1/companies/{c}/shifts", shift("X", "FIXED", "08:00", "17:00"),
                other.companyId())).get("id").asString();
        body.put("defaultShiftId", foreign);
        postAs(hr, "/api/v1/employees", body).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("defaultShiftId")));
    }

    @Test
    @DisplayName("FR-005 supervisors can view shifts but not create them")
    void permissions() throws Exception {
        String sup = data.bearerFor("sup", "SUPERVISOR");
        getAs(sup, "/api/v1/companies/{c}/shifts", acme.companyId()).andExpect(status().isOk());
        postAs(sup, "/api/v1/companies/{c}/shifts", shift("Z", "FIXED", "08:00", "17:00"), acme.companyId())
                .andExpect(status().isForbidden());
    }
}
