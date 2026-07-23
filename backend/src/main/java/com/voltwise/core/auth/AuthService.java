package com.voltwise.core.auth;

import com.voltwise.core.auth.AuthDtos.AuthResponse;
import com.voltwise.core.auth.AuthDtos.SendOtpRequest;
import com.voltwise.core.auth.AuthDtos.SendOtpResponse;
import com.voltwise.core.auth.AuthDtos.VerifyOtpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {
    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int OTP_EXPIRE_SECONDS = 300; // 5 Minutes
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, OtpEntry> otpCache = new ConcurrentHashMap<>();
    private final JavaMailSender mailSender;
    private final JwtTokenProvider jwtTokenProvider;
    private final String mailFrom;

    public AuthService(JavaMailSender mailSender, JwtTokenProvider jwtTokenProvider,
                       @Value("${voltwise.mail.from:noreply@voltflow.space}") String mailFrom) {
        this.mailSender = mailSender;
        this.jwtTokenProvider = jwtTokenProvider;
        this.mailFrom = mailFrom;
    }

    public SendOtpResponse sendOtp(SendOtpRequest request) {
        String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);
        String code = String.format("%06d", RANDOM.nextInt(1000000));
        Instant expiresAt = Instant.now().plusSeconds(OTP_EXPIRE_SECONDS);

        otpCache.put(email, new OtpEntry(code, expiresAt));
        log.info("OTP generated for email {}: {} (Expires in 5m)", email, code);

        sendEmail(email, code);

        return new SendOtpResponse("Doğrulama kodu e-posta adresinize gönderildi.", OTP_EXPIRE_SECONDS);
    }

    public AuthResponse verifyOtp(VerifyOtpRequest request) {
        String email = request.email().trim().toLowerCase(java.util.Locale.ROOT);
        String code = request.code().trim();

        OtpEntry entry = otpCache.get(email);
        if (entry == null) {
            throw new IllegalArgumentException("Doğrulama kodu bulunamadı veya süresi doldu. Lütfen tekrar kod isteyin.");
        }

        if (Instant.now().isAfter(entry.expiresAt())) {
            otpCache.remove(email);
            throw new IllegalArgumentException("Doğrulama kodunun süresi dolmuş. Lütfen tekrar kod isteyin.");
        }

        if (!entry.code().equals(code)) {
            throw new IllegalArgumentException("Girdiğiniz doğrulama kodu hatalı.");
        }

        // Single-use OTP code: invalidate immediately
        otpCache.remove(email);

        String token = jwtTokenProvider.generateToken(email);
        return new AuthResponse(token, email, "Giriş başarılı.");
    }

    private void sendEmail(String recipient, String code) {
        try {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(mailFrom);
            mail.setTo(recipient);
            mail.setSubject("VoltFlow - Giriş Doğrulama Kodunuz");
            mail.setText(String.format("Merhaba,\n\nVoltFlow hesabınıza giriş yapmak için tek kullanımlık doğrulama kodunuz: %s\n\nBu kod 5 dakika boyunca geçerlidir.\n\nİyi günler,\nVoltFlow Ekibi", code));
            mailSender.send(mail);
            log.info("OTP email successfully sent to {}", recipient);
        } catch (Exception ex) {
            log.warn("Could not send mail via SMTP (SendGrid/Mailpit), logging OTP code for fallback: email={} code={}", recipient, code, ex);
        }
    }

    private record OtpEntry(String code, Instant expiresAt) {}
}
