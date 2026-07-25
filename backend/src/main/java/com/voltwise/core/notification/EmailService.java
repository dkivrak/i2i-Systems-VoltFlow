package com.voltwise.core.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {
    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
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
