package com.example.uc.service;

import com.example.uc.exception.DatabaseException;
import com.example.uc.model.ActivityLog;
import com.example.uc.model.User;
import com.example.uc.model.User.Status;
import com.example.uc.repository.UserManagementRepository;
import com.example.uc.util.BCryptUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ═══════════════════════════════════════════════════════════════════
 *  UserManagementService — Tầng Service cho UC-08
 *
 *  Trách nhiệm: toàn bộ logic nghiệp vụ UC-08 (không biết HTTP).
 *  Nhận primitive/model từ Servlet, trả kết quả, ném exception nếu lỗi.
 *
 *  Các phương thức bám sát luồng UC:
 *    listUsers()   → UC8.0 — danh sách có phân trang
 *    searchUsers() → UC8.5 — tìm kiếm có phân trang
 *    createUser()  → UC8.1 — thêm user mới
 *    updateUser()  → UC8.2 — cập nhật thông tin
 *    deleteUser()  → UC8.3 — soft-delete
 *    toggleLock()  → UC8.4 — khóa / mở khóa (auto-toggle)
 * ═══════════════════════════════════════════════════════════════════
 */
public class UserManagementService {

    public static final int PAGE_SIZE = 10;   // NFR-02: phân trang backend

    private final UserManagementRepository repo;

    public UserManagementService(UserManagementRepository repo) {
        this.repo = repo;
    }

    // ════════════════════════════════════════════════════════════════
    //  8.0 — DANH SÁCH USER (phân trang)
    // ════════════════════════════════════════════════════════════════

    /**
     * 8.0.1 — Lấy danh sách user phân trang.
     * Trả về map chứa: users, totalPages, currentPage, totalUsers.
     *
     * @param page trang hiện tại (1-based, mặc định 1 nếu < 1)
     */
    public Map<String, Object> listUsers(int page) {
        if (page < 1) page = 1;

        List<User> users    = repo.findAllPaged(page, PAGE_SIZE);
        int        total    = repo.countAll();
        int        totalPages = (int) Math.ceil((double) total / PAGE_SIZE);
        if (totalPages < 1) totalPages = 1;

        Map<String, Object> result = new HashMap<>();
        result.put("users",       users);
        result.put("totalPages",  totalPages);
        result.put("currentPage", page);
        result.put("totalUsers",  total);
        return result;
    }

    // ════════════════════════════════════════════════════════════════
    //  8.5 — TÌM KIẾM USER
    // ════════════════════════════════════════════════════════════════

    /**
     * 8.5.3 — Tìm kiếm user theo từ khoá.
     * Nếu keyword rỗng sau trim → trả về danh sách đầy đủ (8.5-A2).
     *
     * @param keyword  từ khoá người dùng nhập
     * @param page     trang (1-based)
     */
    public Map<String, Object> searchUsers(String keyword, int page) {
        if (page < 1) page = 1;

        // 8.5-A2: không có từ khoá → trả danh sách đầy đủ
        if (keyword == null || keyword.isBlank()) {
            return listUsers(page);
        }

        String kw         = keyword.trim();
        List<User> users  = repo.search(kw, page, PAGE_SIZE);
        int total         = repo.countSearch(kw);
        int totalPages    = (int) Math.ceil((double) total / PAGE_SIZE);
        if (totalPages < 1) totalPages = 1;

        Map<String, Object> result = new HashMap<>();
        result.put("users",       users);
        result.put("totalPages",  totalPages);
        result.put("currentPage", page);
        result.put("totalUsers",  total);
        result.put("keyword",     kw);
        return result;
    }

    // ════════════════════════════════════════════════════════════════
    //  8.1 — THÊM USER
    // ════════════════════════════════════════════════════════════════

    /**
     * Luồng 8.1.3 → 8.1.7:
     *   8.1.3 Admin nhập thông tin và nhấn Thêm
     *   8.1.4 Validate đầu vào + kiểm tra trùng (BR-03)
     *   8.1.5 Hash password và lưu vào CSDL (BR-04)
     *   8.1.6 Gán ID trả về
     *   8.1.7 Ghi Audit Log (BR-05)
     *
     * @param adminId     ID Admin đang thực hiện (BR-05)
     * @param ipAddress   IP client (cho Log)
     * @param userAgent   UA header (cho Log)
     * @param u           User đã gán field từ form (password chưa hash)
     * @param rawPassword mật khẩu thô — sẽ được hash tại đây
     * @return User vừa tạo (với ID)
     * @throws IllegalArgumentException nếu validate thất bại hoặc trùng dữ liệu
     */
    public User createUser(int adminId, String ipAddress, String userAgent,
                           User u, String rawPassword) {

        // 8.1-E1: validate đầu vào
        Map<String, String> errors = validateForCreate(u, rawPassword);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(buildErrorMessage(errors));
        }

        // 8.1.4: kiểm tra trùng username / email / phone (BR-03)
        // Admin tạo thủ công → provider mặc định là LOCAL
        // Chỉ check bản ghi deleted_at IS NULL (HƯỚNG 1)
        String dupField = repo.checkDuplicate(u.getUsername(), u.getEmail(), u.getPhone());
        if (dupField != null) {
            throw new IllegalArgumentException(
                    "Trùng lặp dữ liệu: " + dupField + " đã tồn tại trong hệ thống.");
        }

        // 8.1.5: hash password trước khi lưu (BR-04)
        u.setPasswordHash(BCryptUtil.hashPassword(rawPassword));

        // 8.1.5: lưu vào CSDL
        User created = repo.insert(u);

        // 8.1.7: ghi Audit Log (BR-05)
        safeInsertLog(new ActivityLog(
                adminId, "USER_CREATE",
                "Admin tạo mới user: " + created.getEmail() + " (id=" + created.getId() + ")",
                ipAddress, userAgent, "USER", created.getId()
        ));

        return created;
    }

    // ════════════════════════════════════════════════════════════════
    //  8.2 — CẬP NHẬT USER
    // ════════════════════════════════════════════════════════════════

    /**
     * Luồng 8.2.3 → 8.2.7:
     *   8.2.3 Admin chỉnh sửa và nhấn Lưu
     *   8.2.4 Validate đầu vào + kiểm tra trùng loại trừ chính user
     *   8.2.5 Cập nhật vào CSDL
     *   8.2.6 Ghi Audit Log
     *   8.2.7 Trả bản ghi mới nhất
     *
     * @param adminId   ID Admin đang thực hiện
     * @param u         User với các field đã được gán từ form
     */
    public User updateUser(int adminId, String ipAddress, String userAgent, User u) {

        // 8.2-E1: validate đầu vào
        Map<String, String> errors = validateForUpdate(u);
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException(buildErrorMessage(errors));
        }

        // 8.2.4: kiểm tra trùng, loại trừ chính user đang sửa
        // Truyền authProvider để check đúng composite unique (email, auth_provider)
        String authProvider = u.getAuthProvider() != null
                ? u.getAuthProvider().name()
                : "LOCAL";

        String dupField = repo.checkDuplicate(
                u.getUsername(), u.getEmail(), u.getPhone(), authProvider, u.getId());
        if (dupField != null) {
            throw new IllegalArgumentException(
                    "Trùng lặp dữ liệu: " + dupField + " đã tồn tại trong hệ thống.");
        }

        // 8.2.5: cập nhật CSDL — chỉ tác động bản ghi chưa bị xoá
        boolean updated = repo.update(u);
        if (!updated) {
            throw new IllegalArgumentException("Không tìm thấy user hoặc user đã bị xoá.");
        }

        // 8.2.6: ghi Audit Log
        safeInsertLog(new ActivityLog(
                adminId, "USER_UPDATE",
                "Admin cập nhật user id=" + u.getId() + " (" + u.getEmail() + ")",
                ipAddress, userAgent, "USER", u.getId()
        ));

        // 8.2.7: trả về bản ghi mới nhất (chỉ active)
        return repo.findActiveById(u.getId());
    }

    // ════════════════════════════════════════════════════════════════
    //  8.3 — XOÁ USER (soft-delete)
    // ════════════════════════════════════════════════════════════════

    /**
     * Luồng UC8.3 — SOFT-DELETE:
     *   8.3.3 Admin nhấn Xác nhận
     *   8.3.4 Kiểm tra BR-01 (không tự xoá chính mình)
     *         + Kiểm tra user còn tồn tại và chưa bị xoá
     *   8.3.5 Cập nhật deleted_at = NOW(), deleted_by = adminId
     *   8.3.6 Ghi Audit Log
     *   8.3.7 Cập nhật danh sách (phía Servlet)
     *
     * Sau khi soft-delete, email/username của user đó được phép dùng lại
     * khi tạo tài khoản mới (HƯỚNG 1 — xem checkDuplicate).
     *
     * @param adminId      ID Admin đang thực hiện
     * @param targetUserId ID user cần xoá
     */
    public void deleteUser(int adminId, int targetUserId,
                           String ipAddress, String userAgent) {

        // 8.3.4 / BR-01: chặn Admin tự xoá chính mình
        if (adminId == targetUserId) {
            throw new IllegalArgumentException("Không thể xóa tài khoản đang đăng nhập.");
        }

        // Dùng findById (bao gồm cả đã xoá) để đưa ra thông báo chính xác
        User target = repo.findById(targetUserId);
        if (target == null) {
            throw new IllegalArgumentException("Không tìm thấy user.");
        }
        if (target.isDeleted()) {
            throw new IllegalArgumentException("User này đã bị xoá trước đó.");
        }

        // 8.3.5: soft-delete
        boolean done = repo.softDelete(targetUserId, adminId);
        if (!done) {
            throw new IllegalArgumentException("Xoá user thất bại, vui lòng thử lại.");
        }

        // 8.3.6: ghi Audit Log (BR-05)
        safeInsertLog(new ActivityLog(
                adminId, "USER_DELETE",
                "Admin soft-delete user id=" + targetUserId + " (" + target.getEmail() + ")",
                ipAddress, userAgent, "USER", targetUserId
        ));
    }

    // ════════════════════════════════════════════════════════════════
    //  8.4 — KHÓA / MỞ KHÓA USER
    // ════════════════════════════════════════════════════════════════

    /**
     * Luồng UC8.4 (tự toggle LOCKED ↔ ACTIVE):
     *   8.4.3 Admin nhấn Xác nhận
     *   8.4.4 Kiểm tra BR-01 + user còn tồn tại và chưa bị xoá
     *   8.4.5 Cập nhật status LOCKED / ACTIVE vào CSDL
     *   8.4.6 Invalidate sessions (giới hạn kỹ thuật — xem repo)
     *   8.4.7 Ghi Audit Log
     *   8.4.8 Trả trạng thái mới để UI cập nhật
     *
     *   8.4-A2: user đang LOCKED → đổi thành ACTIVE (mở khóa).
     *
     * @param adminId      ID Admin đang thực hiện
     * @param targetUserId ID user cần khóa/mở
     * @return trạng thái mới sau khi toggle
     */
    public Status toggleLock(int adminId, int targetUserId,
                             String ipAddress, String userAgent) {

        // 8.4.4 / BR-01: chặn Admin tự khóa chính mình
        if (adminId == targetUserId) {
            throw new IllegalArgumentException("Không thể khóa tài khoản đang đăng nhập.");
        }

        // Dùng findById để phân biệt "không tồn tại" vs "đã xoá"
        User target = repo.findById(targetUserId);
        if (target == null) {
            throw new IllegalArgumentException("Không tìm thấy user.");
        }
        if (target.isDeleted()) {
            throw new IllegalArgumentException("Không thể khóa user đã bị xoá.");
        }

        // 8.4-A2: toggle trạng thái
        Status newStatus = (target.getStatus() == Status.LOCKED)
                ? Status.ACTIVE
                : Status.LOCKED;

        // 8.4.5: cập nhật CSDL
        repo.updateStatus(targetUserId, newStatus);

        // 8.4.6: invalidate sessions (no-op hiện tại — xem comment trong repo)
        repo.invalidateUserSessions(targetUserId);

        // 8.4.7: ghi Audit Log (BR-05)
        String action = (newStatus == Status.LOCKED) ? "USER_LOCK" : "USER_UNLOCK";
        String desc   = (newStatus == Status.LOCKED)
                ? "Admin khoá user id=" + targetUserId + " (" + target.getEmail() + ")"
                : "Admin mở khoá user id=" + targetUserId + " (" + target.getEmail() + ")";

        safeInsertLog(new ActivityLog(
                adminId, action, desc, ipAddress, userAgent, "USER", targetUserId));

        return newStatus;
    }

    // ════════════════════════════════════════════════════════════════
    //  VALIDATE HELPERS
    // ════════════════════════════════════════════════════════════════

    /** Validate cho UC8.1 (thêm mới) — bao gồm kiểm tra password. */
    private Map<String, String> validateForCreate(User u, String rawPassword) {
        Map<String, String> errors = new HashMap<>();

        if (u.getEmail() == null || u.getEmail().isBlank())
            errors.put("email", "Email không được để trống.");
        else if (!u.getEmail().matches("^[\\w._%+\\-]+@[\\w.\\-]+\\.[a-zA-Z]{2,}$"))
            errors.put("email", "Định dạng Email không hợp lệ.");

        if (u.getFullName() == null || u.getFullName().isBlank())
            errors.put("fullName", "Họ và tên không được để trống.");

        if (rawPassword == null || rawPassword.isBlank())
            errors.put("password", "Mật khẩu không được để trống.");
        else if (rawPassword.length() < 6)
            errors.put("password", "Mật khẩu phải có ít nhất 6 ký tự.");

        if (u.getPhone() != null && !u.getPhone().isBlank()
                && !u.getPhone().matches("^[0-9+\\-\\s]{7,20}$"))
            errors.put("phone", "Số điện thoại không hợp lệ.");

        return errors;
    }

    /** Validate cho UC8.2 (cập nhật) — không yêu cầu password. */
    private Map<String, String> validateForUpdate(User u) {
        Map<String, String> errors = new HashMap<>();

        if (u.getId() <= 0)
            errors.put("id", "ID user không hợp lệ.");

        if (u.getEmail() == null || u.getEmail().isBlank())
            errors.put("email", "Email không được để trống.");
        else if (!u.getEmail().matches("^[\\w._%+\\-]+@[\\w.\\-]+\\.[a-zA-Z]{2,}$"))
            errors.put("email", "Định dạng Email không hợp lệ.");

        if (u.getFullName() == null || u.getFullName().isBlank())
            errors.put("fullName", "Họ và tên không được để trống.");

        if (u.getPhone() != null && !u.getPhone().isBlank()
                && !u.getPhone().matches("^[0-9+\\-\\s]{7,20}$"))
            errors.put("phone", "Số điện thoại không hợp lệ.");

        return errors;
    }

    private String buildErrorMessage(Map<String, String> errors) {
        return String.join("; ", errors.values());
    }

    // ════════════════════════════════════════════════════════════════
    //  LOG HELPER — không chặn luồng chính nếu log thất bại
    // ════════════════════════════════════════════════════════════════

    private void safeInsertLog(ActivityLog log) {
        try {
            repo.insertLog(log);
        } catch (DatabaseException e) {
            System.err.println("[UserManagementService] WARN: ghi log thất bại action="
                    + log.getAction() + ": " + e.getMessage());
        }
    }
}