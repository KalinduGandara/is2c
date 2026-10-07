package com.is2c.hrm.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.is2c.hrm.security.api.Permissions;
import com.is2c.hrm.security.domain.Role;
import com.is2c.hrm.security.domain.RoleRepository;
import com.is2c.hrm.support.ApiTestBase;

class PermissionApiTest extends ApiTestBase {

    @Autowired
    RoleRepository roles;

    @Test
    @DisplayName("FR-005 nine system roles are seeded")
    void seededRoles() {
        assertThat(roles.findAll()).extracting(Role::getCode).containsExactlyInAnyOrder("SUPER_ADMIN",
                "COMPANY_ADMIN", "HR_MANAGER", "PAYROLL_OFFICER", "BRANCH_MANAGER", "DEPARTMENT_MANAGER",
                "SUPERVISOR", "EMPLOYEE", "AUDITOR");
    }

    @Test
    @DisplayName("FR-005 auditor has only VIEW and EXPORT permissions")
    void auditorReadOnly() {
        Role auditor = roles.findByCode("AUDITOR").orElseThrow();
        assertThat(auditor.getPermissions()).isNotEmpty()
                .allMatch(p -> p.endsWith(":VIEW") || p.endsWith(":EXPORT"));
    }

    @Test
    @DisplayName("FR-005 / CL R2-3 company admin has no payroll permissions by default")
    void companyAdminNoPayroll() {
        assertThat(roles.findByCode("COMPANY_ADMIN").orElseThrow().getPermissions())
                .noneMatch(p -> p.startsWith("PAYROLL:"));
    }

    @Test
    @DisplayName("FR-005 every seeded permission is a valid MODULE:ACTION")
    void validPermissions() {
        assertThat(roles.findAll()).flatExtracting(Role::getPermissions).allMatch(Permissions::isValid);
    }

    @Test
    @DisplayName("FR-005 endpoint allows users holding the permission and rejects others with 403")
    void enforcement() throws Exception {
        getAs(data.bearerFor("hr", "HR_MANAGER"), "/api/v1/test-secured/employee-view").andExpect(status().isOk());
        getAs(data.bearerFor("emp", "EMPLOYEE"), "/api/v1/test-secured/employee-view")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("FR-005 super admin has every permission, including ones not seeded")
    void superAdminAll() throws Exception {
        getAs(data.bearerFor("root", "SUPER_ADMIN"), "/api/v1/test-secured/payroll-configure")
                .andExpect(status().isOk());
        getAs(data.bearerFor("ca", "COMPANY_ADMIN"), "/api/v1/test-secured/payroll-configure")
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("FR-005 permissions of multiple roles are combined")
    void unionOfRoles() throws Exception {
        String both = data.bearerFor("multi", "EMPLOYEE", "PAYROLL_OFFICER");
        getAs(both, "/api/v1/test-secured/payroll-configure").andExpect(status().isOk());
        assertThat(Permissions.ALL).hasSize(77);
    }
}
