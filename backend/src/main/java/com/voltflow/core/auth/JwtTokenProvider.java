package com.voltflow.core.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;

@Component
public class JwtTokenProvider {
    private final String secretKey;
    private final long expirationSeconds;

    public JwtTokenProvider(
            @Value("${voltflow.auth.jwt-secret:VoltFlowVoltFlowSecretKeyForJWTAuthSuperSecure2026}") String secretKey,
            @Value("${voltflow.auth.jwt-expiration-seconds:86400}") long expirationSeconds) {
        if (secretKey == null || secretKey.length() < 32) {
            throw new IllegalArgumentException("JWT secret must contain at least 32 characters");
        }
        if (expirationSeconds <= 0) {
            throw new IllegalArgumentException("JWT expiration must be positive");
        }
        this.secretKey = secretKey;
        this.expirationSeconds = expirationSeconds;
    }

    public String generateToken(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be null or empty");
        }
        long now = Instant.now().getEpochSecond();
        long exp = now + expirationSeconds;
        String cleanEmail = email.trim().toLowerCase(java.util.Locale.ROOT);

        String headerJson = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String payloadJson = String.format("{\"sub\":\"%s\",\"iat\":%d,\"exp\":%d}", cleanEmail, now, exp);

        String headerBase64 = base64UrlEncode(headerJson.getBytes(StandardCharsets.UTF_8));
        String payloadBase64 = base64UrlEncode(payloadJson.getBytes(StandardCharsets.UTF_8));

        String dataToSign = headerBase64 + "." + payloadBase64;
        String signature = hmacSha256(dataToSign, secretKey);

        return dataToSign + "." + signature;
    }

    public String extractEmail(String token) {
        if (token == null || !token.contains(".")) return null;

        String[] parts = token.split("\\.");
        if (parts.length != 3) return null;

        String dataToSign = parts[0] + "." + parts[1];
        String expectedSignature = hmacSha256(dataToSign, secretKey);

        if (!MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.US_ASCII),
                parts[2].getBytes(StandardCharsets.US_ASCII))) {
            return null; // Invalid signature
        }

        String payloadJson;
        try {
            byte[] payloadBytes = base64UrlDecode(parts[1]);
            payloadJson = new String(payloadBytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return null;
        }

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
