<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <title>Trang chủ</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/login.css">
</head>
<body>
<div class="login-wrapper">
    <div class="login-card">
        <div class="login-header">
            <h1>✅ Đăng nhập thành công</h1>
            <h1>✅ Đây là trang bán hàng</h1>
        </div>
        <p style="text-align:center">
            Xin chào, <strong><%= session.getAttribute("fullName") %></strong>
        </p>
        <p style="text-align:center;color:#666">
            Email: <%= session.getAttribute("userEmail") %><br>
            Vai trò: <strong><%= session.getAttribute("userRole") %></strong>
        </p>
        <a href="${pageContext.request.contextPath}/logout"
           class="btn btn-primary" style="display:block;text-align:center;text-decoration:none">
            Đăng xuất
        </a>
    </div>
</div>
</body>
</html>
