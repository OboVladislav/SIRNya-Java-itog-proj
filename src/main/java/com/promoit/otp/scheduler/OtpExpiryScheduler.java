package com.promoit.otp.scheduler;

import com.promoit.otp.dao.OtpCodeDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Periodically transitions ACTIVE codes whose TTL has passed to EXPIRED. */
public class OtpExpiryScheduler {

    private static final Logger log = LoggerFactory.getLogger(OtpExpiryScheduler.class);

    private final OtpCodeDao codeDao;
    private final int intervalSeconds;
    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "otp-expiry-scheduler");
                t.setDaemon(true);
                return t;
            });

    public OtpExpiryScheduler(OtpCodeDao codeDao, int intervalSeconds) {
        this.codeDao = codeDao;
        this.intervalSeconds = intervalSeconds;
    }

    public void start() {
        executor.scheduleAtFixedRate(this::sweep, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        log.info("OTP expiry scheduler started (every {}s)", intervalSeconds);
    }

    public void stop() {
        executor.shutdownNow();
    }

    private void sweep() {
        try {
            int expired = codeDao.markExpired(Instant.now());
            if (expired > 0) {
                log.info("Marked {} OTP code(s) as EXPIRED", expired);
            }
        } catch (Exception e) {
            log.error("OTP expiry sweep failed: {}", e.getMessage(), e);
        }
    }
}
