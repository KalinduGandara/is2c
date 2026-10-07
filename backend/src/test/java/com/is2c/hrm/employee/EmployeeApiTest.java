package com.is2c.hrm.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.is2c.hrm.security.api.ScopeType;
import com.is2c.hrm.security.domain.ScopeItem;
import com.is2c.hrm.security.domain.ScopeItem.ScopeItemType;
import com.is2c.testsupport.ApiTestBase;
import com.is2c.testsupport.OrgFixtures.Structure;

import tools.jackson.databind.JsonNode;

class EmployeeApiTest extends ApiTestBase {

    String hr;
    Structure acme;

    @BeforeEach
    void setUp() throws Exception {
        String root = data.bearerFor("root", "SUPER_ADMIN");
        acme = orgs.structure(root, "ACME");
        hr = data.bearerFor("hr", "HR_MANAGER");
    }

    @Test
    @DisplayName("FR-015 create employee with full profile and emergency contacts")
    void createFull() throws Exception {
        Map<String, Object> body = orgs.employeeBody(acme, "Nimal", "Perera");
        body.put("title", "Mr");
        body.put("nic", "199012345678");
        body.put("dateOfBirth", "1990-05-01");
        body.put("gender", "MALE");
        body.put("maritalStatus", "MARRIED");
        body.put("mobile", "+94771234567");
        body.put("personalEmail", "nimal@example.com");
        body.put("badgeId", "B-100");
        body.put("weeklyOffDays", List.of("SATURDAY", "SUNDAY"));
        body.put("emergencyContacts", List.of(Map.of("name", "Kamala", "relationship", "Spouse", "phone", "0711111111")));
        JsonNode e = orgs.post(hr, "/api/v1/employees", body);
        assertThat(e.get("employeeNumber").asString()).isEqualTo("ACME00001");
        assertThat(e.get("status").asString()).isEqualTo("ACTIVE");
        assertThat(e.get("emergencyContacts").get(0).get("relationship").asString()).isEqualTo("Spouse");
        assertThat(e.get("weeklyOffDays")).hasSize(2);
        getAs(hr, "/api/v1/employees/{id}", e.get("id").asString()).andExpect(status().isOk())
                .andExpect(jsonPath("$.nic").value("199012345678"));
    }

    @Test
    @DisplayName("FR-015 missing mandatory fields are listed individually with 422")
    void mandatoryFields() throws Exception {
        postAs(hr, "/api/v1/employees", Map.of()).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("companyId", "branchId", "departmentId",
                        "designationId", "categoryId", "firstName", "lastName", "joinDate")));
    }

    @Test
    @DisplayName("FR-015 org references must belong to the employee's company; dates must be consistent")
    void referenceAndDateRules() throws Exception {
        String root = data.bearerFor("root2", "SUPER_ADMIN");
        Structure other = orgs.structure(root, "OTHER");
        Map<String, Object> body = orgs.employeeBody(acme, "A", "B");
        body.put("branchId", other.branchId());
        body.put("contractEndDate", "2024-01-01");
        body.put("dateOfBirth", "2026-01-01");
        postAs(hr, "/api/v1/employees", body).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("branchId", "contractEndDate", "dateOfBirth")));
    }

    @Test
    @DisplayName("FR-015 NIC and badge must be unique within the company")
    void uniqueness() throws Exception {
        Map<String, Object> body = orgs.employeeBody(acme, "A", "B");
        body.put("nic", "N1");
        body.put("badgeId", "X1");
        orgs.employee(hr, body);
        Map<String, Object> dup = orgs.employeeBody(acme, "C", "D");
        dup.put("nic", "n1");
        dup.put("badgeId", "x1");
        postAs(hr, "/api/v1/employees", dup).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("nic", "badgeId")));
    }

    @Test
    @DisplayName("FR-014 numbers are generated sequentially and stay unique under concurrent creation")
    void concurrentNumbering() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            int n = i;
            tasks.add(() -> orgs.post(hr, "/api/v1/employees", orgs.employeeBody(acme, "F" + n, "L" + n))
                    .get("employeeNumber").asString());
        }
        List<String> numbers = new ArrayList<>();
        for (Future<String> f : pool.invokeAll(tasks)) {
            numbers.add(f.get());
        }
        pool.shutdown();
        assertThat(numbers).doesNotHaveDuplicates().hasSize(12).allMatch(n -> n.matches("ACME000(0[1-9]|1[0-2])"));
    }

    @Test
    @DisplayName("FR-014 branch/year tokens restart the sequence per branch")
    void branchSequences() throws Exception {
        String root = data.bearerFor("root3", "SUPER_ADMIN");
        UUID kandy = orgs.branch(root, acme.companyId(), "KDY");
        Map<String, Object> s = new HashMap<>(Map.of("weeklyOffDays", List.of("SUNDAY"),
                "employeeNumberFormat", "{BRANCH}-{YYYY}-{SEQ:3}", "payrollStartDay", 1, "absentAsNoPay", true,
                "noPayDivisor", 30, "punchWindowBeforeMinutes", 240, "punchWindowAfterMinutes", 240,
                "weekStartDay", "MONDAY"));
        putAs(root, "/api/v1/companies/{id}/settings", s, acme.companyId()).andExpect(status().isOk());
        assertThat(orgs.post(hr, "/api/v1/employees", orgs.employeeBody(acme, "A", "A")).get("employeeNumber").asString())
                .isEqualTo("CMB-2025-001");
        assertThat(orgs.post(hr, "/api/v1/employees", orgs.employeeBody(acme, "B", "B")).get("employeeNumber").asString())
                .isEqualTo("CMB-2025-002");
        Map<String, Object> k = orgs.employeeBody(acme, "C", "C");
        k.put("branchId", kandy);
        assertThat(orgs.post(hr, "/api/v1/employees", k).get("employeeNumber").asString()).isEqualTo("KDY-2025-001");
    }

    @Test
    @DisplayName("FR-014 explicit numbers need EMPLOYEE:CONFIGURE and must be unique")
    void explicitNumber() throws Exception {
        Map<String, Object> body = orgs.employeeBody(acme, "A", "B");
        body.put("employeeNumber", "LEGACY-7");
        assertThat(orgs.post(hr, "/api/v1/employees", body).get("employeeNumber").asString()).isEqualTo("LEGACY-7");
        postAs(hr, "/api/v1/employees", body).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("employeeNumber"));
        String root = data.bearerFor("root4", "SUPER_ADMIN");
        postAs(root, "/api/v1/admin/roles", Map.of("code", "CLERK", "name", "Clerk",
                "permissions", List.of("EMPLOYEE:VIEW", "EMPLOYEE:ADD"))).andExpect(status().isCreated());
        String clerk = data.bearerFor("clerk", "CLERK");
        body.put("employeeNumber", "LEGACY-8");
        postAs(clerk, "/api/v1/employees", body).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("FR-015 reporting manager must be another employee of the company without cycles")
    void reportingManager() throws Exception {
        UUID boss = orgs.employee(hr, acme, "Boss", "One");
        Map<String, Object> body = orgs.employeeBody(acme, "Sub", "One");
        body.put("reportingManagerId", boss);
        UUID sub = orgs.employee(hr, body);

        JsonNode bossDto = body(getAs(hr, "/api/v1/employees/{id}", boss));
        Map<String, Object> update = orgs.employeeBody(acme, "Boss", "One");
        update.put("version", bossDto.get("version").asLong());
        update.put("reportingManagerId", sub);
        putAs(hr, "/api/v1/employees/{id}", update, boss).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].message").value("reporting line would create a cycle"));
        update.put("reportingManagerId", boss);
        putAs(hr, "/api/v1/employees/{id}", update, boss).andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("NFR-017 stale updates return 409")
    void optimisticLock() throws Exception {
        UUID id = orgs.employee(hr, acme, "A", "B");
        Map<String, Object> body = orgs.employeeBody(acme, "A2", "B");
        body.put("version", 0);
        putAs(hr, "/api/v1/employees/{id}", body, id).andExpect(status().isOk());
        putAs(hr, "/api/v1/employees/{id}", body, id).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("FR-017 history records joining, transfers, promotions and status changes")
    void history() throws Exception {
        String root = data.bearerFor("root5", "SUPER_ADMIN");
        UUID id = orgs.employee(hr, acme, "A", "B");
        UUID newDept = orgs.department(root, acme.companyId(), "FIN", null);
        UUID newDesig = orgs.lookup(root, acme.companyId(), "designations", "LEAD");

        Map<String, Object> body = orgs.employeeBody(acme, "A", "B");
        body.put("departmentId", newDept);
        body.put("version", 0);
        body.put("effectiveDate", "2026-02-01");
        body.put("changeReason", "restructure");
        putAs(hr, "/api/v1/employees/{id}", body, id).andExpect(status().isOk());
        body.put("designationId", newDesig);
        body.put("version", 1);
        putAs(hr, "/api/v1/employees/{id}", body, id).andExpect(status().isOk());
        postAs(hr, "/api/v1/employees/{id}/status", Map.of("status", "RESIGNED", "exitDate", "2026-03-31",
                "reason", "relocating"), id).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESIGNED"))
                .andExpect(jsonPath("$.exitDate").value("2026-03-31"));

        getAs(hr, "/api/v1/employees/{id}/history", id).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[*].eventType", containsInAnyOrder("JOINED", "TRANSFER", "PROMOTION", "STATUS_CHANGE")))
                .andExpect(jsonPath("$[0].eventType").value("STATUS_CHANGE"))
                .andExpect(jsonPath("$[0].effectiveDate").value("2026-03-31"));
    }

    @Test
    @DisplayName("FR-017 resignation/termination require exit date (not before joining) and a reason")
    void statusRules() throws Exception {
        UUID id = orgs.employee(hr, acme, "A", "B");
        postAs(hr, "/api/v1/employees/{id}/status", Map.of("status", "TERMINATED"), id)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("reason", "exitDate")));
        postAs(hr, "/api/v1/employees/{id}/status", Map.of("status", "TERMINATED", "exitDate", "2020-01-01",
                "reason", "x"), id).andExpect(jsonPath("$.errors[0].field").value("exitDate"));
        postAs(hr, "/api/v1/employees/{id}/status", Map.of("status", "INACTIVE"), id)
                .andExpect(jsonPath("$.errors[0].field").value("reason"));
        postAs(hr, "/api/v1/employees/{id}/status", Map.of("status", "INACTIVE", "reason", "long leave"), id)
                .andExpect(status().isOk());
        postAs(hr, "/api/v1/employees/{id}/status", Map.of("status", "ACTIVE"), id)
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("FR-017 unused employees are soft-deleted and disappear")
    void softDelete() throws Exception {
        UUID id = orgs.employee(hr, acme, "A", "B");
        deleteAs(hr, "/api/v1/employees/{id}", id).andExpect(status().isNoContent());
        getAs(hr, "/api/v1/employees/{id}", id).andExpect(status().isNotFound());
        deleteAs(data.bearerFor("sup", "SUPERVISOR"), "/api/v1/employees/{id}", id).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("FR-018 search by text and filters with paging and sorting")
    void search() throws Exception {
        Map<String, Object> a = orgs.employeeBody(acme, "Saman", "Silva");
        a.put("nic", "851234567V");
        orgs.employee(hr, a);
        orgs.employee(hr, acme, "Kamal", "Fernando");
        UUID gone = orgs.employee(hr, acme, "Ruwan", "Silva");
        postAs(hr, "/api/v1/employees/{id}/status", Map.of("status", "RESIGNED", "exitDate", "2026-01-31", "reason", "x"), gone);

        getAs(hr, "/api/v1/employees?q=SILVA").andExpect(jsonPath("$.totalElements").value(2));
        getAs(hr, "/api/v1/employees?q=851234").andExpect(jsonPath("$.content[0].firstName").value("Saman"));
        getAs(hr, "/api/v1/employees?q=acme0000").andExpect(jsonPath("$.totalElements").value(3));
        getAs(hr, "/api/v1/employees?status=ACTIVE&q=silva").andExpect(jsonPath("$.totalElements").value(1));
        getAs(hr, "/api/v1/employees?departmentId={d}&sort=lastName,asc", acme.departmentId())
                .andExpect(jsonPath("$.content[0].lastName").value("Fernando"))
                .andExpect(jsonPath("$.content[0].departmentName").value("OPS dept"))
                .andExpect(jsonPath("$.content[0].designationName").value("SE"));
        getAs(hr, "/api/v1/employees?size=2").andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.totalPages").value(2));
        getAs(hr, "/api/v1/employees?size=500").andExpect(jsonPath("$.size").value(200));
        getAs(hr, "/api/v1/employees?q=100%25").andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("FR-006 department-scoped users see only their department tree; others are 404 or 403")
    void departmentScope() throws Exception {
        String root = data.bearerFor("root6", "SUPER_ADMIN");
        UUID sub = orgs.department(root, acme.companyId(), "OPS-A", acme.departmentId());
        UUID fin = orgs.department(root, acme.companyId(), "FIN", null);
        UUID inOps = orgs.employee(hr, acme, "In", "Ops");
        Map<String, Object> subBody = orgs.employeeBody(acme, "In", "Sub");
        subBody.put("departmentId", sub);
        orgs.employee(hr, subBody);
        Map<String, Object> finBody = orgs.employeeBody(acme, "In", "Fin");
        finBody.put("departmentId", fin);
        UUID inFin = orgs.employee(hr, finBody);

        String mgr = data.bearer(data.user("dm", ScopeType.DEPARTMENT,
                Set.of(new ScopeItem(ScopeItemType.DEPARTMENT, acme.departmentId())), "DEPARTMENT_MANAGER", "HR_MANAGER"));
        getAs(mgr, "/api/v1/employees").andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].lastName", containsInAnyOrder("Ops", "Sub")));
        getAs(mgr, "/api/v1/employees/{id}", inOps).andExpect(status().isOk());
        getAs(mgr, "/api/v1/employees/{id}", inFin).andExpect(status().isNotFound());
        postAs(mgr, "/api/v1/employees", finBody).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("FR-006 OWN scope sees only the linked employee")
    void ownScope() throws Exception {
        UUID me = orgs.employee(hr, acme, "Me", "Myself");
        UUID other = orgs.employee(hr, acme, "Not", "Me");
        var u = data.user("self", ScopeType.OWN, Set.of(), "SUPERVISOR");
        String bearer = data.bearer(data.linkEmployee(u, me));
        getAs(bearer, "/api/v1/employees").andExpect(jsonPath("$.totalElements").value(1));
        getAs(bearer, "/api/v1/employees/{id}", other).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("FR-007 employee profile responses contain no salary information")
    void noSalary() throws Exception {
        UUID id = orgs.employee(hr, acme, "A", "B");
        String content = getAs(hr, "/api/v1/employees/{id}", id).andReturn().getResponse().getContentAsString();
        assertThat(content.toLowerCase()).doesNotContain("salary").doesNotContain("basic");
    }

    @Test
    @DisplayName("FR-011/FR-013 org records referenced by active employees cannot be deleted")
    void orgDeleteGuards() throws Exception {
        String root = data.bearerFor("root7", "SUPER_ADMIN");
        UUID id = orgs.employee(hr, acme, "A", "B");
        deleteAs(root, "/api/v1/branches/{id}", acme.branchId()).andExpect(status().isUnprocessableContent());
        deleteAs(root, "/api/v1/designations/{id}", acme.designationId()).andExpect(status().isUnprocessableContent());
        deleteAs(root, "/api/v1/departments/{id}", acme.departmentId()).andExpect(status().isUnprocessableContent());
        postAs(hr, "/api/v1/employees/{id}/status", Map.of("status", "TERMINATED", "exitDate", "2026-01-31", "reason", "x"), id);
        deleteAs(root, "/api/v1/designations/{id}", acme.designationId()).andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("FR-008 employee changes are audited with before/after values")
    void audited() throws Exception {
        UUID id = orgs.employee(hr, acme, "A", "B");
        Map<String, Object> body = orgs.employeeBody(acme, "Changed", "B");
        body.put("version", 0);
        putAs(hr, "/api/v1/employees/{id}", body, id);
        String auditor = data.bearerFor("aud", "AUDITOR");
        getAs(auditor, "/api/v1/audit?entityType=EMPLOYEE&action=UPDATE&entityId=" + id)
                .andExpect(jsonPath("$.content[*].action", hasItem("UPDATE")))
                .andExpect(jsonPath("$.content[0].before").value(containsString("\"firstName\":\"A\"")))
                .andExpect(jsonPath("$.content[0].after").value(containsString("\"firstName\":\"Changed\"")));
    }
}
