package com.is2c.hrm.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import com.is2c.testsupport.ApiTestBase;

class ApiDocsTest {

    @Nested
    @TestPropertySource(properties = "hrm.api-docs=true")
    class Enabled extends ApiTestBase {

        @Test
        @DisplayName("NFR-007 OpenAPI 3 spec at /api/docs documents the /api/v1 endpoints with bearer auth; UI at /api/docs/ui")
        void spec() throws Exception {
            String spec = mvc.perform(get("/api/docs")).andExpect(status().isOk()).andReturn().getResponse()
                    .getContentAsString();
            assertThat(spec).contains("\"openapi\":\"3.", "/api/v1/payroll/periods", "/api/v1/employees",
                    "/api/v1/reports/{key}", "bearerAuth");
            int ui = mvc.perform(get("/api/docs/ui")).andReturn().getResponse().getStatus();
            assertThat(ui).isIn(200, 302);
        }
    }

    @Nested
    class Disabled extends ApiTestBase {

        @Test
        @DisplayName("NFR-007 API docs are off unless HRM_API_DOCS=true")
        void off() throws Exception {
            mvc.perform(get("/api/docs")).andExpect(status().isNotFound());
        }
    }
}
