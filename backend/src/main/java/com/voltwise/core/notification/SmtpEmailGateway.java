package com.voltwise.core.notification;

import com.voltwise.core.config.VoltWiseProperties;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class SmtpEmailGateway implements EmailGateway {
    private final JavaMailSender mailSender;
    private final VoltWiseProperties properties;

    public SmtpEmailGateway(JavaMailSender mailSender, VoltWiseProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(String recipient, String subject, String body) {
        if (!properties.getMail().isEnabled()) throw new IllegalStateException("Email delivery is disabled");
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.getMail().getFrom());
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(body);
        mailSender.send(message);
    }
}
