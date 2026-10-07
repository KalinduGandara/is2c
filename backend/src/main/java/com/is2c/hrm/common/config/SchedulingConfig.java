package com.is2c.hrm.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Scheduled jobs (nightly attendance, leave accrual); disabled with hrm.jobs.enabled=false (tests). */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "hrm.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
