package com.example.uc.dto;

/**
 * ════════════════════════════════════════════════════════════════════
 *  GoogleLoginRequestDTO — DTO cho UC-01 nhánh Google
 *                          (UC1.1: Đã có tài khoản / UC1.2: Tự đăng ký)
 *
 *  Mục đích : Đóng gói thông tin người dùng đã được xác thực bởi Google
 *             (sau khi verifyIdToken hoặc exchangeCodeAndVerify thành công)
 *             để truyền sang AuthService mà không lộ Map<String,String>.
 *
 *  Được tạo bởi : LoginServlet.processGoogleUser()
 *  Được sử dụng bởi : AuthService.loginWithGoogle()
 * ════════════════════════════════════════════════════════════════════
 */
public class GoogleLoginRequestDTO {

    /**
     * Email Google đã xác thực — trích xuất từ JWT payload (trường "email").
     * Đây là định danh chính để tìm/tạo tài khoản (bước 1.1.7 / 1.2.1).
     */
    private final String email;

    /**
     * Tên đầy đủ người dùng — trích xuất từ JWT payload (trường "name").
     * Dùng để khởi tạo full_name khi tạo tài khoản mới (UC1.2 bước 1.2.1).
     * Fallback về email nếu Google không trả về tên.
     */
    private final String fullName;

    /**
     * IP thực của client — trích xuất từ header X-Forwarded-For / X-Real-IP / RemoteAddr.
     * Dùng để ghi ActivityLog (bước 1.0.9 / 1.2.4).
     */
    private final String ipAddress;

    /**
     * User-Agent header của trình duyệt client (tối đa 512 ký tự).
     * Dùng để ghi ActivityLog (bước 1.0.9 / 1.2.4).
     */
    private final String userAgent;

    public GoogleLoginRequestDTO(String email, String fullName,
                                 String ipAddress, String userAgent) {
        this.email     = email;
        this.fullName  = fullName;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }

    public String getEmail()     { return email; }
    public String getFullName()  { return fullName; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
}