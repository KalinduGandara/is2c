package com.is2c.hrm.common;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.is2c.hrm.common.error.NotFoundException;
import com.is2c.hrm.common.web.CorrelationId;
import com.is2c.hrm.support.IntegrationTest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@IntegrationTest
@Import(ErrorHandlingTest.ProbeController.class)
class ErrorHandlingTest {

    @Autowired
    MockMvc mvc;

    @Test
    @DisplayName("NFR-009 business exception renders problem+json with code and correlationId")
    void notFound() throws Exception {
        mvc.perform(get("/test-probe/missing"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Thing 42 not found"))
                .andExpect(jsonPath("$.correlationId").isString());
    }

    @Test
    @DisplayName("NFR-009 bean validation failure returns 422 with field errors")
    void validation() throws Exception {
        mvc.perform(post("/test-probe/validate").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }

    @Test
    @DisplayName("NFR-009 malformed JSON returns 400")
    void malformed() throws Exception {
        mvc.perform(post("/test-probe/validate").contentType(MediaType.APPLICATION_JSON).content("{oops"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    @DisplayName("NFR-009 unexpected error returns generic 500 without stack trace")
    void unexpected() throws Exception {
        mvc.perform(get("/test-probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("secret detail"))));
    }

    @Test
    @DisplayName("NFR-008 correlation id is echoed when supplied")
    void correlationEchoed() throws Exception {
        mvc.perform(get("/test-probe/missing").header(CorrelationId.HEADER, "abc-123"))
                .andExpect(header().string(CorrelationId.HEADER, "abc-123"))
                .andExpect(jsonPath("$.correlationId").value("abc-123"));
    }

    @Test
    @DisplayName("NFR-008 correlation id is generated when absent or invalid")
    void correlationGenerated() throws Exception {
        mvc.perform(get("/test-probe/missing").header(CorrelationId.HEADER, "bad value with spaces!"))
                .andExpect(header().string(CorrelationId.HEADER, matchesPattern("[0-9a-f\\-]{36}")));
    }

    @RestController
    static class ProbeController {

        @GetMapping("/test-probe/missing")
        String missing() {
            throw new NotFoundException("Thing", 42);
        }

        @GetMapping("/test-probe/boom")
        String boom() {
            throw new IllegalStateException("secret detail");
        }

        @PostMapping("/test-probe/validate")
        String validate(@Valid @RequestBody Probe probe) {
            return probe.name();
        }

        record Probe(@NotBlank String name) {
        }
    }
}
