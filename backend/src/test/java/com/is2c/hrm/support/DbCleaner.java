package com.is2c.hrm.support;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Truncates all application tables except migration history and the migration-seeded system roles, whose
 * permissions are restored to the seeded state (tests may edit them).
 */
@Component
public class DbCleaner {

    private static final Set<String> PRESERVED = Set.of("flyway_schema_history", "role", "role_permission");
    private static List<Map<String, Object>> seededPermissions;

    private final JdbcTemplate jdbc;

    public DbCleaner(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public synchronized void clean() {
        if (seededPermissions == null) {
            seededPermissions = jdbc.queryForList("""
                    SELECT rp.role_id, rp.permission FROM role_permission rp JOIN role r ON r.id = rp.role_id
                    WHERE r.system
                    """);
        }
        List<String> tables = jdbc.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public'", String.class).stream()
                .filter(t -> !PRESERVED.contains(t))
                .toList();
        if (!tables.isEmpty()) {
            jdbc.execute("TRUNCATE TABLE " + String.join(", ", tables) + " CASCADE");
        }
        jdbc.update("DELETE FROM role_permission");
        jdbc.update("DELETE FROM role WHERE system = FALSE");
        jdbc.batchUpdate("INSERT INTO role_permission (role_id, permission) VALUES (?, ?)",
                seededPermissions.stream().map(r -> new Object[] {r.get("role_id"), r.get("permission")}).toList());
    }
}
