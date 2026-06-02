package com.example.uc.service;

import com.example.uc.exception.AuthException;
import com.example.uc.exception.AuthException.ErrorCode;
import com.example.uc.exception.DatabaseException;
import com.example.uc.model.ActivityLog;
import com.example.uc.model.User;
import com.example.uc.repository.UserRepository;
import com.example.uc.util.BCryptUtil;

/**
 * ═══════════════════════════════════════════════════════════════════
 *  AuthService — Tầng Service
 *
 *  Trách nhiệm: TOÀN BỘ logic nghiệp vụ của UC-01 (Đăng nhập hệ thống).
 *  KHÔNG biết HTTP (request/response) — nhận primitive / model, trả User.
 *
 *  Luồng được xử lý:
 *    • Basic Flow  (1.0.x) — đăng nhập Email/Mật khẩu
 *    • UC1.1       (1.1.x) — đăng nhập Google (tài khoản đã tồn tại)
 *    • UC1.2       (1.2.x) — tự động đăng ký khi đăng nhập Google lần đầu
 *
 *  Quy ước ném exception:
 *    - AuthException     → lỗi nghiệp vụ (sai mật khẩu, tài khoản khóa…)
 *    - DatabaseException → lỗi kết nối CSDL (1.0-EDB / 1.1-EDB)
 *    Servlet bắt cả hai và phản hồi thông báo phù hợp.
 * ═══════════════════════════════════════════════════════════════════
 */
public class AuthService {

    // ─── Dependency: tiêm qua constructor (dễ unit test) ────────────
    private final UserRepository userRepository;

    public AuthService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // ════════════════════════════════════════════════════════════════
    //  BASIC FLOW — Đăng nhập Email / Mật khẩu (1.0.x)
    // ════════════════════════════════════════════════════════════════

    /**
     * Xác thực đăng nhập nội bộ (email + password).
     *
     * Luồng xử lý:
     *   1.0.4 → Tìm user theo email trong CSDL
     *   1.0-E2→ Không tìm thấy hoặc sai mật khẩu → tăng bộ đếm, ném AuthException
     *   1.0.5 → Kiểm tra status tài khoản
     *   1.0-E3→ LOCKED → ghi log, ném AuthException
     *   1.0.6 → Reset bộ đếm sai về 0
     *   1.0.9 → Ghi log thành công
     *   return User để Servlet tạo session (1.0.8)
     *
     * @param email    email đã được validate ở Servlet (1.0.3)
     * @param password mật khẩu plaintext, đối chiếu với bcrypt hash (BR-04)
     * @return User hợp lệ → Servlet tạo session và điều hướng (1.0.8 / 1.0.10)
     * @throws AuthException     lỗi nghiệp vụ (sai thông tin, tài khoản khóa)
     * @throws DatabaseException lỗi kết nối CSDL (1.0-EDB)
     */
    public User loginWithEmailPassword(String email, String password) {

        // ── 1.0.4: Truy vấn CSDL lấy user theo email ─────────────────
        // DatabaseException nổi lên nếu kết nối thất bại (1.0-EDB)
        User user = userRepository.findByEmail(email);

        // ── 1.0-E2: Không tìm thấy email ──────────────────────────────
        if (user == null) {
            // BR-01: không tiết lộ trường nào sai (dùng thông báo chung)
            throw new AuthException(ErrorCode.INVALID_CREDENTIALS,
                    "Email hoặc mật khẩu không chính xác.");
        }

        // ── 1.0-E2: Sai mật khẩu — đối chiếu bcrypt hash (BR-04) ──────
        if (!BCryptUtil.checkPassword(password, user.getPasswordHash())) {
            // Tăng bộ đếm sai; nếu >= MAX thì SQL tự LOCK (BR-02)
            // Lỗi ghi CSDL ở đây bỏ qua (log nhưng không chặn luồng lỗi)
            safeIncreaseFailedAttempts(user.getId());

            // BR-01: thông báo chung, không chỉ rõ email hay password sai
            throw new AuthException(ErrorCode.INVALID_CREDENTIALS,
                    "Email hoặc mật khẩu không chính xác.");
        }

        // ── 1.0.5: Kiểm tra trạng thái tài khoản ──────────────────────
        if ("LOCKED".equals(user.getStatus())) {
            // 1.0-E3: ghi log cố gắng truy cập khi bị khóa
            safeInsertLog(new ActivityLog(user.getId(), "ACCOUNT_LOCKED_ACCESS",
                    "Cố gắng truy cập tài khoản bị khóa: " + email));

            throw new AuthException(ErrorCode.ACCOUNT_LOCKED,
                    "Tài khoản đã bị khóa. Vui lòng liên hệ quản trị viên.");
        }

        // ── 1.0.6: Đăng nhập thành công → reset bộ đếm về 0 ──────────
        userRepository.resetFailedAttempts(user.getId());

        // ── 1.0.9: Ghi log đăng nhập thành công ───────────────────────
        safeInsertLog(new ActivityLog(user.getId(), "LOGIN_SUCCESS",
                "Đăng nhập thành công bằng tài khoản nội bộ: " + email));

        // 1.0.7 + return: role đã có trong User → Servlet đọc để điều hướng (1.0.10)
        return user;
    }

    // ════════════════════════════════════════════════════════════════
    //  UC1.1 + UC1.2 — Đăng nhập / Tự đăng ký qua Google
    // ════════════════════════════════════════════════════════════════

    /**
     * Xử lý đăng nhập Google sau khi đã xác thực token thành công ở Servlet.
     *
     * Luồng xử lý:
     *   1.1.8 → Tìm user theo email + provider='GOOGLE'
     *   null  → Rẽ UC1.2: tạo tài khoản mới (1.2.1 → 1.2.3), ghi log (1.2.5)
     *   found → UC1.1: kiểm tra status (1.1.9), ghi log (1.0.9)
     *   return User → Servlet tạo session + điều hướng (1.1.10 / 1.2.4 / 1.2.6)
     *
     * @param email    email từ Google ID Token (đã xác thực chữ ký)
     * @param fullName tên đầy đủ từ Google profile
     * @return User hợp lệ
     * @throws AuthException     tài khoản bị khóa (1.1-E2)
     * @throws DatabaseException lỗi kết nối CSDL (1.1-EDB)
     */
    public User loginWithGoogle(String email, String fullName) {

        // ── 1.1.8: Kiểm tra email từ Google đã tồn tại trong hệ thống chưa ──
        User user = userRepository.findByEmailAndProvider(email, "GOOGLE");

        if (user == null) {
            // ══ Rẽ nhánh UC1.2: tài khoản Google chưa tồn tại ═════════
            return registerGoogleUser(email, fullName);
        }

        // ══ UC1.1: Tài khoản Google đã tồn tại ══════════════════════
        // ── 1.1.9: Kiểm tra trạng thái ───────────────────────────────
        if ("LOCKED".equals(user.getStatus())) {
            safeInsertLog(new ActivityLog(user.getId(), "ACCOUNT_LOCKED_ACCESS",
                    "Cố gắng đăng nhập Google vào tài khoản bị khóa: " + email));

            throw new AuthException(ErrorCode.ACCOUNT_LOCKED,
                    "Tài khoản đã bị khóa. Vui lòng liên hệ quản trị viên.");
        }

        // ── 1.0.9 (qua 1.1.10): Ghi log thành công ───────────────────
        safeInsertLog(new ActivityLog(user.getId(), "LOGIN_SUCCESS",
                "Đăng nhập thành công qua Google: " + email));

        return user;
    }

    // ════════════════════════════════════════════════════════════════
    //  UC1.2 — Tự động đăng ký lần đầu qua Google (private helper)
    // ════════════════════════════════════════════════════════════════

    /**
     * 1.2.1 → 1.2.5 — Tạo tài khoản mới từ thông tin Google.
     *   1.2.1: Khởi tạo bản ghi User từ email + fullName
     *   1.2.2: Gán role='USER' mặc định (BR-03) — thực hiện trong Repository
     *   1.2.3: Lưu CSDL, nhận ID tự sinh từ MySQL AUTO_INCREMENT
     *   1.2.5: Ghi log đăng ký mới
     */
    private User registerGoogleUser(String email, String fullName) {

        // 1.2.1 → 1.2.3: INSERT vào CSDL, trả về User đầy đủ
        User newUser = userRepository.createGoogleUser(email, fullName);

        // 1.2.5: Ghi log sự kiện đăng ký mới
        safeInsertLog(new ActivityLog(newUser.getId(), "GOOGLE_REGISTER",
                "Đăng ký mới tự động qua Google: " + email));

        return newUser;
    }

    // ════════════════════════════════════════════════════════════════
    //  PRIVATE HELPERS — Bắt lỗi phụ trợ không chặn luồng chính
    // ════════════════════════════════════════════════════════════════

    /**
     * Tăng bộ đếm sai mật khẩu.
     * Lỗi CSDL ở đây KHÔNG ném lên vì không muốn che khuất AuthException gốc.
     * Nhưng cần log để vận hành biết có vấn đề.
     */
    private void safeIncreaseFailedAttempts(int userId) {
        try {
            userRepository.increaseFailedAttempts(userId);
        } catch (DatabaseException e) {
            // Ghi log hệ thống — không thay đổi luồng trả về cho người dùng
            System.err.println("[AuthService] WARN: không tăng được failed_attempts userId="
                    + userId + ": " + e.getMessage());
        }
    }

    /**
     * Ghi ActivityLog mà KHÔNG để lỗi ghi log phá vỡ luồng chính.
     * Nếu ghi log thất bại (CSDL lỗi cục bộ), chỉ in ra stderr.
     */
    private void safeInsertLog(ActivityLog log) {
        try {
            userRepository.insertLog(log);
        } catch (DatabaseException e) {
            System.err.println("[AuthService] WARN: không ghi được log action="
                    + log.getAction() + ": " + e.getMessage());
        }
    }
}