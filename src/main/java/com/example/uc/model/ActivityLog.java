package com.example.uc.model;

import java.time.LocalDateTime;

public class ActivityLog {

    private int           id;
    private Integer       userId;
    private String        action;
    private String        description;
    private String        ipAddress;
    private String        userAgent;
    private String        entityType;
    private Integer       entityId;
    private LocalDateTime createdAt;

    // ── Constructors ─────────────────────────────────────────────────

    /** Dùng cho auth logs (không có entity) */
    public ActivityLog(Integer userId, String action, String description) {
        this.userId      = userId;
        this.action      = action;
        this.description = description;
        this.createdAt   = LocalDateTime.now();
    }

    /** Dùng khi có thông tin request */
    public ActivityLog(Integer userId, String action, String description,
                       String ipAddress, String userAgent) {
        this(userId, action, description);
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }

    /** Dùng khi log hành động trên một entity cụ thể */
    public ActivityLog(Integer userId, String action, String description,
                       String ipAddress, String userAgent,
                       String entityType, Integer entityId) {
        this(userId, action, description, ipAddress, userAgent);
        this.entityType = entityType;
        this.entityId   = entityId;
    }

    // ── Getters & Setters ────────────────────────────────────────────

    public int getId()                          { return id; }
    public void setId(int id)                   { this.id = id; }

    public Integer getUserId()                  { return userId; }
    public void setUserId(Integer userId)       { this.userId = userId; }

    public String getAction()                   { return action; }
    public void setAction(String action)        { this.action = action; }

    public String getDescription()              { return description; }
    public void setDescription(String d)        { this.description = d; }

    public String getIpAddress()                { return ipAddress; }
    public void setIpAddress(String ip)         { this.ipAddress = ip; }

    public String getUserAgent()                { return userAgent; }
    public void setUserAgent(String ua)         { this.userAgent = ua; }

    public String getEntityType()               { return entityType; }
    public void setEntityType(String t)         { this.entityType = t; }

    public Integer getEntityId()                { return entityId; }
    public void setEntityId(Integer id)         { this.entityId = id; }

    public LocalDateTime getCreatedAt()         { return createdAt; }
    public void setCreatedAt(LocalDateTime t)   { this.createdAt = t; }
}