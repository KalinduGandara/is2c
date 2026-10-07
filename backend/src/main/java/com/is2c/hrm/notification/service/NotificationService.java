package com.is2c.hrm.notification.service;

import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.is2c.hrm.approval.api.ApprovalEvent;
import com.is2c.hrm.common.config.HrmProperties;
import com.is2c.hrm.notification.api.Notification;
import com.is2c.hrm.notification.api.NotificationChannel;
import com.is2c.hrm.security.domain.AppUser;
import com.is2c.hrm.security.domain.AppUserRepository;

/**
 * Dispatches notifications (FR-074). Event-driven notifications run after the business transaction commits and
 * on a background thread; delivery failures are logged and never propagate to the business operation.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationChannel channel;
    private final AppUserRepository users;
    private final HrmProperties props;

    public NotificationService(NotificationChannel channel, AppUserRepository users, HrmProperties props) {
        this.channel = channel;
        this.users = users;
        this.props = props;
    }

    public boolean emailEnabled() {
        return props.mail().enabled();
    }

    public String channelName() {
        return channel.name();
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(ApprovalEvent event) {
        List<String> emails = users.findAllById(event.recipientUserIds()).stream().filter(AppUser::isActive)
                .map(AppUser::getEmail).filter(Objects::nonNull).filter(e -> !e.isBlank()).toList();
        if (emails.isEmpty()) {
            return;
        }
        String subject = switch (event.kind()) {
            case REQUESTED -> "Approval requested: " + event.summary();
            case COMPLETED -> "Your request was " + event.outcome().toLowerCase() + ": " + event.summary();
        };
        String body = switch (event.kind()) {
            case REQUESTED -> "A " + event.type().replace('_', ' ').toLowerCase()
                    + " is waiting for your approval.\n\n" + event.summary() + "\n\nOpen Premium HRM → Approvals to act on it.";
            case COMPLETED -> "Your " + event.type().replace('_', ' ').toLowerCase() + " was "
                    + event.outcome().toLowerCase() + ".\n\n" + event.summary();
        };
        deliver(Notification.of(emails, subject, body));
    }

    /** Sends synchronously; returns false (and logs) on failure instead of throwing. */
    public boolean deliver(Notification notification) {
        try {
            channel.send(notification);
            return true;
        } catch (RuntimeException e) {
            log.warn("Notification delivery via {} failed for {}: {}", channel.name(), notification.recipients(),
                    e.getMessage());
            return false;
        }
    }
}
