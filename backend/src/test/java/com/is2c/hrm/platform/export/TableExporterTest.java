package com.is2c.hrm.platform.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;

import com.is2c.hrm.platform.export.TableData.Column;

class TableExporterTest {

    private final TableExporter exporter = new TableExporter();

    private TableData sample(int rows) {
        List<List<Object>> data = new ArrayList<>();
        data.add(List.of("E001", "Perera, Nimal", new BigDecimal("100000.00")));
        data.add(List.of("E002", "=HYPERLINK(\"x\")", new BigDecimal("-50.5")));
        for (int i = 3; i <= rows; i++) {
            data.add(List.of(String.format("E%03d", i), "Name " + i, new BigDecimal("1.00")));
        }
        return new TableData("Payroll register", List.of("ACME Ltd", "Period: March 2026"),
                List.of(Column.text("No"), Column.text("Name"), Column.amount("Net")), data,
                List.of("", "Total", new BigDecimal("99949.50")), true, "root", Instant.parse("2026-04-05T06:00:00Z"));
    }

    @Test
    @DisplayName("FR-089 CSV is UTF-8 with BOM, RFC 4180 quoted, plain numbers and formula-injection safe")
    void csv() {
        String csv = new String(exporter.export(sample(2), ExportFormat.CSV), StandardCharsets.UTF_8);
        assertThat(csv).startsWith("﻿");
        List<String> lines = csv.substring(1).lines().toList();
        assertThat(lines.get(0)).isEqualTo("No,Name,Net");
        assertThat(lines.get(1)).isEqualTo("E001,\"Perera, Nimal\",100000.00");
        assertThat(lines.get(2)).isEqualTo("E002,\"'=HYPERLINK(\"\"x\"\")\",-50.50");
        assertThat(lines.get(3)).isEqualTo(",Total,99949.50");
    }

    @Test
    @DisplayName("FR-089 Excel has a title, header row, numeric amount cells and a totals row")
    void xlsx() throws Exception {
        byte[] bytes = exporter.export(sample(2), ExportFormat.XLSX);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet s = wb.getSheetAt(0);
            assertThat(s.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Payroll register");
            int header = s.getFirstRowNum();
            while (!"No".equals(s.getRow(header) == null || s.getRow(header).getCell(0) == null ? null
                    : s.getRow(header).getCell(0).getStringCellValue())) {
                header++;
            }
            assertThat(s.getRow(header).getCell(2).getStringCellValue()).isEqualTo("Net");
            var amount = s.getRow(header + 1).getCell(2);
            assertThat(amount.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(amount.getNumericCellValue()).isEqualTo(100000.0);
            assertThat(s.getRow(header + 2).getCell(1).getStringCellValue()).isEqualTo("=HYPERLINK(\"x\")");
            assertThat(s.getRow(header + 3).getCell(2).getNumericCellValue()).isEqualTo(99949.5);
        }
    }

    @Test
    @DisplayName("FR-095 PDF is A4 landscape with title, filters, generated-by line and page numbers")
    void pdf() throws Exception {
        byte[] bytes = exporter.export(sample(120), ExportFormat.PDF);
        PdfReader reader = new PdfReader(bytes);
        assertThat(reader.getNumberOfPages()).isGreaterThan(1);
        var size = reader.getPageSizeWithRotation(1);
        assertThat(size.getWidth()).isGreaterThan(size.getHeight());
        PdfTextExtractor text = new PdfTextExtractor(reader);
        String first = text.getTextFromPage(1);
        assertThat(first).contains("Payroll register", "ACME Ltd", "Period: March 2026", "root", "100,000.00",
                "Page 1 of " + reader.getNumberOfPages());
        String last = text.getTextFromPage(reader.getNumberOfPages());
        assertThat(last).contains("99,949.50", "Page " + reader.getNumberOfPages());
        reader.close();
    }
}
