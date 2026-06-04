package com.example.uc.repository;

import com.example.uc.cofig.DatabaseConfig;
import com.example.uc.exception.DatabaseException;
import com.example.uc.model.ActivityLog;
import com.example.uc.model.User;
import com.example.uc.model.User.Role;
import com.example.uc.model.User.Status;
import com.example.uc.model.User.AuthProvider;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════════════
 *  UserManagementRepository — Tầng Repository cho UC-08
 *
 *  Trách nhiệm: TOÀN BỘ truy vấn SQL liên quan đến quản lý User.
 *  Mỗi method có comment số bước UC tương ứng (VD: 8.1.5, 8.3.5…).
 *
 *  Soft-delete (UC8.3):
 *    • Mọi query SELECT chủ động chỉ lấy deleted_at IS NULL.
 *    • "Xoá" = cập nhật deleted_at = NOW(), deleted_by = adminId.
 *
 *  checkDuplicate — HƯỚNG 1:
 *    • Chỉ kiểm tra bản ghi có deleted_at IS NULL.
 *    • Email đã bị soft-delete được phép đăng ký lại.
 *    • Unique DB-level được đảm bảo bằng virtual column (xem migration).
 * ═══════════════════════════════════════════════════════════════════
 */
public class UserManagementRepository {

    // ════════════════════════════════════════════════════════════════
    //  8.0.1 + 8.0.2 — DANH SÁCH USER (có phân trang — NFR-02)
    // ════════════════════════════════════════════════════════════════

    /**
     * 8.0.1 — Truy vấn danh sách User từ CSDL với phân trang.
     * Chỉ lấy các bản ghi chưa bị soft-delete (deleted_at IS NULL).
     *
     * @param page     trang hiện tại (1-based)
     * @param pageSize số bản ghi mỗi trang
     */
    public List<User> findAllPaged(int page, int pageSize) {
        final String sql =
                "SELECT * FROM users " +
                        "WHERE deleted_at IS NULL " +
                        "ORDER BY created_at DESC " +
                        "LIMIT ? OFFSET ?";

        List<User> list = new ArrayList<>();
        int offset = (page - 1) * pageSize;

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, pageSize);
            ps.setInt(2, offset);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
            return list;

        } catch (SQLException e) {
            throw new DatabaseException("findAllPaged thất bại: page=" + page, e);
        }
    }

    /**
     * Đếm tổng số user chưa xoá — dùng để tính tổng trang (NFR-02).
     */
    public int countAll() {
        final String sql = "SELECT COUNT(*) FROM users WHERE deleted_at IS NULL";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            return rs.next() ? rs.getInt(1) : 0;

        } catch (SQLException e) {
            throw new DatabaseException("countAll thất bại", e);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  8.5 — TÌM KIẾM USER
    // ════════════════════════════════════════════════════════════════

    /**
     * 8.5.3 — Tìm kiếm user theo từ khoá trên full_name, email, username, phone.
     * Chỉ trả về bản ghi chưa bị soft-delete.
     *
     * @param keyword  từ khoá tìm kiếm (đã được trim ở service)
     * @param page     trang (1-based)
     * @param pageSize số bản ghi mỗi trang
     */
    public List<User> search(String keyword, int page, int pageSize) {
        final String sql =
                "SELECT * FROM users " +
                        "WHERE deleted_at IS NULL " +
                        "  AND (full_name LIKE ? OR email LIKE ? OR username LIKE ? OR phone LIKE ?) " +
                        "ORDER BY created_at DESC " +
                        "LIMIT ? OFFSET ?";

        String pattern = "%" + keyword + "%";
        int offset = (page - 1) * pageSize;
        List<User> list = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, pattern);
            ps.setString(2, pattern);
            ps.setString(3, pattern);
            ps.setString(4, pattern);
            ps.setInt(5, pageSize);
            ps.setInt(6, offset);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }
            return list;

        } catch (SQLException e) {
            throw new DatabaseException("search thất bại: keyword=" + keyword, e);
        }
    }

    /**
     * Đếm kết quả tìm kiếm — dùng để tính tổng trang.
     */
    public int countSearch(String keyword) {
        final String sql =
                "SELECT COUNT(*) FROM users " +
                        "WHERE deleted_at IS NULL " +
                        "  AND (full_name LIKE ? OR email LIKE ? OR username LIKE ? OR phone LIKE ?)";

        String pattern = "%" + keyword + "%";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, pattern);
            ps.setString(2, pattern);
            ps.setString(3, pattern);
            ps.setString(4, pattern);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }

        } catch (SQLException e) {
            throw new DatabaseException("countSearch thất bại: keyword=" + keyword, e);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  8.1 — THÊM USER
    // ════════════════════════════════════════════════════════════════

    /**
     * 8.1.4 — Kiểm tra trùng lặp khi INSERT (BR-03).
     * Shortcut: authProvider mặc định "LOCAL" khi Admin tạo thủ công.
     * Trả về tên field đầu tiên bị trùng, hoặc null nếu không trùng.
     */
    public String checkDuplicate(String username, String email, String phone) {
        return checkDuplicate(username, email, phone, AuthProvider.LOCAL.name(), -1);
    }

    /**
     * 8.2.4 — Kiểm tra trùng lặp khi UPDATE — loại trừ chính user đang sửa.
     *
     * @param authProvider provider của user đang sửa (LOCAL hoặc GOOGLE)
     *                     — cần thiết vì unique DB là (email, auth_provider)
     * @param excludeId    ID user đang update, truyền -1 nếu INSERT
     */
    public String checkDuplicate(String username, String email,
                                 String phone, String authProvider, int excludeId) {
        try (Connection conn = DatabaseConfig.getConnection()) {

            // ── Check USERNAME ──────────────────────────────────────
            // Unique: username chỉ enforce trên bản ghi deleted_at IS NULL
            // (đảm bảo bởi virtual column uq_username_active trên DB)
            if (username != null && !username.isBlank()) {
                String sql = "SELECT 1 FROM users " +
                        "WHERE username = ? AND deleted_at IS NULL" +
                        (excludeId > 0 ? " AND id != ?" : "");

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, username);
                    if (excludeId > 0) ps.setInt(2, excludeId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) return "username";
                    }
                }
            }

            // ── Check EMAIL + PROVIDER ──────────────────────────────
            // Unique DB-level là composite (email, auth_provider)
            // → phải check cả 2 để tránh false-positive
            if (email != null && !email.isBlank()) {
                String sql = "SELECT 1 FROM users " +
                        "WHERE email = ? AND auth_provider = ? AND deleted_at IS NULL" +
                        (excludeId > 0 ? " AND id != ?" : "");

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, email);
                    ps.setString(2, authProvider != null ? authProvider : AuthProvider.LOCAL.name());
                    if (excludeId > 0) ps.setInt(3, excludeId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) return "email";
                    }
                }
            }

            // ── Check PHONE ─────────────────────────────────────────
            // Phone không gắn với provider → check đơn giản
            if (phone != null && !phone.isBlank()) {
                String sql = "SELECT 1 FROM users " +
                        "WHERE phone = ? AND deleted_at IS NULL" +
                        (excludeId > 0 ? " AND id != ?" : "");

                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, phone);
                    if (excludeId > 0) ps.setInt(2, excludeId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) return "phone";
                    }
                }
            }

            return null; // không trùng

        } catch (SQLException e) {
            throw new DatabaseException("checkDuplicate thất bại", e);
        }
    }

    /**
     * 8.1.5 — Lưu user mới vào CSDL.
     * Password đã được hash ở Service trước khi truyền vào (BR-04).
     * Trả về User vừa tạo với ID được gán (8.1.6).
     */
    public User insert(User u) {
        final String sql =
                "INSERT INTO users " +
                        "(username, email, phone, password_hash, full_name, " +
                        " role, status, auth_provider, " +
                        " date_of_birth, gender, address_line, city, province, country) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            setNullableString(ps, 1,  u.getUsername());
            ps.setString    (2,       u.getEmail());
            setNullableString(ps, 3,  u.getPhone());
            setNullableString(ps, 4,  u.getPasswordHash());
            setNullableString(ps, 5,  u.getFullName());
            ps.setString    (6,  u.getRole()         != null ? u.getRole().name()         : Role.USER.name());
            ps.setString    (7,  u.getStatus()       != null ? u.getStatus().name()       : Status.ACTIVE.name());
            ps.setString    (8,  u.getAuthProvider() != null ? u.getAuthProvider().name() : AuthProvider.LOCAL.name());

            if (u.getDateOfBirth() != null) ps.setDate(9, Date.valueOf(u.getDateOfBirth()));
            else                             ps.setNull(9, Types.DATE);

            setNullableString(ps, 10, u.getGender()   != null ? u.getGender().name() : null);
            setNullableString(ps, 11, u.getAddressLine());
            setNullableString(ps, 12, u.getCity());
            setNullableString(ps, 13, u.getProvince());
            ps.setString    (14, u.getCountry() != null ? u.getCountry() : "Vietnam");

            ps.executeUpdate();

            // 8.1.6 — lấy ID được gán tự động, trả về bản ghi đầy đủ
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return findActiveById(keys.getInt(1));
            }
            throw new DatabaseException("INSERT user không trả về generated key");

        } catch (SQLException e) {
            throw new DatabaseException("insert user thất bại: email=" + u.getEmail(), e);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  8.2 — CẬP NHẬT USER
    // ════════════════════════════════════════════════════════════════

    /**
     * 8.2.5 — Cập nhật thông tin User vào CSDL.
     * Không cho phép đổi password tại đây (tách riêng).
     * Không cập nhật deleted_at, auth_provider.
     * Chỉ tác động lên bản ghi chưa bị soft-delete.
     */
    public boolean update(User u) {
        final String sql =
                "UPDATE users SET " +
                        "  username=?, email=?, phone=?, full_name=?, " +
                        "  role=?, status=?, " +
                        "  date_of_birth=?, gender=?, " +
                        "  address_line=?, city=?, province=?, country=? " +
                        "WHERE id = ? AND deleted_at IS NULL";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            setNullableString(ps, 1,  u.getUsername());
            ps.setString    (2,       u.getEmail());
            setNullableString(ps, 3,  u.getPhone());
            setNullableString(ps, 4,  u.getFullName());
            ps.setString    (5,       u.getRole().name());
            ps.setString    (6,       u.getStatus().name());

            if (u.getDateOfBirth() != null) ps.setDate(7, Date.valueOf(u.getDateOfBirth()));
            else                             ps.setNull(7, Types.DATE);

            setNullableString(ps, 8,  u.getGender()   != null ? u.getGender().name() : null);
            setNullableString(ps, 9,  u.getAddressLine());
            setNullableString(ps, 10, u.getCity());
            setNullableString(ps, 11, u.getProvince());
            ps.setString    (12, u.getCountry() != null ? u.getCountry() : "Vietnam");
            ps.setInt       (13, u.getId());

            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            throw new DatabaseException("update user thất bại: id=" + u.getId(), e);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  8.3 — XOÁ USER (soft-delete)
    // ════════════════════════════════════════════════════════════════

    /**
     * 8.3.5 — Soft-delete: ghi deleted_at = NOW(), deleted_by = adminId.
     * KHÔNG xoá vật lý — giữ toàn vẹn FK của các bảng liên quan.
     * Chỉ tác động nếu bản ghi chưa bị xoá (idempotent).
     */
    public boolean softDelete(int userId, int deletedByAdminId) {
        final String sql =
                "UPDATE users SET deleted_at = NOW(), deleted_by = ? " +
                        "WHERE id = ? AND deleted_at IS NULL";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, deletedByAdminId);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            throw new DatabaseException("softDelete thất bại: userId=" + userId, e);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  8.4 — KHÓA / MỞ KHÓA USER
    // ════════════════════════════════════════════════════════════════

    /**
     * 8.4.5 — Cập nhật trạng thái User thành LOCKED hoặc ACTIVE.
     * Chỉ tác động lên bản ghi chưa bị soft-delete.
     *
     * @param userId    ID user cần thay đổi
     * @param newStatus trạng thái mới (LOCKED hoặc ACTIVE)
     */
    public boolean updateStatus(int userId, Status newStatus) {
        final String sql =
                "UPDATE users SET status = ? WHERE id = ? AND deleted_at IS NULL";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, newStatus.name());
            ps.setInt   (2, userId);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {
            throw new DatabaseException("updateStatus thất bại: userId=" + userId, e);
        }
    }

    /**
     * 8.4.6 — Huỷ phiên đăng nhập của user bị khoá.
     *
     * Giới hạn kỹ thuật: kiến trúc Jakarta Servlet + in-memory HttpSession
     * không cho phép force-invalidate session của user khác từ tầng Repository.
     * Hiện tại status = LOCKED được kiểm tra tại Filter mỗi request → đủ an toàn.
     *
     * TODO: implement đầy đủ khi nâng lên Spring Security / Redis session.
     */
    public void invalidateUserSessions(int userId) {
        // no-op — xem comment trên
    }

    // ════════════════════════════════════════════════════════════════
    //  HELPER — findById
    // ════════════════════════════════════════════════════════════════

    /**
     * Tìm user đang active theo ID (deleted_at IS NULL).
     * Dùng sau INSERT (8.1.6) và sau UPDATE (8.2.7).
     * Trả về null nếu không tồn tại hoặc đã bị soft-delete.
     */
    public User findActiveById(int id) {
        final String sql = "SELECT * FROM users WHERE id = ? AND deleted_at IS NULL";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
            return null;

        } catch (SQLException e) {
            throw new DatabaseException("findActiveById thất bại: id=" + id, e);
        }
    }

    /**
     * Tìm user theo ID kể cả đã bị soft-delete.
     * Dùng khi cần kiểm tra tồn tại trước khi thực hiện softDelete / toggleLock.
     * Service sẽ tự kiểm tra trường deleted_at để đưa ra thông báo phù hợp.
     */
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
            throw new DatabaseException("findById thất bại: id=" + id, e);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  GHI LOG (BR-05)
    // ════════════════════════════════════════════════════════════════

    /**
     * 8.1.7 / 8.2.6 / 8.3.6 / 8.4.7 — Ghi Audit Log vào CSDL.
     */
    public void insertLog(ActivityLog log) {
        final String sql =
                "INSERT INTO activity_log " +
                        "(user_id, action, description, ip_address, user_agent, entity_type, entity_id) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            setNullableInt   (ps, 1, log.getUserId());
            ps.setString     (2,     log.getAction());
            setNullableString(ps, 3, log.getDescription());
            setNullableString(ps, 4, log.getIpAddress());
            setNullableString(ps, 5, log.getUserAgent());
            setNullableString(ps, 6, log.getEntityType());
            setNullableInt   (ps, 7, log.getEntityId());
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new DatabaseException("insertLog thất bại: action=" + log.getAction(), e);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ════════════════════════════════════════════════════════════════

    /** Ánh xạ ResultSet → User object. */
    private User mapRow(ResultSet rs) throws SQLException {
        User u = new User();
        u.setId                 (rs.getInt("id"));
        u.setUsername           (rs.getString("username"));
        u.setEmail              (rs.getString("email"));
        u.setPasswordHash       (rs.getString("password_hash"));
        u.setRole               (rs.getString("role"));
        u.setStatus             (rs.getString("status"));
        u.setFailedLoginAttempts(rs.getInt("failed_login_attempts"));
        u.setAuthProvider       (rs.getString("auth_provider"));
        u.setFullName           (rs.getString("full_name"));
        u.setPhone              (rs.getString("phone"));
        u.setAvatarUrl          (rs.getString("avatar_url"));

        Date dob = rs.getDate("date_of_birth");
        if (dob != null) u.setDateOfBirth(dob.toLocalDate());

        u.setGender     (rs.getString("gender"));
        u.setAddressLine(rs.getString("address_line"));
        u.setCity       (rs.getString("city"));
        u.setProvince   (rs.getString("province"));
        u.setCountry    (rs.getString("country"));

        Timestamp deletedAt = rs.getTimestamp("deleted_at");
        if (deletedAt != null) u.setDeletedAt(deletedAt.toLocalDateTime());

        try {
            int deletedBy = rs.getInt("deleted_by");
            if (!rs.wasNull()) u.setDeletedBy(deletedBy);
        } catch (SQLException ignored) { /* cột chưa tồn tại trong môi trường cũ */ }

        Timestamp lastLogin = rs.getTimestamp("last_login_at");
        if (lastLogin != null) u.setLastLoginAt(lastLogin.toLocalDateTime());

        Timestamp createdAt = rs.getTimestamp("created_at");
        if (createdAt != null) u.setCreatedAt(createdAt.toLocalDateTime());

        Timestamp updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) u.setUpdatedAt(updatedAt.toLocalDateTime());

        return u;
    }

    private void setNullableString(PreparedStatement ps, int idx, String val) throws SQLException {
        if (val != null && !val.isBlank()) ps.setString(idx, val);
        else                               ps.setNull(idx, Types.VARCHAR);
    }

    private void setNullableInt(PreparedStatement ps, int idx, Integer val) throws SQLException {
        if (val != null) ps.setInt(idx, val);
        else             ps.setNull(idx, Types.INTEGER);
    }
}