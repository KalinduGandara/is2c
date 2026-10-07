package com.is2c.hrm.approval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import com.is2c.hrm.approval.api.ApprovalOutcome;
import com.is2c.hrm.approval.api.ApprovalSubmission;
import com.is2c.hrm.approval.service.ApprovalService;
import com.is2c.hrm.security.api.ScopeType;
import com.is2c.hrm.security.domain.AppUser;
import com.is2c.hrm.security.domain.ScopeItem;
import com.is2c.hrm.security.domain.ScopeItem.ScopeItemType;
import com.is2c.testsupport.ApiTestBase;
import com.is2c.testsupport.OrgFixtures.Structure;

@Import(ApprovalApiTest.Config.class)
class ApprovalApiTest extends ApiTestBase {

    @TestConfiguration
    static class Config {
        @Bean
        RecordingHandler recordingHandler() {
            return new RecordingHandler();
        }
    }

    @Autowired
    RecordingHandler handler;

    @Autowired
    ApprovalService approvals;

    String admin;
    Structure acme;
    UUID managerEmp;
    UUID staffEmp;
    AppUser managerUser;
    AppUser staffUser;
    AppUser hrUser;
    String manager;
    String staff;
    String hr;

    @BeforeEach
    void setUp() throws Exception {
        handler.calls.clear();
        admin = data.bearerFor("root", "SUPER_ADMIN");
        acme = orgs.structure(admin, "ACME");
        hrUser = data.user("hr", ScopeType.COMPANY, Set.of(new ScopeItem(ScopeItemType.COMPANY, acme.companyId())), "HR_MANAGER");
        hr = data.bearer(hrUser);
        managerEmp = orgs.employee(hr, acme, "Mana", "Ger");
        Map<String, Object> body = orgs.employeeBody(acme, "Staff", "Member");
        body.put("reportingManagerId", managerEmp);
        staffEmp = orgs.employee(hr, body);
        managerUser = data.linkEmployee(data.user("manager", ScopeType.OWN, Set.of(), "EMPLOYEE"), managerEmp);
        staffUser = data.linkEmployee(data.user("staff", ScopeType.OWN, Set.of(), "EMPLOYEE"), staffEmp);
        manager = data.bearer(managerUser);
        staff = data.bearer(staffUser);
    }

    private String workflow(Map<String, Object> extra, List<Map<String, Object>> levels) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("companyId", acme.companyId(), "txnType", "TEST", "levels", levels));
        body.putAll(extra);
        return body(postAs(admin, "/api/v1/approval/workflows", body).andExpect(status().isCreated())).get("id").asString();
    }

    private static Map<String, Object> level(String type, String role, UUID user) {
        Map<String, Object> m = new HashMap<>(Map.of("approverType", type));
        m.put("roleCode", role);
        m.put("userId", user);
        return m;
    }

    private ApprovalOutcome submit(AppUser as, UUID subject) {
        return data.runAs(as, () -> approvals.submit(new ApprovalSubmission("TEST", UUID.randomUUID(), acme.companyId(),
                subject, "Test item")));
    }

    @Test
    @DisplayName("FR-071/FR-008 without an enabled workflow the item is auto-approved (audited) and the handler called")
    void autoApprove() throws Exception {
        ApprovalOutcome outcome = submit(staffUser, staffEmp);
        assertThat(outcome.autoApproved()).isTrue();
        assertThat(handler.calls).hasSize(1).allMatch(c -> c.startsWith("approved:"));
        getAs(admin, "/api/v1/audit?entityType=APPROVAL&action=AUTO_APPROVE")
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.content[0].entityId")
                        .value(outcome.transactionId().toString()));
    }

    @Test
    @DisplayName("FR-070 default workflows are seeded for new companies")
    void seeded() throws Exception {
        getAs(admin, "/api/v1/approval/workflows?companyId={c}", acme.companyId()).andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[?(@.txnType == 'LEAVE_REQUEST')].levels[0].approverType").value("REPORTING_MANAGER"))
                .andExpect(jsonPath("$[?(@.txnType == 'ATTENDANCE_CORRECTION')].enabled").value(false))
                .andExpect(jsonPath("$[?(@.txnType == 'PAYROLL_APPROVAL')].levels[0].roleCode").value("HR_MANAGER"));
    }

    @Test
    @DisplayName("FR-071/072 two-level workflow: manager then HR (each sees it in their inbox); final approval calls the handler")
    void multiLevel() throws Exception {
        workflow(Map.of(), List.of(level("REPORTING_MANAGER", null, null), level("ROLE", "HR_MANAGER", null)));
        ApprovalOutcome outcome = submit(staffUser, staffEmp);
        assertThat(outcome.autoApproved()).isFalse();
        String id = outcome.transactionId().toString();

        getAs(manager, "/api/v1/approval/inbox").andExpect(jsonPath("$", hasSize(1)));
        getAs(hr, "/api/v1/approval/inbox").andExpect(jsonPath("$", hasSize(0)));
        postAs(hr, "/api/v1/approval/transactions/{id}/approve", Map.of(), id).andExpect(status().isForbidden());
        postAs(manager, "/api/v1/approval/transactions/{id}/approve", Map.of("comment", "ok"), id)
                .andExpect(jsonPath("$.currentLevel").value(2)).andExpect(jsonPath("$.status").value("PENDING"));
        assertThat(handler.calls).isEmpty();
        getAs(hr, "/api/v1/approval/inbox").andExpect(jsonPath("$", hasSize(1)));
        postAs(hr, "/api/v1/approval/transactions/{id}/approve", Map.of(), id).andExpect(jsonPath("$.status").value("APPROVED"));
        UUID reference = data.runAs(hrUser, () -> approvals.view(outcome.transactionId()).transaction().getReferenceId());
        assertThat(handler.calls).containsExactly("approved:" + reference);
        postAs(hr, "/api/v1/approval/transactions/{id}/approve", Map.of(), id).andExpect(status().isConflict());

        getAs(staff, "/api/v1/approval/transactions/{id}", id).andExpect(status().isOk())
                .andExpect(jsonPath("$.history[*].action", org.hamcrest.Matchers.contains("SUBMIT", "APPROVE", "APPROVE")))
                .andExpect(jsonPath("$.history[1].comment").value("ok"));
        getAs(staff, "/api/v1/approval/my-requests").andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("FR-071 rejection needs a comment and notifies the handler")
    void reject() throws Exception {
        workflow(Map.of(), List.of(level("REPORTING_MANAGER", null, null)));
        String id = submit(staffUser, staffEmp).transactionId().toString();
        postAs(manager, "/api/v1/approval/transactions/{id}/reject", Map.of(), id).andExpect(status().isUnprocessableContent());
        postAs(manager, "/api/v1/approval/transactions/{id}/reject", Map.of("comment", "busy period"), id)
                .andExpect(jsonPath("$.status").value("REJECTED"));
        assertThat(handler.calls).singleElement().asString().endsWith(":busy period");
    }

    @Test
    @DisplayName("FR-071 only the requester can cancel a pending item")
    void cancel() throws Exception {
        workflow(Map.of(), List.of(level("REPORTING_MANAGER", null, null)));
        String id = submit(staffUser, staffEmp).transactionId().toString();
        postAs(manager, "/api/v1/approval/transactions/{id}/cancel", Map.of(), id).andExpect(status().isForbidden());
        postAs(staff, "/api/v1/approval/transactions/{id}/cancel", Map.of(), id).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(handler.calls).singleElement().asString().startsWith("cancelled:");
    }

    @Test
    @DisplayName("FR-071 requester and subject are never approvers; empty levels fall back to HR_MANAGER")
    void exclusionsAndFallback() throws Exception {
        workflow(Map.of(), List.of(level("REPORTING_MANAGER", null, null)));
        // The manager has no reporting manager → fallback to HR.
        var outcome = submit(managerUser, managerEmp);
        getAs(hr, "/api/v1/approval/transactions/{id}", outcome.transactionId())
                .andExpect(jsonPath("$.history[*].action", hasItem("FALLBACK")))
                .andExpect(jsonPath("$.canAct").value(true));
        // HR submitting about themselves cannot approve their own request.
        var hrEmp = orgs.employee(hr, acme, "Hr", "Person");
        data.linkEmployee(hrUser, hrEmp);
        var own = submit(hrUser, hrEmp);
        postAs(data.bearer(hrUser), "/api/v1/approval/transactions/{id}/approve", Map.of(), own.transactionId())
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("FR-070 the most specific workflow (department over company) applies")
    void mostSpecific() throws Exception {
        AppUser specific = data.user("deptapprover", "SUPERVISOR");
        workflow(Map.of(), List.of(level("ROLE", "HR_MANAGER", null)));
        workflow(Map.of("departmentId", acme.departmentId()), List.of(level("USER", null, specific.getId())));
        var outcome = submit(staffUser, staffEmp);
        getAs(data.bearer(specific), "/api/v1/approval/inbox").andExpect(jsonPath("$", hasSize(1)));
        getAs(hr, "/api/v1/approval/inbox").andExpect(jsonPath("$", hasSize(0)));
        // A level snapshot is kept: changing the workflow later does not reroute the pending item.
        postAs(data.bearer(specific), "/api/v1/approval/transactions/{id}/approve", Map.of(), outcome.transactionId())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    @DisplayName("FR-073 delegates act on behalf of the delegator during the delegation period")
    void delegation() throws Exception {
        workflow(Map.of(), List.of(level("REPORTING_MANAGER", null, null)));
        AppUser deputy = data.user("deputy", "EMPLOYEE");
        postAs(manager, "/api/v1/approval/delegations", Map.of("delegateUserId", managerUser.getId(),
                "fromDate", "2026-03-01", "toDate", "2026-03-31")).andExpect(status().isUnprocessableContent());
        postAs(manager, "/api/v1/approval/delegations", Map.of("delegateUserId", deputy.getId(),
                "fromDate", "2026-03-01", "toDate", "2026-03-31")).andExpect(status().isCreated());
        postAs(manager, "/api/v1/approval/delegations", Map.of("delegateUserId", deputy.getId(),
                "fromDate", "2026-03-20", "toDate", "2026-04-10")).andExpect(status().isUnprocessableContent());
        String deputyBearer = data.bearer(deputy);
        var outcome = submit(staffUser, staffEmp);
        getAs(deputyBearer, "/api/v1/approval/inbox").andExpect(jsonPath("$", hasSize(1)));
        postAs(deputyBearer, "/api/v1/approval/transactions/{id}/approve", Map.of(), outcome.transactionId())
                .andExpect(jsonPath("$.status").value("APPROVED"));
        getAs(staff, "/api/v1/approval/transactions/{id}", outcome.transactionId())
                .andExpect(jsonPath("$.history[1].onBehalfOf").value(managerUser.getId().toString()));
    }

    @Test
    @DisplayName("FR-073 expired delegations give no rights")
    void expiredDelegation() throws Exception {
        workflow(Map.of(), List.of(level("REPORTING_MANAGER", null, null)));
        AppUser deputy = data.user("deputy2", "EMPLOYEE");
        postAs(manager, "/api/v1/approval/delegations", Map.of("delegateUserId", deputy.getId(),
                "fromDate", "2026-01-01", "toDate", "2026-01-31")).andExpect(status().isCreated());
        submit(staffUser, staffEmp);
        getAs(data.bearer(deputy), "/api/v1/approval/inbox").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("FR-070 workflow validation: known type, 1-5 levels, branch xor department, unique scope")
    void workflowValidation() throws Exception {
        Map<String, Object> bad = new HashMap<>(Map.of("companyId", acme.companyId(), "txnType", "NOPE",
                "levels", List.of(level("ROLE", "HR_MANAGER", null))));
        postAs(admin, "/api/v1/approval/workflows", bad).andExpect(status().isUnprocessableContent());
        bad.put("txnType", "TEST");
        bad.put("levels", java.util.Collections.nCopies(6, level("ROLE", "HR_MANAGER", null)));
        postAs(admin, "/api/v1/approval/workflows", bad).andExpect(jsonPath("$.errors[0].field").value("levels"));
        bad.put("levels", List.of(level("ROLE", "NO_SUCH_ROLE", null)));
        postAs(admin, "/api/v1/approval/workflows", bad).andExpect(status().isUnprocessableContent());
        bad.put("levels", List.of(level("ROLE", "HR_MANAGER", null)));
        bad.put("branchId", acme.branchId());
        bad.put("departmentId", acme.departmentId());
        postAs(admin, "/api/v1/approval/workflows", bad).andExpect(status().isUnprocessableContent());
        workflow(Map.of(), List.of(level("ROLE", "HR_MANAGER", null)));
        postAs(admin, "/api/v1/approval/workflows", Map.of("companyId", acme.companyId(), "txnType", "TEST",
                "levels", List.of(level("ROLE", "HR_MANAGER", null)))).andExpect(status().isUnprocessableContent());
        postAs(hr, "/api/v1/approval/workflows", Map.of()).andExpect(status().isForbidden());
    }
}
