package com.is2c.testsupport;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

public class EmbeddedPostgresInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        TestPropertyValues.of(
                "spring.datasource.url=" + EmbeddedPostgres.jdbcUrl(),
                "spring.datasource.username=postgres",
                "spring.datasource.password=postgres"
        ).applyTo(context.getEnvironment());
    }
}
