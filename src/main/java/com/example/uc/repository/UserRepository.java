package com.example.uc.repository;

import com.example.uc.cofig.DatabaseConfig;
import com.example.uc.exception.DatabaseException;
import com.example.uc.model.ActivityLog;
import com.example.uc.model.User;
import com.example.uc.model.User.AuthProvider;
import com.example.uc.model.User.Role;
import com.example.uc.model.User.Status;

import java.sql.*;

public class UserRepository {

    private static final int MAX_FAILED_ATTEMPTS = 5;

    // ════════════════════════════════════════════════════════════════
    //  TRUY VẤN USER
    // ════════════════════════════════════════════════════════════════

    /**
     * 1.0.4 — Tìm user theo email.
     * Trả null nếu không tìm thấy → AuthService xử lý 1.0-E2.
     */
    public User findByEmail(String email) {
        final String sql = "SELECT * FROM users WHERE email = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
            return null;

        } catch (SQLException e) {
            throw new DatabaseException("findByEmail thất bại cho email: " + email, e);
        }
    }

    /**
     * 1.1.8 — Kiểm tra email từ Google đã tồn tại với provider=GOOGLE chưa.
     * Truyền vào AuthProvider enum thay vì String thô.
     */
    public User findByEmailAndProvider(String email, AuthProvider provider) {
        final String sql = "SELECT * FROM users WHERE email = ? AND auth_provider = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email);
            ps.setString(2, provider.name());   // enum → String cho MySQL ENUM column

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
            return null;

        } catch (SQLException e) {
            throw new DatabaseException(
                    "findByEmailAndProvider thất bại: email=" + email
                            + ", provider=" + provider, e);
        }
    }

    /** Helper: tìm theo ID — dùng sau INSERT để lấy bản ghi vừa tạo (1.2.3). */
    public User findById(int id) {
        final String sql = "SELECT * FROM users WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
            return null;

        } catch (SQLException e) {
            throw new DatabaseException("findById thất bại cho id=" + id, e);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  CẬP NHẬT TRẠNG THÁI ĐĂNG NHẬP
    // ════════════════════════════════════════════════════════════════

    /** 1.0.6 — Reset bộ đếm sai mật khẩu về 0 sau khi đăng nhập thành công. */
    public void resetFailedAttempts(int userId) {
        final String sql = "UPDATE users SET failed_login_attempts = 0 WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new DatabaseException(
                    "resetFailedAttempts thất bại cho userId=" + userId, e);
        }
    }

    /**
     * 1.0-E2 — Tăng bộ đếm sai mật khẩu lên 1.
     * Nếu tổng >= MAX_FAILED_ATTEMPTS → tự động LOCK (BR-02).
     * Logic LOCK trong SQL để tránh race condition.
     */
    public void increaseFailedAttempts(int userId) {
        final String sql =
                "UPDATE users " +
                        "SET failed_login_attempts = failed_login_attempts + 1, " +
                        "    status = CASE " +
                        "        WHEN failed_login_attempts + 1 >= ? THEN ? " +
                        "        ELSE status " +
                        "    END " +
                        "WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1,    MAX_FAILED_ATTEMPTS);
            ps.setString(2, Status.LOCKED.name());  // enum → String
            ps.setInt(3,    userId);
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new DatabaseException(
                    "increaseFailedAttempts thất bại cho userId=" + userId, e);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  TẠO USER MỚI (UC1.2)
    // ════════════════════════════════════════════════════════════════

    /**
     * 1.2.1 → 1.2.3 — Tự động tạo tài khoản từ thông tin Google.
     * Gán role=USER (BR-03), status=ACTIVE, provider=GOOGLE.
     */
    public User createGoogleUser(String email, String fullName) {
        final String sql =
                "INSERT INTO users (email, role, status, auth_provider, full_name) " +
                        "VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, email);
            ps.setString(2, Role.USER.name());          // enum → String
            ps.setString(3, Status.ACTIVE.name());      // enum → String
            ps.setString(4, AuthProvider.GOOGLE.name()); // enum → String
            ps.setString(5, fullName);
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return findById(keys.getInt(1));
                }
            }

            throw new DatabaseException(
                    "Không lấy được ID sau khi INSERT Google user: " + email);

        } catch (SQLException e) {
            throw new DatabaseException(
                    "createGoogleUser thất bại cho email=" + email, e);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  GHI LOG
    // ════════════════════════════════════════════════════════════════

    /** Ghi ActivityLog — caller dùng safeInsertLog để không chặn luồng chính. */
    public void insertLog(ActivityLog log) {
        final String sql =
                "INSERT INTO activity_log (user_id, action, description) VALUES (?, ?, ?)";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            if (log.getUserId() != null) {
                ps.setInt(1, log.getUserId());
            } else {
                ps.setNull(1, Types.INTEGER);
            }
            ps.setString(2, log.getAction());
            ps.setString(3, log.getDescription());
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new DatabaseException(
                    "insertLog thất bại: action=" + log.getAction(), e);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  PRIVATE HELPER
    // ════════════════════════════════════════════════════════════════

    /** Ánh xạ ResultSet → User, dùng enum factory method `from()`. */
    private User mapRow(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId(rs.getInt("id"));
        u.setEmail(rs.getString("email"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setRole(rs.getString("role"));           // gọi setRole(String) → Role.from()
        u.setStatus(rs.getString("status"));       // gọi setStatus(String) → Status.from()
        u.setFailedLoginAttempts(rs.getInt("failed_login_attempts"));
        u.setAuthProvider(rs.getString("auth_provider")); // → AuthProvider.from()
        u.setFullName(rs.getString("full_name"));
        return u;
    }
}