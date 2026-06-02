package com.example.uc.cofig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * ═══════════════════════════════════════════════════════════════════
 *  DatabaseConfig — Quản lý kết nối MySQL qua DriverManager
 *
 *  Liên quan luồng:
 *    1.0-EDB — Lỗi kết nối CSDL (break fragment): ném DatabaseException
 *    1.1-EDB — Tương tự cho nhánh Google OAuth
 * ═══════════════════════════════════════════════════════════════════
 */
public class DatabaseConfig {

    // ─── Thông tin kết nối MySQL ────────────────────────────────────
    private static final String DB_URL =
            "jdbc:mysql://localhost:3306/uc" +
                    "?useUnicode=true" +
                    "&characterEncoding=utf8" +
                    "&useSSL=false" +
                    "&allowPublicKeyRetrieval=true" +
                    "&serverTimezone=UTC";

    private static final String DB_USER     = "root";
    private static final String DB_PASSWORD = "";

    // ─── Load driver 1 lần khi class được load ──────────────────────
    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError(
                    "Không tìm thấy MySQL JDBC Driver: " + e.getMessage()
            );
        }
    }

    /**
     * Tạo Connection mới mỗi lần gọi.
     * Dùng trong try-with-resources để tự động đóng khi xong.
     *
     * @throws SQLException nếu không kết nối được CSDL (1.0-EDB / 1.1-EDB)
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }

    /**
     * Giữ lại để AppContextListener gọi khi shutdown — không làm gì
     * vì DriverManager không có pool cần đóng.
     */
    public static void closePool() {
        // DriverManager không dùng pool → không cần xử lý
    }

    // ─── Private constructor: không cho instantiate ─────────────────
    private DatabaseConfig() {}
}