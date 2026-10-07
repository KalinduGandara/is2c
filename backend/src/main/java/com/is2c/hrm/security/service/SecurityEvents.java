package com.is2c.hrm.security.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class SecurityEvents {

    private static final Logger log = LoggerFactory.getLogger("SECURITY");

    private final ApplicationEventPublisher publisher;

    public SecurityEvents(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void publish(SecurityEvent.Type type, UUID userId, String username, String detail) {
        log.info("{} user={} id={} {}", type, username, userId, detail == null ? "" : detail);
        publisher.publishEvent(new SecurityEvent(type, userId, username, detail));
    }
}
