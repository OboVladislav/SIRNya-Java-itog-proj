package com.promoit.otp.service.notification;

/** A single delivery channel for OTP codes. */
public interface NotificationService {

    NotificationChannel channel();

    /**
     * Delivers a code to the recipient.
     *
     * @param recipient channel-specific address (email, phone, chat id); may be {@code null}
     *                  when the channel uses a configured default.
     * @param code      the generated OTP code.
     */
    void sendCode(String recipient, String code);
}
