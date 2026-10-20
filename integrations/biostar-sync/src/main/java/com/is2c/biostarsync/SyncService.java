package com.is2c.biostarsync;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import tools.jackson.databind.json.JsonMapper;

/**
 * Runs a sync: BioStar users first (creating missing HRM employees), then attendance events. Only one sync runs at a
 * time; manual runs cover a date range, automatic runs continue from the latest punch already read (minus a look-back
 * for late uploads). Re-sending a punch is harmless: HRM skips it as a duplicate of the same external id.
 */
@Service
public class SyncService {

    /** A sync to run: a date range (manual) or, when {@code from} is null, the automatic continuation. */
    public record Request(String trigger, boolean preview, LocalDate from, LocalDate to) {
        static Request scheduled() {
            return new Request("SCHEDULED", false, null, null);
        }

        boolean automatic() {
            return from == null;
        }
    }

    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    private final SettingsStore store;
    private final JsonMapper json;
    private final Clock clock;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    /** Description of the sync in progress, null when idle. */
    private final AtomicReference<String> running = new AtomicReference<>();
    private volatile Instant lastScheduledStart;
    /** Events per BioStar page (and per HRM batch, which allows 5,000); smaller in tests. */
    int eventPage = 1000;

    public SyncService(SettingsStore store, JsonMapper json, Clock clock) {
        this.store = store;
        this.json = json;
        this.clock = clock;
    }

    public String running() {
        return running.get();
    }

    /** Starts the sync in the background; false when another sync is already running. */
    public boolean start(Request r) {
        if (!running.compareAndSet(null, (r.preview() ? "Preview" : "Sync") + " started " + Instant.now(clock))) {
            return false;
        }
        executor.submit(() -> {
            try {
                run(r);
            } finally {
                running.set(null);
            }
        });
        return true;
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    void tick() {
        Settings s = store.settings();
        Instant now = Instant.now(clock);
        if (s.scheduleEnabled && (lastScheduledStart == null || !now.isBefore(
                lastScheduledStart.plus(Duration.ofMinutes(Math.max(1, s.scheduleIntervalMinutes)))))) {
            if (start(Request.scheduled())) {
                lastScheduledStart = now;
            }
        }
    }

    /** Runs a sync in the calling thread and records its report. */
    SyncReport run(Request r) {
        Settings s = store.settings();
        SyncReport rep = new SyncReport();
        rep.started = Instant.now(clock);
        rep.trigger = r.trigger();
        rep.preview = r.preview();
        try {
            String missing = s.missingForSync();
            if (!missing.isEmpty()) {
                throw new IllegalStateException("Not configured: " + missing);
            }
            Instant watermark = store.state().lastEventTime;
            if (r.automatic() && watermark == null && s.scheduleStartDate == null) {
                throw new IllegalStateException("Set the automatic sync start date");
            }
            BiostarClient bio = biostar(s);
            bio.login(s.biostarLoginId, s.biostarPassword);
            HrmClient hrm = hrm(s);
            hrm.login();
            HrmClient.Company company = hrm.companies().stream().filter(c -> c.id().equals(s.companyId)).findFirst()
                    .orElseThrow(() -> new IllegalStateException("HRM company not found or not visible to the account"));
            ZoneId zone = ZoneId.of(company.timeZone());

            Set<String> synced = syncUsers(bio, hrm, s, rep);

            Instant from;
            Instant to;
            if (!r.automatic()) {
                from = r.from().atStartOfDay(zone).toInstant();
                to = r.to().plusDays(1).atStartOfDay(zone).toInstant().minusMillis(1);
                rep.scope = r.from() + " to " + r.to();
            } else {
                from = watermark != null ? watermark.minus(Duration.ofHours(Math.max(0, s.scheduleLookbackHours)))
                        : s.scheduleStartDate.atStartOfDay(zone).toInstant();
                // A day ahead, so punches from devices whose clock runs a little fast are not missed.
                to = Instant.now(clock).plus(Duration.ofDays(1));
                rep.scope = watermark != null ? "punches since " + from.atZone(zone).toLocalDateTime()
                        : "first automatic run, from " + s.scheduleStartDate;
            }
            syncEvents(bio, hrm, s, synced, from, to, r, rep);

            if (!r.preview() && rep.usersCreated > 0) {
                rep.rematched = hrm.rematch(s.companyId);
            }
        } catch (RuntimeException e) {
            log.warn("Sync failed", e);
            rep.failure = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        }
        rep.finished = Instant.now(clock);
        store.addReport(rep);
        return rep;
    }

    /** Creates HRM employees for users of mapped groups; returns the ids of all such users. */
    private Set<String> syncUsers(BiostarClient bio, HrmClient hrm, Settings s, SyncReport rep) {
        List<BiostarClient.User> users = bio.users();
        rep.usersInBiostar = users.size();
        Set<String> existing = hrm.employeeNumbers(s.companyId);
        Set<String> synced = new HashSet<>();
        for (BiostarClient.User u : users) {
            if (u.disabled() || s.groupDepartments.getOrDefault(u.groupId(), "").isBlank()) {
                rep.usersSkipped++;
                continue;
            }
            synced.add(u.id());
            if (existing.contains(u.id().toLowerCase(Locale.ROOT))) {
                rep.usersExisting++;
            } else if (rep.preview) {
                rep.usersCreated++;
            } else {
                try {
                    switch (hrm.createEmployee(Mapping.employee(u, s))) {
                        case CREATED -> rep.usersCreated++;
                        case EXISTS -> rep.usersExisting++;
                    }
                } catch (Http.CallException e) {
                    if (e.status == 0) {
                        throw e; // HRM unreachable: stop instead of failing every user
                    }
                    rep.usersFailed++;
                    rep.error("user " + u.id() + " (" + u.name() + "): " + e.getMessage());
                }
            }
        }
        return synced;
    }

    private void syncEvents(BiostarClient bio, HrmClient hrm, Settings s, Set<String> synced, Instant from,
            Instant to, Request r, SyncReport rep) {
        Instant now = Instant.now(clock);
        Instant latest = null;
        for (int offset = 0;; offset += eventPage) {
            List<BiostarClient.Event> page = bio.events(from, to, offset, eventPage);
            rep.eventsRead += page.size();
            List<Map<String, Object>> batch = new ArrayList<>();
            for (BiostarClient.Event e : page) {
                if (e.userId() == null || !synced.contains(e.userId())) {
                    rep.eventsSkipped++;
                } else {
                    batch.add(Mapping.event(e, s));
                }
                Instant t = Instant.parse(e.datetime());
                if (!t.isAfter(now) && (latest == null || t.isAfter(latest))) {
                    latest = t; // a device clock set in the future must not push the watermark ahead
                }
            }
            rep.eventsSent += batch.size();
            if (!r.preview() && !batch.isEmpty()) {
                HrmClient.IngestResult res = hrm.ingest(batch);
                rep.accepted += res.accepted();
                rep.duplicates += res.duplicates();
                rep.unmatched += res.unmatched();
                rep.rejected += res.rejected();
                res.errors().forEach(rep::error);
            }
            if (r.automatic() && !r.preview() && latest != null) {
                store.saveWatermark(latest); // saved per page so a failure later resumes from here
            }
            if (page.size() < eventPage) {
                return;
            }
        }
    }

    BiostarClient biostar(Settings s) {
        return new BiostarClient(s.biostarUrl, s.biostarTrustAllCerts, json);
    }

    HrmClient hrm(Settings s) {
        return new HrmClient(s.hrmUrl, s.hrmUsername, s.hrmPassword, s.hrmApiKey, json);
    }
}
