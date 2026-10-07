package com.is2c.hrm.org.api;

import java.util.Optional;
import java.util.UUID;

/** Implemented by the employee module so org-level scope logic can resolve employees without a dependency cycle. */
public interface EmployeePlacementLookup {

    Optional<EmployeePlacement> find(UUID employeeId);
}
