package com.example.uc.servlet;

import com.example.uc.dto.GoogleLoginRequestDTO;
import com.example.uc.dto.GoogleTokenPayloadDTO;
import com.example.uc.dto.LoginRequestDTO;
import com.example.uc.dto.LoginResponseDTO;
import com.example.uc.exception.AuthException;
import com.example.uc.exception.DatabaseException;
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

/**
 *  LoginServlet — UC-01: Đăng nhập hệ thống (Email/Mật khẩu + Google)
 *  Phụ thuộc   : AuthService    — xử lý nghiệp vụ đăng nhập
 *                GoogleOAuthUtil — xác thực token/code Google
 *                ValidationUtil  — validate email/password đầu vào
 */
@WebServlet(name = "LoginServlet", urlPatterns = {"/login"})
public class LoginServlet extends HttpServlet {

    private AuthService authService;

    @Override
    public void init() throws ServletException {
        authService = new AuthService(new UserRepository());
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        req.setCharacterEncoding("UTF-8");
        String code = req.getParameter("code");

        // 1.1.4 (Alt) — Có authorization code từ Google → xử lý Code Flow
        if (code != null) {
            handleGoogleLoginWithCode(code, req, resp);
            return;
        }

        // 1.0.1 (Basic) — Không có code → hiển thị form đăng nhập
        req.getRequestDispatcher("/WEB-INF/login.jsp").forward(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        req.setCharacterEncoding("UTF-8");
        String loginType = req.getParameter("loginType");

        // 1.1.2 (Alt) — Nút Google được nhấn → ID Token flow
        if ("google".equals(loginType)) {
            handleGoogleLogin(req, resp);
        } else {
            // 1.0.2 (Basic) — Form email/password được submit
            handleLocalLogin(req, resp);
        }
    }

    private void handleLocalLogin(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // 1.0.3 — Đọc dữ liệu người dùng nhập từ form
        String email    = req.getParameter("email");
        String password = req.getParameter("password");

        // 1.0.4 — Validate định dạng email và password
        String emailError    = ValidationUtil.validateEmail(email);
        String passwordError = ValidationUtil.validatePassword(password);

        // 1.0-E1 — Có lỗi validate → trả về form kèm lỗi từng trường
        if (emailError != null || passwordError != null) {
            req.setAttribute("emailError",    emailError);
            req.setAttribute("passwordError", passwordError);
            req.getRequestDispatcher("/WEB-INF/login.jsp").forward(req, resp);
            return;
        }

        // 1.0.5 — Đóng gói DTO, truyền ip và userAgent để ghi log
        LoginRequestDTO loginDto = new LoginRequestDTO(
                email, password, getClientIp(req), getClientUserAgent(req));

        try {
            // 1.0.5→1.0.10 — Ủy quyền toàn bộ nghiệp vụ xác thực cho AuthService
            LoginResponseDTO response = authService.loginWithEmailPassword(loginDto);

            // 1.0.11 — Đăng nhập thành công → tạo session mới (hủy session cũ trước)
            createSession(req, response);

            // 1.0.12 — Điều hướng đến /admin/dashboard hoặc /home theo role
            redirectByRole(req, response, resp);

        } catch (AuthException e) {
            // 1.0-E2 — Sai credentials hoặc tài khoản bị khóa → hiển thị lỗi
            req.setAttribute("error", e.getMessage());
            req.getRequestDispatcher("/WEB-INF/login.jsp").forward(req, resp);

        } catch (DatabaseException e) {
            // 1.0-E3 — Lỗi tầng CSDL → log server, hiển thị thông báo chung
            getServletContext().log("DB Error handleLocalLogin: " + e.getMessage(), e);
            req.setAttribute("error", "Hệ thống đang gặp sự cố. Vui lòng thử lại sau.");
            req.getRequestDispatcher("/WEB-INF/login.jsp").forward(req, resp);
        }
    }

    private void handleGoogleLogin(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // 1.1.3 — Đọc idToken từ form (được JS SDK điền vào hidden input)
        String idTokenString = req.getParameter("idToken");

        GoogleTokenPayloadDTO payload;
        try {
            // 1.1.5 — Xác thực và decode ID Token → lấy email + name
            payload = GoogleOAuthUtil.verifyIdToken(idTokenString);
        } catch (SecurityException e) {
            // 1.1-E1 — Token không hợp lệ → log và báo lỗi người dùng
            getServletContext().log("OAuth Error verifyIdToken: " + e.getMessage());
            req.setAttribute("error", "Xác thực qua tài khoản Google thất bại. Vui lòng thử lại.");
            req.getRequestDispatcher("/WEB-INF/login.jsp").forward(req, resp);
            return;
        }

        // 1.1.6 — Có payload hợp lệ → xử lý nghiệp vụ đăng nhập/đăng ký Google
        processGoogleUser(payload, req, resp);
    }

    private void handleGoogleLoginWithCode(String code,
                                           HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        GoogleTokenPayloadDTO payload;
        try {
            // 1.1.4b — Đổi authorization code → ID Token → decode JWT payload
            payload = GoogleOAuthUtil.exchangeCodeAndVerify(code);
        } catch (SecurityException e) {
            // 1.1-E1 — Exchange thất bại hoặc JWT lỗi → log và báo lỗi người dùng
            getServletContext().log("OAuth Error exchangeCodeAndVerify: " + e.getMessage());
            req.setAttribute("error", "Xác thực Google thất bại. Vui lòng thử lại.");
            req.getRequestDispatcher("/WEB-INF/login.jsp").forward(req, resp);
            return;
        }

        // 1.1.4c — Có payload hợp lệ → xử lý nghiệp vụ đăng nhập/đăng ký Google
        processGoogleUser(payload, req, resp);
    }

    private void processGoogleUser(GoogleTokenPayloadDTO payload,
                                   HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // 1.1.7 — Đóng gói DTO từ Google payload + ip, userAgent để ghi log
        GoogleLoginRequestDTO googleDto = new GoogleLoginRequestDTO(
                payload.getEmail(), payload.getName(),
                getClientIp(req), getClientUserAgent(req));

        try {
            // 1.1.8→1.1.13 — Ủy quyền nghiệp vụ: đăng nhập (UC1.1) hoặc đăng ký (UC1.2)
            LoginResponseDTO response = authService.loginWithGoogle(googleDto);

            // 1.1.14 — Thành công → tạo session mới (hủy session cũ trước)
            createSession(req, response);

            // 1.1.15 — Điều hướng đến /admin/dashboard hoặc /home theo role
            redirectByRole(req, response, resp);

        } catch (AuthException e) {
            // 1.1-E2 — Provider không khớp (LOCAL vs GOOGLE) hoặc tài khoản bị khóa
            req.setAttribute("error", e.getMessage());
            req.getRequestDispatcher("/WEB-INF/login.jsp").forward(req, resp);

        } catch (DatabaseException e) {
            // 1.1-E3 — Lỗi tầng CSDL → log server, hiển thị thông báo chung
            getServletContext().log("DB Error processGoogleUser: " + e.getMessage(), e);
            req.setAttribute("error", "Hệ thống đang gặp sự cố. Vui lòng thử lại sau.");
            req.getRequestDispatcher("/WEB-INF/login.jsp").forward(req, resp);
        }
    }

    //  1.0.11 / 1.1.14 — Tạo HTTP Session sau đăng nhập thành công
    private void createSession(HttpServletRequest req, LoginResponseDTO response) {
        // Hủy session cũ trước khi tạo mới → chống Session Fixation
        HttpSession old = req.getSession(false);
        if (old != null) old.invalidate();

        HttpSession session = req.getSession(true);
        session.setAttribute("userId",    response.getId());
        session.setAttribute("userEmail", response.getEmail());
        session.setAttribute("userRole",  response.getRole());
        session.setAttribute("fullName",  response.getFullName());
        session.setAttribute("avatarUrl", response.getAvatarUrl());
        session.setMaxInactiveInterval(30 * 60);
    }

    //  1.0.12 / 1.1.15 — Điều hướng sau đăng nhập theo role
    private void redirectByRole(HttpServletRequest req, LoginResponseDTO response,
                                HttpServletResponse resp) throws IOException {
        String contextPath = req.getContextPath();
        if (response.isAdmin()) {
            resp.sendRedirect(contextPath + "/admin/dashboard");
        } else {
            resp.sendRedirect(contextPath + "/home");
        }
    }

    //  Helper — Lấy IP thực của client
    //  Dùng tại: 1.0.5 (handleLocalLogin), 1.1.7 (processGoogleUser)
    private String getClientIp(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) return ip.split(",")[0].trim();
        ip = req.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank()) return ip.trim();
        return req.getRemoteAddr();
    }

    //  Helper — Lấy và cắt ngắn User-Agent header
    //  Dùng tại: 1.0.5 (handleLocalLogin), 1.1.7 (processGoogleUser)
    private String getClientUserAgent(HttpServletRequest req) {
        String ua = req.getHeader("User-Agent");
        if (ua != null && ua.length() > 512) return ua.substring(0, 512);
        return ua;
    }
}