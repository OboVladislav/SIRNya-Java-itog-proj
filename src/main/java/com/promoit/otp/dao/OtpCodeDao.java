package com.promoit.otp.dao;

import com.promoit.otp.db.Database;
import com.promoit.otp.model.OtpCode;
import com.promoit.otp.model.OtpStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

public class OtpCodeDao {

    private final Database db;

    public OtpCodeDao(Database db) {
        this.db = db;
    }

    public OtpCode insert(OtpCode code) {
        String sql = """
                INSERT INTO otp_codes (user_id, operation_id, code, status, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, code.getUserId());
            ps.setString(2, code.getOperationId());
            ps.setString(3, code.getCode());
            ps.setString(4, code.getStatus().name());
            ps.setTimestamp(5, Timestamp.from(code.getCreatedAt()));
            ps.setTimestamp(6, Timestamp.from(code.getExpiresAt()));
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                code.setId(keys.getLong(1));
            }
            return code;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to insert OTP code", e);
        }
    }

    /** Finds an ACTIVE code for the given user, optionally scoped to an operation. */
    public Optional<OtpCode> findActive(long userId, String operationId, String code) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, user_id, operation_id, code, status, created_at, expires_at "
                        + "FROM otp_codes WHERE user_id = ? AND code = ? AND status = 'ACTIVE'");
        if (operationId != null) {
            sql.append(" AND operation_id = ?");
        }
        sql.append(" ORDER BY created_at DESC LIMIT 1");

        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            ps.setLong(1, userId);
            ps.setString(2, code);
            if (operationId != null) {
                ps.setString(3, operationId);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find active OTP code", e);
        }
    }

    public void updateStatus(long id, OtpStatus status) {
        String sql = "UPDATE otp_codes SET status = ? WHERE id = ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setLong(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to update OTP status", e);
        }
    }

    /** Marks every ACTIVE code whose expiry has passed as EXPIRED. Returns affected rows. */
    public int markExpired(Instant now) {
        String sql = "UPDATE otp_codes SET status = 'EXPIRED' WHERE status = 'ACTIVE' AND expires_at < ?";
        try (Connection conn = db.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.from(now));
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to expire OTP codes", e);
        }
    }

    private OtpCode map(ResultSet rs) throws SQLException {
        OtpCode code = new OtpCode();
        code.setId(rs.getLong("id"));
        code.setUserId(rs.getLong("user_id"));
        code.setOperationId(rs.getString("operation_id"));
        code.setCode(rs.getString("code"));
        code.setStatus(OtpStatus.valueOf(rs.getString("status")));
        code.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        code.setExpiresAt(rs.getTimestamp("expires_at").toInstant());
        return code;
    }
}
