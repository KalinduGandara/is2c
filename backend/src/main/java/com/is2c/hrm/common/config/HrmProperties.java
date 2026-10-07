package com.is2c.hrm.common.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed application configuration bound from {@code hrm.*} / {@code HRM_*} (NFR-001). */
@ConfigurationProperties(prefix = "hrm")
public record HrmProperties(
        Edition edition,
        Db db,
        Jwt jwt,
        Storage storage,
        Mail mail,
        Cors cors,
        Admin admin,
        boolean apiDocs) {

    public enum Edition { ONPREM, CLOUD }

    public HrmProperties {
        edition = edition == null ? Edition.ONPREM : edition;
        db = db == null ? new Db(null) : db;
        jwt = jwt == null ? new Jwt(null, null, null) : jwt;
        storage = storage == null ? new Storage(null) : storage;
        mail = mail == null ? new Mail(false, null) : mail;
        cors = cors == null ? new Cors(null) : cors;
        admin = admin == null ? new Admin(null, null) : admin;
    }

    public record Db(String vendor) {
        public Db {
            vendor = vendor == null ? "postgresql" : vendor;
        }
    }

    public record Jwt(String secret, Duration accessTokenTtl, Duration refreshTokenTtl) {
        public Jwt {
            accessTokenTtl = accessTokenTtl == null ? Duration.ofMinutes(15) : accessTokenTtl;
            refreshTokenTtl = refreshTokenTtl == null ? Duration.ofDays(7) : refreshTokenTtl;
        }
    }

    public record Storage(Path path) {
        public Storage {
            path = path == null ? Path.of("data", "files") : path;
        }
    }

    public record Mail(boolean enabled, String from) {
    }

    public record Cors(List<String> origins) {
        public Cors {
            origins = origins == null ? List.of() : List.copyOf(origins);
        }
    }

    public record Admin(String username, String password) {
    }
}
