package com.is2c.biostarsync;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** BioStar 2 REST API (session header {@code bs-session-id}); see the server's /swagger/index.html. */
class BiostarClient {

    record User(String id, String name, String groupId, String groupName, boolean disabled) {
    }

    /**
     * One device event; {@code userId}, {@code groupId} and {@code tnaKey} may be null. {@code key} is
     * "deviceId:index" (the device's own event sequence number): BioStar's {@code id} is NOT unique, it restarts in
     * every monthly event table.
     */
    record Event(String key, String datetime, String userId, String groupId, String deviceId, String tnaKey) {
    }

    /**
     * Event types that mean "a user was recognised" (VERIFY_*_SUCCESS, IDENTIFY_*_SUCCESS, DUAL_AUTH_SUCCESS,
     * BYPASS_SUCCESS_*) from {@code GET /api/event_types}. Everything else (failed scans, door and device events) is
     * not attendance.
     */
    static final List<String> SUCCESS_EVENT_TYPES = List.of(
            "4096", "4097", "4098", "4099", "4100", "4101", "4102", "4103", "4104", "4105", "4106", "4107",
            "4112", "4113", "4114", "4115", "4118", "4119", "4120", "4121", "4122", "4123", "4128", "4129",
            "4133", "4134", "4135", "4136", "4137", "4138", "4139", "4140",
            "4864", "4865", "4866", "4867", "4868", "4869", "4870", "4871", "4872",
            "5632", "6912", "6913", "6914", "6915");

    // events/search condition operators
    private static final int IN = 2;
    private static final int BETWEEN = 3;

    private static final int USER_PAGE = 500;
    private static final DateTimeFormatter BIOSTAR_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC);

    private final Http http;
    private String session;

    BiostarClient(String baseUrl, boolean trustAllCerts, JsonMapper json) {
        this.http = new Http(baseUrl, trustAllCerts, json);
    }

    void login(String loginId, String password) {
        var r = http.send(http.request("/api/login").header("Content-Type", "application/json")
                .POST(http.body(Map.of("User", Map.of("login_id", loginId, "password", password)))).build());
        session = r.headers().firstValue("bs-session-id")
                .orElseThrow(() -> new Http.CallException(r.statusCode(), "BioStar login returned no bs-session-id"));
    }

    List<User> users() {
        List<User> out = new ArrayList<>();
        for (int offset = 0;; offset += USER_PAGE) {
            JsonNode c = http.sendJson(http.request("/api/users?limit=" + USER_PAGE + "&offset=" + offset
                    + "&order_by=user_id:false").header("bs-session-id", session).GET().build()).path("UserCollection");
            JsonNode rows = c.path("rows");
            rows.forEach(r -> out.add(new User(r.path("user_id").asString(), r.path("name").asString("").trim(),
                    text(r.path("user_group_id").path("id")), r.path("user_group_id").path("name").asString(""),
                    "true".equals(r.path("disabled").asString()))));
            if (rows.size() < USER_PAGE || out.size() >= c.path("total").asInt(Integer.MAX_VALUE)) {
                return out;
            }
        }
    }

    /**
     * One page of recognised-user events with {@code datetime} in [from, to], oldest first. Offset paging over a fixed
     * time range returns every event exactly once (checked against a single unpaged call on a live server).
     */
    List<Event> events(Instant from, Instant to, int offset, int limit) {
        List<Map<String, Object>> conditions = List.of(
                Map.of("column", "datetime", "operator", BETWEEN,
                        "values", List.of(BIOSTAR_TIME.format(from), BIOSTAR_TIME.format(to))),
                Map.of("column", "event_type_id", "operator", IN, "values", SUCCESS_EVENT_TYPES));
        Map<String, Object> query = Map.of("limit", limit, "offset", offset, "conditions", conditions,
                "orders", List.of(Map.of("column", "datetime", "descending", false)));
        JsonNode rows = http.sendJson(http.request("/api/events/search").header("bs-session-id", session)
                .header("Content-Type", "application/json").POST(http.body(Map.of("Query", query))).build())
                .path("EventCollection").path("rows");
        List<Event> out = new ArrayList<>();
        rows.forEach(r -> {
            String device = text(r.path("device_id").path("id"));
            out.add(new Event(device + ":" + r.path("index").asString(), r.path("datetime").asString(),
                    text(r.path("user_id").path("user_id")), text(r.path("user_group_id").path("id")), device,
                    text(r.path("tna_key"))));
        });
        return out;
    }

    private static String text(JsonNode n) {
        String s = n.isMissingNode() || n.isNull() ? null : n.asString();
        return s == null || s.isBlank() ? null : s;
    }
}
