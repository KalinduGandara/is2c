package com.is2c.hrm.report.service;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

import com.is2c.hrm.platform.export.TableData;

/**
 * One report of the catalog (FR-095). {@code requiresAny}: extra permissions beyond REPORT:VIEW (any of them);
 * the builder applies data scope through the owning module's services.
 */
public record ReportDefinition(String key, String title, List<Param> params, Set<String> requiresAny,
        boolean landscape, Function<ReportParams, TableData> builder) {

    /**
     * A report parameter. {@code type} tells the UI which input to render: company, date, year, branch, department,
     * designation, category, employeeStatus, leaveType, roster, payrollPeriod, text.
     */
    public record Param(String name, String label, String type, boolean required) {
    }

    public static Param param(String name, String label, String type, boolean required) {
        return new Param(name, label, type, required);
    }
}
