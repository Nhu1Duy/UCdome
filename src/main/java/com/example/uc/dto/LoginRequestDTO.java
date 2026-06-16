package com.example.uc.dto;

/**
 * ════════════════════════════════════════════════════════════════════
 *  LoginRequestDTO — DTO cho UC-01: Đăng nhập hệ thống (Email/Mật khẩu)
 *
 *  Mục đích : Đóng gói dữ liệu đầu vào từ HTTP request (tầng Servlet)
 *             trước khi truyền sang tầng Service.
 *             Giúp tách biệt hoàn toàn HttpServletRequest khỏi Service/Repository.
 *
 *  Được tạo bởi : LoginServlet.handleLocalLogin()
 *  Được sử dụng bởi : AuthService.loginWithEmailPassword()
 * ════════════════════════════════════════════════════════════════════
 */
public class LoginRequestDTO {

    /** Email người dùng nhập vào form đăng nhập (bước 1.0.2) */
    private final String email;

    /** Mật khẩu người dùng nhập vào form đăng nhập (bước 1.0.2) */
    private final String password;

    /**
     * IP thực của client — được trích xuất từ header X-Forwarded-For
     * hoặc X-Real-IP hoặc RemoteAddr (hỗ trợ reverse proxy).
     * Dùng để ghi ActivityLog (bước 1.0.9).
     */
    private final String ipAddress;

    /**
     * User-Agent header của trình duyệt client.
     * Giới hạn tối đa 512 ký tự để tránh lưu dữ liệu quá lớn vào DB.
     * Dùng để ghi ActivityLog (bước 1.0.9).
     */
    private final String userAgent;

    public LoginRequestDTO(String email, String password,
                           String ipAddress, String userAgent) {
        this.email     = email;
        this.password  = password;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }

    public String getEmail()     { return email; }
    public String getPassword()  { return password; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
}