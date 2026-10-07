package com.is2c.hrm.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

import com.is2c.testsupport.ApiTestBase;
import com.is2c.testsupport.OrgFixtures.Structure;

import tools.jackson.databind.JsonNode;

class EmployeeExtrasApiTest extends ApiTestBase {

    static final byte[] PDF = "%PDF-1.4\n%test\n".getBytes(StandardCharsets.US_ASCII);
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};

    String hr;
    String root;
    Structure acme;

    @BeforeEach
    void setUp() throws Exception {
        root = data.bearerFor("root", "SUPER_ADMIN");
        acme = org.structure(root, "ACME");
        hr = data.bearerFor("hr", "HR_MANAGER");
    }

    private void field(String key, String type, boolean required, List<String> options) throws Exception {
        Map<String, Object> f = new HashMap<>(Map.of("key", key, "label", key.toUpperCase(), "type", type,
                "required", required));
        if (options != null) {
            f.put("options", options);
        }
        postAs(hr, "/api/v1/companies/{c}/custom-fields", f, acme.companyId()).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("FR-019 custom field definitions validate key format, uniqueness and SELECT options")
    void definitions() throws Exception {
        field("blood_group", "SELECT", false, List.of("A+", "B+", "O+"));
        postAs(hr, "/api/v1/companies/{c}/custom-fields", Map.of("key", "blood_group", "label", "x", "type", "TEXT"),
                acme.companyId()).andExpect(status().isUnprocessableContent());
        postAs(hr, "/api/v1/companies/{c}/custom-fields", Map.of("key", "Bad-Key", "label", "x", "type", "TEXT"),
                acme.companyId()).andExpect(jsonPath("$.errors[0].field").value("key"));
        postAs(hr, "/api/v1/companies/{c}/custom-fields", Map.of("key", "shirt", "label", "x", "type", "SELECT"),
                acme.companyId()).andExpect(jsonPath("$.errors[0].field").value("options"));
        getAs(hr, "/api/v1/companies/{c}/custom-fields", acme.companyId()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].options", hasSize(3)));
        postAs(data.bearerFor("sup", "SUPERVISOR"), "/api/v1/companies/{c}/custom-fields",
                Map.of("key", "x", "label", "x", "type", "TEXT"), acme.companyId()).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("FR-019 employee custom values are validated by type, options and required flag")
    void valueValidation() throws Exception {
        field("tshirt", "SELECT", false, List.of("S", "M", "L"));
        field("dependants", "NUMBER", false, null);
        field("medical_due", "DATE", false, null);
        field("smoker", "BOOLEAN", false, null);
        field("epf_no", "TEXT", true, null);
        Map<String, Object> body = org.employeeBody(acme, "A", "B");
        body.put("customFields", Map.of("tshirt", "XL", "dependants", "two", "medical_due", "31/12/2026",
                "smoker", "maybe", "unknown", "x"));
        postAs(hr, "/api/v1/employees", body).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("customFields.tshirt",
                        "customFields.dependants", "customFields.medical_due", "customFields.smoker",
                        "customFields.epf_no", "customFields.unknown")));
        body.put("customFields", Map.of("tshirt", "M", "dependants", "2.50", "medical_due", "2026-12-31",
                "smoker", "FALSE", "epf_no", "E123"));
        JsonNode e = org.post(hr, "/api/v1/employees", body);
        assertThat(e.get("customFields").get("dependants").asString()).isEqualTo("2.5");
        assertThat(e.get("customFields").get("smoker").asString()).isEqualTo("false");
        assertThat(e.get("customFields").get("epf_no").asString()).isEqualTo("E123");
    }

    @Test
    @DisplayName("FR-019 deactivated fields are hidden but their values are kept")
    void deactivate() throws Exception {
        field("locker", "TEXT", false, null);
        Map<String, Object> body = org.employeeBody(acme, "A", "B");
        body.put("customFields", Map.of("locker", "L-12"));
        JsonNode e = org.post(hr, "/api/v1/employees", body);
        String id = e.get("id").asString();
        JsonNode def = body(getAs(hr, "/api/v1/companies/{c}/custom-fields", acme.companyId())).get(0);
        putAs(hr, "/api/v1/custom-fields/{id}", Map.of("label", "Locker", "active", false, "version",
                def.get("version").asLong()), def.get("id").asString()).andExpect(status().isOk());
        getAs(hr, "/api/v1/employees/{id}", id).andExpect(jsonPath("$.customFields.locker").doesNotExist());
        putAs(hr, "/api/v1/custom-fields/{id}", Map.of("label", "Locker", "active", true, "version",
                def.get("version").asLong() + 1), def.get("id").asString()).andExpect(status().isOk());
        getAs(hr, "/api/v1/employees/{id}", id).andExpect(jsonPath("$.customFields.locker").value("L-12"));
    }

    private ResultActions upload(UUID employee, String docType, String fileName, byte[] bytes) throws Exception {
        return mvc.perform(multipart("/api/v1/employees/{id}/documents", employee)
                .file(new MockMultipartFile("file", fileName, "application/octet-stream", bytes))
                .param("docType", docType).param("title", "My " + docType).param("expiryDate", "2030-01-01")
                .header("Authorization", hr));
    }

    @Test
    @DisplayName("FR-020 upload, list, download (as attachment) and soft-delete documents")
    void documents() throws Exception {
        UUID emp = org.employee(hr, acme, "A", "B");
        JsonNode doc = body(upload(emp, "Contract", "../../etc/contract.pdf", PDF).andExpect(status().isCreated()));
        assertThat(doc.get("contentType").asString()).isEqualTo("application/pdf");
        assertThat(doc.get("fileName").asString()).isEqualTo("contract.pdf");
        upload(emp, "NIC", "nic.png", PNG).andExpect(status().isCreated());

        getAs(hr, "/api/v1/employees/{id}/documents", emp).andExpect(jsonPath("$", hasSize(2)));
        getAs(hr, "/api/v1/employees/{id}/documents/{d}/content", emp, doc.get("id").asString())
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"contract.pdf\""))
                .andExpect(content().contentType("application/pdf"))
                .andExpect(content().bytes(PDF));

        deleteAs(hr, "/api/v1/employees/{id}/documents/{d}", emp, doc.get("id").asString()).andExpect(status().isNoContent());
        getAs(hr, "/api/v1/employees/{id}/documents", emp).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("FR-020 disallowed content types (sniffed, not by extension) and oversize files are rejected")
    void documentValidation() throws Exception {
        UUID emp = org.employee(hr, acme, "A", "B");
        upload(emp, "Contract", "evil.pdf", "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors[0].field").value("file"));
        upload(emp, "Passport", "x.pdf", PDF).andExpect(jsonPath("$.errors[0].field").value("docType"));
        byte[] big = new byte[(int) (10L * 1024 * 1024) + 1];
        System.arraycopy(PDF, 0, big, 0, PDF.length);
        upload(emp, "Contract", "big.pdf", big).andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("FR-020 DOCX files are recognised by structure; plain ZIPs are rejected")
    void docx() throws Exception {
        UUID emp = org.employee(hr, acme, "A", "B");
        upload(emp, "Certificate", "c.docx", zip("[Content_Types].xml", "word/document.xml"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        upload(emp, "Certificate", "c.docx", zip("readme.txt")).andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("FR-020 documents respect data scope and permissions")
    void documentAccess() throws Exception {
        UUID emp = org.employee(hr, acme, "A", "B");
        upload(emp, "Contract", "c.pdf", PDF).andExpect(status().isCreated());
        getAs(data.bearerFor("e", "EMPLOYEE"), "/api/v1/employees/{id}/documents", emp).andExpect(status().isForbidden());
    }

    private static byte[] zip(String... entries) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(out)) {
            for (String e : entries) {
                z.putNextEntry(new ZipEntry(e));
                z.write("x".getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
        }
        return out.toByteArray();
    }
}
