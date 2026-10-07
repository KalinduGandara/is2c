package com.is2c.hrm.leave;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
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
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import com.is2c.hrm.attendance.api.AttendanceLockPolicy;
import com.is2c.hrm.security.api.ScopeType;
import com.is2c.hrm.security.domain.AppUser;
import com.is2c.testsupport.ApiTestBase;
import com.is2c.testsupport.OrgFixtures.Structure;

import tools.jackson.databind.JsonNode;

@Import(LeaveRequestApiTest.Config.class)
class LeaveRequestApiTest extends ApiTestBase {

    static class Lock implements AttendanceLockPolicy {
        final Set<LocalDate> locked = new HashSet<>();

        @Override
        public boolean isLocked(UUID companyId, LocalDate date) {
            return locked.contains(date);
        }

        @Override
        public void attendanceChanged(UUID companyId, Collection<LocalDate> dates) {
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        Lock leaveTestLock() {
            return new Lock();
        }
    }

    @Autowired
    Lock lock;

    String admin;
    String hr;
    Structure acme;
    UUID staffEmp;
    AppUser staffUser;
    AppUser managerUser;
    String staff;
    String manager;
    Map<String, String> typeIds = new HashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        lock.locked.clear();
        clock.set(Instant.parse("2026-03-20T12:00:00Z")); // Friday
        admin = data.bearerFor("root", "SUPER_ADMIN");
        hr = data.bearerFor("hr", "HR_MANAGER");
        acme = orgs.structure(admin, "ACME");
        String day = body(postAs(admin, "/api/v1/companies/{c}/shifts", Map.of("code", "DAY", "name", "Day", "type", "FIXED",
                "s1Start", "08:30", "s1End", "17:30"), acme.companyId())).get("id").asString();
        Map<String, Object> mgr = orgs.employeeBody(acme, "Mana", "Ger");
        mgr.put("defaultShiftId", day);
        UUID managerEmp = orgs.employee(hr, mgr);
        Map<String, Object> body = orgs.employeeBody(acme, "Staff", "Member");
        body.put("reportingManagerId", managerEmp);
        body.put("defaultShiftId", day);
        staffEmp = orgs.employee(hr, body);
        staffUser = data.linkEmployee(data.user("staff", ScopeType.OWN, Set.of(), "EMPLOYEE"), staffEmp);
        managerUser = data.linkEmployee(data.user("manager", ScopeType.OWN, Set.of(), "EMPLOYEE"), managerEmp);
        staff = data.bearer(staffUser);
        manager = data.bearer(managerUser);
        for (JsonNode t : body(getAs(hr, "/api/v1/companies/{c}/leave-types", acme.companyId()))) {
            typeIds.put(t.get("code").asString(), t.get("id").asString());
        }
    }

    private Map<String, Object> leave(String type, String from, String to, String part) {
        Map<String, Object> m = new HashMap<>(Map.of("leaveTypeId", typeIds.get(type), "fromDate", from, "toDate", to,
                "reason", "personal"));
        m.put("halfDayPart", part);
        return m;
    }

    private ResultActions applyOwn(Map<String, Object> body) throws Exception {
        return postAs(staff, "/api/v1/me/leave/requests", body);
    }

    private double available(String code) throws Exception {
        for (JsonNode b : body(getAs(staff, "/api/v1/me/leave/balances?year=2026"))) {
            if (b.get("leaveTypeCode").asString().equals(code)) {
                return b.get("available").asDouble();
            }
        }
        throw new IllegalStateException(code);
    }

    private String inboxItem(String bearer) throws Exception {
        return body(getAs(bearer, "/api/v1/approval/inbox")).get(0).get("id").asString();
    }

    @Test
    @DisplayName("FR-063 days count workdays only (weekly-off Sunday and holidays excluded) and are held as pending")
    void dayCounting() throws Exception {
        postAs(admin, "/api/v1/companies/{c}/holidays", Map.of("date", "2026-03-25", "name", "Poya", "type", "PUBLIC"), acme.companyId());
        applyOwn(leave("ANNUAL", "2026-03-23", "2026-03-29", null)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.days").value(5.0)).andExpect(jsonPath("$.status").value("PENDING"));
        assertThat(available("ANNUAL")).isEqualTo(9.0);
        applyOwn(leave("CASUAL", "2026-03-30", "2026-03-30", "MORNING")).andExpect(jsonPath("$.days").value(0.5));
    }

    @Test
    @DisplayName("FR-063 validation: date order, single year, half-day rules, zero days, balance")
    void validation() throws Exception {
        applyOwn(leave("ANNUAL", "2026-03-24", "2026-03-23", null)).andExpect(status().isUnprocessableContent());
        applyOwn(leave("ANNUAL", "2026-12-30", "2027-01-02", null)).andExpect(jsonPath("$.errors[0].message")
                .value(org.hamcrest.Matchers.containsString("two leave years")));
        applyOwn(leave("ANNUAL", "2026-03-23", "2026-03-24", "MORNING")).andExpect(jsonPath("$.errors[0].field").value("halfDayPart"));
        applyOwn(leave("ANNUAL", "2026-03-22", "2026-03-22", null)).andExpect(jsonPath("$.errors[0].message")
                .value(org.hamcrest.Matchers.containsString("no working days")));
        applyOwn(leave("CASUAL", "2026-03-23", "2026-04-01", null)).andExpect(jsonPath("$.errors[0].message")
                .value(org.hamcrest.Matchers.containsString("insufficient balance")));
        applyOwn(leave("NOPAY", "2026-03-23", "2026-04-30", null)).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("FR-063 overlapping requests are rejected, except opposite half days on the same date")
    void overlap() throws Exception {
        applyOwn(leave("ANNUAL", "2026-03-23", "2026-03-24", null)).andExpect(status().isCreated());
        applyOwn(leave("CASUAL", "2026-03-24", "2026-03-24", null)).andExpect(status().isUnprocessableContent());
        applyOwn(leave("CASUAL", "2026-03-26", "2026-03-26", "MORNING")).andExpect(status().isCreated());
        applyOwn(leave("ANNUAL", "2026-03-26", "2026-03-26", "AFTERNOON")).andExpect(status().isCreated());
        applyOwn(leave("ANNUAL", "2026-03-26", "2026-03-26", "MORNING")).andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("FR-063 supporting documents are required above the configured number of days")
    void attachments() throws Exception {
        JsonNode medical = null;
        for (JsonNode t : body(getAs(hr, "/api/v1/companies/{c}/leave-types", acme.companyId()))) {
            if (t.get("code").asString().equals("MEDICAL")) {
                medical = t;
            }
        }
        putAs(hr, "/api/v1/leave-types/{id}", Map.of("code", "MEDICAL", "name", "Medical leave", "annualDays", 7,
                "attachmentAboveDays", 2, "version", medical.get("version").asLong()), medical.get("id").asString())
                .andExpect(status().isOk());
        applyOwn(leave("MEDICAL", "2026-03-23", "2026-03-25", null)).andExpect(jsonPath("$.errors[0].field").value("files"));
        MockMultipartFile json = new MockMultipartFile("data", "", MediaType.APPLICATION_JSON_VALUE,
                toJson(leave("MEDICAL", "2026-03-23", "2026-03-25", null)).getBytes(StandardCharsets.UTF_8));
        MockMultipartFile pdf = new MockMultipartFile("files", "medical.pdf", "application/pdf",
                "%PDF-1.4\n%%EOF".getBytes(StandardCharsets.US_ASCII));
        String id = body(mvc.perform(multipart("/api/v1/me/leave/requests").file(json).file(pdf).header("Authorization", staff))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.attachments", hasSize(1)))).get("id").asString();
        String attachment = body(getAs(hr, "/api/v1/leave/requests/{id}", id)).get("attachments").get(0).get("id").asString();
        getAs(hr, "/api/v1/leave/requests/{id}/attachments/{a}", id, attachment).andExpect(status().isOk());
    }

    @Test
    @DisplayName("FR-064 approval moves pending to taken and marks attendance ON_LEAVE")
    void approve() throws Exception {
        applyOwn(leave("ANNUAL", "2026-03-16", "2026-03-17", null)).andExpect(status().isCreated());
        getAs(hr, "/api/v1/approval/inbox").andExpect(jsonPath("$", hasSize(0)));
        postAs(manager, "/api/v1/approval/transactions/{id}/approve", Map.of(), inboxItem(manager)).andExpect(status().isOk());
        getAs(staff, "/api/v1/me/leave/requests").andExpect(jsonPath("$[0].status").value("APPROVED"));
        JsonNode annual = null;
        for (JsonNode b : body(getAs(staff, "/api/v1/me/leave/balances?year=2026"))) {
            if (b.get("leaveTypeCode").asString().equals("ANNUAL")) {
                annual = b;
            }
        }
        assertThat(annual.get("taken").asDouble()).isEqualTo(2.0);
        assertThat(annual.get("pending").asDouble()).isEqualTo(0.0);
        getAs(staff, "/api/v1/me/attendance?from=2026-03-16&to=2026-03-17").andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].status").value("ON_LEAVE"))
                .andExpect(jsonPath("$[0].leavePaid").value(true));
    }

    @Test
    @DisplayName("FR-064/066 rejection releases pending days; unpaid leave is flagged unpaid in attendance")
    void rejectAndUnpaid() throws Exception {
        applyOwn(leave("CASUAL", "2026-03-23", "2026-03-24", null));
        postAs(manager, "/api/v1/approval/transactions/{id}/reject", Map.of("comment", "peak season"), inboxItem(manager))
                .andExpect(status().isOk());
        getAs(staff, "/api/v1/me/leave/requests").andExpect(jsonPath("$[0].status").value("REJECTED"))
                .andExpect(jsonPath("$[0].decisionNote").value("peak season"));
        assertThat(available("CASUAL")).isEqualTo(7.0);

        applyOwn(leave("NOPAY", "2026-03-18", "2026-03-18", null));
        postAs(manager, "/api/v1/approval/transactions/{id}/approve", Map.of(), inboxItem(manager));
        getAs(staff, "/api/v1/me/attendance?from=2026-03-18&to=2026-03-18")
                .andExpect(jsonPath("$[0].status").value("ON_LEAVE")).andExpect(jsonPath("$[0].leavePaid").value(false));
    }

    @Test
    @DisplayName("FR-064 employees cancel pending requests; HR can cancel approved ones and days return")
    void cancellation() throws Exception {
        String pending = body(applyOwn(leave("CASUAL", "2026-03-23", "2026-03-23", null))).get("id").asString();
        postAs(staff, "/api/v1/me/leave/requests/{id}/cancel", Map.of(), pending).andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(available("CASUAL")).isEqualTo(7.0);
        getAs(manager, "/api/v1/approval/inbox").andExpect(jsonPath("$", hasSize(0)));

        String approved = body(applyOwn(leave("ANNUAL", "2026-03-16", "2026-03-16", null))).get("id").asString();
        postAs(manager, "/api/v1/approval/transactions/{id}/approve", Map.of(), inboxItem(manager));
        postAs(staff, "/api/v1/me/leave/requests/{id}/cancel", Map.of(), approved).andExpect(status().isConflict());
        postAs(hr, "/api/v1/leave/requests/{id}/cancel", Map.of(), approved).andExpect(status().isUnprocessableContent());
        postAs(hr, "/api/v1/leave/requests/{id}/cancel", Map.of("reason", "worked instead"), approved)
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertThat(available("ANNUAL")).isEqualTo(14.0);
        getAs(staff, "/api/v1/me/attendance?from=2026-03-16&to=2026-03-16").andExpect(jsonPath("$[0].status").value("ABSENT"));
    }

    @Test
    @DisplayName("FR-063/053 leave cannot be applied on payroll-locked dates")
    void locked() throws Exception {
        lock.locked.add(LocalDate.parse("2026-03-24"));
        applyOwn(leave("ANNUAL", "2026-03-23", "2026-03-24", null)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("ATTENDANCE_LOCKED"));
    }

    @Test
    @DisplayName("FR-063 HR applies on behalf; employees without LEAVE:ADD cannot")
    void onBehalf() throws Exception {
        Map<String, Object> body = leave("ANNUAL", "2026-03-23", "2026-03-23", null);
        body.put("employeeId", staffEmp);
        postAs(hr, "/api/v1/leave/requests", body).andExpect(status().isCreated());
        postAs(staff, "/api/v1/leave/requests", body).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("FR-065 calendar: HR sees leaves in scope and holidays; employees only their own")
    void calendar() throws Exception {
        postAs(admin, "/api/v1/companies/{c}/holidays", Map.of("date", "2026-03-25", "name", "Poya", "type", "PUBLIC"), acme.companyId());
        applyOwn(leave("ANNUAL", "2026-03-23", "2026-03-24", null));
        Map<String, Object> other = leave("ANNUAL", "2026-03-23", "2026-03-23", null);
        other.put("employeeId", managerUser.getEmployeeId());
        postAs(hr, "/api/v1/leave/requests", other).andExpect(status().isCreated());
        getAs(hr, "/api/v1/leave/calendar?companyId={c}&from=2026-03-01&to=2026-03-31&includePending=true", acme.companyId())
                .andExpect(jsonPath("$[?(@.kind == 'LEAVE')]", hasSize(2)))
                .andExpect(jsonPath("$[?(@.kind == 'HOLIDAY')]", hasSize(1)));
        getAs(staff, "/api/v1/leave/calendar?companyId={c}&from=2026-03-01&to=2026-03-31&includePending=true", acme.companyId())
                .andExpect(jsonPath("$[?(@.kind == 'LEAVE')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.kind == 'LEAVE')].employeeId").value(staffEmp.toString()));
        getAs(hr, "/api/v1/leave/calendar?companyId={c}&from=2026-03-01&to=2026-03-31", acme.companyId())
                .andExpect(jsonPath("$[?(@.kind == 'LEAVE')]", hasSize(0)));
        getAs(hr, "/api/v1/leave/requests?companyId={c}&status=PENDING", acme.companyId()).andExpect(jsonPath("$", hasSize(2)));
    }
}
