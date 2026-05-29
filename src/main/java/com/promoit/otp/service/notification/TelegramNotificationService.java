package com.promoit.otp.service.notification;

import com.promoit.otp.util.PropertiesLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/** Sends OTP codes through a Telegram bot using the Telegram Bot API. */
public class TelegramNotificationService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotificationService.class);

    private final String apiUrl;
    private final String defaultChatId;
    private HttpClient httpClient;

    public TelegramNotificationService() {
        Properties config = loadConfig();
        this.apiUrl = config.getProperty("telegram.apiUrl");
        this.defaultChatId = config.getProperty("telegram.chatId");
    }

    private Properties loadConfig() {
        return PropertiesLoader.load("telegram.properties");
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.TELEGRAM;
    }

    @Override
    public void sendCode(String recipient, String code) {
        String chatId = (recipient == null || recipient.isBlank()) ? defaultChatId : recipient;
        String message = String.format("Your confirmation code is: %s", code);
        String url = String.format("%s?chat_id=%s&text=%s",
                apiUrl, urlEncode(chatId), urlEncode(message));
        sendTelegramRequest(url);
    }

    private synchronized HttpClient httpClient() {
        if (httpClient == null) {
            httpClient = HttpClient.newHttpClient();
        }
        return httpClient;
    }

    private void sendTelegramRequest(String url) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();
            if (statusCode != 200) {
                log.error("Telegram API error. Status code: {}, body: {}", statusCode, response.body());
                throw new RuntimeException("Telegram API returned status " + statusCode);
            }
            log.info("Telegram message sent successfully");
        } catch (InterruptedException e) {
            log.error("Error sending Telegram message: {}", e.getMessage(), e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Telegram send interrupted", e);
        } catch (IOException e) {
            log.error("Error sending Telegram message: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to send Telegram message", e);
        }
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
