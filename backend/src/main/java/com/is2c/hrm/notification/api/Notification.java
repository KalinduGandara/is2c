package com.is2c.hrm.notification.api;

import java.util.List;

/** A message to one or more email addresses, with optional attachments (FR-074, FR-088). */
public record Notification(List<String> recipients, String subject, String body, List<Attachment> attachments) {

    public record Attachment(String fileName, String contentType, byte[] content) {
    }

    public static Notification of(List<String> recipients, String subject, String body) {
        return new Notification(recipients, subject, body, List.of());
    }
}
