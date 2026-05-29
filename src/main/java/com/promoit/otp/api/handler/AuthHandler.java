package com.promoit.otp.api.handler;

import com.promoit.otp.api.BaseHandler;
import com.promoit.otp.model.Role;
import com.promoit.otp.model.User;
import com.promoit.otp.service.ApiException;
import com.promoit.otp.service.TokenService;
import com.promoit.otp.service.UserService;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.util.Map;

/** Public endpoints: POST /api/auth/register and POST /api/auth/login. */
public class AuthHandler extends BaseHandler {

    private final UserService userService;

    public AuthHandler(UserService userService, TokenService tokenService) {
        super(tokenService);
        this.userService = userService;
    }

    @Override
    protected void handleRequest(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.endsWith("/register")) {
            register(exchange);
        } else if (path.endsWith("/login")) {
            login(exchange);
        } else {
            throw new ApiException(404, "Unknown auth endpoint");
        }
    }

    private void register(HttpExchange exchange) throws IOException {
        requireMethod(exchange, "POST");
        Map<String, Object> body = readBody(exchange);
        String login = requireString(body, "login");
        String password = requireString(body, "password");
        Role role = parseRole(optString(body, "role"));

        User user = userService.register(login, password, role);
        sendJson(exchange, 201, Map.of(
                "id", user.getId(),
                "login", user.getLogin(),
                "role", user.getRole().name()));
    }

    private void login(HttpExchange exchange) throws IOException {
        requireMethod(exchange, "POST");
        Map<String, Object> body = readBody(exchange);
        String login = requireString(body, "login");
        String password = requireString(body, "password");

        String token = userService.login(login, password);
        sendJson(exchange, 200, Map.of("token", token));
    }

    private Role parseRole(String raw) {
        if (raw == null || raw.isBlank()) {
            return Role.USER;
        }
        try {
            return Role.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException(400, "Unknown role: " + raw);
        }
    }
}
