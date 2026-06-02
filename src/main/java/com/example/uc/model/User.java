package com.example.uc.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class User {

    // ── Enums ────────────────────────────────────────────────────────

    public enum AuthProvider {
        LOCAL, GOOGLE;
        public static AuthProvider from(String v) {
            return v == null ? LOCAL : valueOf(v.toUpperCase());
        }
    }

    public enum Role {
        USER, ADMIN;
        public static Role from(String v) {
            return v == null ? USER : valueOf(v.toUpperCase());
        }
    }

    public enum Status {
        ACTIVE, LOCKED;
        public static Status from(String v) {
            return v == null ? ACTIVE : valueOf(v.toUpperCase());
        }
    }

    public enum Gender {
        MALE, FEMALE, OTHER;
        public static Gender from(String v) {
            return v == null ? null : valueOf(v.toUpperCase());
        }
    }

    // ── Fields ───────────────────────────────────────────────────────

    private int           id;
    private String        email;
    private String        passwordHash;
    private Role          role;
    private Status        status;
    private int           failedLoginAttempts;
    private AuthProvider  authProvider;
    private String        fullName;
    private String        phone;
    private String        avatarUrl;
    private LocalDate     dateOfBirth;
    private Gender        gender;
    private String        addressLine;
    private String        city;
    private String        province;
    private String        country;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ── Constructors ─────────────────────────────────────────────────

    public User() {}

    public User(String email, String passwordHash, Role role,
                Status status, AuthProvider authProvider, String fullName) {
        this.email               = email;
        this.passwordHash        = passwordHash;
        this.role                = role;
        this.status              = status;
        this.authProvider        = authProvider;
        this.fullName            = fullName;
        this.failedLoginAttempts = 0;
        this.country             = "Vietnam";
    }

    // ── Getters & Setters ────────────────────────────────────────────

    public int getId()                              { return id; }
    public void setId(int id)                       { this.id = id; }

    public String getEmail()                        { return email; }
    public void setEmail(String email)              { this.email = email; }

    public String getPasswordHash()                 { return passwordHash; }
    public void setPasswordHash(String h)           { this.passwordHash = h; }

    public Role getRole()                           { return role; }
    public void setRole(Role role)                  { this.role = role; }
    public void setRole(String role)                { this.role = Role.from(role); }

    public Status getStatus()                       { return status; }
    public void setStatus(Status status)            { this.status = status; }
    public void setStatus(String status)            { this.status = Status.from(status); }

    public int getFailedLoginAttempts()             { return failedLoginAttempts; }
    public void setFailedLoginAttempts(int n)       { this.failedLoginAttempts = n; }

    public AuthProvider getAuthProvider()           { return authProvider; }
    public void setAuthProvider(AuthProvider p)     { this.authProvider = p; }
    public void setAuthProvider(String p)           { this.authProvider = AuthProvider.from(p); }

    public String getFullName()                     { return fullName; }
    public void setFullName(String fullName)        { this.fullName = fullName; }

    public String getPhone()                        { return phone; }
    public void setPhone(String phone)              { this.phone = phone; }

    public String getAvatarUrl()                    { return avatarUrl; }
    public void setAvatarUrl(String url)            { this.avatarUrl = url; }

    public LocalDate getDateOfBirth()               { return dateOfBirth; }
    public void setDateOfBirth(LocalDate d)         { this.dateOfBirth = d; }

    public Gender getGender()                       { return gender; }
    public void setGender(Gender g)                 { this.gender = g; }
    public void setGender(String g)                 { this.gender = Gender.from(g); }

    public String getAddressLine()                  { return addressLine; }
    public void setAddressLine(String a)            { this.addressLine = a; }

    public String getCity()                         { return city; }
    public void setCity(String c)                   { this.city = c; }

    public String getProvince()                     { return province; }
    public void setProvince(String p)               { this.province = p; }

    public String getCountry()                      { return country; }
    public void setCountry(String c)                { this.country = c; }

    public LocalDateTime getLastLoginAt()           { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime t)     { this.lastLoginAt = t; }

    public LocalDateTime getCreatedAt()             { return createdAt; }
    public void setCreatedAt(LocalDateTime t)       { this.createdAt = t; }

    public LocalDateTime getUpdatedAt()             { return updatedAt; }
    public void setUpdatedAt(LocalDateTime t)       { this.updatedAt = t; }
}