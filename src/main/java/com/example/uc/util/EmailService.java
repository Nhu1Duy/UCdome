package com.example.uc.util;

import jakarta.mail.*;
import jakarta.mail.internet.*;

import java.io.UnsupportedEncodingException;
import java.util.Properties;

/**
 * Tiện ích gửi email qua SMTP.
 *
 * 3.0.10 — Hệ thống gọi dịch vụ Email Server gửi mã OTP đến địa chỉ Email của Actor.
 *
 * Cấu hình thực tế nên đọc từ file application.properties hoặc biến môi trường.
 * Demo này hardcode SMTP Gmail — thay bằng config thật trong production.
 *
 * Dependency cần thêm vào pom.xml:
 *   <dependency>
 *     <groupId>com.sun.mail</groupId>
 *     <artifactId>jakarta.mail</artifactId>
 *     <version>2.0.1</version>
 *   </dependency>
 */
public class EmailService {

    // ── Cấu hình SMTP ───────────────────────────────────────────────
    // THAY THẾ bằng biến môi trường hoặc file config trong production!
    private static final String SMTP_HOST     = System.getenv().getOrDefault("SMTP_HOST", "smtp.gmail.com");
    private static final String SMTP_PORT     = System.getenv().getOrDefault("SMTP_PORT", "587");
    private static final String SMTP_USER     = System.getenv().getOrDefault("SMTP_USER", "enyunakling01@gmail.com");
    private static final String SMTP_PASSWORD = System.getenv().getOrDefault("SMTP_PASSWORD", "kzyr ales zvhz zozd");
    private static final String FROM_NAME     = "NLU System";

    /**
     * 3.0.10 — Gửi email chứa mã OTP đến địa chỉ email của người dùng.
     *
     * @param toEmail   Địa chỉ email người nhận
     * @param otpCode   Mã OTP plaintext (6 chữ số) — KHÔNG băm vì cần gửi cho user đọc
     * @throws MessagingException nếu gửi thất bại → caller ném 3.0-EDB
     */
    public static void sendOtpEmail(String toEmail, String otpCode) throws MessagingException, UnsupportedEncodingException {
        Properties props = new Properties();
        props.put("mail.smtp.auth",            "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host",            SMTP_HOST);
        props.put("mail.smtp.port",            SMTP_PORT);
        props.put("mail.smtp.ssl.trust",       SMTP_HOST);

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(SMTP_USER, SMTP_PASSWORD);
            }
        });

        Message message = new MimeMessage(session);
        message.setFrom(new InternetAddress(SMTP_USER, FROM_NAME));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail));
        message.setSubject("[NLU System] Mã xác thực khôi phục mật khẩu");

        // Nội dung HTML của email
        String htmlContent = buildOtpEmailContent(otpCode);
        message.setContent(htmlContent, "text/html; charset=UTF-8");

        Transport.send(message);
    }

    /**
     * Tạo nội dung HTML cho email OTP.
     * BR-02: Thông báo rõ OTP chỉ có hiệu lực 2 phút.
     */
    private static String buildOtpEmailContent(String otpCode) {
        return "<!DOCTYPE html><html lang='vi'><body style='font-family:Segoe UI,sans-serif;background:#f4f4f4;padding:20px'>"
             + "<div style='max-width:480px;margin:auto;background:#fff;border-radius:12px;padding:32px;box-shadow:0 4px 16px rgba(0,0,0,.1)'>"
             + "<h2 style='color:#1a7a4a;text-align:center'>🌿 NLU System</h2>"
             + "<p style='color:#374151'>Bạn đã yêu cầu khôi phục mật khẩu. Mã xác thực của bạn là:</p>"
             + "<div style='text-align:center;margin:24px 0'>"
             + "<span style='font-size:2.5rem;font-weight:700;letter-spacing:8px;color:#1a7a4a;background:#f0fdf4;padding:12px 24px;border-radius:8px;border:2px dashed #1a7a4a'>"
             + otpCode
             + "</span></div>"
             + "<p style='color:#dc2626;font-size:.9rem'>⏱ Mã này chỉ có hiệu lực trong <strong>2 phút</strong>.</p>"
             + "<p style='color:#6b7280;font-size:.85rem'>Nếu bạn không yêu cầu đặt lại mật khẩu, hãy bỏ qua email này.</p>"
             + "</div></body></html>";
    }
}
