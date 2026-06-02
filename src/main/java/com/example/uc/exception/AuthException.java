package com.example.uc.exception;

/**
 * AuthException — Lỗi nghiệp vụ xác thực (authentication).
 *
 * Ném từ AuthService khi:
 *   - Email/mật khẩu sai (1.0-E2)
 *   - Tài khoản bị khóa (1.0-E3 / 1.1-E2)
 *   - Token Google không hợp lệ (1.1-E1)
 *
 * Bắt ở Servlet → hiển thị thông báo tương ứng cho người dùng.
 */
public class AuthException extends RuntimeException {

    /** Mã lỗi để Servlet phân biệt loại lỗi nghiệp vụ */
    public enum ErrorCode {
        INVALID_CREDENTIALS,   // 1.0-E2: sai email hoặc mật khẩu
        ACCOUNT_LOCKED,        // 1.0-E3: tài khoản bị khóa
        OAUTH_FAILED           // 1.1-E1: lỗi xác thực Google
    }

    private final ErrorCode errorCode;

    public AuthException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}