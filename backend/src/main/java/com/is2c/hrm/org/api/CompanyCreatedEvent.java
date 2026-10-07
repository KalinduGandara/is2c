package com.is2c.hrm.org.api;

import java.util.UUID;

/**
 * Published synchronously inside the company-creation transaction so other modules can seed their per-company
 * defaults (leave types, workflows, statutory components) without org depending on them (FR-010).
 */
public record CompanyCreatedEvent(UUID companyId, String statutoryPack) {
}
