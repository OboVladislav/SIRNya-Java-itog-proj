package com.promoit.otp.service.notification;

import java.util.EnumMap;
import java.util.Map;

/** Routes a code to the requested {@link NotificationChannel}. */
public class NotificationDispatcher {

    private final Map<NotificationChannel, NotificationService> services = new EnumMap<>(NotificationChannel.class);

    public NotificationDispatcher(String filePath) {
        register(new FileNotificationService(filePath));
        register(new EmailNotificationService());
        register(new SmsNotificationService());
        register(new TelegramNotificationService());
    }

    private void register(NotificationService service) {
        services.put(service.channel(), service);
    }

    public void send(NotificationChannel channel, String recipient, String code) {
        NotificationService service = services.get(channel);
        if (service == null) {
            throw new IllegalArgumentException("Unsupported channel: " + channel);
        }
        service.sendCode(recipient, code);
    }
}
