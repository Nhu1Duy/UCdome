<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Đăng nhập hệ thống</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/login.css">
</head>
<body>
<div class="login-wrapper">
    <div class="login-card">
        <div class="login-header">
            <h1>🌿 NLU System</h1>
            <p>Đăng nhập vào hệ thống</p>
        </div>

        <%-- ─── Thông báo lỗi chung ──────────────────────────── --%>
        <% if (request.getAttribute("error") != null) { %>
            <div class="alert alert-error">
                ⚠️ <%= request.getAttribute("error") %>
            </div>
        <% } %>

        <%-- ═══ BASIC FLOW — Form Email / Mật khẩu ═══════════════ --%>
        <%-- 1.0.1 — Hệ thống hiển thị form đăng nhập              --%>
        <form action="${pageContext.request.contextPath}/login"
              method="post" novalidate id="localForm">
            <input type="hidden" name="loginType" value="local">

            <%-- 1.0.2 — Actor nhập Email --%>
            <div class="form-group">
                <label for="email">Email</label>
                <input type="email" id="email" name="email"
                       placeholder="vd: user@nlu.edu.vn"
                       value="<%= request.getParameter("email") != null
                                  ? request.getParameter("email") : "" %>"
                       class="<%= request.getAttribute("emailError") != null ? "input-error" : "" %>">
                <%-- 1.0-E1 — Hiển thị lỗi ngay tại trường email --%>
                <% if (request.getAttribute("emailError") != null) { %>
                    <span class="field-error"><%= request.getAttribute("emailError") %></span>
                <% } %>
            </div>

            <%-- 1.0.2 — Actor nhập Mật khẩu --%>
            <div class="form-group">
                <label for="password">Mật khẩu</label>
                <input type="password" id="password" name="password"
                       placeholder="Nhập mật khẩu"
                       class="<%= request.getAttribute("passwordError") != null ? "input-error" : "" %>">
                <%-- 1.0-E1 — Hiển thị lỗi ngay tại trường mật khẩu --%>
                <% if (request.getAttribute("passwordError") != null) { %>
                    <span class="field-error"><%= request.getAttribute("passwordError") %></span>
                <% } %>
            </div>

            <%-- 1.0.2 — Nhấn nút Đăng nhập → POST /login --%>
            <button type="submit" class="btn btn-primary">Đăng nhập</button>
        </form>

        <div class="divider"><span>hoặc</span></div>

        <%-- ═══ UC1.1 — Nút Đăng nhập bằng Google ════════════════ --%>
        <%-- 1.1.1 — Actor chọn "Đăng nhập bằng Google"            --%>
        <button class="btn btn-google" onclick="handleGoogleLogin()">
            <svg width="18" height="18" viewBox="0 0 24 24">
                <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
                <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
                <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/>
                <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/>
            </svg>
            Đăng nhập bằng Google
        </button>

        <div class="demo-info">
            <strong>Tài khoản demo:</strong><br>
            Admin: <code>admin@nlu.edu.vn</code> / <code>admin123</code><br>
            User:  <code>user@nlu.edu.vn</code>  / <code>user123</code><br>
            Locked: <code>locked@nlu.edu.vn</code> / <code>admin123</code>
        </div>
    </div>
</div>

<%-- ─── Google Identity Services (GSI) Script ───────────────── --%>
<script>
    function handleGoogleLogin() {
        const clientId = '551138929631-6rbmqqdg881t3bfaiplf8k296t3sku9b.apps.googleusercontent.com';
        const redirectUri = 'http://localhost:8080/login_app/login';
        const scope = 'openid email profile';
        const state = Math.random().toString(36).substring(2); // chống CSRF

        window.location.href =
            'https://accounts.google.com/o/oauth2/v2/auth' +
            '?client_id=' + clientId +
            '&redirect_uri=' + encodeURIComponent(redirectUri) +
            '&response_type=code' +
            '&scope=' + encodeURIComponent(scope) +
            '&state=' + state;
    }
</script>
</body>
</html>
