package com.is2c.hrm.leave.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.is2c.hrm.org.domain.Company;
import com.is2c.hrm.org.service.OrgDirectory;

/** FR-062 daily accrual, year-end carry-forward and expiry (all idempotent). */
@Component
@ConditionalOnProperty(name = "hrm.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class LeaveJobs {

    private static final Logger log = LoggerFactory.getLogger(LeaveJobs.class);

    private final LeaveBalanceService balances;
    private final OrgDirectory org;
    private final Clock clock;

    public LeaveJobs(LeaveBalanceService balances, OrgDirectory org, Clock clock) {
        this.balances = balances;
        this.org = org;
        this.clock = clock;
    }

    @Scheduled(cron = "${hrm.jobs.leave-cron:0 30 0 * * *}")
    public void run() {
        for (Company c : org.allCompanies()) {
            LocalDate today = LocalDate.now(clock.withZone(ZoneId.of(c.getTimeZone())));
            try {
                balances.runYearEnd(c.getId(), today.getYear() - 1);
                balances.runAccrual(c.getId(), today);
                balances.runExpiry(c.getId(), today);
            } catch (RuntimeException e) {
                log.error("Leave jobs failed for company {}", c.getCode(), e);
            }
        }
    }
}
