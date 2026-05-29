package com.promoit.otp.api.handler;

import com.promoit.otp.api.BaseHandler;
import com.promoit.otp.model.OtpConfig;
import com.promoit.otp.model.Role;
import com.promoit.otp.model.User;
import com.promoit.otp.service.ApiException;
import com.promoit.otp.service.OtpConfigService;
import com.promoit.otp.service.TokenService;
import com.promoit.otp.service.UserService;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Administrator-only endpoints (require an ADMIN token):
 * <ul>
 *   <li>GET/PUT /api/admin/config — read or change OTP configuration</li>
 *   <li>GET /api/admin/users — list non-admin users</li>
 *   <li>DELETE /api/admin/users/{id} — delete a user and their OTP codes</li>
 * </ul>
 */
public class AdminHandler extends BaseHandler {

    private final UserService userService;
    private final OtpConfigService configService;

    public AdminHandler(UserService userService, OtpConfigService configService, TokenService tokenService) {
        super(tokenService);
        this.userService = userService;
        this.configService = configService;
    }

    @Override
    protected void handleRequest(HttpExchange exchange) throws IOException {
        requireRole(exchange, Role.ADMIN);
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if (path.endsWith("/config")) {
            if (method.equalsIgnoreCase("GET")) {
                getConfig(exchange);
            } else if (method.equalsIgnoreCase("PUT")) {
                updateConfig(exchange);
            } else {
                throw new ApiException(405, "Method not allowed");
            }
        } else if (path.contains("/users")) {
            if (method.equalsIgnoreCase("GET")) {
                listUsers(exchange);
            } else if (method.equalsIgnoreCase("DELETE")) {
                deleteUser(exchange, path);
            } else {
                throw new ApiException(405, "Method not allowed");
            }
        } else {
            throw new ApiException(404, "Unknown admin endpoint");
        }
    }

    private void getConfig(HttpExchange exchange) throws IOException {
        OtpConfig config = configService.get();
        sendJson(exchange, 200, Map.of(
                "codeLength", config.getCodeLength(),
                "ttlSeconds", config.getTtlSeconds()));
    }

    private void updateConfig(HttpExchange exchange) throws IOException {
        Map<String, Object> body = readBody(exchange);
        int codeLength = requireInt(body, "codeLength");
        int ttlSeconds = requireInt(body, "ttlSeconds");
        OtpConfig updated = configService.update(codeLength, ttlSeconds);
        sendJson(exchange, 200, Map.of(
                "codeLength", updated.getCodeLength(),
                "ttlSeconds", updated.getTtlSeconds()));
    }

    private void listUsers(HttpExchange exchange) throws IOException {
        List<Map<String, Object>> result = new ArrayList<>();
        for (User user : userService.listNonAdminUsers()) {
            result.add(Map.of(
                    "id", user.getId(),
                    "login", user.getLogin(),
                    "role", user.getRole().name()));
        }
        sendJson(exchange, 200, result);
    }

    private void deleteUser(HttpExchange exchange, String path) throws IOException {
        long id = parseTrailingId(path);
        userService.deleteUser(id);
        sendJson(exchange, 200, Map.of("deleted", id));
    }

    private long parseTrailingId(String path) {
        String[] parts = path.split("/");
        try {
            return Long.parseLong(parts[parts.length - 1]);
        } catch (NumberFormatException e) {
            throw new ApiException(400, "User id must be provided in the path: /api/admin/users/{id}");
        }
    }
}
