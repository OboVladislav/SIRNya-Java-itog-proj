package com.promoit.otp.config;

import com.promoit.otp.util.PropertiesLoader;

import java.util.Properties;

/**
 * Loads application-wide settings from {@code application.properties} on the classpath.
 * Channel-specific settings (email/sms/telegram) are loaded by their own services.
 */
public final class AppConfig {

    private final Properties props;

    public AppConfig() {
        this("application.properties");
    }

    public AppConfig(String resource) {
        this.props = PropertiesLoader.load(resource);
    }

    public String get(String key) {
        String value = props.getProperty(key);
        if (value == null) {
            throw new IllegalStateException("Missing required property: " + key);
        }
        return value.trim();
    }

    public String get(String key, String defaultValue) {
        return props.getProperty(key, defaultValue).trim();
    }

    public int getInt(String key) {
        return Integer.parseInt(get(key));
    }

    public int getInt(String key, int defaultValue) {
        String value = props.getProperty(key);
        return value == null ? defaultValue : Integer.parseInt(value.trim());
    }
}
