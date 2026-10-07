package com.is2c.hrm.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.mock.web.MockMultipartFile;

import tools.jackson.databind.JsonNode;

/** FR-100 self-service endpoints for a user linked to an employee (EMPLOYEE role: no module permissions). */
class SelfServiceApiTest extends FinalizedPayrollTestBase {

    static final byte[] PDF = "%PDF-1.4\n%test\n".getBytes(StandardCharsets.US_ASCII);

    String me;
    String other;

    @BeforeEach
    void link() {
        me = data.bearer(data.linkEmployee(data.user("one", "EMPLOYEE"), e1));
        other = data.bearer(data.linkEmployee(data.user("two", "EMPLOYEE"), e2));
    }

    @Test
    @DisplayName("FR-100 own profile with organisation names; unlinked users get 404")
    void profile() throws Exception {
        getAs(me, "/api/v1/me/profile").andExpect(status().isOk())
                .andExpect(jsonPath("$.employee.firstName").value("One"))
                .andExpect(jsonPath("$.employee.personalEmail").value("one@example.com"))
                .andExpect(jsonPath("$.departmentName").value("OPS dept"))
                .andExpect(jsonPath("$.companyName").isNotEmpty());
        getAs(data.bearerFor("nobody", "EMPLOYEE"), "/api/v1/me/profile").andExpect(status().isNotFound());
        getAs(data.bearerFor("nobody2", "EMPLOYEE"), "/api/v1/me/leave/requests").andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("FR-100 own documents can be listed and downloaded, others' cannot")
    void documents() throws Exception {
        String id = body(mvc.perform(multipart("/api/v1/employees/{id}/documents", e1)
                .file(new MockMultipartFile("file", "contract.pdf", "application/pdf", PDF))
                .param("docType", "Contract").param("title", "Contract").header("Authorization", hr))
                .andExpect(status().isCreated())).get("id").asString();
        getAs(me, "/api/v1/me/documents").andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].title").value("Contract"));
        getAs(me, "/api/v1/me/documents/{id}/content", id).andExpect(status().isOk()).andExpect(content().bytes(PDF));
        getAs(other, "/api/v1/me/documents").andExpect(jsonPath("$", hasSize(0)));
        getAs(other, "/api/v1/me/documents/{id}/content", id).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("FR-040 own schedule for a date range")
    void schedule() throws Exception {
        JsonNode days = body(getAs(me, "/api/v1/me/schedule?from=2026-03-16&to=2026-03-22").andExpect(status().isOk()));
        assertThat(days).hasSize(7);
        assertThat(days.get(0).get("date").asString()).isEqualTo("2026-03-16");
        assertThat(days.get(6).get("dayType").asString()).isEqualTo("WEEKLY_OFF");
        getAs(me, "/api/v1/me/schedule?from=2026-01-01&to=2026-12-31").andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("FR-055 own attendance day with raw events")
    void attendanceDay() throws Exception {
        getAs(me, "/api/v1/me/attendance/2026-03-16").andExpect(status().isOk())
                .andExpect(jsonPath("$.daily.date").value("2026-03-16")).andExpect(jsonPath("$.events").isArray());
    }

    @Test
    @DisplayName("FR-088 employees list and download only their own finalized payslips")
    void payslips() throws Exception {
        String payslipNo = resultOf(e1).get("payslipNo").asString();
        getAs(me, "/api/v1/me/payslips").andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].periodId").value(march)).andExpect(jsonPath("$[0].payslipNo").value(payslipNo))
                .andExpect(jsonPath("$[0].net").value(92000.0));
        byte[] pdf = getAs(me, "/api/v1/me/payslips/{p}.pdf", march).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        PdfReader reader = new PdfReader(pdf);
        assertThat(new PdfTextExtractor(reader).getTextFromPage(1)).contains(payslipNo, "One Person");
        reader.close();
        String feb = processed(2);
        getAs(me, "/api/v1/me/payslips/{p}.pdf", feb).andExpect(status().isNotFound());
        getAs(data.bearerFor("nobody", "EMPLOYEE"), "/api/v1/me/payslips").andExpect(status().isNotFound());
    }
}
