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
