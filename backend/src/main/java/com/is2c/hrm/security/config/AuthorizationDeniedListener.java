package com.is2c.hrm.security.config;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.security.authorization.AuthorizationEventPublisher;
import org.springframework.security.authorization.SpringAuthorizationEventPublisher;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.core.Authentication;

import com.is2c.hrm.security.service.SecurityEvent;
import com.is2c.hrm.security.service.SecurityEvents;

/** Turns authorization denials of authenticated users (e.g. failed @PreAuthorize) into PERMISSION_DENIED events. */
@Configuration
public class AuthorizationDeniedListener {

    private final SecurityEvents events;

    public AuthorizationDeniedListener(SecurityEvents events) {
        this.events = events;
    }

    @Bean
    static AuthorizationEventPublisher authorizationEventPublisher(ApplicationEventPublisher publisher) {
        return new SpringAuthorizationEventPublisher(publisher);
    }

    @EventListener
    public void on(AuthorizationDeniedEvent<?> event) {
        Authentication auth = event.getAuthentication().get();
        if (auth instanceof HrmAuthenticationToken token) {
            events.publish(SecurityEvent.Type.PERMISSION_DENIED, token.getPrincipal().id(),
                    token.getPrincipal().username(), String.valueOf(event.getAuthorizationResult()));
        }
    }
}
