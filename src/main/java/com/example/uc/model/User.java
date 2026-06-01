package com.example.uc.model;

/**
 * Model ánh xạ bảng Users trong CSDL
 * Dùng trong: Basic Flow 1.0.4, 1.0.5, 1.0.7
 *             UC1.1 bước 1.1.8, 1.1.9
 *             UC1.2 bước 1.2.1 → 1.2.3
 */
public class User {
    private int id;
    private String email;
    private String passwordHash;       // BR-04: lưu hash, không plaintext
    private String role;               // 1.0.7: truy vấn vai trò
    private String status;             // 1.0.5: ACTIVE | LOCKED
    private int failedLoginAttempts;   // BR-02: đếm sai mật khẩu
    private String authProvider;       // "LOCAL" | "GOOGLE"
    private String fullName;

    public User() {}

    public User(String email, String passwordHash, String role,
                String status, String authProvider, String fullName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.status = status;
        this.authProvider = authProvider;
        this.fullName = fullName;
        this.failedLoginAttempts = 0;
    }

    // Getters & Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public void setFailedLoginAttempts(int failedLoginAttempts) {
        this.failedLoginAttempts = failedLoginAttempts;
    }

    public String getAuthProvider() { return authProvider; }
    public void setAuthProvider(String authProvider) { this.authProvider = authProvider; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
}
