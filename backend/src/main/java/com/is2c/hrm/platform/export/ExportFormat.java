package com.is2c.hrm.platform.export;

import java.util.Locale;

import com.is2c.hrm.common.error.ValidationException;

/** Output formats for tabular exports (FR-089, FR-095). */
public enum ExportFormat {
    PDF("application/pdf", "pdf"),
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx"),
    CSV("text/csv;charset=UTF-8", "csv");

    private final String contentType;
    private final String extension;

    ExportFormat(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    /** Parses a {@code format} request parameter; unknown values are a 422. */
    public static ExportFormat parse(String value) {
        if (value != null) {
            for (ExportFormat f : values()) {
                if (f.extension.equals(value.toLowerCase(Locale.ROOT))) {
                    return f;
                }
            }
        }
        throw new ValidationException("format", "must be one of pdf, xlsx, csv");
    }
}
