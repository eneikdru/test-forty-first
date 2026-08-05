package com.eneik.generated.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

public class JwtTokenUtil {
    private final byte[] secretKey;

    public JwtTokenUtil() {
        // Generate a cryptographically secure random 256-bit key at startup to avoid hardcoded secrets in code
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        this.secretKey = key;
    }

    public String generateToken(Map<String, Object> claims, ObjectMapper objectMapper) throws Exception {
        String headerJson = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String payloadJson = objectMapper.writeValueAsString(claims);

        String encodedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
        String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));

        String signingInput = encodedHeader + "." + encodedPayload;

        Mac sha256HMAC = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey, "HmacSHA256");
        sha256HMAC.init(secretKeySpec);

        byte[] signatureBytes = sha256HMAC.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        String encodedSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);

        return signingInput + "." + encodedSignature;
    }

    public boolean verifyToken(String token, ObjectMapper objectMapper) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return false;
            }
            String header = parts[0];
            String payload = parts[1];
            String signature = parts[2];

            String signingInput = header + "." + payload;

            Mac sha256HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey, "HmacSHA256");
            sha256HMAC.init(secretKeySpec);

            byte[] signatureBytes = sha256HMAC.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
            String expectedSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);

            // Constant-time comparison to prevent timing attacks
            return MessageDigest.isEqual(
                signature.getBytes(StandardCharsets.UTF_8),
                expectedSignature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> parseClaims(String token, ObjectMapper objectMapper) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }
            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            String payloadJson = new String(payloadBytes, StandardCharsets.UTF_8);
            return objectMapper.readValue(payloadJson, Map.class);
        } catch (Exception e) {
            return null;
        }
    }
}
