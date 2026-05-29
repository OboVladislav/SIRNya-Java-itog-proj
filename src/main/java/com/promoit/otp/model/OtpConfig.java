package com.promoit.otp.model;

/**
 * Singleton configuration of OTP codes. Only one row of this ever exists in the DB.
 */
public class OtpConfig {

    private int codeLength;
    private int ttlSeconds;

    public OtpConfig() {
    }

    public OtpConfig(int codeLength, int ttlSeconds) {
        this.codeLength = codeLength;
        this.ttlSeconds = ttlSeconds;
    }

    public int getCodeLength() {
        return codeLength;
    }

    public void setCodeLength(int codeLength) {
        this.codeLength = codeLength;
    }

    public int getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(int ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }
}
