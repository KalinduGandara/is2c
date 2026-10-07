package com.is2c.hrm.scope;

import static org.hamcrest.Matchers.hasSize;
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

import com.is2c.hrm.security.api.ScopeType;
import com.is2c.hrm.security.domain.ScopeItem;
import com.is2c.hrm.security.domain.ScopeItem.ScopeItemType;
import com.is2c.testsupport.ApiTestBase;
import com.is2c.testsupport.OrgFixtures.Structure;

/** FR-006: department-scoped users cannot run company-wide payroll/roster actions or see other departments' rows. */
class ScopeEnforcementApiTest extends ApiTestBase {

    String admin;
    String hr;
    Structure acme;
    UUID finance;
    UUID opsEmp;
    UUID finEmp;
    String day;

    @BeforeEach
    void setUp() throws Exception {
        clock.set(Instant.parse("2026-04-05T06:00:00Z"));
        admin = data.bearerFor("root", "SUPER_ADMIN");
        hr = data.bearerFor("hr", "HR_MANAGER");
        acme = orgs.structure(admin, "ACME");
        finance = orgs.department(admin, acme.companyId(), "FIN", null);
        day = body(postAs(admin, "/api/v1/companies/{c}/shifts", Map.of("code", "DAY", "name", "Day", "type", "FIXED",
                "s1Start", "08:30", "s1End", "17:30"), acme.companyId())).get("id").asString();
        opsEmp = orgs.employee(hr, body("Ops", acme.departmentId()));
        finEmp = orgs.employee(hr, body("Fin", finance));
    }

    private Map<String, Object> body(String first, UUID department) {
        Map<String, Object> b = orgs.employeeBody(acme, first, "Person");
        b.put("joinDate", "2025-01-01");
        b.put("departmentId", department);
        b.put("badgeId", first.toUpperCase());
        return b;
    }

    private String opsUser(String name, String role) {
        return data.bearer(data.user(name, ScopeType.DEPARTMENT,
                Set.of(new ScopeItem(ScopeItemType.DEPARTMENT, acme.departmentId())), role));
    }

    @Test
    @DisplayName("FR-006 payroll period actions need whole-company scope; results/exceptions are filtered to scope")
    void payroll() throws Exception {
        String deptPo = opsUser("dpo", "PAYROLL_OFFICER");
        postAs(deptPo, "/api/v1/payroll/periods", Map.of("companyId", acme.companyId(), "year", 2026, "month", 3))
                .andExpect(status().isForbidden());
        String po = data.bearerFor("po", "PAYROLL_OFFICER");
        String period = body(postAs(po, "/api/v1/payroll/periods", Map.of("companyId", acme.companyId(), "year", 2026,
                "month", 3)).andExpect(status().isCreated())).get("id").asString();
        postAs(deptPo, "/api/v1/payroll/periods/{id}/process", null, period).andExpect(status().isForbidden());
        postAs(po, "/api/v1/payroll/periods/{id}/process", null, period).andExpect(status().isOk());
        // neither employee has a salary structure: one NO_SALARY_STRUCTURE exception each
        getAs(po, "/api/v1/payroll/periods/{id}/exceptions", period).andExpect(jsonPath("$", hasSize(2)));
        getAs(deptPo, "/api/v1/payroll/periods/{id}/exceptions", period).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].employeeId").value(opsEmp.toString()));
        postAs(deptPo, "/api/v1/payroll/periods/{id}/submit", Map.of(), period).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("FR-006 department users manage only their department's rosters and see only their employees' entries")
    void rosters() throws Exception {
        String dm = opsUser("dm", "DEPARTMENT_MANAGER");
        Map<String, Object> companyWide = new HashMap<>(Map.of("companyId", acme.companyId(), "name", "All",
                "periodType", "WEEKLY", "startDate", "2026-03-16"));
        postAs(dm, "/api/v1/rosters", companyWide).andExpect(status().isForbidden());
        String all = body(postAs(hr, "/api/v1/rosters", companyWide).andExpect(status().isCreated())).get("id").asString();
        postAs(hr, "/api/v1/rosters/{id}/bulk-assign", Map.of("employeeIds", List.of(opsEmp, finEmp), "from", "2026-03-16",
                "to", "2026-03-17", "shiftId", day), all).andExpect(status().isOk());
        getAs(dm, "/api/v1/rosters/{id}", all).andExpect(jsonPath("$.entries", hasSize(2)))
                .andExpect(jsonPath("$.entries[?(@.employeeId == '" + finEmp + "')]", hasSize(0)));
        postAs(dm, "/api/v1/rosters/{id}/publish", null, all).andExpect(status().isForbidden());

        Map<String, Object> own = new HashMap<>(companyWide);
        own.put("name", "Ops");
        own.put("startDate", "2026-03-23");
        own.put("departmentId", acme.departmentId());
        String ops = body(postAs(dm, "/api/v1/rosters", own).andExpect(status().isCreated())).get("id").asString();
        postAs(dm, "/api/v1/rosters/{id}/publish", null, ops).andExpect(status().isOk());
    }

    @Test
    @DisplayName("FR-046/FR-006 CSV import rejects rows of employees outside the importer's scope")
    void csvImportScope() throws Exception {
        String deptHr = opsUser("dhr", "HR_MANAGER");
        byte[] csv = ("employee,timestamp,direction\nOPS,2026-03-16T08:00:00,IN\nFIN,2026-03-16T08:00:00,IN\n")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart("/api/v1/attendance/import")
                .file(new org.springframework.mock.web.MockMultipartFile("file", "p.csv", "text/csv", csv))
                .param("companyId", acme.companyId().toString()).header("Authorization", deptHr))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accepted").value(1)).andExpect(jsonPath("$.rejected").value(1))
                .andExpect(jsonPath("$.errors[0].row").value(3));
    }
}
