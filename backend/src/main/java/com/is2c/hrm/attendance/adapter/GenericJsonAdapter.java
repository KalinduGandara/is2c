package com.is2c.hrm.attendance.adapter;

import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.is2c.hrm.attendance.api.EventDirection;

/** The generic JSON format accepted by the ingestion endpoint (FR-045). */
@Component
public class GenericJsonAdapter implements AttendanceSourceAdapter {

    public record JsonEvent(String externalId, String deviceId, String employeeIdentifier, String timestamp,
            String direction) {
    }

    public record JsonPayload(List<JsonEvent> events) {
    }

    @Override
    public AdapterType type() {
        return AdapterType.GENERIC_JSON;
    }

    @Override
    public List<ParsedEvent> parse(Object payload) {
        JsonPayload p = (JsonPayload) payload;
        List<ParsedEvent> out = new ArrayList<>();
        List<JsonEvent> events = p.events() == null ? List.of() : p.events();
        for (int i = 0; i < events.size(); i++) {
            JsonEvent e = events.get(i);
            int row = i + 1;
            if (e == null || blank(e.externalId()) || blank(e.employeeIdentifier()) || blank(e.timestamp())) {
                out.add(ParsedEvent.rejected(row, "externalId, employeeIdentifier and timestamp are required"));
                continue;
            }
            if (e.externalId().length() > 100 || e.employeeIdentifier().length() > 100) {
                out.add(ParsedEvent.rejected(row, "identifier too long"));
                continue;
            }
            EventDirection direction;
            try {
                direction = blank(e.direction()) ? EventDirection.UNKNOWN
                        : EventDirection.valueOf(e.direction().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                out.add(ParsedEvent.rejected(row, "direction must be IN, OUT or UNKNOWN"));
                continue;
            }
            try {
                Timestamps.Parsed t = Timestamps.parse(e.timestamp());
                out.add(new ParsedEvent(row, e.externalId().trim(), e.deviceId(), e.employeeIdentifier().trim(),
                        t.instant(), t.local(), direction, null));
            } catch (DateTimeParseException ex) {
                out.add(ParsedEvent.rejected(row, "timestamp must be ISO-8601"));
            }
        }
        return out;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
