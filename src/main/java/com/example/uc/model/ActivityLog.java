package com.example.uc.model;

import java.time.LocalDateTime;

/**
 * Model cho bảng ActivityLog
 * Dùng trong:
 *   1.0.9  — ghi log đăng nhập thành công
 *   1.0-E3 — ghi log cảnh báo tài khoản bị khóa
 *   1.1-E1 — ghi log lỗi OAuth
 *   1.2.5  — ghi log đăng ký mới qua Google
 */
public class ActivityLog {
    private int id;
    private Integer userId;          // nullable nếu chưa xác định user
    private String action;           // LOGIN_SUCCESS | LOGIN_FAILED | ACCOUNT_LOCKED | OAUTH_ERROR | GOOGLE_REGISTER
    private String description;
    private LocalDateTime createdAt;

    public ActivityLog(Integer userId, String action, String description) {
        this.userId = userId;
        this.action = action;
        this.description = description;
        this.createdAt = LocalDateTime.now();
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public Integer getUserId() { return userId; }
    public String getAction() { return action; }
    public String getDescription() { return description; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
