package com.is2c.biostarsync;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Runs syncs against in-process fakes of BioStar 2 and HRM that answer like the real servers. */
class SyncServiceTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private HttpServer biostar;
    private HttpServer hrm;
    private final List<JsonNode> eventSearches = new CopyOnWriteArrayList<>();
    private final List<JsonNode> createdEmployees = new CopyOnWriteArrayList<>();
    private final List<JsonNode> ingested = new CopyOnWriteArrayList<>();
    private final List<String> rematches = new CopyOnWriteArrayList<>();

    @TempDir
    Path dataDir;
    private SettingsStore store;
    private SyncService sync;

    /** Users: 100 exists in HRM, 101 is new, 102 is in the unmapped "Remove" group. */
    private static final String USERS = """
            {"UserCollection":{"total":"3","rows":[
             {"user_id":"100","name":"Shameetha Fernando","disabled":"false","user_group_id":{"id":"3005","name":"Legal"}},
             {"user_id":"101","name":"Kumi Miranda","disabled":"false","user_group_id":{"id":"3005","name":"Legal"}},
             {"user_id":"102","name":"Gone Person","disabled":"false","user_group_id":{"id":"9","name":"Remove"}}]},
             "Response":{"code":"0","message":"Success"}}""";

    /**
     * Two events share BioStar id 80 (ids restart in every monthly table). Event index 9004 belongs to the unmapped
     * group and 9005 has no user (as BioStar sends failed or anonymous events).
     */
    private static final List<String> EVENTS = List.of(
            event(80, 9001, "2025-03-03T03:40:00.00Z", "101", "3005", "\"tna_key\":\"1\","),
            event(80, 9002, "2025-04-28T03:41:31.00Z", "101", "3005", "\"tna_key\":\"2\","),
            event(81, 9003, "2025-04-28T03:42:31.00Z", "100", "3005", ""),
            event(82, 9004, "2025-04-28T03:43:31.00Z", "102", "9", "\"tna_key\":\"2\","),
            """
            {"id":"83","index":"9005","datetime":"2025-04-28T03:44:31.00Z","user_group_id":{"id":"0","name":""},
             "device_id":{"id":"540086297"},"event_type_id":{"code":"4865"},"user_id":{"photo_exists":"false"}}""");

    private static String event(int id, int index, String datetime, String user, String group, String tna) {
        return """
                {"id":"%d","index":"%d","datetime":"%s","user_id":{"user_id":"%s","name":"x"},
                 "user_group_id":{"id":"%s","name":"g"},"device_id":{"id":"540086297","name":"BioStation L2"},
                 "event_type_id":{"code":"4865"},%s"is_dst":"0"}""".formatted(id, index, datetime, user, group, tna);
    }

    @BeforeEach
    void start() throws IOException {
        biostar = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        biostar.createContext("/api/login", ex -> {
            ex.getResponseHeaders().add("bs-session-id", "sess");
            respond(ex, 200, "{\"User\":{}}");
        });
        biostar.createContext("/api/users", ex -> respond(ex, 200, USERS));
        biostar.createContext("/api/events/search", ex -> {
            JsonNode q = json.readTree(ex.getRequestBody()).path("Query");
            eventSearches.add(q);
            JsonNode range = q.path("conditions").get(0).path("values");
            Instant from = Instant.parse(range.get(0).asString());
            Instant to = Instant.parse(range.get(1).asString());
            List<String> inRange = EVENTS.stream().filter(e -> {
                Instant t = Instant.parse(json.readTree(e).path("datetime").asString());
                return !t.isBefore(from) && !t.isAfter(to);
            }).toList();
            int offset = q.path("offset").asInt();
            List<String> rows = inRange.subList(Math.min(offset, inRange.size()),
                    Math.min(offset + q.path("limit").asInt(), inRange.size()));
            respond(ex, 200, "{\"EventCollection\":{\"rows\":[" + String.join(",", rows) + "]}}");
        });
        biostar.start();

        hrm = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        hrm.createContext("/api/v1/auth/login", ex -> respond(ex, 200, "{\"accessToken\":\"tok\"}"));
        hrm.createContext("/api/v1/auth/me", ex -> respond(ex, 200, "{\"mustChangePassword\":false}"));
        hrm.createContext("/api/v1/companies", ex -> respond(ex, 200,
                "[{\"id\":\"c1\",\"legalName\":\"Acme\",\"timeZone\":\"Asia/Colombo\"}]"));
        hrm.createContext("/api/v1/employees", ex -> {
            if ("POST".equals(ex.getRequestMethod())) {
                createdEmployees.add(json.readTree(ex.getRequestBody()));
                respond(ex, 201, "{}");
            } else {
                respond(ex, 200, "{\"content\":[{\"employeeNumber\":\"100\"}],\"totalPages\":1}");
            }
        });
        hrm.createContext("/api/v1/integrations/attendance/events", ex -> {
            JsonNode body = json.readTree(ex.getRequestBody());
            ingested.add(body);
            respond(ex, 200, "{\"accepted\":" + body.path("events").size()
                    + ",\"duplicates\":0,\"unmatched\":0,\"rejected\":0,\"errors\":[]}");
        });
        hrm.createContext("/api/v1/attendance/rematch", ex -> {
            rematches.add(ex.getRequestURI().getQuery());
            respond(ex, 200, "{\"matched\":0}");
        });
        hrm.start();

        store = new SettingsStore(json, dataDir);
        Settings s = new Settings();
        s.biostarUrl = "http://127.0.0.1:" + biostar.getAddress().getPort();
        s.biostarLoginId = "admin";
        s.biostarPassword = "pw";
        s.hrmUrl = "http://127.0.0.1:" + hrm.getAddress().getPort();
        s.hrmUsername = "sync";
        s.hrmPassword = "pw";
        s.hrmApiKey = "hrm_key";
        s.companyId = "c1";
        s.branchId = "b1";
        s.designationId = "d1";
        s.categoryId = "k1";
        s.joinDate = LocalDate.of(2024, 1, 1);
        s.groupDepartments = Map.of("3005", "dep-legal", "9", "");
        s.scheduleStartDate = LocalDate.of(2025, 4, 1);
        store.save(s);
        sync = new SyncService(store, json, Clock.systemUTC());
        sync.eventPage = 2; // exercise offset paging
    }

    @AfterEach
    void stop() {
        biostar.stop(0);
        hrm.stop(0);
    }

    @Test
    @DisplayName("Manual sync creates missing employees of mapped groups, then sends only their recognised punches")
    void manualSync() {
        SyncReport r = sync.run(new SyncService.Request("MANUAL", false, LocalDate.of(2025, 3, 1),
                LocalDate.of(2025, 4, 28)));

        assertThat(r.failure).isNull();
        assertThat(r.usersInBiostar).isEqualTo(3);
        assertThat(r.usersExisting).isEqualTo(1);
        assertThat(r.usersCreated).isEqualTo(1);
        assertThat(r.usersSkipped).isEqualTo(1);
        assertThat(createdEmployees).singleElement().satisfies(e -> {
            assertThat(e.path("employeeNumber").asString()).isEqualTo("101");
            assertThat(e.path("badgeId").asString()).isEqualTo("101");
            assertThat(e.path("departmentId").asString()).isEqualTo("dep-legal");
        });

        assertThat(r.eventsRead).isEqualTo(5);
        assertThat(r.eventsSkipped).isEqualTo(2);
        assertThat(r.accepted).isEqualTo(3);
        List<JsonNode> sent = ingested.stream().flatMap(b -> b.path("events").valueStream()).toList();
        assertThat(sent).extracting(e -> e.path("externalId").asString())
                .as("same BioStar id in two months still gives two distinct punches")
                .containsExactly("540086297:9001", "540086297:9002", "540086297:9003");
        assertThat(sent).extracting(e -> e.path("direction").asString()).containsExactly("IN", "OUT", "UNKNOWN");
        assertThat(eventSearches).extracting(q -> q.path("offset").asInt()).containsExactly(0, 2, 4);

        // Range is whole local days in the company's time zone (UTC+5:30), sent in UTC; only success codes.
        JsonNode conditions = eventSearches.getFirst().path("conditions");
        assertThat(conditions.get(0).path("values").toString())
                .isEqualTo("[\"2025-02-28T18:30:00.000Z\",\"2025-04-28T18:29:59.999Z\"]");
        assertThat(conditions.get(1).path("values").toString()).contains("\"4865\"").doesNotContain("\"5124\"");

        assertThat(rematches).containsExactly("companyId=c1");
        assertThat(store.state().lastEventTime).as("manual runs do not move the automatic position").isNull();
        assertThat(store.state().history).hasSize(1);
    }

    @Test
    @DisplayName("Automatic sync starts from the start date, then re-reads from the latest punch minus the look-back")
    void automaticSync() {
        SyncReport first = sync.run(new SyncService.Request("SCHEDULED", false, null, null));
        assertThat(first.failure).isNull();
        assertThat(first.scope).contains("2025-04-01");
        assertThat(first.eventsRead).isEqualTo(4);
        assertThat(store.state().lastEventTime).isEqualTo(Instant.parse("2025-04-28T03:44:31Z"));

        eventSearches.clear();
        SyncReport second = sync.run(new SyncService.Request("SCHEDULED", false, null, null));
        assertThat(second.failure).isNull();
        assertThat(second.scope).startsWith("punches since 2025-04-27T09:14:31");
        assertThat(eventSearches.getFirst().path("conditions").get(0).path("values").get(0).asString())
                .isEqualTo("2025-04-27T03:44:31.000Z");
    }

    @Test
    @DisplayName("Preview reads both systems but writes nothing")
    void preview() {
        SyncReport r = sync.run(new SyncService.Request("MANUAL", true, LocalDate.of(2025, 4, 1),
                LocalDate.of(2025, 4, 30)));
        assertThat(r.failure).isNull();
        assertThat(r.usersCreated).isEqualTo(1);
        assertThat(r.eventsSent).isEqualTo(2);
        assertThat(store.state().lastEventTime).isNull();
        assertThat(createdEmployees).isEmpty();
        assertThat(ingested).isEmpty();
        assertThat(rematches).isEmpty();
    }

    @Test
    @DisplayName("A sync without configuration fails with a clear message and is recorded")
    void notConfigured() {
        store.save(new Settings());
        SyncReport r = sync.run(new SyncService.Request("MANUAL", false, LocalDate.now(), LocalDate.now()));
        assertThat(r.failure).startsWith("Not configured: BioStar connection");
        assertThat(store.state().history).hasSize(1);
    }

    private static void respond(HttpExchange ex, int status, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, b.length);
        ex.getResponseBody().write(b);
        ex.close();
    }
}
