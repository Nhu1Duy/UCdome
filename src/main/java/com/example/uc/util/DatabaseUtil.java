package com.example.uc.util;

import org.h2.jdbcx.JdbcDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Quản lý kết nối CSDL (H2 in-memory thay thế MySQL/PostgreSQL thực).
 *
 * ⚠️  ĐIỀU CHỈNH so với UC-01:
 *   Đề cương dùng CSDL thực (MySQL). Ở đây dùng H2 in-memory để demo
 *   chạy ngay không cần cài DB. Chỉ cần thay URL/driver là chạy với DB thực.
 *
 * Liên quan đến luồng: 1.0-EDB / 1.1-EDB — Lỗi kết nối CSDL (break fragment)
 */
public class DatabaseUtil {

    private static DataSource dataSource;

    static {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:logindb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        ds.setUser("sa");
        ds.setPassword("");
        dataSource = ds;
        initSchema();
    }

    public static Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Khởi tạo schema và dữ liệu mẫu khi ứng dụng khởi động.
     * Trong môi trường thực: schema đã có sẵn trong CSDL.
     */
    private static void initSchema() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // Bảng Users
            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS users (" +
                            "    id INT AUTO_INCREMENT PRIMARY KEY," +
                            "    email VARCHAR(255) NOT NULL UNIQUE," +
                            "    password_hash VARCHAR(255)," +
                            "    role VARCHAR(50) NOT NULL DEFAULT 'USER'," +
                            "    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'," +
                            "    failed_login_attempts INT NOT NULL DEFAULT 0," +
                            "    auth_provider VARCHAR(20) NOT NULL DEFAULT 'LOCAL'," +
                            "    full_name VARCHAR(255)" +
                            ")"
            );

            stmt.execute(
                    "CREATE TABLE IF NOT EXISTS activity_log (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY, " +
                            "user_id INT, " +
                            "action VARCHAR(100) NOT NULL, " +
                            "description VARCHAR(500), " +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                            ")"
            );
            stmt.execute(
                    "MERGE INTO users (email, password_hash, role, status, auth_provider, full_name) " +
                            "KEY(email) " +
                            "VALUES " +
                            "('admin@nlu.edu.vn', " +
                            "'$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', " +
                            "'ADMIN', 'ACTIVE', 'LOCAL', 'Quản trị viên'), " +
                            "('user@nlu.edu.vn', " +
                            "'$2a$10$8K1p/a0dL1LXMIgoEDFrwOeSJfIRaFjRhFVoANB0tUHMjv0jdKoWO', " +
                            "'USER', 'ACTIVE', 'LOCAL', 'Người dùng'), " +
                            "('locked@nlu.edu.vn', " +
                            "'$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', " +
                            "'USER', 'LOCKED', 'LOCAL', 'Tài khoản bị khóa')"
            );
            // admin123 | user123 | admin123

        } catch (SQLException e) {
            throw new RuntimeException("Không thể khởi tạo CSDL: " + e.getMessage(), e);
        }
    }
}
