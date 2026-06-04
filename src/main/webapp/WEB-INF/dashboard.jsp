<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <title>Admin Dashboard</title>
    <link rel="stylesheet"
          href="${pageContext.request.contextPath}/css/login.css">
</head>
<body>

<div class="login-wrapper">
    <div class="login-card">

        <div class="login-header">
            <h1>🛠 ADMIN DASHBOARD</h1>
            <p>Trang quản trị hệ thống</p>
        </div>

        <p style="text-align:center">
            Xin chào,
            <strong>
                <%= session.getAttribute("fullName") %>
            </strong>
        </p>

        <p style="text-align:center;color:#666">
            Email:
            <%= session.getAttribute("userEmail") %>
            <br>
            Vai trò:
            <strong>
                <%= session.getAttribute("userRole") %>
            </strong>
        </p>

        <hr>

        <div style="display:flex;flex-direction:column;gap:10px">

            <a href="${pageContext.request.contextPath}/admin/users"
               class="btn btn-primary"
               style="text-decoration:none;text-align:center">
                Quản lý người dùng
            </a>

            <a href="#"
               class="btn btn-primary"
               style="text-decoration:none;text-align:center">
                Quản lý sản phẩm
            </a>

            <a href="#"
               class="btn btn-primary"
               style="text-decoration:none;text-align:center">
                Xem báo cáo
            </a>

            <a href="${pageContext.request.contextPath}/logout"
               class="btn btn-primary"
               style="text-decoration:none;text-align:center">
                Đăng xuất
            </a>

        </div>

    </div>
</div>

</body>
</html>