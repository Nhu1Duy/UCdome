package com.example.uc.exception;

/**
 * DatabaseException — Đại diện lỗi tầng CSDL.
 *
 * Ném từ Repository khi:
 *   - Không lấy được Connection từ pool
 *   - Câu SQL thất bại (constraint, timeout…)
 *
 * Bắt ở Service và chuyển tiếp lên Servlet → Servlet hiển thị
 * thông báo lỗi thân thiện (1.0-EDB / 1.1-EDB).
 *
 * Là RuntimeException để không bắt buộc khai báo throws ở mọi nơi,
 * nhưng vẫn được bắt cụ thể ở tầng Servlet.
 */
public class DatabaseException extends RuntimeException {

    public DatabaseException(String message) {
        super(message);
    }

    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
}