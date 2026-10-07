package com.is2c.hrm.platform.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfContentByte;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.PdfStamper;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import com.is2c.hrm.platform.export.TableData.Column;
import com.is2c.hrm.platform.export.TableData.Type;

/** Renders a {@link TableData} as CSV, Excel or print-friendly A4 PDF (FR-089, FR-095). */
@Component
public class TableExporter {

    private static final DateTimeFormatter GENERATED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'")
            .withZone(ZoneOffset.UTC);

    public byte[] export(TableData data, ExportFormat format) {
        return switch (format) {
            case CSV -> csv(data);
            case XLSX -> xlsx(data);
            case PDF -> pdf(data);
        };
    }

    public static String generatedLine(TableData data) {
        return "Generated " + GENERATED.format(data.generatedAt()) + " by " + data.generatedBy();
    }

    /** Amount display: thousands separators, 2 decimals, HALF_UP. */
    public static String amount(BigDecimal value) {
        DecimalFormat f = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.ROOT));
        f.setRoundingMode(RoundingMode.HALF_UP);
        return f.format(value);
    }

    // ---------------------------------------------------------------- CSV

    private byte[] csv(TableData data) {
        StringBuilder sb = new StringBuilder("﻿");
        appendCsv(sb, data.columns().stream().map(Column::header).map(h -> (Object) h).toList(), null);
        data.rows().forEach(r -> appendCsv(sb, r, data.columns()));
        if (data.footer() != null) {
            appendCsv(sb, data.footer(), data.columns());
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendCsv(StringBuilder sb, List<Object> cells, List<Column> columns) {
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            Object v = cells.get(i);
            String s;
            if (v == null) {
                s = "";
            } else if (v instanceof BigDecimal d) {
                s = d.setScale(columns != null && columns.get(i).type() == Type.AMOUNT ? 2 : Math.max(d.scale(), 0),
                        RoundingMode.HALF_UP).toPlainString();
            } else if (v instanceof Number) {
                s = v.toString();
            } else {
                s = v.toString();
                // neutralise spreadsheet formula injection in text cells
                if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) {
                    s = "'" + s;
                }
            }
            if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
                s = '"' + s.replace("\"", "\"\"") + '"';
            }
            sb.append(s);
        }
        sb.append("\r\n");
    }

    // ---------------------------------------------------------------- Excel

    private byte[] xlsx(TableData data) {
        try (SXSSFWorkbook wb = new SXSSFWorkbook(200); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(safeSheetName(data.title()));
            org.apache.poi.ss.usermodel.Font bold = wb.createFont();
            bold.setBold(true);
            CellStyle boldStyle = wb.createCellStyle();
            boldStyle.setFont(bold);
            CellStyle money = wb.createCellStyle();
            money.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));
            CellStyle boldMoney = wb.createCellStyle();
            boldMoney.cloneStyleFrom(money);
            boldMoney.setFont(bold);

            int r = 0;
            cell(sheet.createRow(r++), 0, data.title(), boldStyle, null);
            for (String line : data.headerLines()) {
                cell(sheet.createRow(r++), 0, line, null, null);
            }
            cell(sheet.createRow(r++), 0, generatedLine(data), null, null);
            r++;
            Row header = sheet.createRow(r++);
            for (int c = 0; c < data.columns().size(); c++) {
                cell(header, c, data.columns().get(c).header(), boldStyle, null);
            }
            for (List<Object> row : data.rows()) {
                Row x = sheet.createRow(r++);
                for (int c = 0; c < row.size(); c++) {
                    cell(x, c, row.get(c), null, money);
                }
            }
            if (data.footer() != null) {
                Row x = sheet.createRow(r);
                for (int c = 0; c < data.footer().size(); c++) {
                    cell(x, c, data.footer().get(c), boldStyle, boldMoney);
                }
            }
            for (int c = 0; c < data.columns().size(); c++) {
                sheet.setColumnWidth(c, (data.columns().get(c).type() == Type.TEXT ? 22 : 16) * 256);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void cell(Row row, int col, Object v, CellStyle textStyle, CellStyle moneyStyle) {
        if (v == null) {
            return;
        }
        Cell cell = row.createCell(col);
        if (v instanceof BigDecimal d) {
            cell.setCellValue(d.doubleValue());
            if (moneyStyle != null) {
                cell.setCellStyle(moneyStyle);
            }
        } else if (v instanceof Number n) {
            cell.setCellValue(n.doubleValue());
        } else {
            cell.setCellValue(v.toString());
            if (textStyle != null) {
                cell.setCellStyle(textStyle);
            }
        }
    }

    private static String safeSheetName(String title) {
        String s = title.replaceAll("[\\\\/?*\\[\\]:]", " ");
        return s.length() > 31 ? s.substring(0, 31) : s;
    }

    // ---------------------------------------------------------------- PDF

    private byte[] pdf(TableData data) {
        Rectangle size = data.landscape() ? PageSize.A4.rotate() : PageSize.A4;
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        Document doc = new Document(size, 28, 28, 28, 36);
        PdfWriter.getInstance(doc, body);
        doc.open();
        Font titleFont = new Font(Font.HELVETICA, 14, Font.BOLD);
        Font small = new Font(Font.HELVETICA, 8);
        Font cellFont = new Font(Font.HELVETICA, 8);
        Font headFont = new Font(Font.HELVETICA, 8, Font.BOLD);
        doc.add(new Paragraph(data.title(), titleFont));
        for (String line : data.headerLines()) {
            doc.add(new Paragraph(line, small));
        }
        doc.add(new Paragraph(generatedLine(data), small));
        doc.add(new Paragraph(" ", small));

        PdfPTable table = new PdfPTable(data.columns().size());
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        for (Column c : data.columns()) {
            table.addCell(pdfCell(c.header(), headFont, c.type() != Type.TEXT, true));
        }
        for (List<Object> row : data.rows()) {
            addPdfRow(table, row, data.columns(), cellFont);
        }
        if (data.footer() != null) {
            addPdfRow(table, data.footer(), data.columns(), headFont);
        }
        doc.add(table);
        doc.close();
        return stampPageNumbers(body.toByteArray());
    }

    private static void addPdfRow(PdfPTable table, List<Object> row, List<Column> columns, Font font) {
        for (int c = 0; c < columns.size(); c++) {
            Object v = c < row.size() ? row.get(c) : null;
            String s = v == null ? "" : v instanceof BigDecimal d ? amount(d) : v.toString();
            table.addCell(pdfCell(s, font, columns.get(c).type() != Type.TEXT, false));
        }
    }

    private static PdfPCell pdfCell(String text, Font font, boolean right, boolean header) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(right ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
        cell.setPadding(3);
        if (header) {
            cell.setGrayFill(0.9f);
        }
        return cell;
    }

    /** Second pass: "Page n of N" footer on every page. */
    static byte[] stampPageNumbers(byte[] pdf) {
        try {
            PdfReader reader = new PdfReader(pdf);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfStamper stamper = new PdfStamper(reader, out);
            int total = reader.getNumberOfPages();
            BaseFont font = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, false);
            for (int i = 1; i <= total; i++) {
                Rectangle page = reader.getPageSizeWithRotation(i);
                PdfContentByte over = stamper.getOverContent(i);
                over.beginText();
                over.setFontAndSize(font, 8);
                over.showTextAligned(Element.ALIGN_RIGHT, "Page " + i + " of " + total, page.getRight() - 28, 18, 0);
                over.endText();
            }
            stamper.close();
            reader.close();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
