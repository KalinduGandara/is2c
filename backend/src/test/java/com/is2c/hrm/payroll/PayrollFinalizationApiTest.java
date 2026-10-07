package com.is2c.hrm.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.springframework.jdbc.core.JdbcTemplate;

import com.is2c.testsupport.ApiTestBase;
import com.is2c.testsupport.OrgFixtures.Structure;

import tools.jackson.databind.JsonNode;

class PayrollFinalizationApiTest extends ApiTestBase {

    @Autowired
    JdbcTemplate jdbc;

    String admin;
    String hr;
    String po;
    Structure acme;
    UUID e1;
    UUID e2;
    Map<String, String> comps = new HashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        clock.set(Instant.parse("2026-04-05T06:00:00Z"));
        admin = data.bearerFor("root", "SUPER_ADMIN");
        hr = data.bearerFor("hr", "HR_MANAGER");
        po = data.bearerFor("po", "PAYROLL_OFFICER");
        acme = orgs.structure(admin, "ACME");
        for (JsonNode c : body(getAs(po, "/api/v1/companies/{c}/payroll-components", acme.companyId()))) {
            comps.put(c.get("code").asString(), c.get("id").asString());
        }
        e1 = employee("One", "2025-01-01");
        e2 = employee("Two", "2025-01-01");
        structure(e1, 100000, List.of());
        structure(e2, 80000, List.of());
    }

    private UUID employee(String first, String joined) throws Exception {
        Map<String, Object> b = orgs.employeeBody(acme, first, "Person");
        b.put("joinDate", joined);
        return orgs.employee(hr, b);
    }

    private void structure(UUID employee, int basic, List<Map<String, Object>> lines) throws Exception {
        postAs(po, "/api/v1/employees/{e}/salary-structures", Map.of("effectiveFrom", "2025-01-01", "basicSalary", basic,
                "lines", lines), employee).andExpect(status().isCreated());
    }

    private String processedMarch() throws Exception {
        String id = body(postAs(po, "/api/v1/payroll/periods", Map.of("companyId", acme.companyId(), "year", 2026, "month", 3))
                .andExpect(status().isCreated())).get("id").asString();
        postAs(po, "/api/v1/payroll/periods/{id}/process", null, id).andExpect(status().isOk());
        return id;
    }

    private String periodStatus(String period) throws Exception {
        return body(getAs(po, "/api/v1/payroll/periods/{id}", period)).get("status").asString();
    }

    private String approvalOf(String period) throws Exception {
        return body(getAs(po, "/api/v1/payroll/periods/{id}", period)).get("approvalId").asString();
    }

    private String approvedMarch() throws Exception {
        String march = processedMarch();
        postAs(po, "/api/v1/payroll/periods/{id}/submit", Map.of(), march).andExpect(status().isOk());
        postAs(hr, "/api/v1/approval/transactions/{id}/approve", Map.of("comment", "ok"), approvalOf(march))
                .andExpect(status().isOk());
        return march;
    }

    @Test
    @DisplayName("FR-087 submit creates a PAYROLL_APPROVAL transaction; approval by HR Manager sets APPROVED")
    void submitAndApprove() throws Exception {
        String march = processedMarch();
        postAs(po, "/api/v1/payroll/periods/{id}/submit", Map.of(), march).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_REVIEW"));
        String txn = approvalOf(march);
        getAs(hr, "/api/v1/approval/inbox").andExpect(jsonPath("$[0].id").value(txn))
                .andExpect(jsonPath("$[0].type").value("PAYROLL_APPROVAL"));
        postAs(po, "/api/v1/payroll/periods/{id}/submit", Map.of(), march).andExpect(status().isConflict());
        postAs(hr, "/api/v1/approval/transactions/{id}/approve", Map.of("comment", "ok"), txn).andExpect(status().isOk());
        assertThat(periodStatus(march)).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("FR-086 submission is rejected while stale or with blocking exceptions")
    void submitGuards() throws Exception {
        String march = processedMarch();
        postAs(po, "/api/v1/payroll/periods/{id}/items", Map.of("employeeId", e1, "componentId", comps.get("BONUS"),
                "amount", 1000), march).andExpect(status().isCreated());
        postAs(po, "/api/v1/payroll/periods/{id}/submit", Map.of(), march).andExpect(status().isUnprocessableContent());
        postAs(po, "/api/v1/payroll/periods/{id}/process", null, march).andExpect(status().isOk());
        // a loan larger than pay produces a blocking NEGATIVE_NET exception
        postAs(po, "/api/v1/employees/{e}/salary-structures", Map.of("effectiveFrom", "2026-03-01", "basicSalary", 80000,
                "lines", List.of(Map.of("componentId", comps.get("LOAN_DEDUCTION"), "amount", 200000, "excluded", false))), e2)
                .andExpect(status().isCreated());
        postAs(po, "/api/v1/payroll/periods/{id}/process", null, march).andExpect(status().isOk());
        postAs(po, "/api/v1/payroll/periods/{id}/submit", Map.of(), march).andExpect(status().isUnprocessableContent());
        assertThat(periodStatus(march)).isEqualTo("DRAFT");
        String open = body(postAs(po, "/api/v1/payroll/periods", Map.of("companyId", acme.companyId(), "year", 2026, "month", 2))
                .andExpect(status().isCreated())).get("id").asString();
        postAs(po, "/api/v1/payroll/periods/{id}/submit", Map.of(), open).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("FR-087 rejection in the approval engine and send-back return the period to DRAFT")
    void rejectAndSendBack() throws Exception {
        String march = processedMarch();
        postAs(po, "/api/v1/payroll/periods/{id}/submit", Map.of(), march).andExpect(status().isOk());
        postAs(hr, "/api/v1/approval/transactions/{id}/reject", Map.of("comment", "check OT"), approvalOf(march))
                .andExpect(status().isOk());
        assertThat(periodStatus(march)).isEqualTo("DRAFT");

        postAs(po, "/api/v1/payroll/periods/{id}/submit", Map.of(), march).andExpect(status().isOk());
        String txn = approvalOf(march);
        postAs(po, "/api/v1/payroll/periods/{id}/send-back", Map.of(), march).andExpect(status().isUnprocessableContent());
        postAs(po, "/api/v1/payroll/periods/{id}/send-back", Map.of("comment", "missed a bonus"), march)
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"));
        getAs(hr, "/api/v1/approval/transactions/{id}", txn).andExpect(jsonPath("$.transaction.status").value("CANCELLED"));
        postAs(po, "/api/v1/payroll/periods/{id}/send-back", Map.of("comment", "again"), march).andExpect(status().isConflict());
        JsonNode audit = body(getAs(admin, "/api/v1/audit?entityType=PAYROLL_PERIOD&entityId={id}", march));
        assertThat(audit.toString()).contains("SUBMIT", "SEND_BACK");
    }

    @Test
    @DisplayName("FR-087 finalize requires PAYROLL:APPROVE and an APPROVED period; locks results and assigns payslip numbers")
    void finalizeLocks() throws Exception {
        String march = processedMarch();
        postAs(hr, "/api/v1/payroll/periods/{id}/finalize", Map.of(), march).andExpect(status().isConflict());
        postAs(po, "/api/v1/payroll/periods/{id}/submit", Map.of(), march).andExpect(status().isOk());
        postAs(hr, "/api/v1/approval/transactions/{id}/approve", Map.of("comment", "ok"), approvalOf(march));
        postAs(po, "/api/v1/payroll/periods/{id}/finalize", Map.of(), march).andExpect(status().isForbidden());
        postAs(hr, "/api/v1/payroll/periods/{id}/finalize", Map.of(), march).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINALIZED")).andExpect(jsonPath("$.finalizedAt").isNotEmpty());

        JsonNode results = body(getAs(po, "/api/v1/payroll/periods/{id}/results", march));
        assertThat(results).hasSize(2);
        List<String> numbers = new java.util.ArrayList<>();
        for (JsonNode r : results) {
            assertThat(r.get("locked").asBoolean()).isTrue();
            numbers.add(r.get("payslipNo").asString());
        }
        assertThat(numbers).doesNotContainNull().doesNotHaveDuplicates().allMatch(n -> !n.isBlank());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payroll_result_line l JOIN payroll_result r ON r.id = l.result_id"
                + " WHERE r.period_id = ? AND NOT l.locked", Long.class, UUID.fromString(march))).isZero();

        postAs(po, "/api/v1/payroll/periods/{id}/process", null, march).andExpect(status().isConflict());
        postAs(po, "/api/v1/payroll/periods/{id}/items", Map.of("employeeId", e1, "componentId", comps.get("BONUS"),
                "amount", 1000), march).andExpect(status().isConflict());
        postAs(hr, "/api/v1/payroll/periods/{id}/finalize", Map.of(), march).andExpect(status().isConflict());
        JsonNode audit = body(getAs(admin, "/api/v1/audit?entityType=PAYROLL_PERIOD&entityId={id}", march));
        assertThat(audit.toString()).contains("FINALIZE", "APPROVED");
    }

    @Test
    @DisplayName("FR-087 payslip numbers are unique per company across periods")
    void payslipNumbersUnique() throws Exception {
        String march = approvedMarch();
        postAs(hr, "/api/v1/payroll/periods/{id}/finalize", Map.of(), march).andExpect(status().isOk());
        String april = body(postAs(po, "/api/v1/payroll/periods", Map.of("companyId", acme.companyId(), "year", 2026, "month", 4))
                .andExpect(status().isCreated())).get("id").asString();
        clock.set(Instant.parse("2026-05-05T06:00:00Z"));
        String po2 = data.bearerFor("po2", "PAYROLL_OFFICER");
        String hr2 = data.bearerFor("hr2", "HR_MANAGER");
        postAs(po2, "/api/v1/payroll/periods/{id}/process", null, april).andExpect(status().isOk());
        postAs(po2, "/api/v1/payroll/periods/{id}/submit", Map.of(), april).andExpect(status().isOk());
        postAs(hr2, "/api/v1/approval/transactions/{id}/approve", Map.of("comment", "ok"),
                body(getAs(po2, "/api/v1/payroll/periods/{id}", april)).get("approvalId").asString()).andExpect(status().isOk());
        postAs(hr2, "/api/v1/payroll/periods/{id}/finalize", Map.of(), april).andExpect(status().isOk());
        List<String> all = jdbc.queryForList("SELECT payslip_no FROM payroll_result", String.class);
        assertThat(all).hasSize(4).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("FR-087 the database rejects UPDATE and DELETE of locked result rows")
    void databaseTrigger() throws Exception {
        String march = approvedMarch();
        postAs(hr, "/api/v1/payroll/periods/{id}/finalize", Map.of(), march).andExpect(status().isOk());
        UUID p = UUID.fromString(march);
        assertThatThrownBy(() -> jdbc.update("UPDATE payroll_result SET net = 1 WHERE period_id = ?", p))
                .hasMessageContaining("locked");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM payroll_result WHERE period_id = ?", p))
                .hasMessageContaining("locked");
        assertThatThrownBy(() -> jdbc.update("UPDATE payroll_result_line SET amount = 1 WHERE result_id IN"
                + " (SELECT id FROM payroll_result WHERE period_id = ?)", p)).hasMessageContaining("locked");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM payroll_result_line WHERE result_id IN"
                + " (SELECT id FROM payroll_result WHERE period_id = ?)", p)).hasMessageContaining("locked");
    }

    @Test
    @DisplayName("FR-053 attendance is locked from submission and unlocked again when sent back")
    void attendanceLock() throws Exception {
        String march = processedMarch();
        Map<String, Object> adj = Map.of("employeeId", e1, "date", "2026-03-10", "in", "2026-03-10T08:30:00",
                "out", "2026-03-10T17:30:00", "reason", "manual");
        postAs(po, "/api/v1/payroll/periods/{id}/submit", Map.of(), march).andExpect(status().isOk());
        postAs(hr, "/api/v1/attendance/adjustments", adj).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ATTENDANCE_LOCKED"));
        postAs(po, "/api/v1/payroll/periods/{id}/send-back", Map.of("comment", "fix attendance"), march)
                .andExpect(status().isOk());
        postAs(hr, "/api/v1/attendance/adjustments", adj).andExpect(status().isOk());
        assertThat(body(getAs(po, "/api/v1/payroll/periods/{id}", march)).get("stale").asBoolean()).isTrue();
    }
}
