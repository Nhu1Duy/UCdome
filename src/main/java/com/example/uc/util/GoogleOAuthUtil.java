package com.example.uc.util;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class GoogleOAuthUtil {

    private static final String CLIENT_ID     = "551138929631-6rbmqqdg881t3bfaiplf8k296t3sku9b.apps.googleusercontent.com";
    private static final String CLIENT_SECRET = "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"; // ← lấy từ Google Console
    private static final String REDIRECT_URI  = "http://localhost:8080/login_app/login";

    /**
     * Bước 1: Đổi authorization code lấy ID Token từ Google
     */
    public static Map<String, String> exchangeCodeAndVerify(String code)
            throws SecurityException {

        if (code == null || code.isBlank()) {
            throw new SecurityException("Authorization code không hợp lệ.");
        }

        try {
            // Gọi Google token endpoint
            URL url = new URL("https://oauth2.googleapis.com/token");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            String body = "code="          + URLEncoder.encode(code,         StandardCharsets.UTF_8)
                    + "&client_id="        + URLEncoder.encode(CLIENT_ID,     StandardCharsets.UTF_8)
                    + "&client_secret="    + URLEncoder.encode(CLIENT_SECRET,  StandardCharsets.UTF_8)
                    + "&redirect_uri="     + URLEncoder.encode(REDIRECT_URI,   StandardCharsets.UTF_8)
                    + "&grant_type=authorization_code";

            conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));

            int status = conn.getResponseCode();
            InputStream is = (status == 200) ? conn.getInputStream() : conn.getErrorStream();
            String response = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            if (status != 200) {
                throw new SecurityException("Google token exchange thất bại: " + response);
            }

            // Parse id_token từ JSON response
            String idToken = extractJson(response, "id_token");
            if (idToken == null) {
                throw new SecurityException("Không lấy được id_token từ Google.");
            }

            // Decode payload của JWT (phần giữa, không verify chữ ký — đủ cho demo)
            return decodeJwtPayload(idToken);

        } catch (IOException e) {
            throw new SecurityException("Lỗi kết nối Google: " + e.getMessage());
        }
    }

    /**
     * Decode phần payload của JWT (base64url), lấy email và name
     */
    private static Map<String, String> decodeJwtPayload(String jwt) throws SecurityException {
        String[] parts = jwt.split("\\.");
        if (parts.length < 2) {
            throw new SecurityException("JWT không hợp lệ.");
        }

        // Base64url decode (không cần padding)
        byte[] decoded = Base64.getUrlDecoder().decode(
                parts[1].replace("-", "+").replace("_", "/")
                        + "==".substring(0, (4 - parts[1].length() % 4) % 4)
        );
        String payload = new String(decoded, StandardCharsets.UTF_8);

        String email = extractJson(payload, "email");
        String name  = extractJson(payload, "name");

        if (email == null) {
            throw new SecurityException("Token không chứa thông tin email.");
        }

        Map<String, String> result = new HashMap<>();
        result.put("email", email);
        result.put("name",  name != null ? name : email);
        return result;
    }

    /**
     * Parse giá trị string từ JSON đơn giản (không cần thư viện)
     */
    private static String extractJson(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;

        idx = json.indexOf(":", idx) + 1;
        while (idx < json.length() && json.charAt(idx) == ' ') idx++;

        if (json.charAt(idx) == '"') {
            int end = json.indexOf('"', idx + 1);
            return json.substring(idx + 1, end);
        }
        return null;
    }

    /**
     * Giữ lại method cũ cho backward compatibility (demo mode)
     */
    public static Map<String, String> verifyIdToken(String idTokenString)
            throws SecurityException {
        if (idTokenString == null || idTokenString.isBlank()) {
            throw new SecurityException("ID Token không hợp lệ.");
        }
        if (!idTokenString.startsWith("demo:")) {
            throw new SecurityException("ID Token không hợp lệ.");
        }
        String[] parts = idTokenString.split(":", 3);
        if (parts.length != 3 || parts[1].isBlank()) {
            throw new SecurityException("ID Token sai định dạng.");
        }
        Map<String, String> payload = new HashMap<>();
        payload.put("email", parts[1].trim());
        payload.put("name",  parts[2].trim());
        return payload;
    }
}