package com.is2c.hrm.leave;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.is2c.hrm.security.api.ScopeType;
import com.is2c.testsupport.ApiTestBase;
import com.is2c.testsupport.OrgFixtures.Structure;

import tools.jackson.databind.JsonNode;

class LeaveBalanceApiTest extends ApiTestBase {

    String admin;
    String hr;
    Structure acme;
    UUID emp;

    @BeforeEach
    void setUp() throws Exception {
        clock.set(Instant.parse("2026-03-16T04:30:00Z"));
        admin = data.bearerFor("root", "SUPER_ADMIN");
        hr = data.bearerFor("hr", "HR_MANAGER");
        acme = orgs.structure(admin, "ACME");
        emp = orgs.employee(hr, acme, "Leave", "Taker");
    }

    private String typeId(String code) throws Exception {
        for (JsonNode t : body(getAs(hr, "/api/v1/companies/{c}/leave-types", acme.companyId()))) {
            if (t.get("code").asString().equals(code)) {
                return t.get("id").asString();
            }
        }
        throw new IllegalStateException(code);
    }

    private JsonNode balance(String code, int year) throws Exception {
        for (JsonNode b : body(getAs(hr, "/api/v1/leave/balances?employeeId={e}&year={y}", emp, year))) {
            if (b.get("leaveTypeCode").asString().equals(code)) {
                return b;
            }
        }
        throw new IllegalStateException(code);
    }

    @Test
    @DisplayName("FR-060 default leave types are seeded: Annual 14, Casual 7, Medical 7, No Pay unlimited")
    void seeded() throws Exception {
        getAs(hr, "/api/v1/companies/{c}/leave-types", acme.companyId()).andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[?(@.code == 'ANNUAL')].annualDays").value(14.0))
                .andExpect(jsonPath("$[?(@.code == 'CASUAL')].annualDays").value(7.0))
                .andExpect(jsonPath("$[?(@.code == 'MEDICAL')].proRata").value(false))
                .andExpect(jsonPath("$[?(@.code == 'NOPAY')].paid").value(false))
                .andExpect(jsonPath("$[?(@.code == 'NOPAY')].unlimited").value(true));
    }

    @Test
    @DisplayName("FR-060 leave types are configurable; codes unique; days in 0.5 steps")
    void typeCrud() throws Exception {
        Map<String, Object> lieu = new HashMap<>(Map.of("code", "LIEU", "name", "Lieu leave", "annualDays", 3.5,
                "accrualMethod", "MONTHLY", "cfMaxDays", 2, "cfExpiryMonths", 3));
        postAs(hr, "/api/v1/companies/{c}/leave-types", lieu, acme.companyId()).andExpect(status().isCreated())
                .andExpect(jsonPath("$.accrualMethod").value("MONTHLY"));
        postAs(hr, "/api/v1/companies/{c}/leave-types", lieu, acme.companyId()).andExpect(status().isUnprocessableContent());
        lieu.put("code", "BAD");
        lieu.put("annualDays", 1.25);
        postAs(hr, "/api/v1/companies/{c}/leave-types", lieu, acme.companyId())
                .andExpect(jsonPath("$.errors[0].field").value("annualDays"));
        postAs(data.bearerFor("sup", "SUPERVISOR"), "/api/v1/companies/{c}/leave-types", lieu, acme.companyId())
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("FR-061/062 balances are created with the annual grant; opening and adjustments need reasons")
    void balancesAndPostings() throws Exception {
        JsonNode annual = balance("ANNUAL", 2026);
        org.assertj.core.api.Assertions.assertThat(annual.get("entitled").asDouble()).isEqualTo(14.0);
        org.assertj.core.api.Assertions.assertThat(annual.get("available").asDouble()).isEqualTo(14.0);
        String annualType = typeId("ANNUAL");
        postAs(hr, "/api/v1/leave/balances/opening", Map.of("employeeId", emp, "leaveTypeId", annualType, "year", 2026,
                "days", 2, "reason", "migrated from old system")).andExpect(status().isOk());
        postAs(hr, "/api/v1/leave/balances/adjust", Map.of("employeeId", emp, "leaveTypeId", annualType, "year", 2026,
                "days", -1.5, "reason", "correction")).andExpect(status().isOk());
        postAs(hr, "/api/v1/leave/balances/adjust", Map.of("employeeId", emp, "leaveTypeId", annualType, "year", 2026,
                "days", 1, "reason", "")).andExpect(status().isUnprocessableContent());
        JsonNode after = balance("ANNUAL", 2026);
        org.assertj.core.api.Assertions.assertThat(after.get("available").asDouble()).isEqualTo(14.5);
        getAs(hr, "/api/v1/leave/balances/{id}/ledger", after.get("id").asString())
                .andExpect(jsonPath("$[*].type", org.hamcrest.Matchers.containsInAnyOrder("ACCRUAL", "OPENING", "ADJUSTMENT")));
        getAs(data.bearerFor("aud", "AUDITOR"), "/api/v1/audit?action=ADJUST_BALANCE").andExpect(jsonPath("$.content", hasSize(1)));
    }

    @Test
    @DisplayName("FR-062 mid-year joiners get a pro-rated annual grant")
    void proRataJoiner() throws Exception {
        Map<String, Object> body = orgs.employeeBody(acme, "New", "Joiner");
        body.put("joinDate", "2026-03-01");
        UUID joiner = orgs.employee(hr, body);
        JsonNode balances = body(getAs(hr, "/api/v1/leave/balances?employeeId={e}&year=2026", joiner));
        for (JsonNode b : balances) {
            if (b.get("leaveTypeCode").asString().equals("ANNUAL")) {
                // 14 × 10 / 12 = 11.67 → 11.5
                org.assertj.core.api.Assertions.assertThat(b.get("entitled").asDouble()).isEqualTo(11.5);
            }
            if (b.get("leaveTypeCode").asString().equals("NOPAY")) {
                org.assertj.core.api.Assertions.assertThat(b.get("unlimited").asBoolean()).isTrue();
            }
        }
    }

    @Test
    @DisplayName("FR-062 monthly accrual credits each employed month once (idempotent job)")
    void monthlyAccrual() throws Exception {
        postAs(hr, "/api/v1/companies/{c}/leave-types", Map.of("code", "MONTHLY", "name", "Monthly", "annualDays", 12,
                "accrualMethod", "MONTHLY"), acme.companyId()).andExpect(status().isCreated());
        postAs(hr, "/api/v1/leave/jobs/accrual?companyId={c}&asOf=2026-03-16", null, acme.companyId()).andExpect(status().isOk());
        postAs(hr, "/api/v1/leave/jobs/accrual?companyId={c}&asOf=2026-03-16", null, acme.companyId()).andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(balance("MONTHLY", 2026).get("entitled").asDouble()).isEqualTo(3.0);
        postAs(hr, "/api/v1/leave/jobs/accrual?companyId={c}&asOf=2026-05-01", null, acme.companyId());
        org.assertj.core.api.Assertions.assertThat(balance("MONTHLY", 2026).get("entitled").asDouble()).isEqualTo(5.0);
    }

    @Test
    @DisplayName("FR-062 year-end carries forward up to the cap; unused carried days expire after the expiry month")
    void carryForwardAndExpiry() throws Exception {
        String annualId = typeId("ANNUAL");
        var annualType = body(getAs(hr, "/api/v1/companies/{c}/leave-types", acme.companyId()));
        long version = 0;
        for (JsonNode t : annualType) {
            if (t.get("code").asString().equals("ANNUAL")) {
                version = t.get("version").asLong();
            }
        }
        putAs(hr, "/api/v1/leave-types/{id}", Map.of("code", "ANNUAL", "name", "Annual leave", "annualDays", 14,
                "proRata", true, "cfMaxDays", 5, "cfExpiryMonths", 3, "version", version), annualId).andExpect(status().isOk());
        clock.set(Instant.parse("2025-06-01T04:30:00Z"));
        hr = data.bearerFor("hr25", "HR_MANAGER");
        balance("ANNUAL", 2025);
        clock.set(Instant.parse("2026-01-01T04:30:00Z"));
        hr = data.bearerFor("hr26", "HR_MANAGER");
        postAs(hr, "/api/v1/leave/jobs/year-end?companyId={c}&year=2025", null, acme.companyId())
                .andExpect(jsonPath("$.affected").value(1));
        postAs(hr, "/api/v1/leave/jobs/year-end?companyId={c}&year=2025", null, acme.companyId())
                .andExpect(jsonPath("$.affected").value(0));
        JsonNode y26 = balance("ANNUAL", 2026);
        org.assertj.core.api.Assertions.assertThat(y26.get("carriedForward").asDouble()).isEqualTo(5.0);
        org.assertj.core.api.Assertions.assertThat(y26.get("available").asDouble()).isEqualTo(19.0);

        postAs(hr, "/api/v1/leave/jobs/expiry?companyId={c}&asOf=2026-03-31", null, acme.companyId())
                .andExpect(jsonPath("$.affected").value(0));
        postAs(hr, "/api/v1/leave/jobs/expiry?companyId={c}&asOf=2026-04-01", null, acme.companyId())
                .andExpect(jsonPath("$.affected").value(1));
        org.assertj.core.api.Assertions.assertThat(balance("ANNUAL", 2026).get("expired").asDouble()).isEqualTo(5.0);
    }

    @Test
    @DisplayName("FR-061 employees see their own balances via self-service")
    void ownBalances() throws Exception {
        var u = data.user("self", ScopeType.OWN, Set.of(), "EMPLOYEE");
        String self = data.bearer(data.linkEmployee(u, emp));
        getAs(self, "/api/v1/me/leave/balances?year=2026").andExpect(jsonPath("$", hasSize(4)));
        getAs(self, "/api/v1/leave/balances?employeeId={e}&year=2026", emp).andExpect(status().isForbidden());
    }
}
