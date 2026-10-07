package com.is2c.testsupport;

import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Single embedded PostgreSQL instance shared by all integration tests in the JVM.
 * Real PostgreSQL binaries, so vendor-specific migrations (triggers) are exercised without Docker.
 */
public final class EmbeddedPostgres {

    private static io.zonky.test.db.postgres.embedded.EmbeddedPostgres instance;

    private EmbeddedPostgres() {
    }

    public static synchronized String jdbcUrl() {
        if (instance == null) {
            try {
                instance = io.zonky.test.db.postgres.embedded.EmbeddedPostgres.builder().start();
            } catch (IOException e) {
                throw new UncheckedIOException("Could not start embedded PostgreSQL", e);
            }
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    instance.close();
                } catch (IOException ignored) {
                    // JVM is exiting
                }
            }));
        }
        return instance.getJdbcUrl("postgres", "postgres");
    }
}
