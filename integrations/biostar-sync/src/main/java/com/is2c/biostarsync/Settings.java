package com.is2c.biostarsync;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/** Everything the user configures in the UI. Stored as JSON by {@link SettingsStore}. */
public class Settings {

    // BioStar 2
    public String biostarUrl = "";
    public String biostarLoginId = "";
    public String biostarPassword = "";
    /** Accept the BioStar server's certificate without validation (BioStar ships a self-signed one). */
    public boolean biostarTrustAllCerts;

    // Premium HRM
    public String hrmUrl = "";
    public String hrmUsername = "";
    public String hrmPassword = "";
    /** API key of the HRM attendance source (Attendance → Sources); it must belong to {@link #companyId}. */
    public String hrmApiKey = "";

    // Employee defaults for new employees (BioStar has no such data)
    public String companyId = "";
    public String branchId = "";
    public String designationId = "";
    public String categoryId = "";
    public LocalDate joinDate;
    /** BioStar user group id → HRM department id. Groups not listed here (or mapped to "") are not synced. */
    public Map<String, String> groupDepartments = new LinkedHashMap<>();

    // tna_key (the T&A function key pressed on the device) → direction; other keys become UNKNOWN
    public String tnaKeyIn = "1";
    public String tnaKeyOut = "2";

    // Automatic sync
    public boolean scheduleEnabled;
    public int scheduleIntervalMinutes = 15;
    /** First day the automatic sync imports when it has not run yet. */
    public LocalDate scheduleStartDate;
    /**
     * Each automatic run re-reads this many hours before the latest punch already read, to catch punches that
     * offline devices upload late (they keep their original time). HRM skips the ones it already has.
     */
    public int scheduleLookbackHours = 24;

    boolean biostarConfigured() {
        return !biostarUrl.isBlank() && !biostarLoginId.isBlank() && !biostarPassword.isBlank();
    }

    boolean hrmConfigured() {
        return !hrmUrl.isBlank() && !hrmUsername.isBlank() && !hrmPassword.isBlank();
    }

    /** Missing items that prevent a sync, empty when ready. */
    String missingForSync() {
        StringBuilder m = new StringBuilder();
        if (!biostarConfigured()) {
            m.append("BioStar connection, ");
        }
        if (!hrmConfigured() || hrmApiKey.isBlank()) {
            m.append("HRM connection and API key, ");
        }
        if (companyId.isBlank() || branchId.isBlank() || designationId.isBlank() || categoryId.isBlank()
                || joinDate == null) {
            m.append("employee defaults, ");
        }
        if (groupDepartments.values().stream().allMatch(String::isBlank)) {
            m.append("at least one user group mapped to a department, ");
        }
        return m.isEmpty() ? "" : m.substring(0, m.length() - 2);
    }
}
