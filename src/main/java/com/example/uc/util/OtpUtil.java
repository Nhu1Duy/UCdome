package com.example.uc.util;

import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Tiện ích tạo và băm mã OTP.
 *
 * BR-02: OTP là mã 6 chữ số ngẫu nhiên an toàn (SecureRandom).
 * BR-04: OTP được băm SHA-256 trước khi lưu vào CSDL để tránh lộ plaintext.
 *
 * Lý do chọn SHA-256 thay BCrypt cho OTP:
 *  - OTP có entropy thấp (6 chữ số = 10^6 khả năng) nên dù lưu plain text
 *    trong session/cookie phía server là không an toàn. Băm SHA-256 là đủ
 *    vì OTP chỉ sống 120 giây và bị vô hiệu hóa ngay sau khi dùng.
 *  - Không dùng BCrypt vì cần so sánh nhanh, không cần slow-hash cho giá trị
 *    tồn tại rất ngắn.
 */
public class OtpUtil {

    /** Số chữ số của mã OTP */
    private static final int OTP_LENGTH = 6;

    /**
     * 3.0.8 — Tạo mã OTP ngẫu nhiên an toàn gồm 6 chữ số.
     *
     * Dùng SecureRandom để đảm bảo tính ngẫu nhiên mật mã học.
     * Kết quả là String có đúng OTP_LENGTH chữ số (có thể có số 0 đứng đầu).
     *
     * @return mã OTP dạng "012345" (luôn đủ 6 ký tự)
     */
    public static String generateOtp() {
        SecureRandom random = new SecureRandom();
        // nextInt(900000) cho giá trị 0..899999, + 100000 → 100000..999999
        // Điều này đảm bảo luôn có đúng 6 chữ số (không bao giờ < 100000)
        int otp = 100000 + random.nextInt(900000);
        return String.valueOf(otp);
    }

    /**
     * Băm mã OTP bằng SHA-256 trước khi lưu CSDL.
     * BR-04 yêu cầu không lưu OTP plaintext.
     *
     * @param plainOtp mã OTP dạng "123456"
     * @return chuỗi hex SHA-256, ví dụ "8d969eef6ecad3c..."
     */
    public static String hashOtp(String plainOtp) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(
                    plainOtp.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // Chuyển byte[] → hex string
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();

        } catch (Exception e) {
            throw new RuntimeException("Lỗi khi băm OTP", e);
        }
    }

    /**
     * So sánh OTP người dùng nhập với hash đã lưu trong CSDL.
     * Dùng MessageDigest.isEqual để tránh timing attack.
     *
     * @param plainOtp   OTP người dùng nhập (chưa băm)
     * @param storedHash Hash SHA-256 đã lưu trong DB
     * @return true nếu trùng khớp
     */
    public static boolean verifyOtp(String plainOtp, String storedHash) {
        if (plainOtp == null || storedHash == null) return false;
        String inputHash = hashOtp(plainOtp);
        // isEqual so sánh constant-time, tránh timing attack
        return MessageDigest.isEqual(
                inputHash.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                storedHash.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
