package com.example.uc.service;

import com.example.uc.dto.GoogleLoginRequestDTO;
import com.example.uc.dto.LoginRequestDTO;
import com.example.uc.dto.LoginResponseDTO;
import com.example.uc.exception.AuthException;
import com.example.uc.exception.AuthException.ErrorCode;
import com.example.uc.exception.DatabaseException;
import com.example.uc.model.ActivityLog;
import com.example.uc.model.User;
import com.example.uc.model.User.AuthProvider;
import com.example.uc.model.User.Status;
import com.example.uc.repository.UserRepository;
import com.example.uc.util.BCryptUtil;

public class AuthService {

    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public LoginResponseDTO loginWithEmailPassword(LoginRequestDTO dto) {

        // 1.0.6 — Tìm user theo email trong CSDL
        User user = userRepository.findByEmail(dto.getEmail());

        // 1.0-E2a — Email không tồn tại → từ chối, không tiết lộ nguyên nhân cụ thể
        if (user == null) {
            throw new AuthException(ErrorCode.INVALID_CREDENTIALS,
                    "Email hoặc mật khẩu không chính xác.");
        }

        // 1.0.7 — Kiểm tra mật khẩu với BCrypt hash
        if (!BCryptUtil.checkPassword(dto.getPassword(), user.getPasswordHash())) {
            // 1.0-E2b — Mật khẩu sai → tăng bộ đếm, tự khóa nếu vượt ngưỡng
            safeIncreaseFailedAttempts(user.getId());
            throw new AuthException(ErrorCode.INVALID_CREDENTIALS,
                    "Email hoặc mật khẩu không chính xác.");
        }

        // 1.0.8 — Kiểm tra trạng thái tài khoản
        if (Status.LOCKED == user.getStatus()) {
            // 1.0-E3 — Tài khoản bị khóa → ghi log, từ chối đăng nhập
            safeInsertLog(new ActivityLog(user.getId(), "ACCOUNT_LOCKED_ACCESS",
                    "Cố gắng truy cập tài khoản bị khóa: " + dto.getEmail(),
                    dto.getIpAddress(), dto.getUserAgent()));
            throw new AuthException(ErrorCode.ACCOUNT_LOCKED,
                    "Tài khoản đã bị khóa. Vui lòng liên hệ quản trị viên.");
        }

        // 1.0.9 — Đăng nhập hợp lệ → reset failed_attempts, cập nhật last_login_at
        userRepository.resetFailedAttempts(user.getId());

        // 1.0.10 — Ghi log đăng nhập thành công → trả DTO về Servlet
        safeInsertLog(new ActivityLog(user.getId(), "LOGIN_SUCCESS",
                "Đăng nhập thành công bằng tài khoản nội bộ: " + dto.getEmail(),
                dto.getIpAddress(), dto.getUserAgent()));

        return new LoginResponseDTO(user);
    }

    // ════════════════════════════════════════════════════════════════
    //  UC1.1/UC1.2 — Đăng nhập qua Google
    //  ──────────────────────────────────────────────────────────────
    //  ALTERNATIVE FLOW (UC1.1 — Tài khoản Google đã tồn tại):
    //    1.1.9  — Tìm user theo email Google (UserRepository.findByEmail)
    //    1.1.10 — Tài khoản tồn tại, kiểm tra provider ≠ LOCAL
    //    1.1.11 — Kiểm tra trạng thái tài khoản (Status.LOCKED?)
    //    1.1.12 — Cập nhật last_login_at (resetFailedAttempts)
    //    1.1.13 — Ghi log đăng nhập Google thành công → trả DTO về Servlet
    //
    //  ALTERNATIVE FLOW (UC1.2 — Tự động đăng ký tài khoản mới):
    //    1.1.9  — findByEmail trả null → chưa có tài khoản
    //    1.2.1  — Gọi registerGoogleUser() → INSERT user mới (role=USER,
    //             status=ACTIVE, provider=GOOGLE)
    //    1.2.2  — Ghi log đăng ký Google (action = GOOGLE_REGISTER)
    //    1.2.3  — Trả DTO về Servlet
    //
    //  EXCEPTION FLOW:
    //    1.1-E2 — Email tồn tại nhưng provider = LOCAL
    //             → AuthException(OAUTH_FAILED): yêu cầu dùng mật khẩu
    //    1.1-E3 — Tài khoản Google bị khóa → ghi log, từ chối
    //             → AuthException(ACCOUNT_LOCKED)
    // ════════════════════════════════════════════════════════════════
    public LoginResponseDTO loginWithGoogle(GoogleLoginRequestDTO dto) {

        // 1.1.9 — Tìm user theo email Google trong CSDL
        User user = userRepository.findByEmail(dto.getEmail());

        // 1.1.9 / UC1.2 — Chưa có tài khoản Google → rẽ nhánh tự động đăng ký
        if (user == null) {
            return registerGoogleUser(dto);
        }
        // 1.1.10 / 1.1-E2 — Email đã đăng ký bằng LOCAL → không cho đăng nhập Google
        if (user != null && AuthProvider.LOCAL == user.getAuthProvider()) {
            throw new AuthException(ErrorCode.OAUTH_FAILED,
                    "Email này đã đăng ký bằng tài khoản hệ thống. Vui lòng đăng nhập bằng mật khẩu.");
        }

        // 1.1.11 / 1.1-E3 — Tài khoản Google tồn tại nhưng đang bị khóa
        if (Status.LOCKED == user.getStatus()) {
            safeInsertLog(new ActivityLog(user.getId(), "ACCOUNT_LOCKED_ACCESS",
                    "Cố gắng đăng nhập Google vào tài khoản bị khóa: " + dto.getEmail(),
                    dto.getIpAddress(), dto.getUserAgent()));
            throw new AuthException(ErrorCode.ACCOUNT_LOCKED,
                    "Tài khoản đã bị khóa. Vui lòng liên hệ quản trị viên.");
        }

        // 1.1.12 — Đăng nhập Google hợp lệ → cập nhật last_login_at
        userRepository.resetFailedAttempts(user.getId());

        // 1.1.13 — Ghi log đăng nhập Google thành công → trả DTO về Servlet
        safeInsertLog(new ActivityLog(user.getId(), "LOGIN_SUCCESS",
                "Đăng nhập thành công qua Google: " + dto.getEmail(),
                dto.getIpAddress(), dto.getUserAgent()));

        return new LoginResponseDTO(user);
    }

    private LoginResponseDTO registerGoogleUser(GoogleLoginRequestDTO dto) {

        // 1.2.1 — INSERT user mới vào DB với provider=GOOGLE
        User newUser = userRepository.createGoogleUser(dto.getEmail(), dto.getFullName());

        // 1.2.2 — Ghi log đăng ký tự động qua Google
        safeInsertLog(new ActivityLog(newUser.getId(), "GOOGLE_REGISTER",
                "Đăng ký mới tự động qua Google: " + dto.getEmail(),
                dto.getIpAddress(), dto.getUserAgent()));

        // 1.2.3 — Trả DTO về loginWithGoogle() → Servlet tạo session
        return new LoginResponseDTO(newUser);
    }

    //  Helper — Tăng bộ đếm failed_attempts (dùng tại 1.0-E2b)
    private void safeIncreaseFailedAttempts(int userId) {
        try {
            userRepository.increaseFailedAttempts(userId);
        } catch (DatabaseException e) {
            System.err.println("[AuthService] WARN: không tăng được failed_attempts userId="
                    + userId + ": " + e.getMessage());
        }
    }

    //  Helper — Ghi ActivityLog an toàn (dùng tại 1.0.10, 1.0-E3, 1.1-E3, 1.2.2)
    private void safeInsertLog(ActivityLog log) {
        try {
            userRepository.insertLog(log);
        } catch (DatabaseException e) {
            System.err.println("[AuthService] WARN: không ghi được log action="
                    + log.getAction() + ": " + e.getMessage());
        }
    }
}