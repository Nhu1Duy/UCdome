package com.example.uc.servlet;

import com.example.uc.exception.AuthException;
import com.example.uc.exception.DatabaseException;
import com.example.uc.repository.OtpRepository;
import com.example.uc.repository.UserRepository;
import com.example.uc.service.PasswordResetService;
import com.example.uc.util.ValidationUtil;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

/**
 * ═══════════════════════════════════════════════════════════════════
 *  ForgotPasswordServlet — Xử lý HTTP cho Bước 1: Nhập Email
 *  URL: /forgot-password
 *
 *  GET  → 3.0.1: Hiển thị form nhập email
 *  POST → 3.0.2 → 3.0.10: Validate email, gửi OTP, chuyển hướng sang /verify-otp
 * ═══════════════════════════════════════════════════════════════════
 */
@WebServlet("/forgot-password")
public class ForgotPasswordServlet extends HttpServlet {

    private PasswordResetService passwordResetService;

    @Override
    public void init() throws ServletException {
        UserRepository userRepo = new UserRepository();
        OtpRepository  otpRepo  = new OtpRepository();
        passwordResetService = new PasswordResetService(userRepo, otpRepo);
    }

    // ── GET /forgot-password ─────────────────────────────────────────
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException  {
        // 3.0.1 — Hệ thống hiển thị giao diện Form nhập Email
        req.getRequestDispatcher("/WEB-INF/forgot-password.jsp").forward(req, resp);
    }

    // ── POST /forgot-password ────────────────────────────────────────
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException, UnsupportedEncodingException{

        // ── 3.0.2: Lấy email từ form ─────────────────────────────────
        String email = req.getParameter("email");
        if (email != null) email = email.trim().toLowerCase();

        // ── 3.0.3: Validate định dạng email đầu vào ──────────────────
        String emailError = ValidationUtil.validateEmail(email);
        if (emailError != null) {
            // 3.0-E1: Validate thất bại → hiển thị lỗi ngay tại form
            req.setAttribute("emailError", emailError);
            req.setAttribute("emailValue", email);
            req.getRequestDispatcher("/WEB-INF/forgot-password.jsp").forward(req, resp);
            return;
        }

        String ipAddress = req.getRemoteAddr();
        String userAgent = req.getHeader("User-Agent");

        try {
            // ── 3.0.4 → 3.0.10: Kiểm tra email, OTP limit, gửi OTP ──
            passwordResetService.requestPasswordReset(email, ipAddress, userAgent);

            // Lưu email vào session để bước xác thực OTP dùng
            HttpSession session = req.getSession(true);
            session.setAttribute("resetEmail", email);
            // Lưu timestamp gửi OTP để hiển thị countdown cooldown ở trang verify
            session.setAttribute("otpSentAt", System.currentTimeMillis());

            // 3.0.10: Điều hướng sang giao diện "Xác thực OTP"
            // Dùng redirect (PRG pattern) để tránh form resubmit khi F5
            resp.sendRedirect(req.getContextPath() + "/verify-otp");

        } catch (AuthException e) {
            switch (e.getErrorCode()) {
                case ACCOUNT_LOCKED:
                    // 3.0-E3 / 3.0-E4: Tài khoản bị khóa
                    req.setAttribute("error", e.getMessage());
                    req.setAttribute("emailValue", email);
                    req.getRequestDispatcher("/WEB-INF/forgot-password.jsp").forward(req, resp);
                    break;
                case INVALID_CREDENTIALS:
                    req.setAttribute("error", e.getMessage());
                    req.setAttribute("emailValue", email);
                    req.getRequestDispatcher("/WEB-INF/forgot-password.jsp")
                            .forward(req, resp);
                    break;
                default:
                    req.setAttribute("error", "Đã xảy ra lỗi. Vui lòng thử lại.");
                    req.getRequestDispatcher("/WEB-INF/forgot-password.jsp").forward(req, resp);
            }
        } catch (RuntimeException e) {
            // 3.0-EDB: Lỗi Email Server hoặc CSDL
            req.setAttribute("error", e.getMessage());
            req.setAttribute("emailValue", email);
            req.getRequestDispatcher("/WEB-INF/forgot-password.jsp").forward(req, resp);
        }

        // ⚠️ Lưu ý bảo mật — 3.0-E2:
        // Nếu email không tồn tại, service trả về bình thường (silent fail).
        // Servlet vẫn redirect sang /verify-otp và hiển thị thông báo trung tính:
        // "Mã xác thực đã được gửi nếu Email tồn tại trên hệ thống."
        // → Tránh lộ thông tin tài khoản nào đăng ký trong hệ thống.
    }
}
