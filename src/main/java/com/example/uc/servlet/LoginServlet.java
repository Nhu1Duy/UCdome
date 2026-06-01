package com.example.uc.servlet;

import com.example.uc.dao.UserDAO;
import com.example.uc.model.ActivityLog;
import com.example.uc.model.User;
import com.example.uc.util.BCryptUtil;
import com.example.uc.util.GoogleOAuthUtil;
import com.example.uc.util.ValidationUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

/**
 * ═══════════════════════════════════════════════════════════════════
 *  LoginServlet — UC-01: Đăng nhập hệ thống
 *  Xử lý cả 3 luồng:
 *    • Basic Flow   (1.0.x)  — đăng nhập Email/Mật khẩu
 *    • UC1.1        (1.1.x)  — đăng nhập Google (tài khoản đã tồn tại)
 *    • UC1.2        (1.2.x)  — tự động đăng ký lần đầu qua Google
 * ═══════════════════════════════════════════════════════════════════
 */
@WebServlet(name = "LoginServlet", urlPatterns = {"/login"})
public class LoginServlet extends HttpServlet {

    private final UserDAO userDAO = new UserDAO();

    // ────────────────────────────────────────────────────────────────
    // GET /login → hiển thị form đăng nhập
    // 1.0.1 — Hệ thống hiển thị Form đăng nhập
    // ────────────────────────────────────────────────────────────────
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String code = req.getParameter("code");

        if (code != null) {
            // Google redirect về với authorization code
            req.setCharacterEncoding("UTF-8");
            handleGoogleLoginWithCode(code, req, resp);
            return;
        }

        // Hiển thị form đăng nhập bình thường
        req.getRequestDispatcher("/WEB-INF/login.jsp").forward(req, resp);
    }

    // ────────────────────────────────────────────────────────────────
    // POST /login → xử lý đăng nhập
    // ────────────────────────────────────────────────────────────────
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        req.setCharacterEncoding("UTF-8");
        String loginType = req.getParameter("loginType"); // "local" hoặc "google"

        if ("google".equals(loginType)) {
            handleGoogleLogin(req, resp);  // → UC1.1 / UC1.2
        } else {
            handleLocalLogin(req, resp);   // → Basic Flow
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  BASIC FLOW — Đăng nhập Email / Mật khẩu
    // ════════════════════════════════════════════════════════════════
    private void handleLocalLogin(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        String email    = req.getParameter("email");
        String password = req.getParameter("password");

        // ── 1.0.3 Validate đầu vào ──────────────────────────────────
        String emailError    = ValidationUtil.validateEmail(email);
        String passwordError = ValidationUtil.validatePassword(password);

        if (emailError != null || passwordError != null) {
            // 1.0-E1 — Validate thất bại: báo lỗi, không gọi CSDL
            req.setAttribute("emailError",    emailError);
            req.setAttribute("passwordError", passwordError);
            req.getRequestDispatcher("/WEB-INF/login.jsp").forward(req, resp);
            return;
        }

        // ── 1.0-EDB: try-catch bắt lỗi kết nối CSDL (break fragment) ──
        try {

            // 1.0.4 — Tìm user theo email, đối chiếu mật khẩu băm (BR-04)
            User user = userDAO.findByEmail(email);

            if (user == null || !BCryptUtil.checkPassword(password, user.getPasswordHash())) {
                // 1.0-E2 — Sai Email hoặc Sai Mật khẩu
                // BR-01: không tiết lộ trường nào sai
                req.setAttribute("error", "Email hoặc mật khẩu không chính xác.");
                if (user != null) {
                    userDAO.increaseFailedAttempts(user.getId()); // 1.0-E2: tăng bộ đếm
                }
                req.getRequestDispatcher("/WEB-INF/login.jsp").forward(req, resp);
                return;
            }

            // 1.0.5 — Kiểm tra trạng thái tài khoản
            if ("LOCKED".equals(user.getStatus())) {
                // 1.0-E3 — Tài khoản bị khóa
                req.setAttribute("error", "Tài khoản đã bị khóa. Vui lòng liên hệ quản trị viên.");
                userDAO.insertLog(new ActivityLog(user.getId(), "ACCOUNT_LOCKED_ACCESS",
                    "Cố gắng truy cập tài khoản bị khóa: " + email));
                req.getRequestDispatcher("/WEB-INF/login.jsp").forward(req, resp);
                return;
            }

            // 1.0.6 — Reset bộ đếm sai mật khẩu về 0
            userDAO.resetFailedAttempts(user.getId());

            // 1.0.7 — Vai trò (role) đã có trong object user từ bước 1.0.4
            // 1.0.8 — Khởi tạo và lưu phiên đăng nhập an toàn
            createSession(req, user);

            // 1.0.9 — Ghi log đăng nhập thành công
            userDAO.insertLog(new ActivityLog(user.getId(), "LOGIN_SUCCESS",
                "Đăng nhập thành công bằng tài khoản nội bộ: " + email));

            // 1.0.10 — Điều hướng theo quyền hạn
            redirectByRole(req, user, resp);

        } catch (SQLException e) {
            // 1.0-EDB — Lỗi kết nối CSDL (break fragment)
            getServletContext().log("DB Error handleLocalLogin: " + e.getMessage(), e);
            req.setAttribute("error", "Hệ thống đang gặp sự cố. Vui lòng thử lại sau.");
            req.getRequestDispatcher("/WEB-INF/login.jsp").forward(req, resp);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  UC1.1 + UC1.2 — Đăng nhập / Tự động đăng ký qua Google
    // ════════════════════════════════════════════════════════════════
    private void handleGoogleLogin(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // 1.1.4 — Frontend gửi ID Token sau khi Google xác thực
        String idTokenString = req.getParameter("idToken");

        try {
            // 1.1.7 — Kiểm tra chữ ký điện tử và tính hợp lệ của Token
            Map<String, String> payload;
            try {
                payload = GoogleOAuthUtil.verifyIdToken(idTokenString);
            } catch (SecurityException e) {
                // 1.1-E1 — Token không hợp lệ hoặc hết hạn
                getServletContext().log("OAuth Error: " + e.getMessage());
                userDAO.insertLog(new ActivityLog(null, "OAUTH_ERROR",
                    "Xác thực Google thất bại: " + e.getMessage()));
                req.setAttribute("error", "Xác thực qua tài khoản Google thất bại. Vui lòng thử lại.");
                req.getRequestDispatcher("/WEB-INF/login.jsp").forward(req, resp);
                return;
            }

            // 1.1.6 — Lấy thông tin từ gói dữ liệu Google trả về
            String email    = payload.get("email");
            String fullName = payload.get("name");

            // 1.1.8 — Kiểm tra Email từ Google đã tồn tại trong hệ thống chưa
            User user = userDAO.findByEmailAndProvider(email, "GOOGLE");

            if (user == null) {
                // ── Rẽ nhánh sang UC1.2 ─────────────────────────────
                // 1.2.1 — Khởi tạo bản ghi User mới từ dữ liệu Google
                // 1.2.2 — Gán role USER mặc định (BR-03)
                // 1.2.3 — Lưu vào CSDL, nhận ID tự sinh
                user = userDAO.createGoogleUser(email, fullName);

                // 1.2.4 — Khởi tạo phiên đăng nhập
                createSession(req, user);

                // 1.2.5 — Ghi log đăng ký mới qua Google
                userDAO.insertLog(new ActivityLog(user.getId(), "GOOGLE_REGISTER",
                    "Đăng ký mới tự động qua Google: " + email));

                // 1.2.6 — Điều hướng về Trang chủ
                resp.sendRedirect(req.getContextPath() + "/home");
                return;
            }

            // ── Tiếp tục UC1.1 (tài khoản đã tồn tại) ──────────────
            // 1.1.9 — Kiểm tra trạng thái hoạt động của tài khoản
            if ("LOCKED".equals(user.getStatus())) {
                userDAO.insertLog(new ActivityLog(user.getId(), "ACCOUNT_LOCKED_ACCESS",
                    "Cố gắng đăng nhập Google vào tài khoản bị khóa: " + email));
                req.setAttribute("error", "Tài khoản đã bị khóa. Vui lòng liên hệ quản trị viên.");
                req.getRequestDispatcher("/WEB-INF/login.jsp").forward(req, resp);
                return;
            }

            // 1.1.10 → tiếp tục như Basic Flow từ 1.0.7 đến 1.0.10
            // 1.0.8 — Khởi tạo phiên
            createSession(req, user);

            // 1.0.9 — Ghi log thành công
            userDAO.insertLog(new ActivityLog(user.getId(), "LOGIN_SUCCESS",
                "Đăng nhập thành công qua Google: " + email));

            // 1.0.10 — Điều hướng theo quyền hạn
            redirectByRole(req, user, resp);

        } catch (SQLException e) {
            // 1.1-EDB — Lỗi kết nối CSDL (break fragment)
            getServletContext().log("DB Error handleGoogleLogin: " + e.getMessage(), e);
            req.setAttribute("error", "Hệ thống đang gặp sự cố. Vui lòng thử lại sau.");
            req.getRequestDispatcher("/WEB-INF/login.jsp").forward(req, resp);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Helper: 1.0.8 — Khởi tạo và lưu phiên đăng nhập an toàn
    // ════════════════════════════════════════════════════════════════
    private void createSession(HttpServletRequest req, User user) {
        // Invalidate phiên cũ trước khi tạo mới (chống Session Fixation)
        HttpSession old = req.getSession(false);
        if (old != null) old.invalidate();

        HttpSession session = req.getSession(true);
        session.setAttribute("userId",    user.getId());
        session.setAttribute("userEmail", user.getEmail());
        session.setAttribute("userRole",  user.getRole());    // 1.0.7
        session.setAttribute("fullName",  user.getFullName());
        session.setMaxInactiveInterval(30 * 60); // 30 phút
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

    private void handleGoogleLoginWithCode(String code,
                                           HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        try {
            // Đổi code lấy token và decode payload
            Map<String, String> payload;
            try {
                payload = GoogleOAuthUtil.exchangeCodeAndVerify(code);
            } catch (SecurityException e) {
                getServletContext().log("OAuth Error: " + e.getMessage());
                userDAO.insertLog(new ActivityLog(null, "OAUTH_ERROR",
                        "Xác thực Google thất bại: " + e.getMessage()));
                req.setAttribute("error", "Xác thực Google thất bại. Vui lòng thử lại.");
                req.getRequestDispatcher("/WEB-INF/login.jsp").forward(req, resp);
                return;
            }

            String email    = payload.get("email");
            String fullName = payload.get("name");

            User user = userDAO.findByEmailAndProvider(email, "GOOGLE");

            if (user == null) {
                user = userDAO.createGoogleUser(email, fullName);
                createSession(req, user);
                userDAO.insertLog(new ActivityLog(user.getId(), "GOOGLE_REGISTER",
                        "Đăng ký mới tự động qua Google: " + email));
                resp.sendRedirect(req.getContextPath() + "/home");
                return;
            }

            if ("LOCKED".equals(user.getStatus())) {
                userDAO.insertLog(new ActivityLog(user.getId(), "ACCOUNT_LOCKED_ACCESS",
                        "Cố gắng đăng nhập Google vào tài khoản bị khóa: " + email));
                req.setAttribute("error", "Tài khoản đã bị khóa. Vui lòng liên hệ quản trị viên.");
                req.getRequestDispatcher("/WEB-INF/login.jsp").forward(req, resp);
                return;
            }

            createSession(req, user);
            userDAO.insertLog(new ActivityLog(user.getId(), "LOGIN_SUCCESS",
                    "Đăng nhập thành công qua Google: " + email));
            redirectByRole(req, user, resp);

        } catch (SQLException e) {
            getServletContext().log("DB Error handleGoogleLoginWithCode: " + e.getMessage(), e);
            req.setAttribute("error", "Hệ thống đang gặp sự cố. Vui lòng thử lại sau.");
            req.getRequestDispatcher("/WEB-INF/login.jsp").forward(req, resp);
        }
    }
}
