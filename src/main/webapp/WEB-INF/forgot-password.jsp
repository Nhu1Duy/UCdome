<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Quên mật khẩu — NLU System</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/login.css">
</head>
<body>
<div class="login-wrapper">
    <div class="login-card">

        <%-- ─── Header ──────────────────────────────────────────────── --%>
        <div class="login-header">
            <h1>🌿 NLU System</h1>
            <p>Khôi phục mật khẩu</p>
        </div>

        <%-- ─── Thông báo lỗi chung (3.0-E3, 3.0-E4, 3.0-EDB) ──────── --%>
        <% if (request.getAttribute("error") != null) { %>
            <div class="alert alert-error">
                ⚠️ <%= request.getAttribute("error") %>
            </div>
        <% } %>

        <%-- ═══ 3.0.1 — Form yêu cầu nhập Email ════════════════════ --%>
        <%-- Trigger: Actor nhấn "Quên mật khẩu" ở trang đăng nhập  --%>
        <form action="${pageContext.request.contextPath}/forgot-password"
              method="post" novalidate>

            <p style="color:#6b7280;font-size:.9rem;margin-bottom:16px">
                Nhập địa chỉ email đã đăng ký. Hệ thống sẽ gửi mã xác thực đến email của bạn.
            </p>

            <%-- 3.0.2 — Trường nhập Email ─────────────────────────── --%>
            <div class="form-group">
                <label for="email">Địa chỉ Email</label>
                <input type="email" id="email" name="email"
                       placeholder="vd: user@nlu.edu.vn"
                       value="<%= request.getAttribute("emailValue") != null
                                  ? request.getAttribute("emailValue") : "" %>"
                       class="<%= request.getAttribute("emailError") != null ? "input-error" : "" %>">

                <%-- 3.0-E1 — Lỗi validate định dạng email ─────────── --%>
                <% if (request.getAttribute("emailError") != null) { %>
                    <span class="field-error"><%= request.getAttribute("emailError") %></span>
                <% } %>
            </div>

            <%-- 3.0.2 — Nút "Gửi mã xác thực" ─────────────────────── --%>
            <button type="submit" class="btn btn-primary">Gửi mã xác thực</button>
        </form>

        <%-- Thông báo trung tính sau khi submit (kể cả khi email không tồn tại) ─ --%>
        <%-- Được hiển thị thay vì trang này nếu không có lỗi → xem ForgotPasswordServlet --%>

        <div style="text-align:center;margin-top:20px">
            <a href="${pageContext.request.contextPath}/login"
               style="color:#1a7a4a;font-size:.9rem;text-decoration:none">
                ← Quay lại đăng nhập
            </a>
        </div>

    </div>
</div>
</body>
</html>
