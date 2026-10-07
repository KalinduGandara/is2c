package com.is2c.hrm.payroll;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

import tools.jackson.databind.JsonNode;

class PayslipExportApiTest extends FinalizedPayrollTestBase {

    private byte[] bytes(String bearer, String url, Object... vars) throws Exception {
        return getAs(bearer, url, vars).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }

    private static List<String> pages(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        PdfTextExtractor text = new PdfTextExtractor(reader);
        List<String> pages = new java.util.ArrayList<>();
        for (int i = 1; i <= reader.getNumberOfPages(); i++) {
            pages.add(text.getTextFromPage(i));
        }
        reader.close();
        return pages;
    }

    @Test
    @DisplayName("FR-088 individual payslip PDF shows company, period, employee, earnings, deductions, employer contributions and attendance")
    void payslip() throws Exception {
        JsonNode r = resultOf(e1);
        String company = body(getAs(admin, "/api/v1/companies/{c}", acme.companyId())).get("legalName").asString();
        getAs(hr, "/api/v1/payroll/periods/{p}/payslips/{e}.pdf", march, e1).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")));
        byte[] pdf = bytes(hr, "/api/v1/payroll/periods/{p}/payslips/{e}.pdf", march, e1);
        List<String> pages = pages(pdf);
        assertThat(pages).hasSize(1);
        assertThat(pages.get(0)).contains(company, "March 2026", r.get("payslipNo").asString(),
                r.get("employeeNumber").asString(), "One Person", r.get("departmentName").asString(),
                r.get("designationName").asString(), "Basic", "100,000.00", "EPF (employee 8%)", "8,000.00",
                "92,000.00", "EPF (employer 12%)", "12,000.00", "ETF (employer 3%)", "3,000.00", "Worked days",
                "No-pay days", "OT hours");
        // generated from the immutable snapshot: same figures every time
        assertThat(pages(bytes(hr, "/api/v1/payroll/periods/{p}/payslips/{e}.pdf", march, e1))).isEqualTo(pages);
    }

    @Test
    @DisplayName("FR-088 bulk payslips: one page per employee, filterable by department")
    void bulk() throws Exception {
        List<String> all = pages(bytes(hr, "/api/v1/payroll/periods/{p}/payslips.pdf", march));
        assertThat(all).hasSize(2);
        assertThat(all.get(0)).contains(resultOf(e1).get("payslipNo").asString());
        assertThat(all.get(1)).contains(resultOf(e2).get("payslipNo").asString(), "73,600.00");
        List<String> fin = pages(bytes(hr, "/api/v1/payroll/periods/{p}/payslips.pdf?departmentId={d}", march, finance));
        assertThat(fin).hasSize(1);
        assertThat(fin.get(0)).contains("Two Person");
    }

    @Test
    @DisplayName("FR-088 payslips only for FINALIZED periods; PAYROLL:VIEW required")
    void guards() throws Exception {
        String feb = processed(2);
        getAs(hr, "/api/v1/payroll/periods/{p}/payslips/{e}.pdf", feb, e1).andExpect(status().isConflict());
        getAs(hr, "/api/v1/payroll/periods/{p}/payslips.pdf", feb).andExpect(status().isConflict());
        String ca = data.bearerFor("ca", "COMPANY_ADMIN");
        getAs(ca, "/api/v1/payroll/periods/{p}/payslips/{e}.pdf", march, e1).andExpect(status().isForbidden());
        getAs(ca, "/api/v1/payroll/periods/{p}/register?format=csv", march).andExpect(status().isForbidden());
        getAs(hr, "/api/v1/payroll/periods/{p}/register?format=doc", march).andExpect(status().isUnprocessableContent());
        // the register is available while reviewing a draft
        getAs(po, "/api/v1/payroll/periods/{p}/register?format=csv", feb).andExpect(status().isOk());
    }

    @Test
    @DisplayName("FR-089 payroll register exports to CSV, Excel and PDF with component columns and totals")
    void register() throws Exception {
        String number = resultOf(e1).get("employeeNumber").asString();
        String csv = new String(bytes(po, "/api/v1/payroll/periods/{p}/register?format=csv", march), StandardCharsets.UTF_8);
        List<String> lines = csv.substring(1).lines().toList();
        List<String> header = List.of(lines.get(0).split(","));
        assertThat(header).contains("Employee No", "Name", "Basic salary", "EPF (employee 8%)", "Gross", "Deductions", "Net",
                "Employer contributions");
        String row = lines.stream().filter(l -> l.startsWith(number + ",")).findFirst().orElseThrow();
        List<String> cells = List.of(row.split(",", -1));
        assertThat(cells.get(header.indexOf("Basic salary"))).isEqualTo("100000.00");
        assertThat(cells.get(header.indexOf("Net"))).isEqualTo("92000.00");
        List<String> total = List.of(lines.get(lines.size() - 1).split(",", -1));
        assertThat(total.get(header.indexOf("Net"))).isEqualTo("165600.00");
        assertThat(total.get(header.indexOf("Employer contributions"))).isEqualTo("27000.00");

        getAs(po, "/api/v1/payroll/periods/{p}/register?format=xlsx", march).andExpect(header().string("Content-Disposition",
                org.hamcrest.Matchers.containsString("payroll-register-2026-03.xlsx")));
        byte[] xlsx = bytes(po, "/api/v1/payroll/periods/{p}/register?format=xlsx", march);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet s = wb.getSheetAt(0);
            Row last = s.getRow(s.getLastRowNum());
            Row head = null;
            for (Row x : s) {
                if (x.getCell(0) != null && "Employee No".equals(x.getCell(0).getStringCellValue())) {
                    head = x;
                }
            }
            int net = -1;
            for (int c = 0; c < head.getLastCellNum(); c++) {
                if ("Net".equals(head.getCell(c).getStringCellValue())) {
                    net = c;
                }
            }
            assertThat(last.getCell(net).getNumericCellValue()).isEqualTo(165600.0);
        }
        assertThat(String.join("\n", pages(bytes(po, "/api/v1/payroll/periods/{p}/register?format=pdf", march))))
                .contains("Payroll register", "March 2026", "165,600.00", "Page 1 of");
    }

    @Test
    @DisplayName("FR-089 department summary totals net pay by department (Excel and PDF)")
    void departmentSummary() throws Exception {
        String ops = resultOf(e1).get("departmentName").asString();
        String fin = resultOf(e2).get("departmentName").asString();
        byte[] xlsx = bytes(hr, "/api/v1/payroll/periods/{p}/department-summary?format=xlsx", march);
        Map<String, Double> net = new java.util.HashMap<>();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet s = wb.getSheetAt(0);
            Row head = null;
            for (Row x : s) {
                if (x.getCell(0) != null && "Department".equals(x.getCell(0).getStringCellValue())) {
                    head = x;
                    continue;
                }
                if (head != null) {
                    int c = 0;
                    while (!"Net".equals(head.getCell(c).getStringCellValue())) {
                        c++;
                    }
                    net.put(x.getCell(0) == null ? "" : x.getCell(0).getStringCellValue(), x.getCell(c).getNumericCellValue());
                }
            }
        }
        assertThat(net).containsEntry(ops, 92000.0).containsEntry(fin, 73600.0).containsEntry("Total", 165600.0);
        assertThat(String.join("\n", pages(bytes(hr, "/api/v1/payroll/periods/{p}/department-summary?format=pdf", march))))
                .contains("Department summary", ops, fin, "165,600.00");
    }

    @Test
    @DisplayName("FR-088 emailing payslips with mail disabled is rejected with a clear message")
    void emailDisabled() throws Exception {
        postAs(po, "/api/v1/payroll/periods/{p}/payslips/email", Map.of(), march)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail", org.hamcrest.Matchers.containsString("mail")));
    }

    @Test
    @DisplayName("FR-095 payroll register and department summary are available as reports (PAYROLL:VIEW)")
    void asReports() throws Exception {
        JsonNode reg = body(getAs(hr, "/api/v1/reports/payroll-register?periodId={p}", march).andExpect(status().isOk()));
        assertThat(reg.get("title").asString()).isEqualTo("Payroll register");
        assertThat(reg.get("rows")).hasSize(2);
        int net = -1;
        for (int i = 0; i < reg.get("columns").size(); i++) {
            if (reg.get("columns").get(i).get("header").asString().equals("Net")) {
                net = i;
            }
        }
        assertThat(reg.get("footer").get(net).asDouble()).isEqualTo(165600.0);
        getAs(hr, "/api/v1/reports/payroll-department-summary/export?format=pdf&periodId={p}", march).andExpect(status().isOk());
        getAs(data.bearerFor("ca", "COMPANY_ADMIN"), "/api/v1/reports/payroll-register?periodId={p}", march)
                .andExpect(status().isForbidden());
    }
}
