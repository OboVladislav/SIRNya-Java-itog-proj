package com.promoit.otp.service.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

/** Persists generated codes by appending them to a file in the project root. */
public class FileNotificationService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(FileNotificationService.class);

    private final Path path;

    public FileNotificationService(String filePath) {
        this.path = Path.of(filePath);
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.FILE;
    }

    @Override
    public void sendCode(String recipient, String code) {
        String line = String.format("%s\trecipient=%s\tcode=%s%n",
                Instant.now(), recipient == null ? "-" : recipient, code);
        try {
            Files.writeString(path, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            log.info("OTP code written to file {}", path.toAbsolutePath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to write OTP code to file", e);
        }
    }
}
