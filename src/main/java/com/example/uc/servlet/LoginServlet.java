package com.example.uc.servlet;

import com.example.uc.exception.AuthException;
import com.example.uc.exception.DatabaseException;
import com.example.uc.model.User;
import com.example.uc.model.User.Role;
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
 * ═══════════════════════════════════════════════════════════════════
 */
@WebServlet(name = "LoginServlet", urlPatterns = {"/login"})
public class LoginServlet extends HttpServlet {

    private AuthService authService;

    @Override
    public void init() throws ServletException {
        authService = new AuthService(new UserRepository());
    }

    // ────────────────────────────────────────────────────────────────
    // GET /login
    // ────────────────────────────────────────────────────────────────
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        req.setCharacterEncoding("UTF-8");
        String code = req.getParameter("code");

        if (code != null) {
            handleGoogleLoginWithCode(code, req, resp);
            return;
        }

        req.getRequestDispatcher("/WEB-INF/login.jsp").forward(req, resp);
    }

    // ────────────────────────────────────────────────────────────────
    // POST /login
    // ────────────────────────────────────────────────────────────────
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        req.setCharacterEncoding("UTF-8");
        String loginType = req.getParameter("loginType");

        if ("google".equals(loginType)) {
            handleGoogleLogin(req, resp);
        } else {
            handleLocalLogin(req, resp);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  BASIC FLOW — Đăng nhập Email / Mật khẩu
    // ════════════════════════════════════════════════════════════════
    private void handleLocalLogin(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String email    = req.getParameter("email");
        String password = req.getParameter("password");

        String emailError    = ValidationUtil.validateEmail(email);
        String passwordError = ValidationUtil.validatePassword(password);

        if (emailError != null || passwordError != null) {
            req.setAttribute("emailError",    emailError);
            req.setAttribute("passwordError", passwordError);
            req.getRequestDispatcher("/WEB-INF/login.jsp").forward(req, resp);
            return;
        }

        try {
            User user = authService.loginWithEmailPassword(email, password);
            createSession(req, user);
            redirectByRole(req, user, resp);

        } catch (AuthException e) {
            req.setAttribute("error", e.getMessage());
            req.getRequestDispatcher("/WEB-INF/login.jsp").forward(req, resp);

        } catch (DatabaseException e) {
            getServletContext().log("DB Error handleLocalLogin: " + e.getMessage(), e);
            req.setAttribute("error", "Hệ thống đang gặp sự cố. Vui lòng thử lại sau.");
            req.getRequestDispatcher("/WEB-INF/login.jsp").forward(req, resp);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  UC1.1 + UC1.2 — Đăng nhập qua Google (ID Token)
    // ════════════════════════════════════════════════════════════════
    private void handleGoogleLogin(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String idTokenString = req.getParameter("idToken");

        Map<String, String> payload;
        try {
            payload = GoogleOAuthUtil.verifyIdToken(idTokenString);
        } catch (SecurityException e) {
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
    //  Helper: xử lý sau khi có Google payload
    // ════════════════════════════════════════════════════════════════
    private void processGoogleUser(Map<String, String> payload,
                                   HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String email    = payload.get("email");
        String fullName = payload.get("name");

        try {
            User user = authService.loginWithGoogle(email, fullName);
            createSession(req, user);
            redirectByRole(req, user, resp);

        } catch (AuthException e) {
            req.setAttribute("error", e.getMessage());
            req.getRequestDispatcher("/WEB-INF/login.jsp").forward(req, resp);

        } catch (DatabaseException e) {
            getServletContext().log("DB Error processGoogleUser: " + e.getMessage(), e);
            req.setAttribute("error", "Hệ thống đang gặp sự cố. Vui lòng thử lại sau.");
            req.getRequestDispatcher("/WEB-INF/login.jsp").forward(req, resp);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Helper: 1.0.8 — Khởi tạo session (chống Session Fixation)
    // ════════════════════════════════════════════════════════════════
    private void createSession(HttpServletRequest req, User user) {
        HttpSession old = req.getSession(false);
        if (old != null) old.invalidate();

        HttpSession session = req.getSession(true);
        session.setAttribute("userId",    user.getId());
        session.setAttribute("userEmail", user.getEmail());
        session.setAttribute("userRole",  user.getRole().name()); // enum → String cho session
        session.setAttribute("fullName",  user.getFullName());
        session.setMaxInactiveInterval(30 * 60);
    }

    // ════════════════════════════════════════════════════════════════
    //  Helper: 1.0.10 — Điều hướng theo quyền hạn
    // ════════════════════════════════════════════════════════════════
    private void redirectByRole(HttpServletRequest req, User user, HttpServletResponse resp)
            throws IOException {
        String contextPath = req.getContextPath();

        // So sánh enum thay vì String — type-safe, không lo typo
        if (Role.ADMIN == user.getRole()) {
            resp.sendRedirect(contextPath + "/admin/dashboard");
        } else {
            resp.sendRedirect(contextPath + "/home");
        }
    }
}