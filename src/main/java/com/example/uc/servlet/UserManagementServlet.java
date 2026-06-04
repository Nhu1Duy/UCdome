package com.example.uc.servlet;

import com.example.uc.exception.DatabaseException;
import com.example.uc.model.User;
import com.example.uc.model.User.Role;
import com.example.uc.model.User.Status;
import com.example.uc.repository.UserManagementRepository;
import com.example.uc.service.UserManagementService;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;

/**
 * ═══════════════════════════════════════════════════════════════════
 *  UserManagementServlet — Tầng Servlet cho UC-08
 *
 *  URL mapping:
 *    GET  /admin/users              → hiển thị danh sách (8.0.2)
 *    GET  /admin/users?q=keyword    → tìm kiếm (8.5)
 *    GET  /admin/users?page=N       → phân trang
 *    POST /admin/users?action=create  → thêm mới (8.1)
 *    POST /admin/users?action=update  → cập nhật (8.2)
 *    POST /admin/users?action=delete  → soft-delete (8.3 đã sửa)
 *    POST /admin/users?action=toggleLock → khóa/mở (8.4)
 *
 *  Pattern chung mỗi POST action:
 *    1. Kiểm tra session / quyền Admin
 *    2. Parse tham số từ request
 *    3. Gọi Service → nhận kết quả hoặc exception
 *    4a. Thành công  → redirect GET (PRG pattern) với flash message
 *    4b. Lỗi nghiệp vụ → redirect về kèm ?error=...
 *    4c. Lỗi DB      → forward đến trang lỗi
 * ═══════════════════════════════════════════════════════════════════
 */
@WebServlet("/admin/users")
public class UserManagementServlet extends HttpServlet {

    private UserManagementService service;

    @Override
    public void init() {
        // Khởi tạo repository và service (không có DI framework)
        service = new UserManagementService(new UserManagementRepository());
    }

    // ════════════════════════════════════════════════════════════════
    //  GET — Hiển thị danh sách / tìm kiếm
    // ════════════════════════════════════════════════════════════════

    /**
     * 8.0.1 → 8.0.2: Lấy danh sách user từ CSDL và forward đến JSP.
     * 8.5.1 → 8.5.4: Nếu có tham số q → tìm kiếm, đặt attribute "keyword".
     */
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // Kiểm tra session Admin
        if (!isAdmin(req)) {
            resp.sendRedirect(req.getContextPath() + "/login");
            return;
        }

        String keyword = req.getParameter("q");
        int page = parseIntParam(req.getParameter("page"), 1);

        try {
            Map<String, Object> data;

            if (keyword != null && !keyword.isBlank()) {
                // 8.5.3: tìm kiếm theo từ khoá
                data = service.searchUsers(keyword, page);
            } else {
                // 8.0.1: lấy toàn bộ danh sách
                data = service.listUsers(page);
            }

            // Đặt các attribute để JSP render (8.0.2)
            data.forEach(req::setAttribute);

            // Flash message từ redirect trước đó (nếu có)
            HttpSession session = req.getSession(false);
            if (session != null) {
                String flash = (String) session.getAttribute("flashSuccess");
                String flashErr = (String) session.getAttribute("flashError");
                if (flash    != null) { req.setAttribute("flashSuccess", flash);    session.removeAttribute("flashSuccess"); }
                if (flashErr != null) { req.setAttribute("flashError",   flashErr); session.removeAttribute("flashError"); }
            }

            req.getRequestDispatcher("/WEB-INF/user-management.jsp").forward(req, resp);

        } catch (DatabaseException e) {
            // 8.0-E1: CSDL không kết nối được
            req.setAttribute("dbError", "Không thể kết nối cơ sở dữ liệu: " + e.getMessage());
            req.getRequestDispatcher("/WEB-INF/user-management.jsp").forward(req, resp);
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  POST — Điều phối action
    // ════════════════════════════════════════════════════════════════

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        req.setCharacterEncoding("UTF-8");

        // Kiểm tra quyền Admin
        if (!isAdmin(req)) {
            resp.sendRedirect(req.getContextPath() + "/login");
            return;
        }

        String action = req.getParameter("action");
        if (action == null) action = "";
        switch (action) {
            case "create":
                handleCreate(req, resp);
                break;
            case "update":
                handleUpdate(req, resp);
                break;
            case "delete":
                handleDelete(req, resp);
                break;
            case "toggleLock":
                handleToggleLock(req, resp);
                break;
            default:
                resp.sendRedirect(req.getContextPath() + "/admin/users");
                break;
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  8.1 — THÊM USER
    // ════════════════════════════════════════════════════════════════

    /**
     * Luồng 8.1.3 → 8.1.7:
     *   Parse form → gọi service.createUser() → redirect kèm flash message.
     *
     *   8.1-E2: trùng dữ liệu → redirect kèm ?error=...
     *   8.1-E1: CSDL lỗi      → redirect kèm ?error=db
     */
    private void handleCreate(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        int adminId = getAdminId(req);
        String ip   = req.getRemoteAddr();
        String ua   = req.getHeader("User-Agent");

        // Parse form data
        User u = buildUserFromRequest(req);
        String rawPassword = req.getParameter("password");

        try {
            User created = service.createUser(adminId, ip, ua, u, rawPassword);
            // 8.1.6: thành công → flash message + redirect
            setFlash(req, "success", "Đã thêm người dùng \"" + created.getFullName() + "\" thành công.");
        } catch (IllegalArgumentException e) {
            // 8.1-E2 / validate lỗi
            setFlash(req, "error", e.getMessage());
        } catch (DatabaseException e) {
            // 8.1-E1: CSDL lỗi
            setFlash(req, "error", "Lỗi cơ sở dữ liệu: " + e.getMessage());
        }

        resp.sendRedirect(req.getContextPath() + "/admin/users");
    }

    // ════════════════════════════════════════════════════════════════
    //  8.2 — CẬP NHẬT USER
    // ════════════════════════════════════════════════════════════════

    /**
     * Luồng 8.2.3 → 8.2.7:
     *   Parse form (kèm userId) → gọi service.updateUser() → redirect.
     *
     *   8.2-E1: validate lỗi  → flash error
     *   8.2-E2: CSDL lỗi      → flash error
     */
    private void handleUpdate(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        int adminId = getAdminId(req);
        String ip   = req.getRemoteAddr();
        String ua   = req.getHeader("User-Agent");

        User u = buildUserFromRequest(req);
        int targetId = parseIntParam(req.getParameter("userId"), 0);
        u.setId(targetId);

        try {
            User updated = service.updateUser(adminId, ip, ua, u);
            setFlash(req, "success", "Đã cập nhật thông tin người dùng \"" + updated.getFullName() + "\".");
        } catch (IllegalArgumentException e) {
            setFlash(req, "error", e.getMessage());
        } catch (DatabaseException e) {
            setFlash(req, "error", "Lỗi cơ sở dữ liệu: " + e.getMessage());
        }

        resp.sendRedirect(req.getContextPath() + "/admin/users");
    }

    // ════════════════════════════════════════════════════════════════
    //  8.3 — XOÁ USER (soft-delete)
    // ════════════════════════════════════════════════════════════════

    /**
     * Luồng UC8.3 (soft-delete):
     *   8.3.3 Admin nhấn Xác nhận trên dialog
     *   8.3.4 Service kiểm tra BR-01 (không tự xoá)
     *   8.3.5 Service ghi deleted_at
     *   8.3.6 Service ghi Log
     *   8.3.7 Redirect để JSP cập nhật danh sách
     *
     *   8.3-E1: tự xoá → flash error
     *   8.3-E2: CSDL lỗi → flash error
     */
    private void handleDelete(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        int adminId    = getAdminId(req);
        int targetId   = parseIntParam(req.getParameter("userId"), 0);
        String ip      = req.getRemoteAddr();
        String ua      = req.getHeader("User-Agent");

        try {
            service.deleteUser(adminId, targetId, ip, ua);
            setFlash(req, "success", "Đã xoá người dùng thành công.");
        } catch (IllegalArgumentException e) {
            // 8.3-E1: không tự xoá chính mình
            setFlash(req, "error", e.getMessage());
        } catch (DatabaseException e) {
            // 8.3-E2: CSDL lỗi
            setFlash(req, "error", "Lỗi cơ sở dữ liệu: " + e.getMessage());
        }

        resp.sendRedirect(req.getContextPath() + "/admin/users");
    }

    // ════════════════════════════════════════════════════════════════
    //  8.4 — KHÓA / MỞ KHÓA USER
    // ════════════════════════════════════════════════════════════════

    /**
     * Luồng UC8.4:
     *   8.4.3 Admin nhấn Xác nhận
     *   8.4.4 Service kiểm tra BR-01 (không tự khoá)
     *   8.4.5 Service toggle LOCKED ↔ ACTIVE
     *   8.4.7 Service ghi Log
     *   8.4.8 Redirect — JSP hiển thị trạng thái mới
     *
     *   8.4-E1: tự khoá → flash error
     *   8.4-E2: CSDL lỗi → flash error
     */
    private void handleToggleLock(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        int adminId  = getAdminId(req);
        int targetId = parseIntParam(req.getParameter("userId"), 0);
        String ip    = req.getRemoteAddr();
        String ua    = req.getHeader("User-Agent");

        try {
            Status newStatus = service.toggleLock(adminId, targetId, ip, ua);
            String msg = (newStatus == Status.LOCKED)
                    ? "Đã khóa tài khoản thành công."
                    : "Đã mở khóa tài khoản thành công.";
            setFlash(req, "success", msg);
        } catch (IllegalArgumentException e) {
            // 8.4-E1
            setFlash(req, "error", e.getMessage());
        } catch (DatabaseException e) {
            // 8.4-E2
            setFlash(req, "error", "Lỗi cơ sở dữ liệu: " + e.getMessage());
        }

        resp.sendRedirect(req.getContextPath() + "/admin/users");
    }

    // ════════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ════════════════════════════════════════════════════════════════

    /** Xây dựng User object từ form parameters — dùng cho cả create và update. */
    private User buildUserFromRequest(HttpServletRequest req) {
        User u = new User();
        u.setUsername(trim(req.getParameter("username")));
        u.setEmail(trim(req.getParameter("email")));
        u.setFullName(trim(req.getParameter("fullName")));
        u.setPhone(trim(req.getParameter("phone")));

        // Role — mặc định USER nếu không có
        String roleStr = req.getParameter("role");
        u.setRole(roleStr != null ? roleStr : "USER");

        // Status — mặc định ACTIVE
        String statusStr = req.getParameter("status");
        u.setStatus(statusStr != null ? statusStr : "ACTIVE");

        // Auth provider — mặc định LOCAL
        u.setAuthProvider("LOCAL");

        // Date of birth — nullable
        String dobStr = req.getParameter("dateOfBirth");
        if (dobStr != null && !dobStr.isBlank()) {
            try { u.setDateOfBirth(LocalDate.parse(dobStr)); }
            catch (Exception ignored) { /* giữ null nếu parse lỗi */ }
        }

        u.setGender(trim(req.getParameter("gender")));
        u.setAddressLine(trim(req.getParameter("addressLine")));
        u.setCity(trim(req.getParameter("city")));
        u.setProvince(trim(req.getParameter("province")));
        String country = trim(req.getParameter("country"));
        u.setCountry(country != null ? country : "Vietnam");

        return u;
    }

    /** Kiểm tra session còn hạn và role = ADMIN. */
    private boolean isAdmin(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        if (s == null || s.getAttribute("userId") == null) return false;
        return "ADMIN".equals(s.getAttribute("userRole"));
    }

    private int getAdminId(HttpServletRequest req) {
        HttpSession s = req.getSession(false);
        Object id = s != null ? s.getAttribute("userId") : null;
        return id instanceof Integer ? (Integer) id : 0;
    }

    /** Lưu flash message vào session để hiển thị sau redirect (PRG pattern). */
    private void setFlash(HttpServletRequest req, String type, String message) {
        HttpSession s = req.getSession(true);
        if ("success".equals(type)) s.setAttribute("flashSuccess", message);
        else                        s.setAttribute("flashError",   message);
    }

    private int parseIntParam(String value, int defaultVal) {
        try { return Integer.parseInt(value); }
        catch (Exception e) { return defaultVal; }
    }

    private String trim(String s) {
        return (s != null && !s.isBlank()) ? s.trim() : null;
    }
}
