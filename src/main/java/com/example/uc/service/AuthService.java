package com.example.uc.service;

import com.example.uc.exception.AuthException;
import com.example.uc.exception.AuthException.ErrorCode;
import com.example.uc.exception.DatabaseException;
import com.example.uc.model.ActivityLog;
import com.example.uc.model.User;
import com.example.uc.model.User.AuthProvider;
import com.example.uc.model.User.Status;
import com.example.uc.repository.UserRepository;
import com.example.uc.util.BCryptUtil;

/**
 * ═══════════════════════════════════════════════════════════════════
 *  AuthService — Tầng Service
 *
 *  Trách nhiệm: TOÀN BỘ logic nghiệp vụ của UC-01 (Đăng nhập hệ thống).
 *  KHÔNG biết HTTP (request/response) — nhận primitive / model, trả User.
 * ═══════════════════════════════════════════════════════════════════
 */
public class AuthService {

    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // ════════════════════════════════════════════════════════════════
    //  BASIC FLOW — Đăng nhập Email / Mật khẩu (1.0.x)
    // ════════════════════════════════════════════════════════════════

    /**
     * @param ipAddress  IP của client (lấy từ servlet, có thể null)
     * @param userAgent  User-Agent header (có thể null)
     */
    public User loginWithEmailPassword(String email, String password,
                                       String ipAddress, String userAgent) {

        // 1.0.4: Truy vấn CSDL
        User user = userRepository.findByEmail(email);

        // 1.0-E2: Không tìm thấy email
        if (user == null) {
            throw new AuthException(ErrorCode.INVALID_CREDENTIALS,
                    "Email hoặc mật khẩu không chính xác.");
        }

        // 1.0-E2: Sai mật khẩu
        if (!BCryptUtil.checkPassword(password, user.getPasswordHash())) {
            safeIncreaseFailedAttempts(user.getId());
            throw new AuthException(ErrorCode.INVALID_CREDENTIALS,
                    "Email hoặc mật khẩu không chính xác.");
        }

        // 1.0.5: Kiểm tra trạng thái — dùng enum, không so sánh String thô
        if (Status.LOCKED == user.getStatus()) {
            safeInsertLog(new ActivityLog(user.getId(), "ACCOUNT_LOCKED_ACCESS",
                    "Cố gắng truy cập tài khoản bị khóa: " + email,
                    ipAddress, userAgent));
            throw new AuthException(ErrorCode.ACCOUNT_LOCKED,
                    "Tài khoản đã bị khóa. Vui lòng liên hệ quản trị viên.");
        }

        // 1.0.6: Reset bộ đếm + cập nhật last_login_at
        userRepository.resetFailedAttempts(user.getId());

        // 1.0.9: Ghi log thành công
        safeInsertLog(new ActivityLog(user.getId(), "LOGIN_SUCCESS",
                "Đăng nhập thành công bằng tài khoản nội bộ: " + email,
                ipAddress, userAgent));

        return user;
    }

    // ════════════════════════════════════════════════════════════════
    //  UC1.1 + UC1.2 — Đăng nhập / Tự đăng ký qua Google
    // ════════════════════════════════════════════════════════════════

    /**
     * @param ipAddress  IP của client (có thể null)
     * @param userAgent  User-Agent header (có thể null)
     */
    public User loginWithGoogle(String email, String fullName,
                                String ipAddress, String userAgent) {

        // 1.1.7: Tìm theo email
        User user = userRepository.findByEmail(email);

        // 1.1.8: Email tồn tại nhưng là LOCAL
        if (user != null && AuthProvider.LOCAL == user.getAuthProvider()) {
            throw new AuthException(ErrorCode.OAUTH_FAILED,
                    "Email này đã đăng ký bằng tài khoản hệ thống. Vui lòng đăng nhập bằng mật khẩu.");
        }

        // Rẽ nhánh UC1.2: chưa có tài khoản Google
        if (user == null) {
            return registerGoogleUser(email, fullName, ipAddress, userAgent);
        }

        // UC1.1: Kiểm tra LOCKED
        if (Status.LOCKED == user.getStatus()) {
            safeInsertLog(new ActivityLog(user.getId(), "ACCOUNT_LOCKED_ACCESS",
                    "Cố gắng đăng nhập Google vào tài khoản bị khóa: " + email,
                    ipAddress, userAgent));
            throw new AuthException(ErrorCode.ACCOUNT_LOCKED,
                    "Tài khoản đã bị khóa. Vui lòng liên hệ quản trị viên.");
        }

        // Reset last_login_at khi đăng nhập Google thành công
        userRepository.resetFailedAttempts(user.getId());

        // 1.0.9: Ghi log thành công
        safeInsertLog(new ActivityLog(user.getId(), "LOGIN_SUCCESS",
                "Đăng nhập thành công qua Google: " + email,
                ipAddress, userAgent));

        return user;
    }

    // ════════════════════════════════════════════════════════════════
    //  UC1.2 — Tự động đăng ký lần đầu qua Google
    // ════════════════════════════════════════════════════════════════

    private User registerGoogleUser(String email, String fullName,
                                    String ipAddress, String userAgent) {
        User newUser = userRepository.createGoogleUser(email, fullName);

        safeInsertLog(new ActivityLog(newUser.getId(), "GOOGLE_REGISTER",
                "Đăng ký mới tự động qua Google: " + email,
                ipAddress, userAgent));

        return newUser;
    }

    // ════════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ════════════════════════════════════════════════════════════════

    private void safeIncreaseFailedAttempts(int userId) {
        try {
            userRepository.increaseFailedAttempts(userId);
        } catch (DatabaseException e) {
            System.err.println("[AuthService] WARN: không tăng được failed_attempts userId="
                    + userId + ": " + e.getMessage());
        }
    }

    private void safeInsertLog(ActivityLog log) {
        try {
            userRepository.insertLog(log);
        } catch (DatabaseException e) {
            System.err.println("[AuthService] WARN: không ghi được log action="
                    + log.getAction() + ": " + e.getMessage());
        }
    }
}
