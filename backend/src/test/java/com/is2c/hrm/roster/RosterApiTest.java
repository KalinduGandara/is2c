package com.is2c.hrm.roster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import com.is2c.hrm.roster.api.ScheduleChangedEvent;
import com.is2c.testsupport.ApiTestBase;
import com.is2c.testsupport.OrgFixtures.Structure;

@RecordApplicationEvents
class RosterApiTest extends ApiTestBase {

    @Autowired
    ApplicationEvents events;

    String admin;
    String hr;
    Structure acme;
    String day;
    String night;
    String early;
    UUID e1;
    UUID e2;
    UUID finEmp;

    @BeforeEach
    void setUp() throws Exception {
        admin = data.bearerFor("root", "SUPER_ADMIN");
        hr = data.bearerFor("hr", "HR_MANAGER");
        acme = orgs.structure(admin, "ACME");
        day = shift("DAY", "FIXED", "08:00", "17:00");
        night = shift("NGT", "NIGHT", "22:00", "06:00");
        early = shift("EAR", "FIXED", "05:00", "13:00");
        e1 = orgs.employee(hr, acme, "One", "Ops");
        e2 = orgs.employee(hr, acme, "Two", "Ops");
        UUID fin = orgs.department(admin, acme.companyId(), "FIN", null);
        Map<String, Object> b = orgs.employeeBody(acme, "Fin", "Person");
        b.put("departmentId", fin);
        finEmp = orgs.employee(hr, b);
    }

    private String shift(String code, String type, String from, String to) throws Exception {
        return body(postAs(admin, "/api/v1/companies/{c}/shifts",
                Map.of("code", code, "name", code, "type", type, "s1Start", from, "s1End", to), acme.companyId()))
                .get("id").asString();
    }

    private String roster(String start, String periodType, UUID departmentId) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("companyId", acme.companyId(), "name", "R " + start,
                "periodType", periodType, "startDate", start));
        body.put("departmentId", departmentId);
        return body(postAs(hr, "/api/v1/rosters", body).andExpect(status().isCreated())).get("id").asString();
    }

    private Map<String, Object> cell(UUID employee, String date, String shiftId) {
        Map<String, Object> m = new HashMap<>();
        m.put("employeeId", employee);
        m.put("date", date);
        m.put("shiftId", shiftId);
        m.put("off", shiftId == null);
        return m;
    }

    private Map<String, Object> update(String reason, Map<String, Object>... cells) {
        Map<String, Object> m = new HashMap<>();
        m.put("entries", List.of(cells));
        m.put("reason", reason);
        return m;
    }

    @Test
    @DisplayName("FR-035 weekly rosters start on the company week-start day; monthly on the 1st")
    void periods() throws Exception {
        postAs(hr, "/api/v1/rosters", Map.of("companyId", acme.companyId(), "name", "Tue", "periodType", "WEEKLY",
                "startDate", "2026-03-17")).andExpect(status().isUnprocessableContent());
        String weekly = roster("2026-03-16", "WEEKLY", null);
        getAs(hr, "/api/v1/rosters/{id}", weekly).andExpect(jsonPath("$.roster.endDate").value("2026-03-22"))
                .andExpect(jsonPath("$.roster.status").value("DRAFT"))
                .andExpect(jsonPath("$.employees", hasSize(3)));
        String monthly = roster("2026-04-01", "MONTHLY", null);
        getAs(hr, "/api/v1/rosters/{id}", monthly).andExpect(jsonPath("$.roster.endDate").value("2026-04-30"));
        postAs(hr, "/api/v1/rosters", Map.of("companyId", acme.companyId(), "name", "Mid", "periodType", "MONTHLY",
                "startDate", "2026-04-02")).andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("FR-035/FR-036 department rosters include descendants only; cells must be in period and roster")
    void departmentRosterAndCellRules() throws Exception {
        String r = roster("2026-03-16", "WEEKLY", acme.departmentId());
        getAs(hr, "/api/v1/rosters/{id}", r).andExpect(jsonPath("$.employees", hasSize(2)));
        putAs(hr, "/api/v1/rosters/{id}/entries", update(null, cell(finEmp, "2026-03-16", day)), r)
                .andExpect(status().isUnprocessableContent());
        putAs(hr, "/api/v1/rosters/{id}/entries", update(null, cell(e1, "2026-03-23", day)), r)
                .andExpect(status().isUnprocessableContent());
        putAs(hr, "/api/v1/rosters/{id}/entries", update(null, cell(e1, "2026-03-16", day), cell(e1, "2026-03-17", null)), r)
                .andExpect(status().isOk());
        getAs(hr, "/api/v1/rosters/{id}", r).andExpect(jsonPath("$.entries", hasSize(2)));
        Map<String, Object> clear = cell(e1, "2026-03-17", null);
        clear.put("clear", true);
        putAs(hr, "/api/v1/rosters/{id}/entries", update(null, clear), r).andExpect(status().isOk());
        getAs(hr, "/api/v1/rosters/{id}", r).andExpect(jsonPath("$.entries", hasSize(1)));
    }

    @Test
    @DisplayName("FR-036 bulk assignment honours the weekday filter")
    void bulkAssign() throws Exception {
        String r = roster("2026-03-16", "WEEKLY", null);
        postAs(hr, "/api/v1/rosters/{id}/bulk-assign", Map.of("employeeIds", List.of(e1, e2), "from", "2026-03-16",
                "to", "2026-03-22", "weekdays", List.of("MONDAY", "WEDNESDAY", "FRIDAY"), "shiftId", day), r)
                .andExpect(status().isOk());
        getAs(hr, "/api/v1/rosters/{id}", r).andExpect(jsonPath("$.entries", hasSize(6)));
        postAs(hr, "/api/v1/rosters/{id}/bulk-assign", Map.of("employeeIds", List.of(e1), "from", "2026-03-21",
                "to", "2026-03-22", "off", true), r).andExpect(status().isOk());
        getAs(hr, "/api/v1/rosters/{id}", r).andExpect(jsonPath("$.entries[?(@.off == true)]", hasSize(2)));
    }

    @Test
    @DisplayName("FR-038 a night shift followed by an early shift the next day is rejected as overlapping")
    void overlapConflict() throws Exception {
        String r = roster("2026-03-16", "WEEKLY", null);
        putAs(hr, "/api/v1/rosters/{id}/entries", update(null, cell(e1, "2026-03-16", night), cell(e1, "2026-03-17", early)), r)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].message", containsString("overlaps")));
        putAs(hr, "/api/v1/rosters/{id}/entries", update(null, cell(e1, "2026-03-16", night), cell(e1, "2026-03-17", day)), r)
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("FR-038 double rostering against another published roster and inactive shifts are conflicts")
    void crossRosterAndInactive() throws Exception {
        String draft = roster("2026-03-16", "WEEKLY", null);
        putAs(hr, "/api/v1/rosters/{id}/entries", update(null, cell(e1, "2026-03-17", day), cell(e2, "2026-03-18", early)), draft)
                .andExpect(status().isOk());
        String other = roster("2026-03-16", "WEEKLY", acme.departmentId());
        putAs(hr, "/api/v1/rosters/{id}/entries", update(null, cell(e1, "2026-03-17", day)), other).andExpect(status().isOk());
        postAs(hr, "/api/v1/rosters/{id}/publish", null, other).andExpect(status().isOk());
        postAs(admin, "/api/v1/shifts/{id}/deactivate", null, early);

        postAs(hr, "/api/v1/rosters/{id}/validate", null, draft).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.message == 'also rostered in another published roster')]", hasSize(1)))
                .andExpect(jsonPath("$[?(@.message == 'uses inactive shift EAR')]", hasSize(1)));
        postAs(hr, "/api/v1/rosters/{id}/publish", null, draft).andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("FR-039 publish, revise with reason (revision history), lock and unlock")
    void lifecycle() throws Exception {
        String r = roster("2026-03-16", "WEEKLY", null);
        putAs(hr, "/api/v1/rosters/{id}/entries", update(null, cell(e1, "2026-03-16", day)), r);
        postAs(hr, "/api/v1/rosters/{id}/publish", null, r).andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.revision").value(1));
        assertThat(events.stream(ScheduleChangedEvent.class)).hasSize(1);

        putAs(hr, "/api/v1/rosters/{id}/entries", update(null, cell(e1, "2026-03-16", night)), r)
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].field").value("reason"));
        putAs(hr, "/api/v1/rosters/{id}/entries", update("swap", cell(e1, "2026-03-16", night), cell(e2, "2026-03-16", day)), r)
                .andExpect(jsonPath("$.revision").value(2));
        getAs(hr, "/api/v1/rosters/{id}/revisions", r).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.employeeId == '" + e1 + "')].oldValue").value("DAY"))
                .andExpect(jsonPath("$[?(@.employeeId == '" + e1 + "')].newValue").value("NGT"))
                .andExpect(jsonPath("$[?(@.employeeId == '" + e2 + "')].oldValue", org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())))
                .andExpect(jsonPath("$[0].reason").value("swap"));
        assertThat(events.stream(ScheduleChangedEvent.class)).hasSize(2);

        postAs(hr, "/api/v1/rosters/{id}/publish", null, r).andExpect(status().isConflict());
        postAs(hr, "/api/v1/rosters/{id}/lock", null, r).andExpect(jsonPath("$.status").value("LOCKED"));
        putAs(hr, "/api/v1/rosters/{id}/entries", update("late", cell(e1, "2026-03-17", day)), r)
                .andExpect(status().isUnprocessableContent());
        postAs(hr, "/api/v1/rosters/{id}/unlock", Map.of("reason", "fix"), r).andExpect(status().isForbidden());
        postAs(admin, "/api/v1/rosters/{id}/unlock", Map.of(), r).andExpect(status().isUnprocessableContent());
        postAs(admin, "/api/v1/rosters/{id}/unlock", Map.of("reason", "fix"), r).andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    @DisplayName("FR-037 copying a previous roster maps entries by day offset")
    void copy() throws Exception {
        String week1 = roster("2026-03-16", "WEEKLY", null);
        postAs(hr, "/api/v1/rosters/{id}/bulk-assign", Map.of("employeeIds", List.of(e1, e2), "from", "2026-03-16",
                "to", "2026-03-20", "shiftId", day), week1);
        String week2 = roster("2026-03-23", "WEEKLY", null);
        postAs(hr, "/api/v1/rosters/{id}/copy-from/{src}", null, week2, week1).andExpect(status().isOk())
                .andExpect(jsonPath("$.copied").value(10)).andExpect(jsonPath("$.skipped").value(0));
        getAs(hr, "/api/v1/rosters/{id}", week2).andExpect(jsonPath("$.entries[?(@.date == '2026-03-23')]", hasSize(2)));
        String monthly = roster("2026-04-01", "MONTHLY", null);
        postAs(hr, "/api/v1/rosters/{id}/copy-from/{src}", null, monthly, week1).andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("FR-033 published roster entries override the default schedule; drafts do not")
    void scheduleIntegration() throws Exception {
        String r = roster("2026-03-16", "WEEKLY", null);
        putAs(hr, "/api/v1/rosters/{id}/entries", update(null, cell(e1, "2026-03-22", night)), r);
        getAs(hr, "/api/v1/schedules?companyId={c}&employeeId={e}&from=2026-03-22&to=2026-03-22", acme.companyId(), e1)
                .andExpect(jsonPath("$[0].days[0].dayType").value("WEEKLY_OFF"));
        postAs(hr, "/api/v1/rosters/{id}/publish", null, r);
        getAs(hr, "/api/v1/schedules?companyId={c}&employeeId={e}&from=2026-03-22&to=2026-03-22", acme.companyId(), e1)
                .andExpect(jsonPath("$[0].days[0].dayType").value("WORKDAY"))
                .andExpect(jsonPath("$[0].days[0].shiftCode").value("NGT"))
                .andExpect(jsonPath("$[0].days[0].source").value("ROSTER"));
    }

    @Test
    @DisplayName("FR-005 supervisors may edit but not publish rosters")
    void permissions() throws Exception {
        String r = roster("2026-03-16", "WEEKLY", null);
        String sup = data.bearerFor("sup", "SUPERVISOR");
        putAs(sup, "/api/v1/rosters/{id}/entries", update(null, cell(e1, "2026-03-16", day)), r).andExpect(status().isOk());
        postAs(sup, "/api/v1/rosters/{id}/publish", null, r).andExpect(status().isForbidden());
    }
}
