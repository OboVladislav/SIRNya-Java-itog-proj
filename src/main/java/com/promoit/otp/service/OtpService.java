package com.promoit.otp.service;

import com.promoit.otp.dao.OtpCodeDao;
import com.promoit.otp.model.OtpCode;
import com.promoit.otp.model.OtpConfig;
import com.promoit.otp.model.OtpStatus;
import com.promoit.otp.service.notification.NotificationChannel;
import com.promoit.otp.service.notification.NotificationDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpCodeDao codeDao;
    private final OtpConfigService configService;
    private final NotificationDispatcher dispatcher;

    public OtpService(OtpCodeDao codeDao, OtpConfigService configService, NotificationDispatcher dispatcher) {
        this.codeDao = codeDao;
        this.configService = configService;
        this.dispatcher = dispatcher;
    }

    /** Generates a code bound to the operation, persists it, and delivers it over the channel. */
    public OtpCode generate(long userId, String operationId, NotificationChannel channel, String recipient) {
        OtpConfig config = configService.get();
        String value = randomCode(config.getCodeLength());

        Instant now = Instant.now();
        OtpCode code = new OtpCode();
        code.setUserId(userId);
        code.setOperationId(operationId);
        code.setCode(value);
        code.setStatus(OtpStatus.ACTIVE);
        code.setCreatedAt(now);
        code.setExpiresAt(now.plus(config.getTtlSeconds(), ChronoUnit.SECONDS));
        codeDao.insert(code);

        log.info("Generated OTP id={} for user={} operation={} via {}",
                code.getId(), userId, operationId, channel);

        dispatcher.send(channel, recipient, value);
        return code;
    }

    /** Validates a code for the user; on success transitions it to USED. */
    public void validate(long userId, String operationId, String code) {
        if (code == null || code.isBlank()) {
            throw new ApiException(400, "code is required");
        }
        OtpCode found = codeDao.findActive(userId, operationId, code)
                .orElseThrow(() -> new ApiException(400, "Code is invalid, already used, or expired"));

        if (found.getExpiresAt().isBefore(Instant.now())) {
            codeDao.updateStatus(found.getId(), OtpStatus.EXPIRED);
            throw new ApiException(400, "Code has expired");
        }

        codeDao.updateStatus(found.getId(), OtpStatus.USED);
        log.info("OTP id={} validated and marked USED for user={}", found.getId(), userId);
    }

    private String randomCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }
}
