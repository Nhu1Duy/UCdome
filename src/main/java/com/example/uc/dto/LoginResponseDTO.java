package com.example.uc.dto;

import com.example.uc.model.User;

/**
 * ════════════════════════════════════════════════════════════════════
 *  LoginResponseDTO — DTO kết quả đăng nhập thành công
 *
 *  Mục đích : Đóng gói chỉ những thông tin cần thiết sau khi đăng nhập,
 *             thay vì trả toàn bộ entity User (có chứa passwordHash,
 *             failedLoginAttempts, deletedAt, v.v. không cần thiết).
 *
 *  Được tạo bởi : AuthService.loginWithEmailPassword() / loginWithGoogle()
 *  Được sử dụng bởi : LoginServlet.createSession() + redirectByRole()
 * ════════════════════════════════════════════════════════════════════
 */
public class LoginResponseDTO {

    /** ID người dùng trong DB — dùng làm khóa session "userId" (bước 1.0.8) */
    private final int id;

    /** Email — lưu vào session "userEmail" để hiển thị và tra cứu (bước 1.0.8) */
    private final String email;

    /**
     * Tên đầy đủ — lưu vào session "fullName" để chào đón người dùng trên UI.
     */
    private final String fullName;

    /**
     * Role dạng String (USER / ADMIN) — lưu vào session "userRole".
     * Dùng để điều hướng (bước 1.0.10) và kiểm soát truy cập trang.
     */
    private final String role;

    /**
     * URL ảnh đại diện (avatar) từ Google hoặc null nếu tài khoản nội bộ.
     * Lưu vào session "avatarUrl" để hiển thị trên header UI.
     */
    private final String avatarUrl;

    /**
     * Xây dựng DTO từ entity User sau khi đăng nhập thành công.
     * Chỉ lấy các trường cần thiết, không lộ thông tin nhạy cảm.
     *
     * @param user  Entity User vừa được xác thực bởi AuthService
     */
    public LoginResponseDTO(User user) {
        this.id        = user.getId();
        this.email     = user.getEmail();
        this.fullName  = user.getFullName();
        this.role      = user.getRole().name();
        this.avatarUrl = user.getAvatarUrl();
    }

    public int    getId()        { return id; }
    public String getEmail()     { return email; }
    public String getFullName()  { return fullName; }
    public String getRole()      { return role; }
    public String getAvatarUrl() { return avatarUrl; }

    /**
     * Kiểm tra người dùng có role ADMIN không.
     * Dùng bởi LoginServlet.redirectByRole() (bước 1.0.10).
     *
     * @return true nếu role là "ADMIN"
     */
    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}