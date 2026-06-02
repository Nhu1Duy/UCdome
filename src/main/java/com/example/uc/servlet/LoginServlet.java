package com.example.uc.servlet;

import com.example.uc.exception.AuthException;
import com.example.uc.exception.DatabaseException;
import com.example.uc.model.User;
import com.example.uc.repository.UserRepository;
import com.example.uc.service.AuthService;
import com.example.uc.util.GoogleOAuthUtil;
import com.example.uc.util.ValidationUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.Map;

/**
 * ═══════════════════════════════════════════════════════════════════
 *  LoginServlet — UC-01: Đăng nhập hệ thống
 *
 *  Trách nhiệm: Nhận HTTP request, validate đầu vào, ủy quyền
 *  toàn bộ logic nghiệp vụ cho AuthService, xử lý response.
 *
 *  KHÔNG chứa logic nghiệp vụ (check password, check LOCKED…)
 *  → Tất cả nằm trong AuthService.
 * ═══════════════════════════════════════════════════════════════════
 */
@WebServlet(name = "LoginServlet", urlPatterns = {"/login"})
public class LoginServlet extends HttpServlet {

    // ─── Dependency: AuthService chứa toàn bộ nghiệp vụ ────────────
    private AuthService authService;

    @Override
    public void init() throws ServletException {
        // Khởi tạo AuthService với UserRepository (có thể thay bằng DI framework)
        authService = new AuthService(new UserRepository());
    }

    // ────────────────────────────────────────────────────────────────
    // GET /login → hiển thị form, hoặc xử lý Google OAuth code
    // ────────────────────────────────────────────────────────────────
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        req.setCharacterEncoding("UTF-8");
        String code = req.getParameter("code");

        if (code != null) {
            // Google redirect về với authorization code
            handleGoogleLoginWithCode(code, req, resp);
            return;
        }

        // 1.0.1 — Hiển thị form đăng nhập
        req.getRequestDispatcher("/WEB-INF/login.jsp").forward(req, resp);
    }

    // ────────────────────────────────────────────────────────────────
    // POST /login → điều phối sang luồng local hoặc google
    // ────────────────────────────────────────────────────────────────
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        req.setCharacterEncoding("UTF-8");
        String loginType = req.getParameter("loginType"); // "local" hoặc "google"

        if ("google".equals(loginType)) {
            handleGoogleLogin(req, resp);   // → UC1.1 / UC1.2
        } else {
            handleLocalLogin(req, resp);    // → Basic Flow
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  BASIC FLOW — Đăng nhập Email / Mật khẩu
    // ════════════════════════════════════════════════════════════════
    private void handleLocalLogin(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String email    = req.getParameter("email");
        String password = req.getParameter("password");

        // ── 1.0.3: Validate đầu vào (Servlet chịu trách nhiệm) ───────
        String emailError    = ValidationUtil.validateEmail(email);
        String passwordError = ValidationUtil.validatePassword(password);

        if (emailError != null || passwordError != null) {
            // 1.0-E1 — Validate thất bại
            req.setAttribute("emailError",    emailError);
            req.setAttribute("passwordError", passwordError);
            req.getRequestDispatcher("/WEB-INF/login.jsp").forward(req, resp);
            return;
        }

        try {
            // ── Ủy quyền toàn bộ nghiệp vụ cho AuthService ───────────
            // AuthService xử lý: 1.0.4 → 1.0-E2 → 1.0.5 → 1.0-E3 → 1.0.6 → 1.0.9
            User user = authService.loginWithEmailPassword(email, password);

            // 1.0.8 — Khởi tạo session (Servlet chịu trách nhiệm HTTP)
            createSession(req, user);

            // 1.0.10 — Điều hướng theo quyền hạn
            redirectByRole(req, user, resp);

        } catch (AuthException e) {
            // Lỗi nghiệp vụ: sai thông tin (1.0-E2) hoặc tài khoản khóa (1.0-E3)
            req.setAttribute("error", e.getMessage());
            req.getRequestDispatcher("/WEB-INF/login.jsp").forward(req, resp);

        } catch (DatabaseException e) {
            // 1.0-EDB — Lỗi kết nối CSDL
            getServletContext().log("DB Error handleLocalLogin: " + e.getMessage(), e);
            req.setAttribute("error", "Hệ thống đang gặp sự cố. Vui lòng thử lại sau.");
            req.getRequestDispatcher("/WEB-INF/login.jsp").forward(req, resp);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  UC1.1 + UC1.2 — Đăng nhập / Tự động đăng ký qua Google (ID Token)
    // ════════════════════════════════════════════════════════════════
    private void handleGoogleLogin(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // 1.1.4 — Frontend gửi ID Token sau khi Google xác thực
        String idTokenString = req.getParameter("idToken");

        // ── 1.1.7: Xác thực token — Servlet chịu trách nhiệm OAuth ──
        Map<String, String> payload;
        try {
            payload = GoogleOAuthUtil.verifyIdToken(idTokenString);
        } catch (SecurityException e) {
            // 1.1-E1 — Token không hợp lệ hoặc hết hạn
            getServletContext().log("OAuth Error: " + e.getMessage());
            req.setAttribute("error", "Xác thực qua tài khoản Google thất bại. Vui lòng thử lại.");
            req.getRequestDispatcher("/WEB-INF/login.jsp").forward(req, resp);
            return;
        }

        processGoogleUser(payload, req, resp);
    }

    // ════════════════════════════════════════════════════════════════
    //  Google OAuth Authorization Code Flow (GET ?code=...)
    // ════════════════════════════════════════════════════════════════
    private void handleGoogleLoginWithCode(String code,
                                           HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        Map<String, String> payload;
        try {
            payload = GoogleOAuthUtil.exchangeCodeAndVerify(code);
        } catch (SecurityException e) {
            getServletContext().log("OAuth Error: " + e.getMessage());
            req.setAttribute("error", "Xác thực Google thất bại. Vui lòng thử lại.");
            req.getRequestDispatcher("/WEB-INF/login.jsp").forward(req, resp);
            return;
        }

        processGoogleUser(payload, req, resp);
    }

    // ════════════════════════════════════════════════════════════════
    //  Helper dùng chung: xử lý sau khi đã có Google payload
    //  → Ủy quyền cho AuthService (UC1.1 / UC1.2)
    // ════════════════════════════════════════════════════════════════
    private void processGoogleUser(Map<String, String> payload,
                                   HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String email    = payload.get("email");
        String fullName = payload.get("name");

        try {
            // AuthService xử lý: 1.1.8 → UC1.2 hoặc UC1.1 → 1.1.9 → 1.0.9
            User user = authService.loginWithGoogle(email, fullName);

            // 1.0.8 / 1.2.4 — Khởi tạo session
            createSession(req, user);

            // 1.0.10 / 1.2.6 — Điều hướng theo quyền hạn
            redirectByRole(req, user, resp);

        } catch (AuthException e) {
            // 1.1-E2 — Tài khoản bị khóa
            req.setAttribute("error", e.getMessage());
            req.getRequestDispatcher("/WEB-INF/login.jsp").forward(req, resp);

        } catch (DatabaseException e) {
            // 1.1-EDB — Lỗi kết nối CSDL
            getServletContext().log("DB Error processGoogleUser: " + e.getMessage(), e);
            req.setAttribute("error", "Hệ thống đang gặp sự cố. Vui lòng thử lại sau.");
            req.getRequestDispatcher("/WEB-INF/login.jsp").forward(req, resp);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Helper: 1.0.8 — Khởi tạo session an toàn (chống Session Fixation)
    // ════════════════════════════════════════════════════════════════
    private void createSession(HttpServletRequest req, User user) {
        // Invalidate phiên cũ trước khi tạo mới
        HttpSession old = req.getSession(false);
        if (old != null) old.invalidate();

        HttpSession session = req.getSession(true);
        session.setAttribute("userId",    user.getId());
        session.setAttribute("userEmail", user.getEmail());
        session.setAttribute("userRole",  user.getRole());     // 1.0.7
        session.setAttribute("fullName",  user.getFullName());
        session.setMaxInactiveInterval(30 * 60);               // 30 phút
    }

    // ════════════════════════════════════════════════════════════════
    //  Helper: 1.0.10 — Điều hướng theo quyền hạn
    // ════════════════════════════════════════════════════════════════
    private void redirectByRole(HttpServletRequest req, User user, HttpServletResponse resp)
            throws IOException {
        String contextPath = req.getContextPath();

        if ("ADMIN".equals(user.getRole())) {
            resp.sendRedirect(contextPath + "/admin/dashboard");
        } else {
            resp.sendRedirect(contextPath + "/home");
        }
    }
}