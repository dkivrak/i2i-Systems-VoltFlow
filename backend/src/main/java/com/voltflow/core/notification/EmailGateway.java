package com.voltflow.core.notification;

public interface EmailGateway {
    void send(String recipient, String subject, String body);
}
