package com.promoit.otp.dao;

import com.promoit.otp.db.Database;
import com.promoit.otp.model.OtpConfig;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * Access to the single-row OTP configuration table. The row is pinned to id = 1,
 * so it is physically impossible to store more than one configuration.
 */
public class OtpConfigDao {

    private final Database db;

    public OtpConfigDao(Database db) {
        this.db = db;
    }

    public Optional<OtpConfig> find() {
        String sql = "SELECT code_length, ttl_seconds FROM otp_config WHERE id = 1";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                return Optional.of(new OtpConfig(rs.getInt("code_length"), rs.getInt("ttl_seconds")));
            }
            return Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to load OTP config", e);
        }
    }

    /** Inserts or updates the single configuration row. */
    public void save(OtpConfig config) {
        String sql = """
                INSERT INTO otp_config (id, code_length, ttl_seconds)
                VALUES (1, ?, ?)
                ON CONFLICT (id) DO UPDATE
                SET code_length = EXCLUDED.code_length,
                    ttl_seconds = EXCLUDED.ttl_seconds
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, config.getCodeLength());
            ps.setInt(2, config.getTtlSeconds());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to save OTP config", e);
        }
    }
}
