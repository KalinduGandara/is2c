package com.is2c.hrm.common.config;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableConfigurationProperties(HrmProperties.class)
@EnableJpaAuditing(auditorAwareRef = "auditorAware", dateTimeProviderRef = "auditingDateTimeProvider")
public class CommonConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    org.springframework.data.auditing.DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> java.util.Optional.of(clock.instant());
    }
}
