package com.example.uc.cofig;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

/**
 * AppContextListener — Lắng nghe vòng đời ứng dụng Servlet.
 *
 * Mục đích:
 *   - contextInitialized: kiểm tra kết nối MySQL khi app khởi động
 *   - contextDestroyed  : log khi app shutdown (DriverManager không cần đóng pool)
 */
@WebListener
public class AppContextListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        // Test kết nối MySQL ngay khi khởi động để phát hiện lỗi sớm
        try {
            DatabaseConfig.getConnection().close();
            sce.getServletContext().log("[App] Kết nối MySQL thành công.");
        } catch (Exception e) {
            sce.getServletContext().log("[App] CẢNH BÁO: Không thể kết nối MySQL: " + e.getMessage());
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        DatabaseConfig.closePool(); // no-op với DriverManager
        sce.getServletContext().log("[App] Ứng dụng đã dừng.");
    }
}