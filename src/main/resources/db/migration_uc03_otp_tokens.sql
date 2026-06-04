-- ═══════════════════════════════════════════════════════════════════
--  Migration UC-03: Tạo bảng otp_tokens
--
--  Bảng lưu trữ mã OTP khôi phục mật khẩu.
--  BR-02: OTP hết hạn sau 120 giây (expires_at = created_at + 120s).
--  BR-04: otp_code lưu dạng SHA-256 hash, không lưu plaintext.
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS otp_tokens (
    id         INT          AUTO_INCREMENT PRIMARY KEY,
    user_id    INT          NOT NULL,
    otp_code   VARCHAR(64)  NOT NULL COMMENT 'SHA-256 hash của mã OTP 6 chữ số',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at DATETIME     NOT NULL COMMENT 'BR-02: created_at + 120 giây',
    used       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT 'BR-04: 1 = đã dùng, không dùng lại',

    CONSTRAINT fk_otp_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE,

    -- Index để tăng tốc truy vấn findLatestByUserId và findLastOtpCreatedAt
    INDEX idx_otp_user_created (user_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
