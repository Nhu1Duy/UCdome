package com.example.uc.repository;

import com.example.uc.cofig.DatabaseConfig;
import com.example.uc.exception.DatabaseException;
import com.example.uc.model.OtpToken;

import java.sql.*;
import java.time.LocalDateTime;

/**
 * ═══════════════════════════════════════════════════════════════════
 *  OtpRepository — Tầng Repository cho bảng otp_tokens
 *
 *  DDL bảng cần tạo trong CSDL:
 *
 *  CREATE TABLE otp_tokens (
 *      id         INT AUTO_INCREMENT PRIMARY KEY,
 *      user_id    INT NOT NULL,
 *      otp_code   VARCHAR(64)  NOT NULL,   -- SHA-256 hash của mã 6 số
 *      created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
 *      expires_at DATETIME     NOT NULL,   -- BR-02: created_at + 120 giây
 *      used       TINYINT(1)   NOT NULL DEFAULT 0,
 *      CONSTRAINT fk_otp_user FOREIGN KEY (user_id) REFERENCES users(id)
 *  );
 * ═══════════════════════════════════════════════════════════════════
 */
public class OtpRepository {

    // ════════════════════════════════════════════════════════════════
    //  LƯU OTP MỚI
    // ════════════════════════════════════════════════════════════════

    /**
     * 3.0.9 — Lưu thông tin mã OTP, thời gian tạo và thời gian hết hạn vào CSDL.
     *
     * Chú ý: otpCode đã được băm SHA-256 trước khi truyền vào đây.
     *
     * @param userId    ID tài khoản yêu cầu khôi phục
     * @param otpHash   Mã OTP đã băm SHA-256 (không lưu plaintext — BR-04)
     * @param expiresAt Thời điểm hết hạn (BR-02: now + 120s)
     */
    public void saveOtp(int userId, String otpHash, LocalDateTime expiresAt) {
        final String sql =
                "INSERT INTO otp_tokens (user_id, otp_code, expires_at) " +
                "VALUES (?, ?, ?)";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            ps.setString(2, otpHash);
            ps.setTimestamp(3, Timestamp.valueOf(expiresAt));
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new DatabaseException(
                    "saveOtp thất bại cho userId=" + userId, e);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  TÌM OTP CÒN HIỆU LỰC
    // ════════════════════════════════════════════════════════════════

    /**
     * 3.0.12 — Tìm OTP chưa sử dụng và chưa hết hạn mới nhất của user.
     *
     * Lấy bản ghi MỚI NHẤT (ORDER BY created_at DESC LIMIT 1) để:
     *  - Tránh conflict khi user nhấn "Gửi lại" nhiều lần
     *  - Chỉ OTP mới nhất là hợp lệ, các OTP cũ bị bỏ qua
     *
     * @param userId ID tài khoản cần tìm OTP
     * @return OtpToken nếu tìm thấy (có thể đã hết hạn — service kiểm tra tiếp)
     *         null nếu không có OTP nào
     */
    public OtpToken findLatestByUserId(int userId) {
        final String sql =
                "SELECT * FROM otp_tokens " +
                "WHERE user_id = ? AND used = 0 " +
                "ORDER BY created_at DESC LIMIT 1";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
            return null;

        } catch (SQLException e) {
            throw new DatabaseException(
                    "findLatestByUserId thất bại cho userId=" + userId, e);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  VÔ HIỆU HÓA OTP
    // ════════════════════════════════════════════════════════════════

    /**
     * 3.0.17 — Vô hiệu hóa OTP sau khi đặt lại mật khẩu thành công.
     * BR-04: Mỗi OTP chỉ xác thực thành công đúng 1 lần — đánh dấu used=1.
     *
     * @param otpId ID của bản ghi OTP cần vô hiệu hóa
     */
    public void markOtpUsed(int otpId) {
        final String sql = "UPDATE otp_tokens SET used = 1 WHERE id = ?";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, otpId);
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new DatabaseException(
                    "markOtpUsed thất bại cho otpId=" + otpId, e);
        }
    }

    /**
     * 3.1.4 — Vô hiệu hóa TẤT CẢ OTP cũ chưa dùng của user khi gửi lại OTP mới.
     *
     * Khi user nhấn "Gửi lại mã", OTP cũ phải bị hủy ngay lập tức để:
     *  - Tránh nhiều OTP hợp lệ cùng tồn tại → bảo mật
     *  - Đảm bảo chỉ OTP mới nhất có tác dụng
     *
     * @param userId ID tài khoản cần hủy tất cả OTP cũ
     */
    public void invalidateAllOtpByUserId(int userId) {
        final String sql =
                "UPDATE otp_tokens SET used = 1 WHERE user_id = ? AND used = 0";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            ps.executeUpdate();

        } catch (SQLException e) {
            throw new DatabaseException(
                    "invalidateAllOtpByUserId thất bại cho userId=" + userId, e);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  ĐẾM THỜI GIAN GỬI GẦN NHẤT (cooldown "Gửi lại")
    // ════════════════════════════════════════════════════════════════

    /**
     * 3.1.2 — Lấy thời điểm tạo OTP gần nhất của user (kể cả đã dùng/hết hạn).
     *
     * Dùng để kiểm tra cooldown: user phải chờ tối thiểu 60 giây giữa 2 lần gửi OTP.
     * Lý do chọn 60s: đủ chặn spam nhưng không gây khó dễ người dùng thật.
     *
     * @param userId ID tài khoản
     * @return LocalDateTime gần nhất, hoặc null nếu chưa gửi lần nào
     */
    public LocalDateTime findLastOtpCreatedAt(int userId) {
        final String sql =
                "SELECT created_at FROM otp_tokens " +
                "WHERE user_id = ? " +
                "ORDER BY created_at DESC LIMIT 1";

        try (Connection conn = DatabaseConfig.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Timestamp ts = rs.getTimestamp("created_at");
                    return ts != null ? ts.toLocalDateTime() : null;
                }
            }
            return null;

        } catch (SQLException e) {
            throw new DatabaseException(
                    "findLastOtpCreatedAt thất bại cho userId=" + userId, e);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  PRIVATE HELPER
    // ════════════════════════════════════════════════════════════════

    private OtpToken mapRow(ResultSet rs) throws SQLException {
        OtpToken token = new OtpToken();
        token.setId(rs.getInt("id"));
        token.setUserId(rs.getInt("user_id"));
        token.setOtpCode(rs.getString("otp_code"));
        token.setUsed(rs.getInt("used") == 1);

        Timestamp created = rs.getTimestamp("created_at");
        if (created != null) token.setCreatedAt(created.toLocalDateTime());

        Timestamp expires = rs.getTimestamp("expires_at");
        if (expires != null) token.setExpiresAt(expires.toLocalDateTime());

        return token;
    }
}
