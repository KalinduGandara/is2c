package com.is2c.testsupport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.is2c.hrm.security.service.PrincipalLoader;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Base for API tests: clean database, reset clock, JSON helpers. */
@IntegrationTest
public abstract class ApiTestBase {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected JsonMapper json;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected TestData data;

    @Autowired
    protected OrgFixtures org;

    @Autowired
    private DbCleaner cleaner;

    @Autowired
    private PrincipalLoader principals;

    @BeforeEach
    void resetState() {
        cleaner.clean();
        principals.evictAll();
        clock.reset();
    }

    protected ResultActions getAs(String auth, String url, Object... vars) throws Exception {
        return mvc.perform(withAuth(get(url, vars), auth));
    }

    protected ResultActions postAs(String auth, String url, Object body, Object... vars) throws Exception {
        return mvc.perform(withAuth(post(url, vars), auth).contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }

    protected ResultActions putAs(String auth, String url, Object body, Object... vars) throws Exception {
        return mvc.perform(withAuth(put(url, vars), auth).contentType(MediaType.APPLICATION_JSON).content(toJson(body)));
    }

    protected ResultActions deleteAs(String auth, String url, Object... vars) throws Exception {
        return mvc.perform(withAuth(delete(url, vars), auth));
    }

    protected JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    protected String toJson(Object body) {
        if (body == null) {
            return "";
        }
        return body instanceof String s ? s : json.writeValueAsString(body);
    }

    private static MockHttpServletRequestBuilder withAuth(MockHttpServletRequestBuilder builder, String auth) {
        return auth == null ? builder : builder.header(HttpHeaders.AUTHORIZATION, auth);
    }
}
