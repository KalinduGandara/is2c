package com.is2c.hrm.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Test clock that can be set and advanced. */
public class MutableClock extends Clock {

    public static final Instant DEFAULT = Instant.parse("2026-03-16T04:30:00Z");

    private volatile Instant now = DEFAULT;

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(now, zone);
    }

    @Override
    public Instant instant() {
        return now;
    }

    public void set(Instant instant) {
        this.now = instant;
    }

    public void advance(Duration duration) {
        this.now = now.plus(duration);
    }

    public void reset() {
        this.now = DEFAULT;
    }
}
