package com.is2c.hrm.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.is2c.testsupport.ApiTestBase;

class HealthEndpointTest extends ApiTestBase {

    @Test
    @DisplayName("NFR-001 health is UP without an SMTP server while mail is disabled")
    void healthUpWithoutMail() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }
}
