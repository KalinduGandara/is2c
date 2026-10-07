package com.is2c.hrm.support;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Creates org structure through the public API so tests exercise the same paths as clients. */
@Component
public class OrgFixtures {

    private final MockMvc mvc;
    private final JsonMapper json;

    public OrgFixtures(MockMvc mvc, JsonMapper json) {
        this.mvc = mvc;
        this.json = json;
    }

    public UUID company(String bearer, String code) throws Exception {
        return id(post(bearer, "/api/v1/companies", Map.of("code", code, "legalName", code + " (Pvt) Ltd")));
    }

    public UUID branch(String bearer, UUID companyId, String code) throws Exception {
        return id(post(bearer, "/api/v1/companies/" + companyId + "/branches", Map.of("code", code, "name", code + " branch")));
    }

    public UUID department(String bearer, UUID companyId, String code, UUID parentId) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("code", code);
        body.put("name", code + " dept");
        body.put("parentId", parentId);
        return id(post(bearer, "/api/v1/companies/" + companyId + "/departments", body));
    }

    public UUID lookup(String bearer, UUID companyId, String kind, String code) throws Exception {
        return id(post(bearer, "/api/v1/companies/" + companyId + "/" + kind, Map.of("code", code, "name", code)));
    }

    /** A company with one branch, one department, a designation and an employment category. */
    public record Structure(UUID companyId, UUID branchId, UUID departmentId, UUID designationId, UUID categoryId) {
    }

    public Structure structure(String bearer, String companyCode) throws Exception {
        UUID company = company(bearer, companyCode);
        return new Structure(company, branch(bearer, company, "CMB"), department(bearer, company, "OPS", null),
                lookup(bearer, company, "designations", "SE"), lookup(bearer, company, "employment-categories", "PERM"));
    }

    public Map<String, Object> employeeBody(Structure s, String firstName, String lastName) {
        Map<String, Object> body = new HashMap<>();
        body.put("companyId", s.companyId());
        body.put("branchId", s.branchId());
        body.put("departmentId", s.departmentId());
        body.put("designationId", s.designationId());
        body.put("categoryId", s.categoryId());
        body.put("firstName", firstName);
        body.put("lastName", lastName);
        body.put("joinDate", "2025-01-15");
        return body;
    }

    public UUID employee(String bearer, Structure s, String firstName, String lastName) throws Exception {
        return id(post(bearer, "/api/v1/employees", employeeBody(s, firstName, lastName)));
    }

    public UUID employee(String bearer, Map<String, Object> body) throws Exception {
        return id(post(bearer, "/api/v1/employees", body));
    }

    public JsonNode post(String bearer, String url, Object body) throws Exception {
        String content = mvc.perform(MockMvcRequestBuilders.post(url).header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(content);
    }

    private static UUID id(JsonNode node) {
        return UUID.fromString(node.get("id").asString());
    }
}
