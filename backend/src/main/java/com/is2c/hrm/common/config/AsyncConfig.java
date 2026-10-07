package com.is2c.hrm.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Background execution for after-commit work such as notifications (FR-074). */
@Configuration
@EnableAsync
public class AsyncConfig {
}
