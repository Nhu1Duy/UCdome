package com.example.uc.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Xử lý xác thực Google OAuth (phiên bản demo không cần thư viện ngoài).
 *
 * Luồng UC1.1:
 *   1.1.7  — Kiểm tra chữ ký điện tử và tính hợp lệ của Token
 *   1.1-E1 — Token không hợp lệ → ném SecurityException
 *
 * ⚠️  ĐIỀU CHỈNH SO VỚI UC-01:
 *   Phiên bản demo này KHÔNG xác thực chữ ký RSA thật từ Google.
 *   Để dùng OAuth thật: thêm thư viện google-api-client và bỏ comment
 *   class GoogleOAuthUtil gốc trong file backup.
 *   Ở đây: token hợp lệ nếu có dạng "demo_token_<email>_<name>"
 */
public class GoogleOAuthUtil {

    /**
     * 1.1.7 — Xác thực ID Token (demo mode).
     * Token hợp lệ format: "demo:<email>:<fullname>"
     * Ví dụ: "demo:nguyenvana@gmail.com:Nguyễn Văn A"
     *
     * @return Map chứa "email" và "name"
     * @throws SecurityException nếu token không hợp lệ (1.1-E1)
     */
    public static Map<String, String> verifyIdToken(String idTokenString)
            throws SecurityException {

        if (idTokenString == null || idTokenString.isBlank()) {
            // 1.1-E1 — Token rỗng hoặc null
            throw new SecurityException(
                "ID Token từ Google không hợp lệ hoặc đã hết hạn.");
        }

        // Demo: kiểm tra prefix "demo:"
        if (!idTokenString.startsWith("demo:")) {
            throw new SecurityException(
                "ID Token từ Google không hợp lệ hoặc đã hết hạn.");
        }

        String[] parts = idTokenString.split(":", 3);
        if (parts.length != 3 || parts[1].isBlank()) {
            throw new SecurityException(
                "ID Token từ Google không hợp lệ (định dạng sai).");
        }

        Map<String, String> payload = new HashMap<>();
        payload.put("email", parts[1].trim());
        payload.put("name",  parts[2].trim());
        return payload;
    }
}
