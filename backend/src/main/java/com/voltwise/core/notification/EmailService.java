package com.voltwise.core.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

import java.util.Properties;

@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private final JavaMailSenderImpl mailSender;

    public EmailService() {
        this.mailSender = new JavaMailSenderImpl();
        this.mailSender.setHost("sandbox.smtp.mailtrap.io");
        this.mailSender.setPort(2525);
        this.mailSender.setUsername("72503ef112ca2a");
        this.mailSender.setPassword("0de7818d58ec0f");

        Properties props = this.mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "false");
        props.put("mail.smtp.ssl.trust", "sandbox.smtp.mailtrap.io");
    }

    /**
     * E-posta gönderir. SMTP/kota hatalarında exception FIRLATMAZ;
     * hata yalnızca log.warn ile kaydedilir ve sistem çalışmaya devam eder.
     *
     * @return true  → gönderim başarılı
     *         false → gönderim sessizce başarısız oldu (kota, bağlantı vb.)
     */
    public boolean sendEmail(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("VoltFlow <noreply@voltflow.local>");
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Gönderim Başarılı - E-posta alıcısı: {}, Konu: {}", to, subject);
            return true;
        } catch (MailException ex) {
            // Kota dolması, kimlik doğrulama hatası, bağlantı sorunları vb.
            // — sessizce geç, frontend'e hata yansıtma.
            log.warn("SMTP sessiz hata (kota/bağlantı?) — alıcı: {}, sebep: {}", to, ex.getMessage());
            return false;
        } catch (Exception ex) {
            log.warn("SMTP beklenmedik hata — alıcı: {}, sebep: {}", to, ex.getMessage());
            return false;
        }
    }
}
