package com.voltwise.core.auth;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;

@Component
public class JwtTokenProvider {
    private static final String SECRET_KEY = "VoltWiseVoltFlowSecretKeyForJWTAuthSuperSecure2026";
    private static final long EXPIRATION_SECONDS = 86400; // 24 Hours

    public String generateToken(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be null or empty");
        }
        long now = Instant.now().getEpochSecond();
        long exp = now + EXPIRATION_SECONDS;
        String cleanEmail = email.trim().toLowerCase(java.util.Locale.ROOT);

        String headerJson = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String payloadJson = String.format("{\"sub\":\"%s\",\"iat\":%d,\"exp\":%d}", cleanEmail, now, exp);

        String headerBase64 = base64UrlEncode(headerJson.getBytes(StandardCharsets.UTF_8));
        String payloadBase64 = base64UrlEncode(payloadJson.getBytes(StandardCharsets.UTF_8));

        String dataToSign = headerBase64 + "." + payloadBase64;
        String signature = hmacSha256(dataToSign, SECRET_KEY);

        return dataToSign + "." + signature;
    }

    public String extractEmail(String token) {
        if (token == null || !token.contains(".")) return null;

        String[] parts = token.split("\\.");
        if (parts.length != 3) return null;

        String dataToSign = parts[0] + "." + parts[1];
        String expectedSignature = hmacSha256(dataToSign, SECRET_KEY);

        if (!expectedSignature.equals(parts[2])) {
            return null; // Invalid signature
        }

        byte[] payloadBytes = base64UrlDecode(parts[1]);
        String payloadJson = new String(payloadBytes, StandardCharsets.UTF_8);

        long exp = extractLongField(payloadJson, "exp");
        if (exp > 0 && Instant.now().getEpochSecond() > exp) {
            return null; // Expired
        }

        return extractStringField(payloadJson, "sub");
    }

    private String hmacSha256(String data, String key) {
        try {
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256Hmac.init(secretKey);
            byte[] signedBytes = sha256Hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return base64UrlEncode(signedBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("Failed to generate HMAC SHA256 signature", e);
        }
    }

    private String base64UrlEncode(byte[] input) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(input);
    }

    private byte[] base64UrlDecode(String input) {
        return Base64.getUrlDecoder().decode(input);
    }

    private String extractStringField(String json, String field) {
        String key = "\"" + field + "\":\"";
        int idx = json.indexOf(key);
        if (idx == -1) return null;
        int start = idx + key.length();
        int end = json.indexOf("\"", start);
        return end != -1 ? json.substring(start, end) : null;
    }

    private long extractLongField(String json, String field) {
        String key = "\"" + field + "\":";
        int idx = json.indexOf(key);
        if (idx == -1) return -1;
        int start = idx + key.length();
        int end = json.indexOf(",", start);
        if (end == -1) end = json.indexOf("}", start);
        if (end == -1) return -1;
        try {
            return Long.parseLong(json.substring(start, end).trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
