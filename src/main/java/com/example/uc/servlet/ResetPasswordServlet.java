package com.example.uc.servlet;

import com.example.uc.exception.AuthException;
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
 *  ResetPasswordServlet — Xử lý HTTP cho Bước 3: Đặt lại mật khẩu
 *  URL: /reset-password
 *
 *  GET  → 3.0.13: Hiển thị form "Đặt lại mật khẩu mới"
 *  POST → 3.0.14 → 3.0.19: Validate, băm, lưu mật khẩu mới; ghi log
 *
 *  Pre-condition: Session phải có "resetUserId" và "resetOtpId"
 *  (set bởi VerifyOtpServlet sau khi xác thực OTP thành công).
 *  Nếu thiếu → redirect về /forgot-password để chống bypass bỏ qua bước OTP.
 * ═══════════════════════════════════════════════════════════════════
 */
@WebServlet("/reset-password")
public class ResetPasswordServlet extends HttpServlet {

    private PasswordResetService passwordResetService;

    @Override
    public void init() throws ServletException {
        UserRepository userRepo = new UserRepository();
        OtpRepository  otpRepo  = new OtpRepository();
        passwordResetService = new PasswordResetService(userRepo, otpRepo);
    }

    // ── GET /reset-password ──────────────────────────────────────────
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // Guard: phải đã qua bước xác thực OTP
        if (!hasValidResetSession(req)) {
            resp.sendRedirect(req.getContextPath() + "/forgot-password");
            return;
        }

        // 3.0.13 — Hiển thị form "Đặt lại mật khẩu mới"
        req.getRequestDispatcher("/WEB-INF/reset-password.jsp").forward(req, resp);
    }

    // ── POST /reset-password ─────────────────────────────────────────
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // Guard: phải đã qua bước xác thực OTP (chống bypass trực tiếp)
        if (!hasValidResetSession(req)) {
            resp.sendRedirect(req.getContextPath() + "/forgot-password");
            return;
        }

        HttpSession session = req.getSession(false);
        int userId = (Integer) session.getAttribute("resetUserId");
        int otpId  = (Integer) session.getAttribute("resetOtpId");

        // ── 3.0.14: Lấy mật khẩu mới từ form ────────────────────────
        String newPassword = req.getParameter("newPassword");
        String confirmPass = req.getParameter("confirmPassword");

        String ipAddress = req.getRemoteAddr();
        String userAgent = req.getHeader("User-Agent");

        try {
            // ── 3.0.15 → 3.0.19: Validate, băm, lưu, reset bộ đếm, ghi log ──
            passwordResetService.resetPassword(
                    userId, otpId, newPassword, confirmPass, ipAddress, userAgent);

            // Đặt lại mật khẩu thành công → xóa toàn bộ session reset
            // (invalidate session cũ để tránh dùng lại session đã xác thực OTP)
            session.removeAttribute("resetUserId");
            session.removeAttribute("resetOtpId");
            session.removeAttribute("resetEmail");
            session.removeAttribute("otpSentAt");

            // Redirect về trang đăng nhập với thông báo thành công (PRG pattern)
            resp.sendRedirect(req.getContextPath() + "/login?resetSuccess=true");

        } catch (AuthException e) {
            // 3.0-E6: Mật khẩu không hợp lệ hoặc không khớp
            // Xóa dữ liệu đã nhập, yêu cầu nhập lại (theo UC)
            req.setAttribute("error", e.getMessage());
            req.getRequestDispatcher("/WEB-INF/reset-password.jsp").forward(req, resp);
        } catch (Exception e) {
            // 3.0-EDB: Lỗi kỹ thuật
            req.setAttribute("error", "Đã xảy ra lỗi kỹ thuật. Vui lòng thử lại sau.");
            req.getRequestDispatcher("/WEB-INF/reset-password.jsp").forward(req, resp);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  HELPER
    // ════════════════════════════════════════════════════════════════

    /**
     * Kiểm tra session có chứa đủ thông tin để thực hiện đặt lại mật khẩu không.
     * Bảo vệ chống bypass: user không thể truy cập /reset-password
     * mà không đi qua bước xác thực OTP trước.
     */
    private boolean hasValidResetSession(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        return session != null
                && session.getAttribute("resetUserId") != null
                && session.getAttribute("resetOtpId")  != null;
    }
}
