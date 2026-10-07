package com.is2c.hrm.payroll;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;

import com.is2c.testsupport.ApiTestBase;
import com.is2c.testsupport.OrgFixtures.Structure;

import tools.jackson.databind.JsonNode;

/** Two employees in two departments with March 2026 processed, approved and finalized. */
abstract class FinalizedPayrollTestBase extends ApiTestBase {

    String admin;
    String hr;
    String po;
    Structure acme;
    UUID finance;
    UUID e1;
    UUID e2;
    String march;

    @BeforeEach
    void finalizedMarch() throws Exception {
        clock.set(Instant.parse("2026-04-05T06:00:00Z"));
        admin = data.bearerFor("root", "SUPER_ADMIN");
        hr = data.bearerFor("hr", "HR_MANAGER");
        po = data.bearerFor("po", "PAYROLL_OFFICER");
        acme = orgs.structure(admin, "ACME");
        finance = orgs.department(admin, acme.companyId(), "FIN", null);
        Map<String, Object> one = orgs.employeeBody(acme, "One", "Person");
        one.put("joinDate", "2025-01-01");
        one.put("personalEmail", "one@example.com");
        e1 = orgs.employee(hr, one);
        Map<String, Object> two = orgs.employeeBody(acme, "Two", "Person");
        two.put("joinDate", "2025-01-01");
        two.put("departmentId", finance);
        e2 = orgs.employee(hr, two);
        structure(e1, 100000);
        structure(e2, 80000);
        march = processed(3);
        postAs(po, "/api/v1/payroll/periods/{id}/submit", Map.of(), march).andExpect(status().isOk());
        postAs(hr, "/api/v1/approval/transactions/{id}/approve", Map.of("comment", "ok"),
                body(getAs(po, "/api/v1/payroll/periods/{id}", march)).get("approvalId").asString())
                .andExpect(status().isOk());
        postAs(hr, "/api/v1/payroll/periods/{id}/finalize", Map.of(), march).andExpect(status().isOk());
    }

    void structure(UUID employee, int basic) throws Exception {
        postAs(po, "/api/v1/employees/{e}/salary-structures", Map.of("effectiveFrom", "2025-01-01", "basicSalary", basic,
                "lines", List.of()), employee).andExpect(status().isCreated());
    }

    String processed(int month) throws Exception {
        String id = body(postAs(po, "/api/v1/payroll/periods", Map.of("companyId", acme.companyId(), "year", 2026,
                "month", month)).andExpect(status().isCreated())).get("id").asString();
        postAs(po, "/api/v1/payroll/periods/{id}/process", null, id).andExpect(status().isOk());
        return id;
    }

    JsonNode resultOf(UUID employee) throws Exception {
        return body(getAs(po, "/api/v1/payroll/periods/{p}/results/{e}", march, employee)).get("result");
    }
}
