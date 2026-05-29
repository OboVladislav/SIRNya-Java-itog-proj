package com.promoit.otp;

import com.promoit.otp.api.HttpServerApp;
import com.promoit.otp.api.handler.AdminHandler;
import com.promoit.otp.api.handler.AuthHandler;
import com.promoit.otp.api.handler.OtpHandler;
import com.promoit.otp.config.AppConfig;
import com.promoit.otp.dao.OtpCodeDao;
import com.promoit.otp.dao.OtpConfigDao;
import com.promoit.otp.dao.UserDao;
import com.promoit.otp.db.Database;
import com.promoit.otp.db.SchemaInitializer;
import com.promoit.otp.scheduler.OtpExpiryScheduler;
import com.promoit.otp.service.OtpConfigService;
import com.promoit.otp.service.OtpService;
import com.promoit.otp.service.TokenService;
import com.promoit.otp.service.UserService;
import com.promoit.otp.service.notification.NotificationChannel;
import com.promoit.otp.service.notification.NotificationDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Application entry point: wires layers together and starts the HTTP server. */
public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws Exception {
        AppConfig config = new AppConfig();

        // --- DB & schema -------------------------------------------------
        new SchemaInitializer(config).initialize();
        Database database = new Database(config);

        // --- DAO layer ---------------------------------------------------
        UserDao userDao = new UserDao(database);
        OtpConfigDao otpConfigDao = new OtpConfigDao(database);
        OtpCodeDao otpCodeDao = new OtpCodeDao(database);

        // --- Service layer ----------------------------------------------
        TokenService tokenService = new TokenService(config);
        UserService userService = new UserService(userDao, tokenService);
        OtpConfigService otpConfigService = new OtpConfigService(otpConfigDao);
        otpConfigService.seedDefaults(config);

        NotificationDispatcher dispatcher = new NotificationDispatcher(config.get("otp.file.path"));
        OtpService otpService = new OtpService(otpCodeDao, otpConfigService, dispatcher);
        NotificationChannel defaultChannel =
                NotificationChannel.valueOf(config.get("otp.defaultChannel").toUpperCase());

        // --- Background expiry job --------------------------------------
        OtpExpiryScheduler scheduler =
                new OtpExpiryScheduler(otpCodeDao, config.getInt("otp.expiryScanIntervalSeconds"));
        scheduler.start();

        // --- API layer ---------------------------------------------------
        int port = config.getInt("server.port");
        HttpServerApp server = new HttpServerApp(port)
                .route("/api/auth/", new AuthHandler(userService, tokenService))
                .route("/api/admin/", new AdminHandler(userService, otpConfigService, tokenService))
                .route("/api/otp/", new OtpHandler(otpService, defaultChannel, tokenService));
        server.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down...");
            scheduler.stop();
            server.stop();
        }));

        log.info("OTP service is ready. Default channel = {}", defaultChannel);
    }
}
