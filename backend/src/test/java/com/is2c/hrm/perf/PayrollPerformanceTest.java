package com.is2c.hrm.perf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.is2c.testsupport.ApiTestBase;
import com.is2c.testsupport.OrgFixtures.Structure;

import tools.jackson.databind.JsonNode;

/** NFR-004: payroll processing incl. attendance reprocessing for 1,000 employees × 1 month < 60 s. Run with -Pperf. */
@Tag("perf")
class PayrollPerformanceTest extends ApiTestBase {

    static final int EMPLOYEES = 1_000;

    @Test
    @DisplayName("NFR-004 payroll for 1,000 employees × 1 month (with attendance reprocessing) completes within 60 s")
    void thousandEmployees() throws Exception {
        clock.set(Instant.parse("2026-04-05T06:00:00Z"));
        String admin = data.bearerFor("root", "SUPER_ADMIN");
        Structure acme = orgs.structure(admin, "ACME");
        String ot = body(postAs(admin, "/api/v1/companies/{c}/ot-policies", Map.of("name", "Std"), acme.companyId()))
                .get("id").asString();
        String day = body(postAs(admin, "/api/v1/companies/{c}/shifts", Map.of("code", "DAY", "name", "Day", "type", "FIXED",
                "s1Start", "08:30", "s1End", "17:30", "breakMinutes", 60, "breakThresholdMinutes", 300, "otPolicyId", ot),
                acme.companyId())).get("id").asString();
        String allowance = null;
        for (JsonNode c : body(getAs(admin, "/api/v1/companies/{c}/payroll-components", acme.companyId()))) {
            if (c.get("code").asString().equals("ALLOWANCE")) {
                allowance = c.get("id").asString();
            }
        }
        String key = body(postAs(admin, "/api/v1/companies/{c}/attendance-sources", Map.of("name", "perf"), acme.companyId()))
                .get("apiKey").asString();

        Random random = new Random(42);
        List<Map<String, Object>> events = new ArrayList<>();
        for (int i = 1; i <= EMPLOYEES; i++) {
            Map<String, Object> b = orgs.employeeBody(acme, "Emp" + i, "Perf");
            b.put("joinDate", "2025-01-01");
            b.put("defaultShiftId", day);
            b.put("badgeId", "P" + i);
            UUID e = orgs.employee(admin, b);
            Map<String, Object> line = new HashMap<>(Map.of("componentId", allowance, "amount", 10000));
            line.put("excluded", false);
            postAs(admin, "/api/v1/employees/{e}/salary-structures", Map.of("effectiveFrom", "2025-01-01",
                    "basicSalary", 60000 + random.nextInt(140000), "lines", List.of(line)), e).andExpect(status().isCreated());
            for (LocalDate d = LocalDate.of(2026, 3, 1); d.getMonthValue() == 3; d = d.plusDays(1)) {
                if (d.getDayOfWeek() == DayOfWeek.SUNDAY || random.nextInt(20) == 0) {
                    continue; // weekly off, or ~5% absence
                }
                String in = d + "T08:" + (20 + random.nextInt(30)) + ":00";
                String out = d + "T" + (17 + random.nextInt(3)) + ":" + (10 + random.nextInt(50)) + ":00";
                events.add(Map.of("externalId", i + "-" + d + "-in", "employeeIdentifier", "P" + i, "timestamp", in, "direction", "IN"));
                events.add(Map.of("externalId", i + "-" + d + "-out", "employeeIdentifier", "P" + i, "timestamp", out, "direction", "OUT"));
            }
        }
        for (int from = 0; from < events.size(); from += 5000) {
            mvc.perform(post("/api/v1/integrations/attendance/events").header("X-Api-Key", key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toJson(Map.of("events", events.subList(from, Math.min(events.size(), from + 5000))))))
                    .andExpect(status().isOk());
        }
        String period = body(postAs(admin, "/api/v1/payroll/periods", Map.of("companyId", acme.companyId(), "year", 2026,
                "month", 3)).andExpect(status().isCreated())).get("id").asString();

        long start = System.nanoTime();
        JsonNode summary = body(postAs(admin, "/api/v1/payroll/periods/{id}/process", null, period).andExpect(status().isOk()));
        Duration took = Duration.ofNanos(System.nanoTime() - start);
        System.out.printf("PERF payroll for %d employees, %d events: %d ms%n", EMPLOYEES, events.size(), took.toMillis());

        assertThat(summary.get("processed").asInt()).isEqualTo(EMPLOYEES);
        assertThat(took).isLessThan(Duration.ofSeconds(60));
    }
}
