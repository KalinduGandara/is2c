package com.is2c.biostarsync;

import java.util.HashMap;
import java.util.Map;

/** Converts BioStar records into HRM request bodies. */
final class Mapping {

    private Mapping() {
    }

    /**
     * New HRM employee for a BioStar user. Employee number and badge are the BioStar user id, which is what HRM
     * matches attendance events on; placement and join date come from the configured defaults.
     */
    static Map<String, Object> employee(BiostarClient.User u, Settings s) {
        String[] name = splitName(u.name(), u.id());
        Map<String, Object> e = new HashMap<>();
        e.put("companyId", s.companyId);
        e.put("branchId", s.branchId);
        e.put("departmentId", s.groupDepartments.get(u.groupId()));
        e.put("designationId", s.designationId);
        e.put("categoryId", s.categoryId);
        e.put("employeeNumber", u.id());
        e.put("badgeId", u.id());
        e.put("firstName", name[0]);
        e.put("lastName", name[1]);
        e.put("joinDate", s.joinDate.toString());
        return e;
    }

    /**
     * BioStar keeps one name field. The last word becomes the last name; a single word is used for both names
     * (HRM requires both); an empty name becomes "BioStar user &lt;id&gt;".
     */
    static String[] splitName(String name, String userId) {
        String n = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (n.isEmpty()) {
            return new String[] { "BioStar user", userId };
        }
        int space = n.lastIndexOf(' ');
        return space < 0 ? new String[] { n, n } : new String[] { n.substring(0, space), n.substring(space + 1) };
    }

    /**
     * HRM generic JSON event. The external id is the unique "deviceId:index"; BioStar's datetime is UTC ("…Z") and is
     * passed through unchanged.
     */
    static Map<String, Object> event(BiostarClient.Event e, Settings s) {
        Map<String, Object> m = new HashMap<>();
        m.put("externalId", e.key());
        m.put("deviceId", e.deviceId());
        m.put("employeeIdentifier", e.userId());
        m.put("timestamp", e.datetime());
        m.put("direction", direction(e.tnaKey(), s));
        return m;
    }

    /** IN/OUT from the T&A key pressed on the device; UNKNOWN lets HRM pair punches chronologically. */
    static String direction(String tnaKey, Settings s) {
        if (tnaKey != null && tnaKey.equals(s.tnaKeyIn.trim())) {
            return "IN";
        }
        if (tnaKey != null && tnaKey.equals(s.tnaKeyOut.trim())) {
            return "OUT";
        }
        return "UNKNOWN";
    }
}
