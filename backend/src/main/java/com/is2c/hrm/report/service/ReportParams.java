package com.is2c.hrm.report.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.is2c.hrm.common.error.ValidationException;
import com.is2c.hrm.platform.export.TableData;
import com.is2c.hrm.platform.export.TableData.Column;
import com.is2c.hrm.security.api.CurrentUser;

/** Typed access to raw query parameters (invalid values are 422) and a factory for the report's table. */
public final class ReportParams {

    private final ReportDefinition definition;
    private final Map<String, String> raw;
    private final Clock clock;

    ReportParams(ReportDefinition definition, Map<String, String> raw, Clock clock) {
        this.definition = definition;
        this.raw = raw;
        this.clock = clock;
        for (ReportDefinition.Param p : definition.params()) {
            if (p.required() && blank(raw.get(p.name()))) {
                throw new ValidationException(p.name(), p.label() + " is required");
            }
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    public String text(String name) {
        String v = raw.get(name);
        return blank(v) ? null : v.trim();
    }

    public UUID uuid(String name) {
        String v = text(name);
        try {
            return v == null ? null : UUID.fromString(v);
        } catch (IllegalArgumentException e) {
            throw new ValidationException(name, "must be a valid id");
        }
    }

    public LocalDate date(String name) {
        String v = text(name);
        try {
            return v == null ? null : LocalDate.parse(v);
        } catch (DateTimeParseException e) {
            throw new ValidationException(name, "must be a date (YYYY-MM-DD)");
        }
    }

    public Integer integer(String name) {
        String v = text(name);
        try {
            return v == null ? null : Integer.valueOf(v);
        } catch (NumberFormatException e) {
            throw new ValidationException(name, "must be a number");
        }
    }

    /** Validates an optional from/to pair. */
    public void requireOrder(LocalDate from, LocalDate to) {
        if (from != null && to != null && to.isBefore(from)) {
            throw new ValidationException("to", "must not be before from");
        }
    }

    public <E extends Enum<E>> E enumValue(String name, Class<E> type) {
        String v = text(name);
        try {
            return v == null ? null : Enum.valueOf(type, v);
        } catch (IllegalArgumentException e) {
            throw new ValidationException(name, "is not a valid value");
        }
    }

    public TableData table(List<String> headerLines, List<Column> columns, List<List<Object>> rows, List<Object> footer) {
        return new TableData(definition.title(), headerLines, columns, rows, footer, definition.landscape(),
                CurrentUser.get().username(), clock.instant());
    }
}
