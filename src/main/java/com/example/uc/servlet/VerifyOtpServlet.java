package com.example.uc.servlet;

import com.example.uc.exception.AuthException;
import com.example.uc.exception.DatabaseException;
import com.example.uc.model.OtpToken;
import com.example.uc.model.User;
import com.example.uc.repository.OtpRepository;
import com.example.uc.repository.UserRepository;
import com.example.uc.service.PasswordResetService;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * ═══════════════════════════════════════════════════════════════════
 *  VerifyOtpServlet — Xử lý HTTP cho Bước 2: Xác thực OTP
 *  URL: /verify-otp
 *
 *  GET          → 3.0.10: Hiển thị form nhập mã OTP
 *  POST action=verify → 3.0.11 → 3.0.12: Xác thực OTP nhập vào
 *  POST action=resend → UC03.1: Gửi lại OTP mới
 *
 *  Pre-condition: Session phải có "resetEmail" (set bởi ForgotPasswordServlet).
 *  Nếu không có → redirect về /forgot-password để chống bypass.
 * ═══════════════════════════════════════════════════════════════════
 */
@WebServlet("/verify-otp")
public class VerifyOtpServlet extends HttpServlet {

    private PasswordResetService passwordResetService;
    private UserRepository       userRepository;

    @Override
    public void init() throws ServletException {
        userRepository = new UserRepository();
        OtpRepository otpRepo = new OtpRepository();
        passwordResetService = new PasswordResetService(userRepository, otpRepo);
    }

    // ── GET /verify-otp ──────────────────────────────────────────────
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // Guard: phải có session resetEmail mới cho vào trang này
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("resetEmail") == null) {
            // Chưa qua bước nhập email → redirect về bước 1
            resp.sendRedirect(req.getContextPath() + "/forgot-password");
            return;
        }

        // 3.0.10 — Hiển thị form xác thực OTP
        // Truyền email (ẩn một phần) để hiển thị "Đã gửi đến u***@gmail.com"
        String email = (String) session.getAttribute("resetEmail");
        req.setAttribute("maskedEmail", maskEmail(email));

        // Truyền thời điểm gửi OTP để JS hiển thị đếm ngược cooldown
        Long sentAt = (Long) session.getAttribute("otpSentAt");
        req.setAttribute("otpSentAt", sentAt != null ? sentAt : 0L);

        req.getRequestDispatcher("/WEB-INF/verify-otp.jsp").forward(req, resp);
    }

    // ── POST /verify-otp ─────────────────────────────────────────────
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // Guard: phải có session hợp lệ
        HttpSession session = req.getSession(false);
        if (session == null || session.getAttribute("resetEmail") == null) {
            resp.sendRedirect(req.getContextPath() + "/forgot-password");
            return;
        }

        String action = req.getParameter("action");
        String email  = (String) session.getAttribute("resetEmail");

        if ("resend".equals(action)) {
            // ── UC03.1: Xử lý "Gửi lại mã OTP" ──────────────────────
            handleResendOtp(req, resp, session, email);
        } else {
            // ── 3.0.11 → 3.0.12: Xử lý xác thực OTP ─────────────────
            handleVerifyOtp(req, resp, session, email);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  HANDLER: Xác thực OTP
    // ════════════════════════════════════════════════════════════════

    private void handleVerifyOtp(HttpServletRequest req, HttpServletResponse resp,
                                 HttpSession session, String email)
            throws ServletException, IOException {

        // ── 3.0.11: Lấy mã OTP từ form ──────────────────────────────
        String plainOtp = req.getParameter("otp");
        if (plainOtp != null) plainOtp = plainOtp.trim();

        // Tìm user để lấy userId
        User user = userRepository.findByEmail(email);
        if (user == null) {
            // Edge case: user bị xóa sau khi session được tạo
            session.invalidate();
            resp.sendRedirect(req.getContextPath() + "/forgot-password");
            return;
        }

        try {
            // ── 3.0.12: Kiểm tra tính hợp lệ và thời gian hiệu lực OTP ──
            OtpToken validToken = passwordResetService.verifyOtp(user.getId(), plainOtp);

            // OTP hợp lệ → lưu userId và otpId vào session cho bước đặt lại mật khẩu
            session.setAttribute("resetUserId", user.getId());
            session.setAttribute("resetOtpId", validToken.getId());
            // Xóa resetEmail khỏi session (không cần nữa, tránh dùng lại)
            // Giữ lại để hiển thị ở trang reset nếu cần

            // 3.0.13 — Điều hướng sang form "Đặt lại mật khẩu mới"
            resp.sendRedirect(req.getContextPath() + "/reset-password");

        } catch (AuthException e) {
            // 3.0-E5: OTP sai hoặc hết hạn
            req.setAttribute("error", e.getMessage());
            req.setAttribute("maskedEmail", maskEmail(email));
            Long sentAt = (Long) session.getAttribute("otpSentAt");
            req.setAttribute("otpSentAt", sentAt != null ? sentAt : 0L);
            req.getRequestDispatcher("/WEB-INF/verify-otp.jsp").forward(req, resp);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  HANDLER: Gửi lại OTP (UC03.1)
    // ════════════════════════════════════════════════════════════════

    private void handleResendOtp(HttpServletRequest req, HttpServletResponse resp,
                                 HttpSession session, String email)
            throws ServletException, IOException {

        User user = userRepository.findByEmail(email);
        if (user == null) {
            session.invalidate();
            resp.sendRedirect(req.getContextPath() + "/forgot-password");
            return;
        }

        String ipAddress = req.getRemoteAddr();
        String userAgent = req.getHeader("User-Agent");

        try {
            // UC03.1: Gửi lại OTP, kiểm tra cooldown
            long waitSeconds = passwordResetService.resendOtp(
                    user.getId(), email, ipAddress, userAgent);

            if (waitSeconds > 0) {
                // ── 3.1.2: Chưa đủ thời gian chờ → thông báo cooldown ──
                req.setAttribute("cooldownSeconds", waitSeconds);
                req.setAttribute("maskedEmail", maskEmail(email));
                Long sentAt = (Long) session.getAttribute("otpSentAt");
                req.setAttribute("otpSentAt", sentAt != null ? sentAt : 0L);
                req.getRequestDispatcher("/WEB-INF/verify-otp.jsp").forward(req, resp);
            } else {
                // Gửi thành công → cập nhật thời điểm gửi trong session
                session.setAttribute("otpSentAt", System.currentTimeMillis());
                req.setAttribute("success", "Đã gửi lại mã xác thực. Vui lòng kiểm tra email.");
                req.setAttribute("maskedEmail", maskEmail(email));
                req.setAttribute("otpSentAt", session.getAttribute("otpSentAt"));
                req.getRequestDispatcher("/WEB-INF/verify-otp.jsp").forward(req, resp);
            }

        } catch (AuthException e) {
            // 3.0-E3 / 3.0-E4: Tài khoản bị khóa
            req.setAttribute("error", e.getMessage());
            req.setAttribute("maskedEmail", maskEmail(email));
            req.getRequestDispatcher("/WEB-INF/verify-otp.jsp").forward(req, resp);
        } catch (RuntimeException e) {
            // 3.1-EDB: Lỗi kỹ thuật khi gửi lại
            req.setAttribute("error", "Không thể gửi lại mã. Vui lòng thử lại sau.");
            req.setAttribute("maskedEmail", maskEmail(email));
            req.getRequestDispatcher("/WEB-INF/verify-otp.jsp").forward(req, resp);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  HELPER: Che giấu một phần địa chỉ email để hiển thị
    // ════════════════════════════════════════════════════════════════

    /**
     * Ví dụ: "user@gmail.com" → "us**@gmail.com"
     * Giữ 2 ký tự đầu, che phần còn lại trước @.
     */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        int atIdx = email.indexOf('@');
        String local  = email.substring(0, atIdx);
        String domain = email.substring(atIdx);
        if (local.length() <= 2) return email; // quá ngắn → không che
        return local.substring(0, 2) + "**" + domain;
    }
}
