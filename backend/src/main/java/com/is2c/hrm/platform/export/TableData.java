package com.is2c.hrm.platform.export;

import java.time.Instant;
import java.util.List;

/**
 * A titled table ready for export. Cells are {@link String}, {@link java.math.BigDecimal} (amount, 2 decimals),
 * other {@link Number}s, dates or null. {@code footer} is an optional totals row.
 */
public record TableData(String title, List<String> headerLines, List<Column> columns, List<List<Object>> rows,
        List<Object> footer, boolean landscape, String generatedBy, Instant generatedAt) {

    public enum Type { TEXT, AMOUNT, NUMBER }

    public record Column(String header, Type type) {
        public static Column text(String header) {
            return new Column(header, Type.TEXT);
        }

        public static Column amount(String header) {
            return new Column(header, Type.AMOUNT);
        }

        public static Column number(String header) {
            return new Column(header, Type.NUMBER);
        }
    }
}
