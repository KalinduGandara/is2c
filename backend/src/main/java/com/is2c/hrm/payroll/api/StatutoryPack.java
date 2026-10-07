package com.is2c.hrm.payroll.api;

/**
 * A country's statutory payroll setup (FR-081): creates editable components and slab tables for a company.
 * Packs only seed data — calculation code never contains statutory values.
 */
public interface StatutoryPack {

    String code();

    String description();

    void install(PackInstaller installer);
}
