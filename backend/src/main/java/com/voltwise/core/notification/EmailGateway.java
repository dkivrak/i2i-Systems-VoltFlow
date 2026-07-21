package com.voltwise.core.notification;

public interface EmailGateway {
    void send(String recipient, String subject, String body);
}
