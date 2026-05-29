package com.promoit.otp.db;

import com.promoit.otp.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Creates the target database (if missing) and the required tables on start-up.
 */
public class SchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(SchemaInitializer.class);

    private final AppConfig config;

    public SchemaInitializer(AppConfig config) {
        this.config = config;
    }

    public void initialize() {
        createDatabaseIfMissing();
        createTables();
    }

    private void createDatabaseIfMissing() {
        String adminUrl = config.get("db.adminUrl");
        String dbName = config.get("db.name");
        String user = config.get("db.user");
        String password = config.get("db.password");

        try (Connection conn = DriverManager.getConnection(adminUrl, user, password)) {
            boolean exists;
            try (var ps = conn.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?")) {
                ps.setString(1, dbName);
                try (ResultSet rs = ps.executeQuery()) {
                    exists = rs.next();
                }
            }
            if (!exists) {
                try (Statement st = conn.createStatement()) {
                    // dbName comes from local config, not user input.
                    st.executeUpdate("CREATE DATABASE \"" + dbName + "\"");
                    log.info("Created database '{}'", dbName);
                }
            } else {
                log.info("Database '{}' already exists", dbName);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to ensure database exists", e);
        }
    }

    private void createTables() {
        String usersTable = """
                CREATE TABLE IF NOT EXISTS users (
                    id            BIGSERIAL PRIMARY KEY,
                    login         VARCHAR(100) NOT NULL UNIQUE,
                    password_hash VARCHAR(255) NOT NULL,
                    role          VARCHAR(20)  NOT NULL
                )
                """;

        String configTable = """
                CREATE TABLE IF NOT EXISTS otp_config (
                    id          INT PRIMARY KEY DEFAULT 1,
                    code_length INT NOT NULL,
                    ttl_seconds INT NOT NULL,
                    CONSTRAINT otp_config_singleton CHECK (id = 1)
                )
                """;

        String codesTable = """
                CREATE TABLE IF NOT EXISTS otp_codes (
                    id           BIGSERIAL PRIMARY KEY,
                    user_id      BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                    operation_id VARCHAR(200),
                    code         VARCHAR(20) NOT NULL,
                    status       VARCHAR(20) NOT NULL,
                    created_at   TIMESTAMPTZ NOT NULL,
                    expires_at   TIMESTAMPTZ NOT NULL
                )
                """;

        try (Connection conn = DriverManager.getConnection(
                config.get("db.url"), config.get("db.user"), config.get("db.password"));
             Statement st = conn.createStatement()) {
            st.executeUpdate(usersTable);
            st.executeUpdate(configTable);
            st.executeUpdate(codesTable);
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_otp_codes_status ON otp_codes(status)");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_otp_codes_user ON otp_codes(user_id)");
            log.info("Schema verified (users, otp_config, otp_codes)");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create tables", e);
        }
    }
}
