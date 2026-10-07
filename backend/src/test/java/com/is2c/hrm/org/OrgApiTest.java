package com.is2c.hrm.org;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import com.is2c.hrm.org.api.CompanyCreatedEvent;
import com.is2c.hrm.security.api.ScopeType;
import com.is2c.hrm.security.domain.ScopeItem;
import com.is2c.hrm.security.domain.ScopeItem.ScopeItemType;
import com.is2c.testsupport.ApiTestBase;

import tools.jackson.databind.JsonNode;

@RecordApplicationEvents
class OrgApiTest extends ApiTestBase {

    @Autowired
    ApplicationEvents events;

    @Autowired
    JdbcTemplate jdbc;

    String admin;

    @BeforeEach
    void setUp() {
        admin = data.bearerFor("root", "SUPER_ADMIN");
    }

    @Test
    @DisplayName("FR-010 creating a company applies defaults, creates settings, audits and publishes CompanyCreatedEvent")
    void createCompany() throws Exception {
        JsonNode c = org.post(admin, "/api/v1/companies", Map.of("code", "acme", "legalName", "Acme (Pvt) Ltd"));
        assertThat(c.get("code").asString()).isEqualTo("ACME");
        assertThat(c.get("currency").asString()).isEqualTo("LKR");
        assertThat(c.get("statutoryPack").asString()).isEqualTo("LK");
        assertThat(c.get("timeZone").asString()).isEqualTo("Asia/Colombo");
        String id = c.get("id").asString();

        getAs(admin, "/api/v1/companies/{id}/settings", id).andExpect(status().isOk())
                .andExpect(jsonPath("$.weeklyOffDays", containsInAnyOrder("SUNDAY")))
                .andExpect(jsonPath("$.employeeNumberFormat").value("{COMPANY}{SEQ:5}"))
                .andExpect(jsonPath("$.payrollStartDay").value(1))
                .andExpect(jsonPath("$.absentAsNoPay").value(true))
                .andExpect(jsonPath("$.noPayDivisor").value(30))
                .andExpect(jsonPath("$.punchWindowBeforeMinutes").value(240));

        assertThat(events.stream(CompanyCreatedEvent.class))
                .containsExactly(new CompanyCreatedEvent(UUID.fromString(id), "LK"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE entity_type='COMPANY' AND action='CREATE'",
                Integer.class)).isEqualTo(1);
    }

    @Test
    @DisplayName("FR-010 duplicate code, invalid currency and invalid time zone are rejected with 422")
    void companyValidation() throws Exception {
        org.company(admin, "ACME");
        postAs(admin, "/api/v1/companies", Map.of("code", "acme", "legalName", "Other"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("code"));
        postAs(admin, "/api/v1/companies", Map.of("code", "X1", "legalName", "X", "currency", "ZZZ", "timeZone", "Mars/Base"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("currency", "timeZone")));
        postAs(admin, "/api/v1/companies", Map.of("code", "bad code!", "legalName", ""))
                .andExpect(jsonPath("$.errors", hasSize(2)));
    }

    @Test
    @DisplayName("FR-010 only global-scope users with ORG:CONFIGURE can create companies")
    void createCompanyRestrictions() throws Exception {
        UUID acme = org.company(admin, "ACME");
        String scopedAdmin = data.bearer(data.user("ca", ScopeType.COMPANY,
                Set.of(new ScopeItem(ScopeItemType.COMPANY, acme)), "COMPANY_ADMIN"));
        postAs(scopedAdmin, "/api/v1/companies", Map.of("code", "B", "legalName", "B")).andExpect(status().isForbidden());
        postAs(data.bearerFor("hr", "HR_MANAGER"), "/api/v1/companies", Map.of("code", "C", "legalName", "C"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("NFR-017 updating with a stale version returns 409")
    void staleUpdate() throws Exception {
        UUID acme = org.company(admin, "ACME");
        Map<String, Object> body = new HashMap<>(Map.of("code", "ACME", "legalName", "Acme 2", "version", 0));
        putAs(admin, "/api/v1/companies/{id}", body, acme).andExpect(status().isOk())
                .andExpect(jsonPath("$.legalName").value("Acme 2"));
        putAs(admin, "/api/v1/companies/{id}", body, acme).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPTIMISTIC_LOCK"));
    }

    @Test
    @DisplayName("FR-016 settings can be changed, are validated and audited")
    void settings() throws Exception {
        UUID acme = org.company(admin, "ACME");
        Map<String, Object> s = new HashMap<>();
        s.put("weeklyOffDays", List.of("SATURDAY", "SUNDAY"));
        s.put("employeeNumberFormat", "E-{BRANCH}-{SEQ:4}");
        s.put("payrollStartDay", 21);
        s.put("absentAsNoPay", false);
        s.put("noPayDivisor", 26);
        s.put("punchWindowBeforeMinutes", 120);
        s.put("punchWindowAfterMinutes", 360);
        s.put("weekStartDay", "SUNDAY");
        putAs(admin, "/api/v1/companies/{id}/settings", s, acme).andExpect(status().isOk())
                .andExpect(jsonPath("$.payrollStartDay").value(21))
                .andExpect(jsonPath("$.weeklyOffDays", containsInAnyOrder("SATURDAY", "SUNDAY")));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE entity_type='COMPANY_SETTINGS'",
                Integer.class)).isEqualTo(1);

        s.put("employeeNumberFormat", "{COMPANY}");
        s.put("payrollStartDay", 29);
        putAs(admin, "/api/v1/companies/{id}/settings", s, acme).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("employeeNumberFormat", "payrollStartDay")));
    }

    @Test
    @DisplayName("FR-011 branch codes are unique per company; deleted branches disappear")
    void branches() throws Exception {
        UUID a = org.company(admin, "A");
        UUID b = org.company(admin, "B");
        UUID colombo = org.branch(admin, a, "CMB");
        org.branch(admin, b, "CMB");
        postAs(admin, "/api/v1/companies/{c}/branches", Map.of("code", "cmb", "name", "dup"), a)
                .andExpect(status().isUnprocessableContent());
        getAs(admin, "/api/v1/companies/{c}/branches", a).andExpect(jsonPath("$", hasSize(1)));
        deleteAs(admin, "/api/v1/branches/{id}", colombo).andExpect(status().isNoContent());
        getAs(admin, "/api/v1/companies/{c}/branches", a).andExpect(jsonPath("$", hasSize(0)));
        org.branch(admin, a, "CMB"); // code reusable after soft delete
    }

    @Test
    @DisplayName("FR-012 departments form a tree; cycles, cross-company parents and deleting parents are rejected")
    void departments() throws Exception {
        UUID a = org.company(admin, "A");
        UUID b = org.company(admin, "B");
        UUID ops = org.department(admin, a, "OPS", null);
        UUID wh = org.department(admin, a, "WH", ops);
        UUID shift = org.department(admin, a, "SHIFT", wh);
        UUID foreign = org.department(admin, b, "FIN", null);

        getAs(admin, "/api/v1/companies/{c}/departments/tree", a).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].code").value("OPS"))
                .andExpect(jsonPath("$[0].children[0].code").value("WH"))
                .andExpect(jsonPath("$[0].children[0].children[0].code").value("SHIFT"));

        Map<String, Object> cyc = new HashMap<>(Map.of("code", "OPS", "name", "Ops"));
        cyc.put("parentId", shift);
        putAs(admin, "/api/v1/departments/{id}", cyc, ops).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].message").value("parent assignment would create a cycle"));
        cyc.put("parentId", foreign);
        putAs(admin, "/api/v1/departments/{id}", cyc, ops).andExpect(status().isUnprocessableContent());
        cyc.put("parentId", ops);
        putAs(admin, "/api/v1/departments/{id}", cyc, ops).andExpect(status().isUnprocessableContent());

        deleteAs(admin, "/api/v1/departments/{id}", wh).andExpect(status().isUnprocessableContent());
        deleteAs(admin, "/api/v1/departments/{id}", shift).andExpect(status().isNoContent());
        deleteAs(admin, "/api/v1/departments/{id}", wh).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("FR-013 designations, grades and employment categories are maintained per company")
    void lookups() throws Exception {
        UUID a = org.company(admin, "A");
        org.lookup(admin, a, "designations", "SE");
        JsonNode grade = org.post(admin, "/api/v1/companies/" + a + "/grades", Map.of("code", "G1", "name", "Grade 1", "rank", 3));
        assertThat(grade.get("rank").asInt()).isEqualTo(3);
        JsonNode cat = org.post(admin, "/api/v1/companies/" + a + "/employment-categories", Map.of("code", "PERM", "name", "Permanent"));
        assertThat(cat.get("otEligible").asBoolean()).isTrue();
        JsonNode exec = org.post(admin, "/api/v1/companies/" + a + "/employment-categories",
                Map.of("code", "EXEC", "name", "Executive", "otEligible", false));
        assertThat(exec.get("otEligible").asBoolean()).isFalse();

        postAs(admin, "/api/v1/companies/{c}/designations", Map.of("code", "se", "name", "dup"), a)
                .andExpect(status().isUnprocessableContent());
        getAs(admin, "/api/v1/companies/{c}/employment-categories", a).andExpect(jsonPath("$", hasSize(2)));
        deleteAs(admin, "/api/v1/employment-categories/{id}", exec.get("id").asString()).andExpect(status().isNoContent());
        getAs(admin, "/api/v1/companies/{c}/employment-categories", a).andExpect(jsonPath("$", hasSize(1)));
        getAs(admin, "/api/v1/companies/{c}/unknown-things", a).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("FR-006 company-scoped users only see their companies; others are 404")
    void companyScope() throws Exception {
        UUID a = org.company(admin, "A");
        UUID b = org.company(admin, "B");
        String scoped = data.bearer(data.user("ca", ScopeType.COMPANY,
                Set.of(new ScopeItem(ScopeItemType.COMPANY, a)), "COMPANY_ADMIN"));
        getAs(scoped, "/api/v1/companies").andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].code").value("A"));
        getAs(scoped, "/api/v1/companies/{id}", b).andExpect(status().isNotFound());
        postAs(scoped, "/api/v1/companies/{c}/branches", Map.of("code", "X", "name", "X"), b)
                .andExpect(status().isNotFound());
        postAs(scoped, "/api/v1/companies/{c}/branches", Map.of("code", "X", "name", "X"), a)
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("FR-006 branch-scoped users can read their company's configuration but not change it")
    void branchScopeReadsCompany() throws Exception {
        UUID a = org.company(admin, "A");
        UUID b = org.company(admin, "B");
        UUID br = org.branch(admin, a, "CMB");
        String bm = data.bearer(data.user("bm", ScopeType.BRANCH,
                Set.of(new ScopeItem(ScopeItemType.BRANCH, br)), "BRANCH_MANAGER"));
        getAs(bm, "/api/v1/companies").andExpect(jsonPath("$", hasSize(1)));
        getAs(bm, "/api/v1/companies/{id}/branches", a).andExpect(status().isOk());
        getAs(bm, "/api/v1/companies/{id}/branches", b).andExpect(status().isNotFound());
        postAs(bm, "/api/v1/companies/{c}/branches", Map.of("code", "X", "name", "X"), a)
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("FR-012 department scope expands to descendants")
    void departmentScopeDescendants() throws Exception {
        UUID a = org.company(admin, "A");
        UUID ops = org.department(admin, a, "OPS", null);
        UUID wh = org.department(admin, a, "WH", ops);
        UUID shift = org.department(admin, a, "SHIFT", wh);
        org.department(admin, a, "FIN", null);
        assertThat(com.is2c.hrm.org.service.DataScopeServiceAccess.descendants(ops, jdbc))
                .containsExactlyInAnyOrder(ops, wh, shift);
    }
}
