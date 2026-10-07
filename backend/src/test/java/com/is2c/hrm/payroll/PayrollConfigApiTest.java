package com.is2c.hrm.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.is2c.testsupport.ApiTestBase;

import tools.jackson.databind.JsonNode;

class PayrollConfigApiTest extends ApiTestBase {

    String admin;
    String payroll;
    UUID company;

    @BeforeEach
    void setUp() throws Exception {
        admin = data.bearerFor("root", "SUPER_ADMIN");
        payroll = data.bearerFor("po", "PAYROLL_OFFICER");
        company = orgs.company(admin, "ACME");
    }

    private JsonNode component(String code) throws Exception {
        for (JsonNode c : body(getAs(payroll, "/api/v1/companies/{c}/payroll-components", company))) {
            if (c.get("code").asString().equals(code)) {
                return c;
            }
        }
        throw new IllegalStateException(code);
    }

    private Map<String, Object> percentOf(String code, String basis, int sequence) {
        return new HashMap<>(Map.of("code", code, "name", code, "kind", "DEDUCTION", "calcType", "PERCENT_OF",
                "basisRef", basis, "ratePercent", 1, "sequence", sequence, "assignment", "AUTOMATIC"));
    }

    @Test
    @DisplayName("FR-081 the LK pack seeds EPF 8/12, ETF 3 and the APIT 2025 table as editable statutory data")
    void seededSriLanka() throws Exception {
        getAs(payroll, "/api/v1/companies/{c}/statutory", company).andExpect(status().isOk())
                .andExpect(jsonPath("$.pack").value("LK"))
                .andExpect(jsonPath("$.components[?(@.code == 'EPF_EE')].ratePercent").value(8.0))
                .andExpect(jsonPath("$.components[?(@.code == 'EPF_ER')].ratePercent").value(12.0))
                .andExpect(jsonPath("$.components[?(@.code == 'ETF')].ratePercent").value(3.0))
                .andExpect(jsonPath("$.components[?(@.code == 'EPF_EE')].basisRef").value("BASE:STATUTORY"))
                .andExpect(jsonPath("$.components[?(@.code == 'APIT')].basisRef").value("BASE:TAXABLE"))
                .andExpect(jsonPath("$.slabTables[0].code").value("APIT_MONTHLY"))
                .andExpect(jsonPath("$.slabTables[0].rows", hasSize(6)))
                .andExpect(jsonPath("$.slabTables[0].rows[0].upperBound").value(150000.0))
                .andExpect(jsonPath("$.slabTables[0].rows[0].ratePercent").value(0.0))
                .andExpect(jsonPath("$.slabTables[0].rows[1].upperBound").value(233333.0))
                .andExpect(jsonPath("$.slabTables[0].rows[1].ratePercent").value(6.0))
                .andExpect(jsonPath("$.slabTables[0].rows[2].ratePercent").value(18.0))
                .andExpect(jsonPath("$.slabTables[0].rows[5].upperBound").isEmpty())
                .andExpect(jsonPath("$.slabTables[0].rows[5].ratePercent").value(36.0));
        JsonNode basic = component("BASIC");
        assertThat(basic.get("systemComponent").asBoolean()).isTrue();
        assertThat(basic.get("sequence").asInt()).isEqualTo(10);
        JsonNode ot = component("OT_HOLIDAY");
        assertThat(ot.get("multiplier").asDouble()).isEqualTo(2.0);
        assertThat(ot.get("divisor").asDouble()).isEqualTo(240.0);
        assertThat(component("OT_OFFDAY").get("multiplier").asDouble()).isEqualTo(1.5);
        assertThat(component("NO_PAY").get("divisor").isNull()).isTrue();
        getAs(payroll, "/api/v1/companies/{c}/payroll-components", company).andExpect(jsonPath("$", hasSize(16)));
    }

    @Test
    @DisplayName("FR-081 statutory values are edited as data and audited")
    void editStatutory() throws Exception {
        JsonNode epf = component("EPF_EE");
        Map<String, Object> body = new HashMap<>(Map.of("name", "EPF (employee)", "kind", "DEDUCTION",
                "calcType", "PERCENT_OF", "basisRef", "BASE:STATUTORY", "ratePercent", 10, "sequence", 100,
                "assignment", "AUTOMATIC", "statutory", true, "version", epf.get("version").asLong()));
        putAs(payroll, "/api/v1/payroll-components/{id}", body, epf.get("id").asString()).andExpect(status().isOk())
                .andExpect(jsonPath("$.ratePercent").value(10.0));
        getAs(data.bearerFor("aud", "AUDITOR"), "/api/v1/audit?entityType=STATUTORY_SETTING")
                .andExpect(jsonPath("$.content", hasSize(1)));
        JsonNode apit = body(getAs(payroll, "/api/v1/companies/{c}/slab-tables", company)).get(0);
        putAs(payroll, "/api/v1/slab-tables/{id}", Map.of("name", "APIT", "version", apit.get("version").asLong(),
                "rows", List.of(Map.of("lowerBound", 0, "upperBound", 200000, "ratePercent", 0),
                        Map.of("lowerBound", 200000, "ratePercent", 10))), apit.get("id").asString()).andExpect(status().isOk())
                .andExpect(jsonPath("$.rows", hasSize(2)));
    }

    @Test
    @DisplayName("FR-080 bases may only depend on earlier components (no forward references or cycles)")
    void graphRules() throws Exception {
        postAs(payroll, "/api/v1/companies/{c}/payroll-components", percentOf("EARLY", "COMPONENT:EPF_EE", 60), company)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[*].field", hasItem("basisRef")));
        postAs(payroll, "/api/v1/companies/{c}/payroll-components", percentOf("TOO_EARLY", "BASE:TAXABLE", 45), company)
                .andExpect(status().isUnprocessableContent());
        postAs(payroll, "/api/v1/companies/{c}/payroll-components", percentOf("WELFARE", "BASE:STATUTORY", 130), company)
                .andExpect(status().isCreated());
        Map<String, Object> self = percentOf("SELFREF", "BASE:TAXABLE", 140);
        self.put("bases", List.of("TAXABLE"));
        postAs(payroll, "/api/v1/companies/{c}/payroll-components", self, company).andExpect(status().isUnprocessableContent());
        postAs(payroll, "/api/v1/companies/{c}/payroll-components", percentOf("WHAT", "SALARY", 150), company)
                .andExpect(status().isUnprocessableContent());
        // Moving BASIC-dependent OT before BASIC breaks the graph.
        JsonNode ot = component("OT_WORKDAY");
        Map<String, Object> moved = new HashMap<>(Map.ofEntries(Map.entry("name", "OT"), Map.entry("kind", "EARNING"),
                Map.entry("calcType", "PER_UNIT"), Map.entry("unitMetric", "OT_WORKDAY_HOURS"),
                Map.entry("unitRateMode", "BASIS"), Map.entry("basisRef", "COMPONENT:BASIC"), Map.entry("divisor", 240),
                Map.entry("multiplier", 1.5), Map.entry("sequence", 5), Map.entry("assignment", "AUTOMATIC"),
                Map.entry("version", ot.get("version").asLong())));
        putAs(payroll, "/api/v1/payroll-components/{id}", moved, ot.get("id").asString()).andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("FR-080 calculation-type specific fields are validated")
    void typeFields() throws Exception {
        postAs(payroll, "/api/v1/companies/{c}/payroll-components", Map.of("code", "SLABBY", "name", "x", "kind", "DEDUCTION",
                "calcType", "SLAB", "basisRef", "BASE:TAXABLE", "sequence", 300), company)
                .andExpect(jsonPath("$.errors[*].field", hasItem("slabTableId")));
        postAs(payroll, "/api/v1/companies/{c}/payroll-components", Map.of("code", "UNIT", "name", "x", "kind", "EARNING",
                "calcType", "PER_UNIT", "sequence", 300), company)
                .andExpect(jsonPath("$.errors[*].field", hasItem("unitMetric")));
        postAs(payroll, "/api/v1/companies/{c}/payroll-components", Map.of("code", "MEAL", "name", "Meal", "kind", "EARNING",
                "calcType", "PER_UNIT", "unitMetric", "WORKED_DAYS", "unitRateMode", "FIXED_AMOUNT", "unitFixedAmount", 250,
                "sequence", 25, "assignment", "AUTOMATIC"), company).andExpect(status().isCreated());
        postAs(payroll, "/api/v1/companies/{c}/payroll-components", Map.of("code", "bad code", "name", "x", "kind", "EARNING",
                "calcType", "FIXED"), company).andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("FR-080 the BASIC system component cannot be deactivated")
    void basicProtected() throws Exception {
        JsonNode basic = component("BASIC");
        putAs(payroll, "/api/v1/payroll-components/{id}", Map.of("name", "Basic", "active", false,
                "version", basic.get("version").asLong()), basic.get("id").asString()).andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("FR-081 slab rows must start at 0, be contiguous, and only the last may be open-ended")
    void slabRules() throws Exception {
        postAs(payroll, "/api/v1/companies/{c}/slab-tables", Map.of("code", "GAP", "name", "Gap", "rows", List.of(
                Map.of("lowerBound", 0, "upperBound", 100, "ratePercent", 0),
                Map.of("lowerBound", 150, "ratePercent", 10))), company).andExpect(status().isUnprocessableContent());
        Map<String, Object> open = new HashMap<>(Map.of("lowerBound", 0, "ratePercent", 0));
        postAs(payroll, "/api/v1/companies/{c}/slab-tables", Map.of("code", "OPEN", "name", "Open", "rows", List.of(
                open, Map.of("lowerBound", 0, "ratePercent", 10))), company).andExpect(status().isUnprocessableContent());
        postAs(payroll, "/api/v1/companies/{c}/slab-tables", Map.of("code", "OK", "name", "Ok", "rows", List.of(
                Map.of("lowerBound", 0, "upperBound", 100, "ratePercent", 0),
                Map.of("lowerBound", 100, "ratePercent", 10))), company).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("FR-010 companies must use a known statutory pack")
    void unknownPack() throws Exception {
        postAs(admin, "/api/v1/companies", Map.of("code", "XX", "legalName", "X", "statutoryPack", "MARS"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].field").value("statutoryPack"));
        getAs(payroll, "/api/v1/payroll/statutory-packs").andExpect(jsonPath("$[0].code").value("LK"));
    }

    @Test
    @DisplayName("FR-007 payroll configuration requires payroll permissions")
    void permissions() throws Exception {
        getAs(data.bearerFor("ca", "COMPANY_ADMIN"), "/api/v1/companies/{c}/payroll-components", company)
                .andExpect(status().isForbidden());
        String hr = data.bearerFor("hr", "HR_MANAGER");
        getAs(hr, "/api/v1/companies/{c}/payroll-components", company).andExpect(status().isOk());
        postAs(hr, "/api/v1/companies/{c}/payroll-components", percentOf("HRX", "GROSS", 300), company)
                .andExpect(status().isForbidden());
    }
}
