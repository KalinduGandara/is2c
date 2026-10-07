package com.is2c.hrm.common;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.is2c.testsupport.ApiTestBase;

class SpaFallbackTest extends ApiTestBase {

    @Test
    @DisplayName("NFR-001 the embedded SPA is served for client-side routes (deep links survive reloads)")
    void clientRoutes() throws Exception {
        // "/" is Spring Boot's welcome page: a forward to index.html (MockMvc does not follow forwards)
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(forwardedUrl("index.html"));
        mvc.perform(get("/payroll/periods/123")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Premium HRM test shell")));
    }

    @Test
    @DisplayName("NFR-001 unknown API paths and missing assets are not masked by the SPA fallback")
    void apiAndAssets() throws Exception {
        mvc.perform(get("/api/v1/nope").header("Authorization", data.bearerFor("root", "SUPER_ADMIN")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/assets/missing.js")).andExpect(status().isNotFound());
    }
}
