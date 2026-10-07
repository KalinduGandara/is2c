package com.is2c.hrm.notification.api;

/** Delivery mechanism (email now; SMS/messaging apps later). Throws on delivery failure. */
public interface NotificationChannel {

    String name();

    void send(Notification notification);
}
