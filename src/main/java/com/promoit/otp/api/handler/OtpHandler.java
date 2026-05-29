package com.promoit.otp.api.handler;

import com.promoit.otp.api.BaseHandler;
import com.promoit.otp.model.OtpCode;
import com.promoit.otp.service.ApiException;
import com.promoit.otp.service.OtpService;
import com.promoit.otp.service.TokenService;
import com.promoit.otp.service.notification.NotificationChannel;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.Map;

/**
 * Authenticated user endpoints:
 * <ul>
 *   <li>POST /api/otp/generate — generate a code for an operation and deliver it</li>
 *   <li>POST /api/otp/validate — validate a previously delivered code</li>
 * </ul>
 */
public class OtpHandler extends BaseHandler {

    private final OtpService otpService;
    private final NotificationChannel defaultChannel;

    public OtpHandler(OtpService otpService, NotificationChannel defaultChannel, TokenService tokenService) {
        super(tokenService);
        this.otpService = otpService;
        this.defaultChannel = defaultChannel;
    }

    @Override
    protected void handleRequest(HttpExchange exchange) throws IOException {
        TokenService.Principal principal = authenticate(exchange);
        String path = exchange.getRequestURI().getPath();

        if (path.endsWith("/generate")) {
            generate(exchange, principal.userId());
        } else if (path.endsWith("/validate")) {
            validate(exchange, principal.userId());
        } else {
            throw new ApiException(404, "Unknown OTP endpoint");
        }
    }

    private void generate(HttpExchange exchange, long userId) throws IOException {
        requireMethod(exchange, "POST");
        Map<String, Object> body = readBody(exchange);
        String operationId = requireString(body, "operationId");
        NotificationChannel channel = parseChannel(optString(body, "channel"));
        String recipient = optString(body, "recipient");

        OtpCode code = otpService.generate(userId, operationId, channel, recipient);
        sendJson(exchange, 201, Map.of(
                "otpId", code.getId(),
                "operationId", operationId,
                "channel", channel.name(),
                "status", code.getStatus().name(),
                "expiresAt", code.getExpiresAt().toString()));
    }

    private void validate(HttpExchange exchange, long userId) throws IOException {
        requireMethod(exchange, "POST");
        Map<String, Object> body = readBody(exchange);
        String code = requireString(body, "code");
        String operationId = optString(body, "operationId");

        otpService.validate(userId, operationId, code);
        sendJson(exchange, 200, Map.of("status", "USED", "valid", true));
    }

    private NotificationChannel parseChannel(String raw) {
        if (raw == null || raw.isBlank()) {
            return defaultChannel;
        }
        try {
            return NotificationChannel.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException(400, "Unknown channel: " + raw
                    + " (allowed: FILE, EMAIL, SMS, TELEGRAM)");
        }
    }
}
