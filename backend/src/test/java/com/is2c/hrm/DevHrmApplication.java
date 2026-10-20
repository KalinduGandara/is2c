package com.is2c.hrm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import java.time.Clock;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.SpringApplication;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

/**
 * Local development launcher: starts an embedded PostgreSQL (data kept in target/devdb) and the application with
 * a bootstrap admin (admin / Admin#Pass123, must change on first login). Run with:
 * {@code ./mvnw spring-boot:test-run -Dspring-boot.run.main-class=com.is2c.hrm.DevHrmApplication}
 * Options (JVM system properties): dev.datadir, dev.clean=true (fresh database), dev.pg.port, server.port,
 * dev.clock.offset-ms (shift the application clock, used for user-guide screenshots), dev.seed=true (sample data for
 * manual testing, see {@link DemoDataSeeder}; combine with dev.clean=true for a fresh set).
 */
public final class DevHrmApplication {

    private DevHrmApplication() {
    }

    public static void main(String[] args) throws IOException {
        Path dataDir = Path.of(System.getProperty("dev.datadir", "target/devdb")).toAbsolutePath();
        boolean clean = Boolean.getBoolean("dev.clean");
        if (clean && Files.exists(dataDir)) {
            // A previous run may have been killed without shutting the database down; stop it and start from scratch.
            stopStalePostmaster(dataDir);
            try (var paths = Files.walk(dataDir)) {
                paths.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
        boolean existing = Files.exists(dataDir.resolve("PG_VERSION"));
        Files.createDirectories(dataDir);
        EmbeddedPostgres pg = EmbeddedPostgres.builder()
                .setDataDirectory(dataDir)
                .setCleanDataDirectory(clean)
                .setPort(Integer.parseInt(System.getProperty("dev.pg.port", "54329")))
                .start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                pg.close();
            } catch (IOException ignored) {
                // shutting down
            }
        }));
        System.out.println((existing ? "Reusing" : "Created") + " dev database at " + dataDir);
        // System properties outrank application.yml, unlike default properties.
        java.util.Map.of(
                "spring.datasource.url", pg.getJdbcUrl("postgres", "postgres"),
                "spring.datasource.username", "postgres",
                "spring.datasource.password", "postgres",
                "hrm.admin.username", "admin",
                "hrm.admin.password", "Admin#Pass123",
                "hrm.jwt.secret", "dev-only-secret-dev-only-secret-0123456789",
                "hrm.storage.path", "target/dev-files",
                "hrm.api-docs", "true").forEach(System::setProperty);
        SpringApplication app = new SpringApplication(HrmApplication.class);
        long offsetMs = Long.getLong("dev.clock.offset-ms", 0L);
        if (offsetMs != 0) {
            // Demo/screenshot runs: shift the application clock (e.g. to office hours) without touching the OS clock.
            System.out.println("Application clock offset by " + java.time.Duration.ofMillis(offsetMs));
            app.addInitializers(ctx -> ctx.addBeanFactoryPostProcessor(beanFactory -> {
                // runs after the configuration classes have been parsed, so the application's clock bean exists
                BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
                registry.removeBeanDefinition("clock");
                registry.registerBeanDefinition("clock", BeanDefinitionBuilder
                        .genericBeanDefinition(Clock.class, () -> Clock.offset(Clock.systemUTC(), java.time.Duration.ofMillis(offsetMs)))
                        .getBeanDefinition());
            }));
        }
        var ctx = app.run(args);
        if (Boolean.getBoolean("dev.seed")) {
            try {
                DemoDataSeeder.seed(ctx);
            } catch (RuntimeException e) {
                // keep the server up so the partial data can be inspected
                org.slf4j.LoggerFactory.getLogger(DevHrmApplication.class).error("Demo data seeding failed", e);
            }
        }
    }

    /** Terminates a postgres left running on {@code dataDir} (its pid is the first line of postmaster.pid). */
    private static void stopStalePostmaster(Path dataDir) throws IOException {
        Path pidFile = dataDir.resolve("postmaster.pid");
        if (!Files.exists(pidFile)) {
            return;
        }
        long pid = Long.parseLong(Files.readAllLines(pidFile).get(0).trim());
        ProcessHandle.of(pid).ifPresent(p -> {
            p.destroy();
            try {
                p.onExit().get(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception e) {
                p.destroyForcibly();
            }
        });
    }
}
