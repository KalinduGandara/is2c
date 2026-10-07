package com.is2c.hrm.payroll;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.is2c.testsupport.ApiTestBase;
import com.is2c.testsupport.OrgFixtures.Structure;

import tools.jackson.databind.JsonNode;

class SalaryStructureApiTest extends ApiTestBase {

    @Autowired
    JdbcTemplate jdbc;

    String admin;
    String payroll;
    String hr;
    Structure acme;
    UUID emp;
    Map<String, String> components = new HashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        admin = data.bearerFor("root", "SUPER_ADMIN");
        payroll = data.bearerFor("po", "PAYROLL_OFFICER");
        hr = data.bearerFor("hr", "HR_MANAGER");
        acme = orgs.structure(admin, "ACME");
        emp = orgs.employee(hr, acme, "Paid", "Person");
        for (JsonNode c : body(getAs(payroll, "/api/v1/companies/{c}/payroll-components", acme.companyId()))) {
            components.put(c.get("code").asString(), c.get("id").asString());
        }
    }

    private Map<String, Object> line(String code, Object amount, boolean excluded) {
        Map<String, Object> m = new HashMap<>();
        m.put("componentId", components.get(code));
        m.put("amount", amount);
        m.put("excluded", excluded);
        return m;
    }

    @Test
    @DisplayName("FR-082 a new structure supersedes the previous one from its effective date")
    void supersede() throws Exception {
        postAs(payroll, "/api/v1/employees/{e}/salary-structures", Map.of("effectiveFrom", "2025-01-01",
                "basicSalary", 100000, "lines", List.of(line("ALLOWANCE", 10000, false))), emp).andExpect(status().isCreated());
        postAs(payroll, "/api/v1/employees/{e}/salary-structures", Map.of("effectiveFrom", "2026-01-01",
                "basicSalary", 120000, "lines", List.of(line("ALLOWANCE", 12000, false), line("EPF_EE", null, true))), emp)
                .andExpect(status().isCreated());
        getAs(payroll, "/api/v1/employees/{e}/salary-structures", emp).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].basicSalary").value(120000.0))
                .andExpect(jsonPath("$[0].effectiveTo").isEmpty())
                .andExpect(jsonPath("$[1].effectiveTo").value("2025-12-31"));
        postAs(payroll, "/api/v1/employees/{e}/salary-structures", Map.of("effectiveFrom", "2025-06-01", "basicSalary", 1),
                emp).andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("FR-082 lines are validated against the component type")
    void lineRules() throws Exception {
        postAs(payroll, "/api/v1/employees/{e}/salary-structures", Map.of("effectiveFrom", "2026-01-01", "basicSalary", 0,
                "lines", List.of(line("BONUS", 5000, false), line("ALLOWANCE", null, false), line("LOAN_DEDUCTION", 100, true),
                        line("BASIC", 1, false))), emp)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors", hasSize(5)))
                .andExpect(jsonPath("$.errors[*].field", hasItem("basicSalary")));
    }

    @Test
    @DisplayName("FR-082 effective dates inside finalized periods are rejected")
    void finalizedPeriod() throws Exception {
        jdbc.update("""
                INSERT INTO payroll_period (id, company_id, period_year, period_month, start_date, end_date, status)
                VALUES (?, ?, 2026, 1, '2026-01-01', '2026-01-31', 'FINALIZED')""", UUID.randomUUID(), acme.companyId());
        postAs(payroll, "/api/v1/employees/{e}/salary-structures", Map.of("effectiveFrom", "2026-01-15", "basicSalary", 1000),
                emp).andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].field").value("effectiveFrom"));
    }

    @Test
    @DisplayName("FR-007 salary structures need PAYROLL:VIEW; employee profiles never show salary")
    void sensitivity() throws Exception {
        postAs(payroll, "/api/v1/employees/{e}/salary-structures", Map.of("effectiveFrom", "2026-01-01", "basicSalary", 1000),
                emp).andExpect(status().isCreated());
        getAs(data.bearerFor("ca", "COMPANY_ADMIN"), "/api/v1/employees/{e}/salary-structures", emp).andExpect(status().isForbidden());
        postAs(hr, "/api/v1/employees/{e}/salary-structures", Map.of("effectiveFrom", "2026-02-01", "basicSalary", 1000), emp)
                .andExpect(status().isForbidden());
        getAs(hr, "/api/v1/employees/{e}", emp).andExpect(jsonPath("$.basicSalary").doesNotExist());
        deleteAs(hr, "/api/v1/employees/{e}", emp).andExpect(status().isUnprocessableContent());
    }
}
