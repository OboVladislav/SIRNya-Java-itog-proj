package com.promoit.otp.service;

import com.promoit.otp.config.AppConfig;
import com.promoit.otp.model.Role;
import com.promoit.otp.util.Json;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/**
 * Issues and verifies compact, signed bearer tokens (a minimal JWT-like scheme):
 * {@code base64url(payloadJson).base64url(hmacSha256(payload))}.
 * The payload carries the user id, login, role and an absolute expiry timestamp.
 */
public class TokenService {

    private final byte[] secret;
    private final long ttlSeconds;

    public TokenService(AppConfig config) {
        this.secret = config.get("token.secret").getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = config.getInt("token.ttlSeconds");
    }

    public String issue(long userId, String login, Role role) {
        long exp = System.currentTimeMillis() / 1000 + ttlSeconds;
        String payloadJson = Json.write(Map.of(
                "sub", userId,
                "login", login,
                "role", role.name(),
                "exp", exp));
        String payload = base64Url(payloadJson.getBytes(StandardCharsets.UTF_8));
        String signature = base64Url(hmac(payload));
        return payload + "." + signature;
    }

    public Optional<Principal> verify(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String[] parts = token.split("\\.");
        if (parts.length != 2) {
            return Optional.empty();
        }
        String expectedSig = base64Url(hmac(parts[0]));
        if (!constantTimeEquals(expectedSig, parts[1])) {
            return Optional.empty();
        }
        try {
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> claims = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(payloadJson, Map.class);
            long exp = ((Number) claims.get("exp")).longValue();
            if (System.currentTimeMillis() / 1000 >= exp) {
                return Optional.empty();
            }
            long sub = ((Number) claims.get("sub")).longValue();
            String login = (String) claims.get("login");
            Role role = Role.valueOf((String) claims.get("role"));
            return Optional.of(new Principal(sub, login, role));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private byte[] hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign token", e);
        }
    }

    private static String base64Url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    /** Authenticated caller extracted from a valid token. */
    public record Principal(long userId, String login, Role role) {
    }
}
