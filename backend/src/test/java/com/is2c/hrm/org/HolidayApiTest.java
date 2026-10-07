package com.is2c.hrm.org;

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

import com.is2c.hrm.security.api.ScopeType;
import com.is2c.testsupport.ApiTestBase;
import com.is2c.testsupport.OrgFixtures.Structure;

class HolidayApiTest extends ApiTestBase {

    String admin;
    Structure acme;
    UUID kandy;

    @BeforeEach
    void setUp() throws Exception {
        admin = data.bearerFor("root", "SUPER_ADMIN");
        acme = orgs.structure(admin, "ACME");
        kandy = orgs.branch(admin, acme.companyId(), "KDY");
    }

    private Map<String, Object> holiday(String date, String name, Boolean all, List<UUID> branches) {
        Map<String, Object> m = new HashMap<>(Map.of("date", date, "name", name, "type", "PUBLIC"));
        if (all != null) {
            m.put("allBranches", all);
        }
        if (branches != null) {
            m.put("branchIds", branches);
        }
        return m;
    }

    @Test
    @DisplayName("FR-025 maintain holidays per company and list them by year")
    void crud() throws Exception {
        String id = body(postAs(admin, "/api/v1/companies/{c}/holidays", holiday("2026-04-14", "Sinhala & Tamil New Year", null, null),
                acme.companyId()).andExpect(status().isCreated())).get("id").asString();
        postAs(admin, "/api/v1/companies/{c}/holidays", holiday("2027-01-15", "Thai Pongal", null, null), acme.companyId());
        getAs(admin, "/api/v1/companies/{c}/holidays?year=2026", acme.companyId()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].allBranches").value(true));
        putAs(admin, "/api/v1/holidays/{id}", Map.of("date", "2026-04-13", "name", "New Year Eve", "type", "MERCANTILE",
                "version", 0), id).andExpect(status().isOk()).andExpect(jsonPath("$.type").value("MERCANTILE"));
        deleteAs(admin, "/api/v1/holidays/{id}", id).andExpect(status().isNoContent());
        getAs(admin, "/api/v1/companies/{c}/holidays?year=2026", acme.companyId()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("FR-025 duplicates are rejected when branch applicability overlaps")
    void duplicates() throws Exception {
        postAs(admin, "/api/v1/companies/{c}/holidays", holiday("2026-05-01", "Kandy perahera", false, List.of(kandy)),
                acme.companyId()).andExpect(status().isCreated());
        postAs(admin, "/api/v1/companies/{c}/holidays", holiday("2026-05-01", "Colombo day", false, List.of(acme.branchId())),
                acme.companyId()).andExpect(status().isCreated());
        postAs(admin, "/api/v1/companies/{c}/holidays", holiday("2026-05-01", "May Day", true, null), acme.companyId())
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.errors[0].field").value("date"));
        postAs(admin, "/api/v1/companies/{c}/holidays", holiday("2026-05-01", "Again", false, List.of(kandy)), acme.companyId())
                .andExpect(status().isUnprocessableContent());
        postAs(admin, "/api/v1/companies/{c}/holidays", holiday("2026-05-02", "None", false, List.of()), acme.companyId())
                .andExpect(jsonPath("$.errors[0].field").value("branchIds"));
    }

    @Test
    @DisplayName("FR-025 copy a year by month/day, skipping Feb 29 and existing dates")
    void copyYear() throws Exception {
        postAs(admin, "/api/v1/companies/{c}/holidays", holiday("2028-02-29", "Leap", null, null), acme.companyId());
        postAs(admin, "/api/v1/companies/{c}/holidays", holiday("2028-12-25", "Christmas", null, null), acme.companyId());
        postAs(admin, "/api/v1/companies/{c}/holidays", holiday("2028-05-01", "May Day", null, null), acme.companyId());
        postAs(admin, "/api/v1/companies/{c}/holidays", holiday("2029-05-01", "Existing", null, null), acme.companyId());
        postAs(admin, "/api/v1/companies/{c}/holidays/copy", Map.of("fromYear", 2028, "toYear", 2029), acme.companyId())
                .andExpect(status().isOk()).andExpect(jsonPath("$.copied").value(1)).andExpect(jsonPath("$.skipped").value(2));
        getAs(admin, "/api/v1/companies/{c}/holidays?year=2029", acme.companyId()).andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("FR-025 holidays are visible to employees of the company but not changeable by them")
    void visibility() throws Exception {
        postAs(admin, "/api/v1/companies/{c}/holidays", holiday("2026-02-04", "Independence Day", null, null), acme.companyId());
        String hr = data.bearerFor("hr", "HR_MANAGER");
        UUID emp = orgs.employee(hr, acme, "Self", "Service");
        var u = data.user("selfie", ScopeType.OWN, Set.of(), "EMPLOYEE");
        String employee = data.bearer(data.linkEmployee(u, emp));
        getAs(employee, "/api/v1/companies/{c}/holidays?year=2026", acme.companyId()).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        postAs(employee, "/api/v1/companies/{c}/holidays", holiday("2026-03-01", "x", null, null), acme.companyId())
                .andExpect(status().isForbidden());
        UUID other = orgs.company(admin, "OTHER");
        getAs(employee, "/api/v1/companies/{c}/holidays?year=2026", other).andExpect(status().isNotFound());
    }
}
