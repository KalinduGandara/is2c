package com.is2c.hrm.attendance.adapter;

import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.is2c.hrm.attendance.api.EventDirection;
import com.is2c.hrm.security.service.TokenHashing;

/**
 * CSV import (FR-046): columns employee identifier, timestamp, direction (optional), device id (optional).
 * A header row is detected and skipped. The external id is a hash of the row content, making re-imports idempotent.
 */
@Component
public class CsvAdapter implements AttendanceSourceAdapter {

    @Override
    public AdapterType type() {
        return AdapterType.CSV;
    }

    @Override
    public List<ParsedEvent> parse(Object payload) {
        String text = (String) payload;
        List<ParsedEvent> out = new ArrayList<>();
        String[] lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip();
            int row = i + 1;
            if (line.isEmpty()) {
                continue;
            }
            List<String> cols = split(line);
            if (i == 0 && isHeader(cols)) {
                continue;
            }
            if (cols.size() < 2 || cols.get(0).isBlank() || cols.get(1).isBlank()) {
                out.add(ParsedEvent.rejected(row, "employee identifier and timestamp are required"));
                continue;
            }
            EventDirection direction;
            String dir = cols.size() > 2 ? cols.get(2).trim() : "";
            try {
                direction = dir.isEmpty() ? EventDirection.UNKNOWN : EventDirection.valueOf(dir.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                out.add(ParsedEvent.rejected(row, "direction must be IN, OUT or UNKNOWN"));
                continue;
            }
            try {
                Timestamps.Parsed t = Timestamps.parse(cols.get(1));
                String device = cols.size() > 3 && !cols.get(3).isBlank() ? cols.get(3).trim() : null;
                String identifier = cols.get(0).trim();
                String externalId = "csv:" + TokenHashing.sha256(identifier + "|" + cols.get(1).trim() + "|" + direction
                        + "|" + (device == null ? "" : device)).substring(0, 40);
                out.add(new ParsedEvent(row, externalId, device, identifier, t.instant(), t.local(), direction, null));
            } catch (DateTimeParseException ex) {
                out.add(ParsedEvent.rejected(row, "timestamp must be ISO-8601 (e.g. 2026-03-16T08:02:00)"));
            }
        }
        return out;
    }

    private static boolean isHeader(List<String> cols) {
        if (cols.size() < 2) {
            return false;
        }
        try {
            Timestamps.parse(cols.get(1));
            return false;
        } catch (DateTimeParseException e) {
            return true;
        }
    }

    /** Minimal CSV splitting with double-quote support. */
    static List<String> split(String line) {
        List<String> cols = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (c == ',' && !quoted) {
                cols.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        cols.add(cur.toString());
        return cols;
    }
}
