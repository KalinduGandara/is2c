package com.is2c.hrm.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;

class DashboardApiTest extends ReportTestBase {

    UUID e3;

    @BeforeEach
    void setUp() throws Exception {
        // Monday 2026-03-16, 13:00 in Colombo
        clock.set(Instant.parse("2026-03-16T07:30:00Z"));
        baseSetUp();
        e3 = employee("Three", null, "B3");
        disableWorkflow("LEAVE_REQUEST");
        postAs(hr, "/api/v1/leave/requests", Map.of("employeeId", e3, "leaveTypeId", leaveType("ANNUAL"),
                "fromDate", "2026-03-16", "toDate", "2026-03-16")).andExpect(status().isCreated());
        punch("1", "B1", "2026-03-16T08:45:00", "IN");
    }

    @Test
    @DisplayName("FR-096 today's headcount, presence, lateness, absence and leave")
    void kpis() throws Exception {
        JsonNode d = body(getAs(hr, "/api/v1/dashboard?companyId={c}", acme.companyId()).andExpect(status().isOk()));
        assertThat(d.get("date").asString()).isEqualTo("2026-03-16");
        assertThat(d.get("activeEmployees").asInt()).isEqualTo(3);
        assertThat(d.get("presentToday").asInt()).isEqualTo(1);
        assertThat(d.get("lateToday").asInt()).isEqualTo(1);
        assertThat(d.get("onLeaveToday").asInt()).isEqualTo(1);
        assertThat(d.get("absentToday").asInt()).isEqualTo(1);
        assertThat(d.get("headcountByDepartment").get("OPS dept").asInt()).isEqualTo(2);
        assertThat(d.get("headcountByDepartment").get("FIN dept").asInt()).isEqualTo(1);
        assertThat(d.get("pendingApprovals").asInt()).isZero();
    }

    @Test
    @DisplayName("FR-096 pending approvals count is the current user's inbox")
    void pendingApprovals() throws Exception {
        // re-enable the seeded leave workflow (reporting manager, HR_MANAGER fallback)
        for (JsonNode w : body(getAs(admin, "/api/v1/approval/workflows?companyId={c}", acme.companyId()))) {
            if (w.get("txnType").asString().equals("LEAVE_REQUEST")) {
                putAs(admin, "/api/v1/approval/workflows/{id}", Map.of("txnType", "LEAVE_REQUEST", "enabled", true,
                        "levels", List.of(Map.of("approverType", "REPORTING_MANAGER")), "version", w.get("version").asLong()),
                        w.get("id").asString()).andExpect(status().isOk());
            }
        }
        postAs(admin, "/api/v1/leave/requests", Map.of("employeeId", e2, "leaveTypeId", leaveType("ANNUAL"),
                "fromDate", "2026-03-25", "toDate", "2026-03-25")).andExpect(status().isCreated());
        getAs(hr, "/api/v1/dashboard?companyId={c}", acme.companyId()).andExpect(jsonPath("$.pendingApprovals").value(1));
    }

    @Test
    @DisplayName("FR-096 payroll figures only for PAYROLL:VIEW")
    void payroll() throws Exception {
        String po = data.bearerFor("po", "PAYROLL_OFFICER");
        getAs(data.bearerFor("ca", "COMPANY_ADMIN"), "/api/v1/dashboard?companyId={c}", acme.companyId())
                .andExpect(jsonPath("$.payroll").doesNotExist());
        getAs(po, "/api/v1/dashboard?companyId={c}", acme.companyId())
                .andExpect(jsonPath("$.payroll.currentPeriod").doesNotExist());
        String feb = body(postAs(po, "/api/v1/payroll/periods", Map.of("companyId", acme.companyId(), "year", 2026, "month", 2))
                .andExpect(status().isCreated())).get("id").asString();
        postAs(po, "/api/v1/payroll/periods/{id}/process", null, feb).andExpect(status().isOk());
        getAs(po, "/api/v1/dashboard?companyId={c}", acme.companyId())
                .andExpect(jsonPath("$.payroll.currentPeriod.status").value("DRAFT"))
                .andExpect(jsonPath("$.payroll.currentPeriod.month").value(2))
                .andExpect(jsonPath("$.payroll.lastProcessedOtCost").value(0));
    }
}
