package com.example.uc.util;

import com.example.uc.dto.GoogleTokenPayloadDTO;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class GoogleOAuthUtil {

    private static final String CLIENT_ID     = "...";
    private static final String CLIENT_SECRET = "...";
    private static final String REDIRECT_URI  = "http://localhost:8080/login_app/login";

    public static GoogleTokenPayloadDTO exchangeCodeAndVerify(String code)
            throws SecurityException {

        // 1.1-E1a — Kiểm tra đầu vào: code phải tồn tại và không rỗng
        if (code == null || code.isBlank()) {
            throw new SecurityException("Authorization code không hợp lệ.");
        }

        try {
            // 1.1.4b — Mở kết nối HTTP POST đến Google token endpoint
            URL url = new URL("https://oauth2.googleapis.com/token");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            // 1.1.4c — Xây dựng body request (form-encoded) và gửi tới Google
            String body = "code="          + URLEncoder.encode(code,          StandardCharsets.UTF_8)
                    + "&client_id="        + URLEncoder.encode(CLIENT_ID,     StandardCharsets.UTF_8)
                    + "&client_secret="    + URLEncoder.encode(CLIENT_SECRET,  StandardCharsets.UTF_8)
                    + "&redirect_uri="     + URLEncoder.encode(REDIRECT_URI,   StandardCharsets.UTF_8)
                    + "&grant_type=authorization_code";
            conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));

            // 1.1.4d — Phân nhánh đọc response theo HTTP status code
            int status = conn.getResponseCode();
            InputStream is = (status == 200) ? conn.getInputStream() : conn.getErrorStream();
            String response = new String(is.readAllBytes(), StandardCharsets.UTF_8);

            // 1.1-E1c — Google từ chối exchange (code hết hạn, đã dùng, redirect_uri sai...)
            if (status != 200) {
                throw new SecurityException("Google token exchange thất bại: " + response);
            }

            // 1.1.4e — Trích xuất "id_token" từ JSON response của Google
            String idToken = extractJsonString(response, "id_token");

            // 1.1-E1d — Không có id_token trong response (cấu hình OAuth scope sai?)
            if (idToken == null) {
                throw new SecurityException("Không lấy được id_token từ Google.");
            }

            // 1.1.5 — Decode phần payload của JWT → { email, name }
            return decodeJwtPayload(idToken);

        } catch (IOException e) {
            // 1.1-E1b — Lỗi kết nối mạng (DNS, timeout, SSL, network unreachable...)
            throw new SecurityException("Lỗi kết nối tới Google token endpoint: " + e.getMessage());
        }
    }

    public static GoogleTokenPayloadDTO verifyIdToken(String idTokenString)
            throws SecurityException {

        if (idTokenString == null || idTokenString.isBlank()) {
            throw new SecurityException("ID Token không hợp lệ.");
        }

        // 1.1-E1b — token dạng "demo:..." trong môi trường dev
        if (!idTokenString.startsWith("demo:")) {
            throw new SecurityException("ID Token không hợp lệ.");
        }
        String[] parts = idTokenString.split(":", 3);

        // 1.1-E1c — Đúng 3 phần VÀ email (parts[1]) không được rỗng
        if (parts.length != 3 || parts[1].isBlank()) {
            throw new SecurityException("ID Token sai định dạng.");
        }

        String email = parts[1].trim();
        String name  = parts[2].trim();

        // Trả GoogleTokenPayloadDTO về LoginServlet.handleGoogleLogin()
        return new GoogleTokenPayloadDTO(email, name.isBlank() ? email : name);
    }

    private static GoogleTokenPayloadDTO decodeJwtPayload(String jwt)
            throws SecurityException {

        String[] parts = jwt.split("\\.");

        // Cần ít nhất header + payload; signature (parts[2]) không dùng ở đây
        if (parts.length < 2) {
            throw new SecurityException("JWT không hợp lệ: thiếu phần payload.");
        }

        // Chuẩn hóa base64url → base64 tiêu chuẩn:
        //   base64url dùng "-" và "_" thay cho "+" và "/" của base64 chuẩn
        //   và bỏ ký tự padding "=" ở cuối → cần khôi phục lại
        String base64 = parts[1].replace("-", "+").replace("_", "/");
        int pad = (4 - base64.length() % 4) % 4;   // số ký tự "=" cần thêm (0, 1, hoặc 2)
        base64 += "==".substring(0, pad);

        byte[] decoded = Base64.getUrlDecoder().decode(base64);
        String payload = new String(decoded, StandardCharsets.UTF_8);

        // Trích xuất email và name từ JSON payload của JWT
        String email = extractJsonString(payload, "email");
        String name  = extractJsonString(payload, "name");

        // email là bắt buộc — name có thể null (dùng email làm fallback)
        if (email == null) {
            throw new SecurityException("JWT payload không chứa trường email.");
        }

        return new GoogleTokenPayloadDTO(email, name != null ? name : email);
    }

    private static String extractJsonString(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx < 0) return null;   // key không tồn tại trong JSON

        idx = json.indexOf(":", idx) + 1;

        // Bỏ qua khoảng trắng giữa ":" và giá trị
        while (idx < json.length() && json.charAt(idx) == ' ') idx++;

        // Chỉ xử lý giá trị kiểu string (bắt đầu bằng '"')
        // Các kiểu khác (number, boolean, null, object) → trả null
        if (idx >= json.length() || json.charAt(idx) != '"') return null;

        int end = json.indexOf('"', idx + 1);
        if (end < 0) return null;   // JSON malformed: không có dấu '"' đóng

        return json.substring(idx + 1, end);
    }
}