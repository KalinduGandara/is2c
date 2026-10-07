package com.is2c.hrm.report.service;

import java.util.List;

/** Contributes reports to the catalog. */
public interface ReportProvider {

    List<ReportDefinition> reports();
}
