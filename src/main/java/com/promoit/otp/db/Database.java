package com.promoit.otp.db;

import com.promoit.otp.config.AppConfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Thin JDBC connection provider. Every DAO call opens a short-lived connection
 * through {@link #getConnection()}; no connection pool is used to keep the
 * dependency surface minimal, as required by the assignment (plain JDBC).
 */
public class Database {

    private final String url;
    private final String user;
    private final String password;

    public Database(AppConfig config) {
        this.url = config.get("db.url");
        this.user = config.get("db.user");
        this.password = config.get("db.password");
    }

    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }
}
