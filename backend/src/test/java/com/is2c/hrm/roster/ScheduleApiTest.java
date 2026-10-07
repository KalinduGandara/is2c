package com.is2c.hrm.roster;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.is2c.testsupport.ApiTestBase;
import com.is2c.testsupport.OrgFixtures.Structure;

class ScheduleApiTest extends ApiTestBase {

    @Test
    @DisplayName("FR-033/FR-040 schedules combine default shift, rotation, weekly-off and holidays")
    void schedules() throws Exception {
        String admin = data.bearerFor("root", "SUPER_ADMIN");
        Structure acme = orgs.structure(admin, "ACME");
        String hr = data.bearerFor("hr", "HR_MANAGER");
        String day = body(postAs(admin, "/api/v1/companies/{c}/shifts",
                Map.of("code", "DAY", "name", "Day", "type", "FIXED", "s1Start", "08:00", "s1End", "17:00"),
                acme.companyId())).get("id").asString();
        String night = body(postAs(admin, "/api/v1/companies/{c}/shifts",
                Map.of("code", "NGT", "name", "Night", "type", "NIGHT", "s1Start", "22:00", "s1End", "06:00"),
                acme.companyId())).get("id").asString();
        Map<String, Object> body = orgs.employeeBody(acme, "Default", "Worker");
        body.put("defaultShiftId", day);
        UUID regular = orgs.employee(hr, body);
        UUID rotating = orgs.employee(hr, body);
        String pattern = body(postAs(admin, "/api/v1/companies/{c}/rotation-patterns",
                Map.of("name", "nights", "days", java.util.Arrays.asList(night, null)), acme.companyId())).get("id").asString();
        postAs(hr, "/api/v1/rotation-patterns/{id}/assignments", Map.of("employeeIds", List.of(rotating),
                "startDate", "2026-03-16"), pattern).andExpect(status().isCreated());
        postAs(admin, "/api/v1/companies/{c}/holidays", Map.of("date", "2026-03-18", "name", "Poya", "type", "PUBLIC"),
                acme.companyId());

        getAs(hr, "/api/v1/schedules?companyId={c}&from=2026-03-16&to=2026-03-22", acme.companyId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].days", hasSize(7)))
                .andExpect(jsonPath("$[?(@.employeeId == '" + regular + "')].days[0].shiftCode").value("DAY"))
                .andExpect(jsonPath("$[?(@.employeeId == '" + regular + "')].days[2].dayType").value("HOLIDAY"))
                .andExpect(jsonPath("$[?(@.employeeId == '" + regular + "')].days[6].dayType").value("WEEKLY_OFF"))
                .andExpect(jsonPath("$[?(@.employeeId == '" + rotating + "')].days[0].shiftCode").value("NGT"))
                .andExpect(jsonPath("$[?(@.employeeId == '" + rotating + "')].days[0].source").value("ROTATION"))
                .andExpect(jsonPath("$[?(@.employeeId == '" + rotating + "')].days[1].dayType").value("WEEKLY_OFF"));

        getAs(hr, "/api/v1/schedules?companyId={c}&from=2026-03-01&to=2026-05-31", acme.companyId())
                .andExpect(status().isUnprocessableContent());
        getAs(data.bearerFor("emp", "EMPLOYEE"), "/api/v1/schedules?companyId={c}&from=2026-03-16&to=2026-03-22",
                acme.companyId()).andExpect(status().isForbidden());
    }
}
