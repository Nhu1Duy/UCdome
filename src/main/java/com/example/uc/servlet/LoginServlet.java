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
 * LoginServlet — UC-01: Đăng nhập hệ thống
 *
 * Các flow được hỗ trợ:
 *   BASIC FLOW  (UC1.0) — Đăng nhập bằng Email / Mật khẩu (doPost)
 *   ALT FLOW    (UC1.1) — Đăng nhập bằng Google qua Authorization Code Flow (doGet)
 *
 * Lưu ý: Flow ID Token (doPost + idToken) đã bị loại bỏ.
 *   Nút "Đăng nhập bằng Google" trong login.jsp redirect trực tiếp tới
 *   Google với response_type=code; Google callback về GET /login?code=...
 *   nên toàn bộ xác thực Google đi qua doGet → handleGoogleCodeFlow().
 *
 * Phụ thuộc:
 *   AuthService     — xử lý nghiệp vụ đăng nhập / tự động đăng ký
 *   GoogleOAuthUtil — exchange authorization code lấy ID Token, decode JWT
 *   ValidationUtil  — validate email / password đầu vào
 */
@WebServlet(name = "LoginServlet", urlPatterns = {"/login"})
public class LoginServlet extends HttpServlet {

    private AuthService authService;

    @Override
    public void init() throws ServletException {
        authService = new AuthService(new UserRepository());
    }

    // ════════════════════════════════════════════════════════════════
    //  doGet — xử lý hai trường hợp:
    //    (a) GET /login            → hiển thị form đăng nhập   (1.0.1)
    //    (b) GET /login?code=...   → Google callback, Code Flow (1.1.4)
    // ════════════════════════════════════════════════════════════════
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        req.setCharacterEncoding("UTF-8");
        String code = req.getParameter("code");

        if (code != null) {
            // 1.1.4 — Google redirect về kèm authorization code → xử lý Code Flow
            handleGoogleCodeFlow(code, req, resp);
        } else {
            // 1.0.1 — Không có code → hiển thị form đăng nhập
            req.getRequestDispatcher("/WEB-INF/login.jsp").forward(req, resp);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  doPost — chỉ xử lý BASIC FLOW: đăng nhập Email / Mật khẩu
    // ════════════════════════════════════════════════════════════════
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        req.setCharacterEncoding("UTF-8");
        // 1.0.2 — Form email/password được submit → xử lý đăng nhập nội bộ
        handleLocalLogin(req, resp);
    }

    // ════════════════════════════════════════════════════════════════
    //  BASIC FLOW — UC1.0: Đăng nhập bằng Email / Mật khẩu
    // ════════════════════════════════════════════════════════════════
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
            // 1.0.5 → 1.0.10 — Ủy quyền toàn bộ nghiệp vụ xác thực cho AuthService
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

    // ════════════════════════════════════════════════════════════════
    //  ALT FLOW — UC1.1: Đăng nhập bằng Google (Authorization Code Flow)
    //
    //  Trigger : GET /login?code=<authorization_code>
    //            (Google redirect sau khi người dùng đồng ý cấp quyền)
    //
    //  Bước:
    //    1.1.4  — Nhận authorization code từ query param "code"
    //    1.1.4b — Gọi GoogleOAuthUtil.exchangeCodeAndVerify():
    //               POST tới https://oauth2.googleapis.com/token
    //               nhận id_token trong response JSON
    //               decode JWT payload → lấy email + name
    //    1.1.5  — Có GoogleTokenPayloadDTO hợp lệ
    //    1.1.6  → 1.1.13 — Ủy quyền nghiệp vụ cho processGoogleUser()
    //
    //  Exception:
    //    1.1-E1 — exchange thất bại hoặc JWT lỗi → log, báo lỗi người dùng
    // ════════════════════════════════════════════════════════════════
    private void handleGoogleCodeFlow(String code,
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

        // 1.1.5 — Có payload hợp lệ → xử lý nghiệp vụ đăng nhập / đăng ký Google
        processGoogleUser(payload, req, resp);
    }

    // ════════════════════════════════════════════════════════════════
    //  Xử lý nghiệp vụ Google sau khi có payload hợp lệ
    //  Dùng chung cho cả UC1.1 (tài khoản đã tồn tại) và UC1.2 (tự đăng ký)
    //
    //  Bước:
    //    1.1.7       — Đóng gói GoogleLoginRequestDTO (email, name, ip, userAgent)
    //    1.1.8→1.1.13 — AuthService.loginWithGoogle() quyết định:
    //                     UC1.1: tài khoản GOOGLE đã có → cập nhật last_login_at, ghi log
    //                     UC1.2: chưa có → INSERT user mới (role=USER, status=ACTIVE,
    //                            provider=GOOGLE), ghi log GOOGLE_REGISTER
    //    1.1.14      — Thành công → tạo session mới (hủy session cũ trước)
    //    1.1.15      — Điều hướng theo role
    //
    //  Exception:
    //    1.1-E2 — Email đã đăng ký bằng LOCAL → yêu cầu dùng mật khẩu
    //    1.1-E3 — Tài khoản Google bị khóa    → ghi log, từ chối
    // ════════════════════════════════════════════════════════════════
    private void processGoogleUser(GoogleTokenPayloadDTO payload,
                                   HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // 1.1.7 — Đóng gói DTO từ Google payload + ip, userAgent để ghi log
        GoogleLoginRequestDTO googleDto = new GoogleLoginRequestDTO(
                payload.getEmail(), payload.getName(),
                getClientIp(req), getClientUserAgent(req));

        try {
            // 1.1.8 → 1.1.13 — Ủy quyền nghiệp vụ: đăng nhập (UC1.1) hoặc đăng ký (UC1.2)
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

    // ════════════════════════════════════════════════════════════════
    //  Helpers dùng chung
    // ════════════════════════════════════════════════════════════════

    /**
     * 1.0.11 / 1.1.14 — Tạo HTTP Session sau đăng nhập thành công.
     * Hủy session cũ trước khi tạo mới để chống Session Fixation Attack.
     */
    private void createSession(HttpServletRequest req, LoginResponseDTO response) {
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

    /**
     * 1.0.12 / 1.1.15 — Điều hướng sau đăng nhập theo role.
     *   ADMIN → /admin/dashboard
     *   USER  → /home
     */
    private void redirectByRole(HttpServletRequest req, LoginResponseDTO response,
                                HttpServletResponse resp) throws IOException {
        String contextPath = req.getContextPath();
        if (response.isAdmin()) {
            resp.sendRedirect(contextPath + "/admin/dashboard");
        } else {
            resp.sendRedirect(contextPath + "/home");
        }
    }

    /**
     * Helper — Lấy IP thực của client.
     * Ưu tiên header X-Forwarded-For (qua reverse proxy) trước getRemoteAddr().
     */
    private String getClientIp(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) return ip.split(",")[0].trim();
        ip = req.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank()) return ip.trim();
        return req.getRemoteAddr();
    }

    /**
     * Helper — Lấy và cắt ngắn User-Agent header (tối đa 512 ký tự).
     */
    private String getClientUserAgent(HttpServletRequest req) {
        String ua = req.getHeader("User-Agent");
        if (ua != null && ua.length() > 512) return ua.substring(0, 512);
        return ua;
    }
}