package com.is2c.testsupport;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.dao.PessimisticLockingFailureException;
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
            truncate("TRUNCATE TABLE " + String.join(", ", tables) + " CASCADE");
        }
        jdbc.update("DELETE FROM role_permission");
        jdbc.update("DELETE FROM role WHERE system = FALSE");
        jdbc.batchUpdate("INSERT INTO role_permission (role_id, permission) VALUES (?, ?)",
                seededPermissions.stream().map(r -> new Object[] {r.get("role_id"), r.get("permission")}).toList());
    }

    /**
     * After-commit async work of the previous test (e.g. notification dispatch) may still hold row locks; the
     * TRUNCATE then deadlocks with it and PostgreSQL aborts one side. Retry a few times.
     */
    private void truncate(String sql) {
        for (int attempt = 1; ; attempt++) {
            try {
                jdbc.execute(sql);
                return;
            } catch (PessimisticLockingFailureException e) {
                if (attempt == 5) {
                    throw e;
                }
                try {
                    Thread.sleep(100L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }
}
