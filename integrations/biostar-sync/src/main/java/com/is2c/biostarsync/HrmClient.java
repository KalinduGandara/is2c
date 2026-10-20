package com.is2c.biostarsync;

import java.net.http.HttpRequest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Premium HRM REST API. User calls sign in as the configured service account (JWT); attendance events go to the
 * device ingestion endpoint with the attendance source's API key.
 */
class HrmClient {

    record Option(String id, String name) {
    }

    record Company(String id, String name, String timeZone) {
    }

    /** Result of creating one employee. */
    enum Created { CREATED, EXISTS }

    record IngestResult(int accepted, int duplicates, int unmatched, int rejected, List<String> errors) {
    }

    private static final int PAGE = 200;

    private final Http http;
    private final String username;
    private final String password;
    private final String apiKey;
    private String token;

    HrmClient(String baseUrl, String username, String password, String apiKey, JsonMapper json) {
        this.http = new Http(baseUrl, false, json);
        this.username = username;
        this.password = password;
        this.apiKey = apiKey;
    }

    /** Signs in and checks the account is usable (a pending password change blocks every other call). */
    void login() {
        JsonNode t = http.sendJson(http.request("/api/v1/auth/login").header("Content-Type", "application/json")
                .POST(http.body(Map.of("username", username, "password", password))).build());
        token = t.path("accessToken").asString();
        JsonNode me = http.sendJson(authed("/api/v1/auth/me").GET().build());
        if (me.path("mustChangePassword").asBoolean()) {
            throw new Http.CallException(403, "the HRM account '" + username
                    + "' must change its password: sign in to HRM once with it and set a new one");
        }
    }

    List<Company> companies() {
        List<Company> out = new ArrayList<>();
        get("/api/v1/companies").forEach(c -> out.add(new Company(c.path("id").asString(),
                c.path("legalName").asString(), c.path("timeZone").asString())));
        return out;
    }

    List<Option> branches(String companyId) {
        return options("/api/v1/companies/" + companyId + "/branches");
    }

    List<Option> departments(String companyId) {
        return options("/api/v1/companies/" + companyId + "/departments");
    }

    List<Option> designations(String companyId) {
        return options("/api/v1/companies/" + companyId + "/designations");
    }

    List<Option> categories(String companyId) {
        return options("/api/v1/companies/" + companyId + "/employment-categories");
    }

    /** Employee numbers (lower case) of every employee of the company, whatever their status. */
    Set<String> employeeNumbers(String companyId) {
        Set<String> out = new HashSet<>();
        for (int page = 0;; page++) {
            JsonNode r = get("/api/v1/employees?companyId=" + companyId + "&size=" + PAGE + "&page=" + page);
            r.path("content").forEach(e -> out.add(e.path("employeeNumber").asString().toLowerCase(Locale.ROOT)));
            if (page + 1 >= r.path("totalPages").asInt()) {
                return out;
            }
        }
    }

    /** Creates an employee; EXISTS when HRM already has that employee number. */
    Created createEmployee(Map<String, Object> employee) {
        try {
            withLogin(() -> http.send(authed("/api/v1/employees").header("Content-Type", "application/json")
                    .POST(http.body(employee)).build()));
            return Created.CREATED;
        } catch (Http.CallException e) {
            if (e.getMessage().contains("employee number already exists")) {
                return Created.EXISTS;
            }
            throw e;
        }
    }

    IngestResult ingest(List<Map<String, Object>> events) {
        JsonNode r = http.sendJson(http.request("/api/v1/integrations/attendance/events")
                .header("Content-Type", "application/json").header("X-Api-Key", apiKey)
                .POST(http.body(Map.of("events", events))).build());
        List<String> errors = new ArrayList<>();
        r.path("errors").forEach(e -> errors.add("event row " + e.path("row").asString() + ": "
                + e.path("reason").asString()));
        return new IngestResult(r.path("accepted").asInt(), r.path("duplicates").asInt(), r.path("unmatched").asInt(),
                r.path("rejected").asInt(), errors);
    }

    /** Links earlier unmatched events to employees created since; returns how many were linked. */
    int rematch(String companyId) {
        return withLogin(() -> http.sendJson(authed("/api/v1/attendance/rematch?companyId=" + companyId)
                .POST(HttpRequest.BodyPublishers.noBody()).build())).path("matched").asInt();
    }

    private List<Option> options(String path) {
        List<Option> out = new ArrayList<>();
        get(path).forEach(o -> out.add(new Option(o.path("id").asString(), o.path("name").asString())));
        return out;
    }

    private JsonNode get(String path) {
        return withLogin(() -> http.sendJson(authed(path).GET().build()));
    }

    private HttpRequest.Builder authed(String path) {
        return http.request(path).header("Authorization", "Bearer " + token);
    }

    /** Runs the call, signing in again once if the access token has expired. */
    private <T> T withLogin(Supplier<T> call) {
        if (token == null) {
            login();
        }
        try {
            return call.get();
        } catch (Http.CallException e) {
            if (e.status != 401) {
                throw e;
            }
            login();
            return call.get();
        }
    }
}
