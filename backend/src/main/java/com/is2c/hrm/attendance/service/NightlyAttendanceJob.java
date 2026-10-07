package com.is2c.hrm.attendance.service;

import java.time.Clock;
import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.is2c.hrm.org.domain.Company;
import com.is2c.hrm.org.service.OrgDirectory;

/** FR-048: processes the previous day for every company each night. */
@Component
@ConditionalOnProperty(name = "hrm.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class NightlyAttendanceJob {

    private static final Logger log = LoggerFactory.getLogger(NightlyAttendanceJob.class);

    private final AttendanceProcessor processor;
    private final OrgDirectory org;
    private final Clock clock;

    public NightlyAttendanceJob(AttendanceProcessor processor, OrgDirectory org, Clock clock) {
        this.processor = processor;
        this.org = org;
        this.clock = clock;
    }

    @Scheduled(cron = "${hrm.jobs.attendance-cron:0 0 1 * * *}")
    public void run() {
        for (Company c : org.allCompanies()) {
            LocalDate yesterday = LocalDate.now(clock.withZone(java.time.ZoneId.of(c.getTimeZone()))).minusDays(1);
            try {
                AttendanceProcessor.ProcessResult r = processor.process(c.getId(), null, yesterday, yesterday);
                log.info("Nightly attendance {} {}: {}", c.getCode(), yesterday, r);
            } catch (RuntimeException e) {
                log.error("Nightly attendance failed for company {}", c.getCode(), e);
            }
        }
    }
}
