package com.is2c.biostarsync;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Outcome of one sync run, shown in the UI history. */
public class SyncReport {

    private static final int MAX_ERRORS = 50;

    public Instant started;
    public Instant finished;
    /** MANUAL or SCHEDULED. */
    public String trigger;
    /** A preview reads both systems but writes nothing. */
    public boolean preview;
    public String scope;

    // Users
    public int usersInBiostar;
    public int usersSkipped;
    public int usersExisting;
    public int usersCreated;
    public int usersFailed;

    // Events
    public int eventsRead;
    /** Events without a user, or of users that are not synced (unmapped group, deleted in BioStar). */
    public int eventsSkipped;
    public int eventsSent;
    public int accepted;
    public int duplicates;
    public int unmatched;
    public int rejected;
    public int rematched;

    public List<String> errors = new ArrayList<>();
    /** Set when the run stopped early. */
    public String failure;

    void error(String message) {
        if (errors.size() < MAX_ERRORS) {
            errors.add(message);
        } else if (errors.size() == MAX_ERRORS) {
            errors.add("… more errors not shown");
        }
    }
}
