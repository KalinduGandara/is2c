package com.is2c.hrm.approval;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.is2c.hrm.security.api.ScopeType;
import com.is2c.hrm.security.domain.ScopeItem;
import com.is2c.hrm.security.domain.ScopeItem.ScopeItemType;
import com.is2c.testsupport.ApiTestBase;

class DelegationScopeApiTest extends ApiTestBase {

    @Test
    @DisplayName("FR-073/FR-006 administrators delegate only for users in companies they cover")
    void scopedDelegations() throws Exception {
        String root = data.bearerFor("root", "SUPER_ADMIN");
        UUID a = orgs.company(root, "A");
        UUID b = orgs.company(root, "B");
        var caA = data.user("caA", ScopeType.COMPANY, Set.of(new ScopeItem(ScopeItemType.COMPANY, a)), "COMPANY_ADMIN");
        var hrA = data.user("hrA", ScopeType.COMPANY, Set.of(new ScopeItem(ScopeItemType.COMPANY, a)), "HR_MANAGER");
        var hrB = data.user("hrB", ScopeType.COMPANY, Set.of(new ScopeItem(ScopeItemType.COMPANY, b)), "HR_MANAGER");
        String admin = data.bearer(caA);
        postAs(admin, "/api/v1/approval/delegations", Map.of("delegatorUserId", hrB.getId(), "delegateUserId", caA.getId(),
                "fromDate", "2026-03-16", "toDate", "2026-03-20")).andExpect(status().isForbidden());
        postAs(root, "/api/v1/approval/delegations", Map.of("delegatorUserId", hrB.getId(), "delegateUserId", data.user("x", "EMPLOYEE").getId(),
                "fromDate", "2026-03-16", "toDate", "2026-03-20")).andExpect(status().isCreated());
        postAs(admin, "/api/v1/approval/delegations", Map.of("delegatorUserId", hrA.getId(), "delegateUserId", caA.getId(),
                "fromDate", "2026-03-16", "toDate", "2026-03-20")).andExpect(status().isCreated());
        getAs(admin, "/api/v1/approval/delegations?all=true").andExpect(jsonPath("$", hasSize(1)));
        getAs(root, "/api/v1/approval/delegations?all=true").andExpect(jsonPath("$", hasSize(2)));
    }
}
