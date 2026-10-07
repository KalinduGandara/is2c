package com.is2c.hrm.org.api;

import java.util.Set;

/** Implemented by payroll: the statutory pack codes a company may choose (FR-010, FR-081). */
public interface StatutoryPackCatalog {

    Set<String> codes();
}
