package com.is2c.hrm.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;

import com.is2c.testsupport.ApiTestBase;

class SchemaValidationTest extends ApiTestBase {

    @Value("${spring.jpa.hibernate.ddl-auto}")
    String ddlAuto;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    @DisplayName("NFR-006 schema comes only from Flyway migrations and Hibernate validates it at start-up")
    void flywayAndValidate() {
        assertThat(ddlAuto).isEqualTo("validate");
        Integer failed = jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE NOT success", Integer.class);
        Integer applied = jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success AND version IS NOT NULL",
                Integer.class);
        assertThat(failed).isZero();
        assertThat(applied).isGreaterThanOrEqualTo(16);
    }
}
