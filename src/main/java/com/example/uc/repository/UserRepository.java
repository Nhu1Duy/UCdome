package com.example.uc.repository;

import com.example.uc.cofig.DatabaseConfig;
import com.example.uc.exception.DatabaseException;
import com.example.uc.model.ActivityLog;
import com.example.uc.model.User;

import java.sql.*;

/**
 * ═══════════════════════════════════════════════════════════════════
 *  UserRepository — Tầng Repository
 *
 *  Trách nhiệm DUY NHẤT: thực thi SQL với MySQL thông qua HikariCP.
 *  KHÔNG chứa logic nghiệp vụ (kiểm tra LOCKED, đếm sai… → AuthService).
 *
 *  Mỗi method:
 *    - Lấy Connection từ pool (try-with-resources → tự trả pool)
 *    - Bắt SQLException, bọc thành DatabaseException (1.0-EDB / 1.1-EDB)
 *    - Comment rõ bước luồng UC-01 tương ứng
 * ═══════════════════════════════════════════════════════════════════
 */
public class UserRepository {

    // ─── Hằng nghiệp vụ (BR-02: max lần sai trước khi khóa) ────────
    private static final int MAX_FAILED_ATTEMPTS = 5;

    // ════════════════════════════════════════════════════════════════
    //  TRUY VẤN USER
    // ════════════════════════════════════════════════════════════════

    /**
     * 1.0.4 — Tìm user theo email để đối chiếu mật khẩu băm (BR-04).
     * Trả null nếu không tìm thấy → AuthService xử lý 1.0-E2.
     *
     * @throws DatabaseException nếu kết nối/SQL thất bại (1.0-EDB)
     */
    public User findByEmail(String email) {
        final String sql = "SELECT * FROM users WHERE email = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
            // Không tìm thấy → trả null, AuthService sẽ xử lý 1.0-E2
            return null;

        } catch (SQLException e) {
            // 1.0-EDB: lỗi kết nối hoặc SQL → ném DatabaseException
            throw new DatabaseException("findByEmail thất bại cho email: " + email, e);
        }
    }

    /**
     * 1.1.8 — Kiểm tra email từ Google đã tồn tại với provider=GOOGLE chưa.
     * Trả null nếu chưa → AuthService rẽ nhánh sang UC1.2 (tự đăng ký).
     *
     * @throws DatabaseException nếu kết nối/SQL thất bại (1.1-EDB)
     */
    public User findByEmailAndProvider(String email, String provider) {
        final String sql = "SELECT * FROM users WHERE email = ? AND auth_provider = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email);
            ps.setString(2, provider);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
            // Null → UC1.2: tạo tài khoản mới
            return null;

        } catch (SQLException e) {
            // 1.1-EDB: lỗi kết nối
            throw new DatabaseException(
                    "findByEmailAndProvider thất bại: email=" + email + ", provider=" + provider, e);
        }
    }

    /**
     * Helper: tìm theo ID — dùng sau INSERT để lấy bản ghi vừa tạo.
     * Dùng trong 1.2.3 sau createGoogleUser().
     *
     * @throws DatabaseException nếu kết nối/SQL thất bại
     */
    public User findById(int id) {
        final String sql = "SELECT * FROM users WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
            return null;

        } catch (SQLException e) {
            throw new DatabaseException("findById thất bại cho id=" + id, e);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  CẬP NHẬT TRẠNG THÁI ĐĂNG NHẬP
    // ════════════════════════════════════════════════════════════════

    /**
     * 1.0.6 — Reset bộ đếm sai mật khẩu về 0 sau khi đăng nhập thành công.
     *
     * @throws DatabaseException nếu UPDATE thất bại (1.0-EDB)
     */
    public void resetFailedAttempts(int userId) {
        final String sql = "UPDATE users SET failed_login_attempts = 0 WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            ps.executeUpdate();

        } catch (SQLException e) {
            // Lỗi reset không chặn đăng nhập → log nhưng vẫn ném để Service xử lý
            throw new DatabaseException("resetFailedAttempts thất bại cho userId=" + userId, e);
        }
    }

    /**
     * 1.0-E2 — Tăng bộ đếm sai mật khẩu lên 1.
     * Nếu tổng >= MAX_FAILED_ATTEMPTS → tự động chuyển status='LOCKED' (BR-02).
     *
     * Logic LOCK được viết trong SQL (atomic, tránh race condition):
     *   CASE WHEN failed_login_attempts + 1 >= MAX → 'LOCKED' ELSE giữ nguyên
     *
     * @throws DatabaseException nếu UPDATE thất bại (1.0-EDB)
     */
    public void increaseFailedAttempts(int userId) {
        final String sql =
                "UPDATE users " +
                        "SET failed_login_attempts = failed_login_attempts + 1, " +
                        "    status = CASE " +
                        "        WHEN failed_login_attempts + 1 >= ? THEN 'LOCKED' " +
                        "        ELSE status " +
                        "    END " +
                        "WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, MAX_FAILED_ATTEMPTS); // BR-02: ngưỡng khóa
            ps.setInt(2, userId);
            ps.executeUpdate();

        } catch (SQLException e) {
            // Ghi log nhưng ném để Service/Servlet biết có lỗi CSDL
            throw new DatabaseException("increaseFailedAttempts thất bại cho userId=" + userId, e);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  TẠO USER MỚI (UC1.2)
    // ════════════════════════════════════════════════════════════════

    /**
     * 1.2.1 → 1.2.3 — Tự động tạo tài khoản từ thông tin Google.
     *   - Gán role='USER' mặc định (BR-03)
     *   - Gán status='ACTIVE', auth_provider='GOOGLE'
     *   - Lấy ID tự sinh từ MySQL AUTO_INCREMENT
     *   - Trả về User đầy đủ (đọc lại từ CSDL)
     *
     * @throws DatabaseException nếu INSERT thất bại (1.1-EDB)
     */
    public User createGoogleUser(String email, String fullName) {
        final String sql =
                "INSERT INTO users (email, role, status, auth_provider, full_name) " +
                        "VALUES (?, 'USER', 'ACTIVE', 'GOOGLE', ?)";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, email);
            ps.setString(2, fullName);
            ps.executeUpdate();

            // 1.2.3 — Lấy ID vừa sinh bởi MySQL AUTO_INCREMENT
            try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    int newId = generatedKeys.getInt(1);
                    return findById(newId); // đọc lại bản ghi đầy đủ
                }
            }

            // Không lấy được ID → INSERT thực sự thất bại
            throw new DatabaseException("Không lấy được ID sau khi INSERT Google user: " + email);

        } catch (SQLException e) {
            // Bao gồm duplicate email (constraint) → DatabaseException
            throw new DatabaseException("createGoogleUser thất bại cho email=" + email, e);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  GHI LOG (Activity Log)
    // ════════════════════════════════════════════════════════════════

    /**
     * Ghi nhận ActivityLog vào bảng activity_log.
     * Dùng tại:
     *   1.0.9  — Ghi log đăng nhập thành công
     *   1.0-E3 — Ghi log truy cập tài khoản bị khóa
     *   1.1-E1 — Ghi log lỗi OAuth
     *   1.2.5  — Ghi log đăng ký mới qua Google
     *
     * Lưu ý: user_id có thể NULL nếu chưa xác định được user (ví dụ 1.1-E1).
     *
     * @throws DatabaseException nếu INSERT thất bại — caller nên log nhưng không
     *                           để lỗi log chặn luồng chính.
     */
    public void insertLog(ActivityLog log) {
        final String sql =
                "INSERT INTO activity_log (user_id, action, description) VALUES (?, ?, ?)";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            // user_id nullable: setNull nếu không có userId
            if (log.getUserId() != null) {
                ps.setInt(1, log.getUserId());
            } else {
                ps.setNull(1, Types.INTEGER);
            }
            ps.setString(2, log.getAction());
            ps.setString(3, log.getDescription());
            ps.executeUpdate();

        } catch (SQLException e) {
            // Lỗi ghi log KHÔNG nên làm hỏng luồng chính
            // → ném DatabaseException để caller quyết định có propagate không
            throw new DatabaseException("insertLog thất bại: action=" + log.getAction(), e);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  PRIVATE HELPER
    // ════════════════════════════════════════════════════════════════

    /**
     * Ánh xạ một hàng ResultSet → đối tượng User.
     * Giữ tập trung: nếu schema đổi, chỉ sửa ở đây.
     */
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