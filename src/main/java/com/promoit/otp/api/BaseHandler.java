package com.promoit.otp.api;

import com.promoit.otp.service.ApiException;
import com.promoit.otp.service.TokenService;
import com.promoit.otp.model.Role;
import com.promoit.otp.util.Json;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Shared behaviour for all HTTP handlers: per-request logging, JSON helpers,
 * uniform error mapping, and token-based authentication / authorization.
 */
public abstract class BaseHandler implements HttpHandler {

    protected static final Logger log = LoggerFactory.getLogger(BaseHandler.class);

    private final TokenService tokenService;

    protected BaseHandler(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public final void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        long started = System.currentTimeMillis();
        log.info("--> {} {} from {}", method, path, exchange.getRemoteAddress());
        try {
            handleRequest(exchange);
        } catch (ApiException e) {
            log.warn("{} {} -> {} ({})", method, path, e.getStatusCode(), e.getMessage());
            sendError(exchange, e.getStatusCode(), e.getMessage());
        } catch (Exception e) {
            log.error("{} {} -> 500 ({})", method, path, e.getMessage(), e);
            sendError(exchange, 500, "Internal server error");
        } finally {
            log.info("<-- {} {} done in {}ms", method, path, System.currentTimeMillis() - started);
            exchange.close();
        }
    }

    protected abstract void handleRequest(HttpExchange exchange) throws IOException;

    // ---- request parsing -------------------------------------------------

    protected Map<String, Object> readBody(HttpExchange exchange) throws IOException {
        byte[] bytes;
        try (InputStream in = exchange.getRequestBody()) {
            bytes = in.readAllBytes();
        }
        if (bytes.length == 0) {
            return Map.of();
        }
        try {
            Map<String, Object> body = Json.readMap(new ByteArrayInputStream(bytes));
            return body == null ? Map.of() : body;
        } catch (IOException e) {
            throw new ApiException(400, "Invalid JSON body");
        }
    }

    protected String requireString(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null || value.toString().isBlank()) {
            throw new ApiException(400, "Field '" + key + "' is required");
        }
        return value.toString();
    }

    protected String optString(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? null : value.toString();
    }

    protected int requireInt(Map<String, Object> body, String key) {
        Object value = body.get(key);
        if (value == null) {
            throw new ApiException(400, "Field '" + key + "' is required");
        }
        try {
            return value instanceof Number n ? n.intValue() : Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            throw new ApiException(400, "Field '" + key + "' must be an integer");
        }
    }

    protected void requireMethod(HttpExchange exchange, String method) {
        if (!exchange.getRequestMethod().equalsIgnoreCase(method)) {
            throw new ApiException(405, "Method not allowed");
        }
    }

    // ---- auth ------------------------------------------------------------

    protected TokenService.Principal authenticate(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new ApiException(401, "Missing or malformed Authorization header");
        }
        String token = header.substring("Bearer ".length()).trim();
        return tokenService.verify(token)
                .orElseThrow(() -> new ApiException(401, "Invalid or expired token"));
    }

    protected TokenService.Principal requireRole(HttpExchange exchange, Role role) {
        TokenService.Principal principal = authenticate(exchange);
        if (principal.role() != role) {
            throw new ApiException(403, "Requires role " + role);
        }
        return principal;
    }

    // ---- responses -------------------------------------------------------

    protected void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] bytes = Json.write(body).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    protected void sendError(HttpExchange exchange, int status, String message) throws IOException {
        sendJson(exchange, status, Map.of("error", message));
    }
}
