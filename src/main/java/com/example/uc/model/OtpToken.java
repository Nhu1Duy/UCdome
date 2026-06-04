package com.example.uc.model;

import java.time.LocalDateTime;

/**
 * Model đại diện cho một mã OTP khôi phục mật khẩu.
 *
 * Lưu ý thiết kế (BR-02, BR-04):
 *  - Mỗi OTP chỉ hợp lệ tối đa 120 giây kể từ created_at.
 *  - Mỗi OTP chỉ được xác thực thành công đúng 1 lần (used = true sau khi dùng).
 */
public class OtpToken {

    private int           id;
    private int           userId;
    private String        otpCode;          // Mã 6 chữ số đã được băm SHA-256 khi lưu DB
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;        // BR-02: createdAt + 120 giây
    private boolean       used;             // BR-04: true = đã sử dụng, không dùng lại được

    // ── Constructors ────────────────────────────────────────────────

    public OtpToken() {}

    public OtpToken(int userId, String otpCode, LocalDateTime createdAt, LocalDateTime expiresAt) {
        this.userId    = userId;
        this.otpCode   = otpCode;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.used      = false;
    }

    // ── Getters & Setters ────────────────────────────────────────────

    public int getId()                             { return id; }
    public void setId(int id)                      { this.id = id; }

    public int getUserId()                         { return userId; }
    public void setUserId(int userId)              { this.userId = userId; }

    public String getOtpCode()                     { return otpCode; }
    public void setOtpCode(String otpCode)         { this.otpCode = otpCode; }

    public LocalDateTime getCreatedAt()            { return createdAt; }
    public void setCreatedAt(LocalDateTime t)      { this.createdAt = t; }

    public LocalDateTime getExpiresAt()            { return expiresAt; }
    public void setExpiresAt(LocalDateTime t)      { this.expiresAt = t; }

    public boolean isUsed()                        { return used; }
    public void setUsed(boolean used)              { this.used = used; }

    // ── Helper ───────────────────────────────────────────────────────

    /**
     * Kiểm tra OTP còn hiệu lực không.
     * Điều kiện: chưa dùng VÀ thời điểm hiện tại chưa vượt expiresAt.
     * BR-02: expiresAt = createdAt + 120s.
     */
    public boolean isValid() {
        return !used && LocalDateTime.now().isBefore(expiresAt);
    }
}
