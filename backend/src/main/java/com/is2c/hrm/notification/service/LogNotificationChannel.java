package com.is2c.hrm.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.is2c.hrm.notification.api.Notification;
import com.is2c.hrm.notification.api.NotificationChannel;

/** Default channel when email is disabled: writes notifications to the log. */
@Component
@ConditionalOnProperty(name = "hrm.mail.enabled", havingValue = "false", matchIfMissing = true)
public class LogNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(LogNotificationChannel.class);

    @Override
    public String name() {
        return "LOG";
    }

    @Override
    public void send(Notification n) {
        log.info("Notification to {}: {} ({} attachment(s))", n.recipients(), n.subject(), n.attachments().size());
    }
}
