package com.example.uc.dto;

/**
 * ════════════════════════════════════════════════════════════════════
 *  GoogleTokenPayloadDTO — DTO kết quả xác thực Google OAuth
 *
 *  Mục đích : Thay thế Map<String,String> mà GoogleOAuthUtil đang trả về,
 *             cung cấp kiểu dữ liệu rõ ràng, type-safe, có Javadoc đầy đủ.
 *
 *  Được tạo bởi : GoogleOAuthUtil.verifyIdToken()
 *                 GoogleOAuthUtil.exchangeCodeAndVerify()
 *  Được sử dụng bởi : LoginServlet.handleGoogleLogin()
 *                     LoginServlet.handleGoogleLoginWithCode()
 *                     LoginServlet.processGoogleUser()
 * ════════════════════════════════════════════════════════════════════
 */
public class GoogleTokenPayloadDTO {

    /**
     * Email người dùng Google đã xác thực — trích xuất từ trường "email" của JWT payload.
     * Không null sau khi xác thực thành công (GoogleOAuthUtil đã kiểm tra).
     */
    private final String email;

    /**
     * Tên đầy đủ người dùng — trích xuất từ trường "name" của JWT payload.
     * Fallback về giá trị email nếu Google không cung cấp.
     */
    private final String name;

    public GoogleTokenPayloadDTO(String email, String name) {
        this.email = email;
        this.name  = name;
    }

    public String getEmail() { return email; }
    public String getName()  { return name; }
}