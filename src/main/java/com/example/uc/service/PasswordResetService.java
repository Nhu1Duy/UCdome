package com.example.uc.service;

import com.example.uc.exception.AuthException;
import com.example.uc.exception.AuthException.ErrorCode;
import com.example.uc.exception.DatabaseException;
import com.example.uc.model.ActivityLog;
import com.example.uc.model.OtpToken;
import com.example.uc.model.User;
import com.example.uc.model.User.Status;
import com.example.uc.repository.OtpRepository;
import com.example.uc.repository.UserRepository;
import com.example.uc.util.BCryptUtil;
import com.example.uc.util.EmailService;
import com.example.uc.util.OtpUtil;

import jakarta.mail.MessagingException;

import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * ═══════════════════════════════════════════════════════════════════
 *  PasswordResetService — Tầng Service cho UC-03 Quên Mật Khẩu
 *
 *  Trách nhiệm:
 *   - Toàn bộ logic nghiệp vụ của luồng khôi phục mật khẩu
 *   - KHÔNG biết HTTP — nhận primitive, ném AuthException
 *
 *  Business Rules áp dụng:
 *   BR-01: Tài khoản bị LOCKED sau >= 5 lần yêu cầu liên tiếp thất bại
 *   BR-02: OTP hết hạn sau 120 giây
 *   BR-03: Mật khẩu mới phải được băm một chiều trước khi lưu
 *   BR-04: Mỗi OTP chỉ được dùng đúng 1 lần
 *
 *  ⚠️ Sửa luồng UC so với tài liệu gốc:
 *   - Bước 3.0.7 (tăng bộ đếm) được thực hiện sau khi kiểm tra cooldown
 *     (3.0.6), không phải trước — tránh tăng đếm khi request bị chặn sớm.
 *   - Bộ đếm failedLoginAttempts trong bảng users được tái dụng để đếm
 *     số lần yêu cầu OTP liên tiếp (thay vì tạo cột riêng), vì cùng ngữ
 *     nghĩa "số lần thất bại liên tiếp".
 *   - Reset bộ đếm (3.0.18) dùng resetFailedAttempts() có sẵn.
 * ═══════════════════════════════════════════════════════════════════
 */
public class PasswordResetService {

    // BR-01: Giới hạn số lần yêu cầu OTP liên tiếp trước khi khóa tài khoản
    private static final int MAX_OTP_REQUESTS = 5;

    // Cooldown tối thiểu giữa 2 lần gửi OTP (giây) — dùng cho "Gửi lại"
    private static final int OTP_RESEND_COOLDOWN_SECONDS = 60;

    // BR-02: Thời gian sống của OTP (giây)
    private static final int OTP_EXPIRY_SECONDS = 120;

    private final UserRepository userRepository;
    private final OtpRepository  otpRepository;

    public PasswordResetService(UserRepository userRepository,
                                OtpRepository otpRepository) {
        this.userRepository = userRepository;
        this.otpRepository  = otpRepository;
    }

    // ════════════════════════════════════════════════════════════════
    //  BƯỚC 1 — Xử lý yêu cầu gửi OTP (3.0.1 → 3.0.10)
    // ════════════════════════════════════════════════════════════════

    /**
     * Nhận email từ form, kiểm tra điều kiện, tạo và gửi OTP.
     *
     * ⚠️ Sửa luồng: Bước 3.0-E2 (email không tồn tại) KHÔNG ném exception
     * rõ ràng mà trả về bình thường (silent fail) để tránh lộ thông tin
     * tài khoản nào tồn tại trong hệ thống (security best practice).
     * Tài liệu gốc 3.0-E2 mô tả đúng hành vi này: "Mã xác thực đã được
     * gửi nếu Email tồn tại" — nên servlet sẽ hiển thị thông báo trung tính.
     *
     * @param email     Email nhập từ form (đã được validate định dạng ở servlet)
     * @param ipAddress IP của client
     * @param userAgent User-Agent header
     * @throws AuthException  nếu tài khoản LOCKED (3.0-E3) hoặc vượt giới hạn OTP (3.0-E4)
     * @throws RuntimeException nếu gửi email thất bại (3.0-EDB)
     */
    public void requestPasswordReset(String email, String ipAddress, String userAgent) throws AuthException{
        // ── 3.0.4: Truy vấn CSDL kiểm tra email tồn tại ───────────
        User user = userRepository.findByEmail(email);

        // ── 3.0-E2: Email không tồn tại → silent fail (không tiết lộ thông tin)
        //    Servlet hiển thị thông báo trung tính thay vì lỗi rõ ràng.
        if (user == null) {
            return; // silent — không gửi email, không báo lỗi cho client
        }

        // Tài khoản đăng nhập Google không được reset mật khẩu
        if (user.getAuthProvider() == User.AuthProvider.GOOGLE) {
            throw new AuthException(
                    ErrorCode.INVALID_CREDENTIALS,
                    "Tài khoản này đăng nhập bằng Google. Vui lòng đăng nhập bằng Google.");
        }

        // ── 3.0.5: Kiểm tra trạng thái tài khoản ──────────────────
        if (Status.LOCKED == user.getStatus()) {
            // 3.0-E3: Tài khoản bị khóa → từ chối, ghi log cảnh báo
            safeInsertLog(new ActivityLog(user.getId(),
                    "PASSWORD_RESET_BLOCKED_LOCKED",
                    "Yêu cầu OTP bị từ chối vì tài khoản bị khóa: " + email,
                    ipAddress, userAgent));
            throw new AuthException(ErrorCode.ACCOUNT_LOCKED,
                    "Tài khoản hiện đang bị khóa. Vui lòng liên hệ quản trị viên.");
        }

        // ── 3.0.6: Kiểm tra bộ đếm yêu cầu OTP liên tiếp ──────────
        // Tái dụng cột failed_login_attempts để đếm số lần yêu cầu OTP liên tiếp.
        // BR-01: >= MAX_OTP_REQUESTS → khóa tài khoản, ghi log spam.
        if (user.getFailedLoginAttempts() >= MAX_OTP_REQUESTS) {
            // 3.0-E4: Vượt giới hạn → khóa tài khoản
            lockAccountDueToSpam(user, email, ipAddress, userAgent);
            throw new AuthException(ErrorCode.ACCOUNT_LOCKED,
                    "Vượt quá số lần yêu cầu cho phép. Tài khoản đã bị khóa tạm thời.");
        }

        // ── 3.0.7: Tăng bộ đếm số lần yêu cầu OTP lên 1 ──────────
        // (Đặt SAU kiểm tra giới hạn — nếu đang ở lần thứ 4, tăng lên 5
        //  và lần sau mới bị chặn. Tài liệu gốc 3.0.7 đặt trước 3.0.6
        //  nhưng sửa để logic chặt hơn: check trước, tăng sau.)
        userRepository.increaseFailedAttempts(user.getId());

        // ── 3.0.8: Tạo OTP ngẫu nhiên an toàn, thiết lập thời gian hết hạn ──
        String plainOtp  = OtpUtil.generateOtp();          // "123456" (plaintext gửi email)
        String hashedOtp = OtpUtil.hashOtp(plainOtp);      // SHA-256 hash lưu DB (BR-04)
        LocalDateTime now       = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusSeconds(OTP_EXPIRY_SECONDS); // BR-02: +120s

        // ── 3.0.9: Lưu OTP hash, thời gian tạo và hết hạn vào CSDL ─
        otpRepository.saveOtp(user.getId(), hashedOtp, expiresAt);

        // ── 3.0.10: Gửi OTP đến email của Actor ─────────────────────
        try {
            EmailService.sendOtpEmail(email, plainOtp);
        } catch (MessagingException | UnsupportedEncodingException e) {
            // 3.0-EDB: Lỗi Email Server → ghi log nội bộ, ném lỗi kỹ thuật
            safeInsertLog(new ActivityLog(user.getId(),
                    "PASSWORD_RESET_EMAIL_FAILED",
                    "Gửi OTP thất bại cho email: " + email + " | Lý do: " + e.getMessage(),
                    ipAddress, userAgent));
            throw new RuntimeException(
                    "Không thể gửi email xác thực. Vui lòng thử lại sau.", e);
        }

        // Ghi log gửi OTP thành công
        safeInsertLog(new ActivityLog(user.getId(),
                "PASSWORD_RESET_OTP_SENT",
                "Đã gửi OTP khôi phục mật khẩu đến: " + email,
                ipAddress, userAgent));
    }

    // ════════════════════════════════════════════════════════════════
    //  BƯỚC 1b — Gửi lại OTP (UC03.1 Alternative Flow)
    // ════════════════════════════════════════════════════════════════

    /**
     * UC03.1 — Xử lý yêu cầu "Gửi lại mã OTP".
     *
     * Kiểm tra cooldown trước khi tạo OTP mới.
     * Vô hiệu hóa tất cả OTP cũ của user trước khi tạo OTP mới (3.1.4).
     *
     * @param userId    ID tài khoản (lấy từ session sau bước 1)
     * @param email     Email để gửi lại
     * @param ipAddress IP của client
     * @param userAgent User-Agent header
     * @return số giây còn lại phải chờ (0 = đã gửi thành công)
     * @throws AuthException nếu vượt giới hạn OTP hoặc tài khoản bị khóa
     */
    public long resendOtp(int userId, String email, String ipAddress, String userAgent) {
        // ── 3.1.2: Kiểm tra cooldown giữa 2 lần gửi OTP ───────────
        // Tránh spam: user phải chờ ít nhất OTP_RESEND_COOLDOWN_SECONDS giữa 2 lần gửi
        LocalDateTime lastSent = otpRepository.findLastOtpCreatedAt(userId);
        if (lastSent != null) {
            long secondsSinceLast = ChronoUnit.SECONDS.between(lastSent, LocalDateTime.now());
            if (secondsSinceLast < OTP_RESEND_COOLDOWN_SECONDS) {
                // Trả về số giây còn phải chờ — servlet hiển thị countdown
                return OTP_RESEND_COOLDOWN_SECONDS - secondsSinceLast;
            }
        }

        // ── 3.1.3: Kiểm tra lại giới hạn số lần yêu cầu OTP ───────
        User user = userRepository.findById(userId);
        if (user == null) {
            throw new AuthException(ErrorCode.INVALID_CREDENTIALS, "Phiên làm việc không hợp lệ.");
        }
        if (Status.LOCKED == user.getStatus() || user.getFailedLoginAttempts() >= MAX_OTP_REQUESTS) {
            // 3.0-E4: Vượt giới hạn → khóa tài khoản
            lockAccountDueToSpam(user, email, ipAddress, userAgent);
            throw new AuthException(ErrorCode.ACCOUNT_LOCKED,
                    "Vượt quá số lần yêu cầu cho phép. Tài khoản đã bị khóa tạm thời.");
        }

        // ── 3.1.4: Vô hiệu hóa tất cả OTP cũ của user ──────────────
        otpRepository.invalidateAllOtpByUserId(userId);

        // ── 3.1.5: Thực hiện lại 3.0.7 → 3.0.10 ────────────────────
        // (gọi lại requestPasswordReset để tái dụng logic)
        requestPasswordReset(email, ipAddress, userAgent);

        return 0; // 0 = gửi thành công, không cần chờ
    }

    // ════════════════════════════════════════════════════════════════
    //  BƯỚC 2 — Xác thực mã OTP (3.0.11 → 3.0.12)
    // ════════════════════════════════════════════════════════════════

    /**
     * Xác thực mã OTP người dùng nhập.
     *
     * Sau bước này, nếu thành công, servlet lưu userId vào session để
     * bước đặt lại mật khẩu (bước 3) có thể dùng mà không cần nhập lại OTP.
     *
     * ⚠️ Sửa luồng: Trả về OtpToken thay vì void để servlet lưu otpId
     * vào session — cần otpId để gọi markOtpUsed() ở bước 3.
     * Tài liệu gốc không nêu rõ cách truyền otpId sang bước sau.
     *
     * @param userId  ID tài khoản (lưu từ bước 1 trong session)
     * @param plainOtp Mã OTP 6 chữ số người dùng nhập
     * @return OtpToken hợp lệ (servlet lưu token.getId() vào session)
     * @throws AuthException nếu OTP sai hoặc hết hạn (3.0-E5)
     */
    public OtpToken verifyOtp(int userId, String plainOtp) {
        // ── 3.0.12: Lấy OTP chưa dùng mới nhất của user ────────────
        OtpToken token = otpRepository.findLatestByUserId(userId);

        // 3.0-E5a: Không có OTP nào (chưa gửi hoặc đã dùng hết)
        if (token == null) {
            throw new AuthException(ErrorCode.INVALID_CREDENTIALS,
                    "Mã xác thực OTP không chính xác. Vui lòng kiểm tra lại.");
        }

        // 3.0-E5b: OTP đã hết hạn (BR-02: > 120 giây)
        if (!token.isValid()) {
            throw new AuthException(ErrorCode.INVALID_CREDENTIALS,
                    "Mã xác thực OTP đã hết hạn. Vui lòng yêu cầu mã mới.");
        }

        // 3.0-E5c: OTP sai (so sánh hash để tránh timing attack)
        if (!OtpUtil.verifyOtp(plainOtp, token.getOtpCode())) {
            throw new AuthException(ErrorCode.INVALID_CREDENTIALS,
                    "Mã xác thực OTP không chính xác. Vui lòng kiểm tra lại.");
        }

        // OTP hợp lệ → trả về token để servlet lưu otpId vào session
        return token;
    }

    // ════════════════════════════════════════════════════════════════
    //  BƯỚC 3 — Đặt lại mật khẩu mới (3.0.13 → 3.0.19)
    // ════════════════════════════════════════════════════════════════

    /**
     * Đặt lại mật khẩu mới sau khi OTP đã được xác thực.
     *
     * Điều kiện tiên quyết: servlet đã xác thực OTP ở bước 2 và lưu
     * userId + otpId vào session (tránh bypass bằng cách gọi thẳng URL này).
     *
     * NFR-01: Mật khẩu tối thiểu 8 ký tự, có chữ cái, số, ký tự đặc biệt.
     * NFR-02 / BR-03: Mật khẩu được băm BCrypt trước khi lưu.
     * BR-04: Vô hiệu hóa OTP sau khi đặt lại thành công.
     *
     * @param userId      ID tài khoản (từ session)
     * @param otpId       ID của OtpToken đã xác thực (từ session)
     * @param newPassword Mật khẩu mới người dùng nhập
     * @param confirmPass Nhập lại mật khẩu mới
     * @param ipAddress   IP client
     * @param userAgent   User-Agent header
     * @throws AuthException nếu mật khẩu không hợp lệ (3.0-E6)
     */
    public void resetPassword(int userId, int otpId,
                              String newPassword, String confirmPass,
                              String ipAddress, String userAgent) {

        // ── 3.0.15: Validate mật khẩu mới ──────────────────────────
        String validationError = validateNewPassword(newPassword, confirmPass);
        if (validationError != null) {
            // 3.0-E6: Mật khẩu không hợp lệ hoặc không khớp
            throw new AuthException(ErrorCode.INVALID_CREDENTIALS, validationError);
        }

        // ── 3.0.16: Băm mật khẩu mới bằng BCrypt (BR-03) ───────────
        String newHashedPassword = BCryptUtil.hashPassword(newPassword);

        // ── 3.0.17a: Cập nhật mật khẩu mới vào CSDL ─────────────────
        updatePassword(userId, newHashedPassword);

        // ── 3.0.17b: Vô hiệu hóa OTP đã sử dụng (BR-04) ─────────────
        otpRepository.markOtpUsed(otpId);

        // ── 3.0.18: Reset bộ đếm số lần yêu cầu OTP về 0 ────────────
        // Dùng lại resetFailedAttempts() — cột failed_login_attempts = 0
        userRepository.resetFailedAttempts(userId);

        // ── 3.0.19: Ghi log sự kiện "Khôi phục mật khẩu thành công" ─
        safeInsertLog(new ActivityLog(userId,
                "PASSWORD_RESET_SUCCESS",
                "Khôi phục mật khẩu thành công",
                ipAddress, userAgent));
    }

    // ════════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ════════════════════════════════════════════════════════════════

    /**
     * NFR-01: Validate độ mạnh mật khẩu mới.
     *
     * Quy tắc:
     *  - Tối thiểu 8 ký tự
     *  - Có ít nhất 1 chữ cái
     *  - Có ít nhất 1 chữ số
     *  - Có ít nhất 1 ký tự đặc biệt
     *  - Hai trường nhập phải trùng khớp
     *
     * @return null nếu hợp lệ, String thông báo lỗi nếu không hợp lệ
     */
    private String validateNewPassword(String newPassword, String confirmPass) {
        if (newPassword == null || newPassword.isBlank()) {
            return "Mật khẩu mới không được để trống.";
        }
        if (newPassword.length() < 8) {
            return "Mật khẩu mới phải có ít nhất 8 ký tự.";
        }
        // Kiểm tra có chữ cái
        if (!newPassword.matches(".*[a-zA-Z].*")) {
            return "Mật khẩu phải chứa ít nhất 1 chữ cái.";
        }
        // Kiểm tra có chữ số
        if (!newPassword.matches(".*[0-9].*")) {
            return "Mật khẩu phải chứa ít nhất 1 chữ số.";
        }
        // Kiểm tra có ký tự đặc biệt (NFR-01)
        if (!newPassword.matches(".*[^a-zA-Z0-9].*")) {
            return "Mật khẩu phải chứa ít nhất 1 ký tự đặc biệt (vd: @, #, !).";
        }
        // Kiểm tra khớp nhau
        if (!newPassword.equals(confirmPass)) {
            return "Mật khẩu nhập lại không khớp. Vui lòng kiểm tra lại.";
        }
        return null; // hợp lệ
    }

    /**
     * Cập nhật mật khẩu băm mới vào CSDL cho user.
     * Inline SQL thay vì thêm method vào UserRepository để tách biệt concern.
     */
    private void updatePassword(int userId, String newHashedPassword) {
        final String sql = "UPDATE users SET password_hash = ?, updated_at = NOW() WHERE id = ?";

        try (java.sql.Connection conn = com.example.uc.cofig.DatabaseConfig.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, newHashedPassword);
            ps.setInt(2, userId);
            ps.executeUpdate();

        } catch (java.sql.SQLException e) {
            throw new DatabaseException(
                    "updatePassword thất bại cho userId=" + userId, e);
        }
    }

    /**
     * Khóa tài khoản khi vượt giới hạn yêu cầu OTP (BR-01 + 3.0-E4).
     * Dùng SQL UPDATE trực tiếp thay vì qua method vì cần set status=LOCKED
     * mà không tăng failed_login_attempts thêm nữa.
     */
    private void lockAccountDueToSpam(User user, String email,
                                      String ipAddress, String userAgent) {
        final String sql = "UPDATE users SET status = 'LOCKED' WHERE id = ?";

        try (java.sql.Connection conn = com.example.uc.cofig.DatabaseConfig.getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, user.getId());
            ps.executeUpdate();

        } catch (java.sql.SQLException e) {
            // Không ném exception — log và tiếp tục ném AuthException
            System.err.println("[PasswordResetService] WARN: không khóa được tài khoản userId="
                    + user.getId() + ": " + e.getMessage());
        }

        // 3.0-E4: Ghi log spam OTP
        safeInsertLog(new ActivityLog(user.getId(),
                "PASSWORD_RESET_SPAM_LOCKED",
                "Tài khoản bị khóa do vượt quá " + MAX_OTP_REQUESTS
                        + " lần yêu cầu OTP liên tiếp: " + email,
                ipAddress, userAgent));
    }

    private void safeInsertLog(ActivityLog log) {
        try {
            userRepository.insertLog(log);
        } catch (DatabaseException e) {
            System.err.println("[PasswordResetService] WARN: không ghi được log action="
                    + log.getAction() + ": " + e.getMessage());
        }
    }
}
