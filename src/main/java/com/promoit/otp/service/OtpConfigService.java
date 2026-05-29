package com.promoit.otp.service;

import com.promoit.otp.config.AppConfig;
import com.promoit.otp.dao.OtpConfigDao;
import com.promoit.otp.model.OtpConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OtpConfigService {

    private static final Logger log = LoggerFactory.getLogger(OtpConfigService.class);

    private final OtpConfigDao configDao;

    public OtpConfigService(OtpConfigDao configDao) {
        this.configDao = configDao;
    }

    /** Seeds the single config row from defaults if it does not exist yet. */
    public void seedDefaults(AppConfig appConfig) {
        if (configDao.find().isEmpty()) {
            OtpConfig defaults = new OtpConfig(
                    appConfig.getInt("otp.defaultLength"),
                    appConfig.getInt("otp.defaultTtlSeconds"));
            configDao.save(defaults);
            log.info("Seeded default OTP config: length={}, ttl={}s",
                    defaults.getCodeLength(), defaults.getTtlSeconds());
        }
    }

    public OtpConfig get() {
        return configDao.find()
                .orElseThrow(() -> new ApiException(500, "OTP configuration is not initialized"));
    }

    public OtpConfig update(int codeLength, int ttlSeconds) {
        if (codeLength < 4 || codeLength > 12) {
            throw new ApiException(400, "codeLength must be between 4 and 12");
        }
        if (ttlSeconds < 10 || ttlSeconds > 86_400) {
            throw new ApiException(400, "ttlSeconds must be between 10 and 86400");
        }
        OtpConfig config = new OtpConfig(codeLength, ttlSeconds);
        configDao.save(config);
        log.info("OTP config updated: length={}, ttl={}s", codeLength, ttlSeconds);
        return config;
    }
}
