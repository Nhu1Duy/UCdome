package com.example.uc.dao;

import com.example.uc.model.ActivityLog;
import com.example.uc.model.User;
import com.example.uc.util.DatabaseUtil;

import java.sql.*;

/**
 * DAO xử lý toàn bộ thao tác CSDL liên quan đến UC-01.
 * Mỗi method được chú thích rõ bước luồng tương ứng.
 */
public class UserDAO {

    private static final int MAX_FAILED_ATTEMPTS = 5; // BR-02

    // ─────────────────────────────────────────────
    // 1.0.4 — Kiểm tra email + đối chiếu mật khẩu băm
    // ─────────────────────────────────────────────
    public User findByEmail(String email) throws SQLException {
        String sql = "SELECT * FROM users WHERE email = ?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return mapRow(rs);
            }
        }
        return null; // không tìm thấy → 1.0-E2
    }

    // ─────────────────────────────────────────────
    // 1.1.8 — Kiểm tra email từ Google đã tồn tại chưa
    // ─────────────────────────────────────────────
    public User findByEmailAndProvider(String email, String provider) throws SQLException {
        String sql = "SELECT * FROM users WHERE email = ? AND auth_provider = ?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, provider);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        }
        return null;
    }

    // ─────────────────────────────────────────────
    // 1.0.6 — Reset bộ đếm sai mật khẩu về 0
    // ─────────────────────────────────────────────
    public void resetFailedAttempts(int userId) throws SQLException {
        String sql = "UPDATE users SET failed_login_attempts = 0 WHERE id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ps.executeUpdate();
        }
    }

    // ─────────────────────────────────────────────
    // 1.0-E2 — Tăng bộ đếm sai mật khẩu lên 1
    //          Nếu >= MAX → tự động LOCK (BR-02)
    // ─────────────────────────────────────────────
    public void increaseFailedAttempts(int userId) throws SQLException {
        String sql =
                "UPDATE users " +
                        "SET failed_login_attempts = failed_login_attempts + 1, " +
                        "    status = CASE " +
                        "        WHEN failed_login_attempts + 1 >= ? THEN 'LOCKED' " +
                        "        ELSE status " +
                        "    END " +
                        "WHERE id = ?";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, MAX_FAILED_ATTEMPTS);
            ps.setInt(2, userId);
            ps.executeUpdate();
        }
    }

    // ─────────────────────────────────────────────
    // 1.2.1 → 1.2.3 — Tự động đăng ký khi đăng nhập Google lần đầu
    // ─────────────────────────────────────────────
    public User createGoogleUser(String email, String fullName) throws SQLException {
        // BR-03: gán role USER mặc định
        String sql =
                "INSERT INTO users (email, role, status, auth_provider, full_name) " +
                        "VALUES (?, 'USER', 'ACTIVE', 'GOOGLE', ?)";

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, email);
            ps.setString(2, fullName);

            ps.executeUpdate();

            // 1.2.3 — Nhận mã định danh (ID) tự sinh từ CSDL
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return findById(keys.getInt(1));
                }
            }
        }

        throw new SQLException("Không lấy được ID sau khi tạo user Google");
    }

    // Helper: tìm theo ID (dùng sau INSERT)
    public User findById(int id) throws SQLException {
        String sql = "SELECT * FROM users WHERE id = ?";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return mapRow(rs);
        }
        return null;
    }

    // ─────────────────────────────────────────────
    // 1.0.9 / 1.0-E3 / 1.1-E1 / 1.2.5
    // Ghi nhận ActivityLog
    // ─────────────────────────────────────────────
    public void insertLog(ActivityLog log) throws SQLException {
        String sql = "INSERT INTO activity_log (user_id, action, description) VALUES (?, ?, ?)";
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            if (log.getUserId() != null) ps.setInt(1, log.getUserId());
            else ps.setNull(1, Types.INTEGER);
            ps.setString(2, log.getAction());
            ps.setString(3, log.getDescription());
            ps.executeUpdate();
        }
    }

    // ─── Map ResultSet → User object ───────────────
    private User mapRow(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getInt("id"));
        u.setEmail(rs.getString("email"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setRole(rs.getString("role"));
        u.setStatus(rs.getString("status"));
        u.setFailedLoginAttempts(rs.getInt("failed_login_attempts"));
        u.setAuthProvider(rs.getString("auth_provider"));
        u.setFullName(rs.getString("full_name"));
        return u;
    }
}
