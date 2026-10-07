package com.is2c.hrm.attendance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.is2c.hrm.attendance.adapter.CsvAdapter;
import com.is2c.hrm.attendance.adapter.GenericJsonAdapter;
import com.is2c.hrm.attendance.adapter.GenericJsonAdapter.JsonEvent;
import com.is2c.hrm.attendance.adapter.ParsedEvent;
import com.is2c.hrm.attendance.api.EventDirection;

class AdapterTest {

    @Test
    @DisplayName("FR-047 JSON adapter: offset timestamps are absolute, local ones stay local; bad rows are rejected")
    void json() {
        List<ParsedEvent> parsed = new GenericJsonAdapter().parse(new GenericJsonAdapter.JsonPayload(List.of(
                new JsonEvent("1", "D1", "B-1", "2026-03-16T08:01:00+05:30", "in"),
                new JsonEvent("2", null, "B-1", "2026-03-16T17:05:00", null),
                new JsonEvent("3", null, "B-1", "yesterday", "IN"),
                new JsonEvent("4", null, "", "2026-03-16T17:05:00", "IN"),
                new JsonEvent("5", null, "B-1", "2026-03-16T17:05:00", "SIDEWAYS"))));
        assertThat(parsed.get(0).instant()).isEqualTo(Instant.parse("2026-03-16T02:31:00Z"));
        assertThat(parsed.get(0).direction()).isEqualTo(EventDirection.IN);
        assertThat(parsed.get(1).localTime()).isEqualTo(LocalDateTime.of(2026, 3, 16, 17, 5));
        assertThat(parsed.get(1).direction()).isEqualTo(EventDirection.UNKNOWN);
        assertThat(parsed.subList(2, 5)).allMatch(ParsedEvent::isRejected);
        assertThat(parsed.get(2).row()).isEqualTo(3);
    }

    @Test
    @DisplayName("FR-046 CSV adapter: header skipped, quoted values, deterministic external ids")
    void csv() {
        String csv = "employee,timestamp,direction,device\n"
                + "B-1,2026-03-16 08:01:00,IN,\"Gate, North\"\n"
                + "B-1,2026-03-16T17:00:00,OUT\n"
                + "\n"
                + "B-2,not-a-time\n";
        List<ParsedEvent> parsed = new CsvAdapter().parse(csv);
        assertThat(parsed).hasSize(3);
        assertThat(parsed.get(0).deviceId()).isEqualTo("Gate, North");
        assertThat(parsed.get(0).localTime()).isEqualTo(LocalDateTime.of(2026, 3, 16, 8, 1));
        assertThat(parsed.get(2).isRejected()).isTrue();
        assertThat(parsed.get(2).row()).isEqualTo(5);
        assertThat(new CsvAdapter().parse(csv).get(1).externalId()).isEqualTo(parsed.get(1).externalId());
    }
}
