package com.is2c.hrm.admin;

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

import com.is2c.hrm.security.api.ScopeType;
import com.is2c.hrm.security.domain.ScopeItem;
import com.is2c.hrm.security.domain.ScopeItem.ScopeItemType;
import com.is2c.testsupport.ApiTestBase;
import com.is2c.testsupport.TestData;

import tools.jackson.databind.JsonNode;

class AdminApiTest extends ApiTestBase {

    String root;
    UUID companyA;
    UUID companyB;

    @BeforeEach
    void setUp() throws Exception {
        root = data.bearerFor("root", "SUPER_ADMIN");
        companyA = orgs.company(root, "A");
        companyB = orgs.company(root, "B");
    }

    private Map<String, Object> user(String username, ScopeType scope, List<String> roles) {
        Map<String, Object> m = new HashMap<>();
        m.put("username", username);
        m.put("displayName", username + " Name");
        m.put("email", username + "@example.com");
        m.put("password", "Init#Passw0rd");
        m.put("roles", roles);
        m.put("scopeType", scope);
        return m;
    }

    private String companyAdminOf(UUID company) {
        return data.bearer(data.user("ca" + company.toString().substring(0, 4), ScopeType.COMPANY,
                Set.of(new ScopeItem(ScopeItemType.COMPANY, company)), "COMPANY_ADMIN"));
    }

    @Test
    @DisplayName("FR-004 created users must change their password and can log in")
    void createUser() throws Exception {
        Map<String, Object> body = user("newbie", ScopeType.COMPANY, List.of("HR_MANAGER"));
        body.put("scopeCompanyIds", List.of(companyA));
        JsonNode u = body(postAs(root, "/api/v1/admin/users", body).andExpect(status().isCreated()));
        assertThat(u.get("mustChangePassword").asBoolean()).isTrue();
        assertThat(u.get("scopeCompanyIds").get(0).asString()).isEqualTo(companyA.toString());
        JsonNode tokens = body(postAs(null, "/api/v1/auth/login", Map.of("username", "newbie", "password", "Init#Passw0rd"))
                .andExpect(status().isOk()));
        getAs("Bearer " + tokens.get("accessToken").asString(), "/api/v1/auth/me")
                .andExpect(jsonPath("$.scopeType").value("COMPANY"))
                .andExpect(jsonPath("$.mustChangePassword").value(true));
    }

    @Test
    @DisplayName("FR-004 validation: unique username (case-insensitive), roles required, password policy, scope items")
    void createValidation() throws Exception {
        data.user("taken", "EMPLOYEE");
        Map<String, Object> dup = user("TAKEN", ScopeType.ALL, List.of("EMPLOYEE"));
        postAs(root, "/api/v1/admin/users", dup).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[*].field", hasItem("username")));
        Map<String, Object> noRoles = user("userx1", ScopeType.ALL, List.of());
        postAs(root, "/api/v1/admin/users", noRoles).andExpect(jsonPath("$.errors[*].field", hasItem("roles")));
        Map<String, Object> weak = user("userx2", ScopeType.ALL, List.of("EMPLOYEE"));
        weak.put("password", "weak");
        postAs(root, "/api/v1/admin/users", weak).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[*].field", hasItem("password")));
        Map<String, Object> emptyScope = user("userx3", ScopeType.BRANCH, List.of("EMPLOYEE"));
        postAs(root, "/api/v1/admin/users", emptyScope).andExpect(jsonPath("$.errors[*].field", hasItem("scopeType")));
        Map<String, Object> own = user("userx4", ScopeType.OWN, List.of("EMPLOYEE"));
        postAs(root, "/api/v1/admin/users", own).andExpect(jsonPath("$.errors[*].field", hasItem("employeeId")));
        Map<String, Object> badRole = user("userx5", ScopeType.ALL, List.of("NOPE"));
        postAs(root, "/api/v1/admin/users", badRole).andExpect(jsonPath("$.errors[*].field", hasItem("roles")));
    }

    @Test
    @DisplayName("FR-004 deactivating a user revokes sessions and blocks access")
    void deactivate() throws Exception {
        var u = data.user("leaver", "HR_MANAGER");
        String refresh = body(postAs(null, "/api/v1/auth/login", Map.of("username", "leaver", "password", TestData.PASSWORD)))
                .get("refreshToken").asString();
        String bearer = data.bearer(u);
        postAs(root, "/api/v1/admin/users/{id}/deactivate", null, u.getId()).andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        getAs(bearer, "/api/v1/auth/me").andExpect(status().isUnauthorized());
        postAs(null, "/api/v1/auth/refresh", Map.of("refreshToken", refresh)).andExpect(status().isUnauthorized());
        postAs(root, "/api/v1/admin/users/{id}/activate", null, u.getId()).andExpect(jsonPath("$.active").value(true));
    }

    @Test
    @DisplayName("FR-004 the last active super admin cannot be deactivated or demoted")
    void lastSuperAdmin() throws Exception {
        var rootUser = data.user("root2", "SUPER_ADMIN");
        String only = data.bearer(rootUser);
        // 'root' from setUp is also a super admin; deactivate it first.
        var first = body(getAs(only, "/api/v1/admin/users?q=root")).get("content");
        UUID rootId = null;
        for (JsonNode n : first) {
            if (n.get("username").asString().equals("root")) {
                rootId = UUID.fromString(n.get("id").asString());
            }
        }
        postAs(only, "/api/v1/admin/users/{id}/deactivate", null, rootId).andExpect(status().isOk());
        postAs(only, "/api/v1/admin/users/{id}/deactivate", null, rootUser.getId())
                .andExpect(status().isUnprocessableContent());
        Map<String, Object> demote = user("root2", ScopeType.ALL, List.of("EMPLOYEE"));
        putAs(only, "/api/v1/admin/users/{id}", demote, rootUser.getId()).andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("FR-003 admin password reset forces a change and revokes sessions")
    void resetPassword() throws Exception {
        var u = data.user("forgetful", "HR_MANAGER");
        String refresh = body(postAs(null, "/api/v1/auth/login", Map.of("username", "forgetful", "password", TestData.PASSWORD)))
                .get("refreshToken").asString();
        postAs(root, "/api/v1/admin/users/{id}/reset-password", Map.of("temporaryPassword", "weak"), u.getId())
                .andExpect(status().isUnprocessableContent());
        postAs(root, "/api/v1/admin/users/{id}/reset-password", Map.of("temporaryPassword", "Temp#Passw0rd"), u.getId())
                .andExpect(status().isNoContent());
        postAs(null, "/api/v1/auth/refresh", Map.of("refreshToken", refresh)).andExpect(status().isUnauthorized());
        String access = body(postAs(null, "/api/v1/auth/login", Map.of("username", "forgetful", "password", "Temp#Passw0rd"))
                .andExpect(status().isOk())).get("accessToken").asString();
        getAs("Bearer " + access, "/api/v1/test-secured/employee-view")
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    @DisplayName("FR-006 company admins only see and manage users within their companies")
    void scopedAdministration() throws Exception {
        String caA = companyAdminOf(companyA);
        var inA = data.user("inA", ScopeType.COMPANY, Set.of(new ScopeItem(ScopeItemType.COMPANY, companyA)), "SUPERVISOR");
        var inB = data.user("inB", ScopeType.COMPANY, Set.of(new ScopeItem(ScopeItemType.COMPANY, companyB)), "SUPERVISOR");
        getAs(caA, "/api/v1/admin/users").andExpect(jsonPath("$.content[*].username", hasItem("inA")))
                .andExpect(jsonPath("$.content[?(@.username == 'inB')]", hasSize(0)))
                .andExpect(jsonPath("$.content[?(@.username == 'root')]", hasSize(0)));
        getAs(caA, "/api/v1/admin/users/{id}", inA.getId()).andExpect(status().isOk());
        getAs(caA, "/api/v1/admin/users/{id}", inB.getId()).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("FR-005/FR-006 privilege escalation is blocked: global scope, super admin role, foreign companies, stronger roles")
    void escalation() throws Exception {
        String caA = companyAdminOf(companyA);
        postAs(caA, "/api/v1/admin/users", user("userg1", ScopeType.ALL, List.of("EMPLOYEE")))
                .andExpect(status().isForbidden());
        Map<String, Object> sa = user("userg2", ScopeType.COMPANY, List.of("SUPER_ADMIN"));
        sa.put("scopeCompanyIds", List.of(companyA));
        postAs(caA, "/api/v1/admin/users", sa).andExpect(status().isForbidden());
        Map<String, Object> foreign = user("userg3", ScopeType.COMPANY, List.of("SUPERVISOR"));
        foreign.put("scopeCompanyIds", List.of(companyB));
        postAs(caA, "/api/v1/admin/users", foreign).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("scope"));
        Map<String, Object> payroll = user("userg4", ScopeType.COMPANY, List.of("PAYROLL_OFFICER"));
        payroll.put("scopeCompanyIds", List.of(companyA));
        postAs(caA, "/api/v1/admin/users", payroll).andExpect(status().isForbidden());
        Map<String, Object> ok = user("userg5", ScopeType.COMPANY, List.of("SUPERVISOR"));
        ok.put("scopeCompanyIds", List.of(companyA));
        postAs(caA, "/api/v1/admin/users", ok).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("FR-005 custom roles: create with valid permissions, edit, delete when unused")
    void customRoles() throws Exception {
        postAs(root, "/api/v1/admin/roles", Map.of("code", "TIMEKEEPER", "name", "Timekeeper",
                "permissions", List.of("ATTENDANCE:VIEW", "BOGUS:VIEW"))).andExpect(status().isUnprocessableContent());
        JsonNode role = body(postAs(root, "/api/v1/admin/roles", Map.of("code", "TIMEKEEPER", "name", "Timekeeper",
                "permissions", List.of("ATTENDANCE:VIEW", "ATTENDANCE:EDIT"))).andExpect(status().isCreated()));
        String id = role.get("id").asString();
        var holder = data.user("tk", "TIMEKEEPER");
        String bearer = data.bearer(holder);
        getAs(bearer, "/api/v1/test-secured/employee-view").andExpect(status().isForbidden());

        putAs(root, "/api/v1/admin/roles/{id}", Map.of("name", "Timekeeper", "version", 0,
                "permissions", List.of("ATTENDANCE:VIEW", "EMPLOYEE:VIEW")), id).andExpect(status().isOk());
        getAs(bearer, "/api/v1/test-secured/employee-view").andExpect(status().isOk());

        deleteAs(root, "/api/v1/admin/roles/{id}", id).andExpect(status().isUnprocessableContent());
        JsonNode other = body(postAs(root, "/api/v1/admin/roles", Map.of("code", "TEMP", "name", "Temp",
                "permissions", List.of())));
        deleteAs(root, "/api/v1/admin/roles/{id}", other.get("id").asString()).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("FR-005 system roles: permissions editable (except super admin), cannot be deleted")
    void systemRoles() throws Exception {
        JsonNode list = body(getAs(root, "/api/v1/admin/roles").andExpect(jsonPath("$", hasSize(9))));
        String hrId = null;
        String saId = null;
        for (JsonNode r : list) {
            if (r.get("code").asString().equals("HR_MANAGER")) {
                hrId = r.get("id").asString();
            }
            if (r.get("code").asString().equals("SUPER_ADMIN")) {
                saId = r.get("id").asString();
            }
        }
        putAs(root, "/api/v1/admin/roles/{id}", Map.of("name", "ignored", "permissions", List.of("EMPLOYEE:VIEW")), hrId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("HR Manager"))
                .andExpect(jsonPath("$.permissions", hasSize(1)));
        putAs(root, "/api/v1/admin/roles/{id}", Map.of("name", "x", "permissions", List.of()), saId)
                .andExpect(status().isUnprocessableContent());
        deleteAs(root, "/api/v1/admin/roles/{id}", hrId).andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("FR-005 users cannot create roles with permissions they do not hold")
    void roleEscalation() throws Exception {
        String caA = companyAdminOf(companyA);
        postAs(caA, "/api/v1/admin/roles", Map.of("code", "SNEAKY", "name", "Sneaky",
                "permissions", List.of("PAYROLL:VIEW"))).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("FR-005 permission catalog lists modules and actions")
    void catalog() throws Exception {
        getAs(root, "/api/v1/admin/permissions").andExpect(jsonPath("$.modules", hasSize(11)))
                .andExpect(jsonPath("$.actions", hasSize(7)));
        getAs(data.bearerFor("e", "EMPLOYEE"), "/api/v1/admin/permissions").andExpect(status().isForbidden());
    }
}
