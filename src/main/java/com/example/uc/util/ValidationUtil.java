package com.example.uc.util;

/**
 * Tiện ích validate đầu vào.
 * Dùng trong: 1.0.3 — Hệ thống thực hiện validate dữ liệu đầu vào
 *             1.0-E1 — Validate đầu vào thất bại
 */
public class ValidationUtil {

    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int MAX_EMAIL_LENGTH    = 255;

    /**
     * 1.0.3 — Validate email: không rỗng, đúng định dạng, không vượt giới hạn
     */
    public static String validateEmail(String email) {
        if (email == null || email.isBlank()) {
            return "Email không được để trống.";
        }
        if (email.length() > MAX_EMAIL_LENGTH) {
            return "Email không hợp lệ (quá dài).";
        }
        // Regex đơn giản kiểm tra định dạng email
        if (!email.matches("^[\\w._%+\\-]+@[\\w.\\-]+\\.[a-zA-Z]{2,}$")) {
            return "Định dạng Email không hợp lệ.";
        }
        return null; // null = hợp lệ
    }

    /**
     * 1.0.3 — Validate mật khẩu: không rỗng, đủ độ dài tối thiểu
     */
    public static String validatePassword(String password) {
        if (password == null || password.isBlank()) {
            return "Mật khẩu không được để trống.";
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            return "Mật khẩu phải có ít nhất " + MIN_PASSWORD_LENGTH + " ký tự.";
        }
        return null;
    }
}
