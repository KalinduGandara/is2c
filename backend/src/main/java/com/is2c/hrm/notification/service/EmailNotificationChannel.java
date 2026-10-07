package com.is2c.hrm.notification.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import com.is2c.hrm.common.config.HrmProperties;
import com.is2c.hrm.notification.api.Notification;
import com.is2c.hrm.notification.api.NotificationChannel;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/** SMTP delivery, enabled with hrm.mail.enabled=true (FR-074, CL-B5). */
@Component
@ConditionalOnProperty(name = "hrm.mail.enabled", havingValue = "true")
public class EmailNotificationChannel implements NotificationChannel {

    private final JavaMailSender sender;
    private final HrmProperties props;

    public EmailNotificationChannel(JavaMailSender sender, HrmProperties props) {
        this.sender = sender;
        this.props = props;
    }

    @Override
    public String name() {
        return "EMAIL";
    }

    @Override
    public void send(Notification n) {
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, !n.attachments().isEmpty(), "UTF-8");
            helper.setFrom(props.mail().from());
            helper.setTo(n.recipients().toArray(String[]::new));
            helper.setSubject(n.subject());
            helper.setText(n.body(), false);
            for (Notification.Attachment a : n.attachments()) {
                helper.addAttachment(a.fileName(), new ByteArrayResource(a.content()), a.contentType());
            }
            sender.send(message);
        } catch (MessagingException e) {
            throw new IllegalStateException("Could not build email", e);
        }
    }
}
