<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ page import="com.example.uc.model.User" %>
<%@ page import="java.util.List" %>
<%--
  user-management.jsp — Giao diện UC-08 Quản lý tài khoản
  Attributes từ Servlet:
    users        List<User>  — danh sách hiện tại (8.0.2 / 8.5.4)
    totalPages   int         — tổng trang (NFR-02)
    currentPage  int         — trang hiện tại
    totalUsers   int         — tổng số bản ghi
    keyword      String      — từ khoá tìm kiếm (8.5, nullable)
    flashSuccess String      — flash thành công (8.1.6 / 8.2.7 / 8.3.7 / 8.4.8)
    flashError   String      — flash lỗi nghiệp vụ
    dbError      String      — lỗi kết nối CSDL (8.0-E1)
--%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Quản lý tài khoản — Admin</title>
    <style>
        *, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
        body { font-family: 'Segoe UI', sans-serif; background: #f0f2f5; color: #333; }

        /* ── Layout ── */
        .page-header { background: #1e3a5f; color: #fff; padding: 16px 28px;
            display: flex; align-items: center; gap: 16px; }
        .page-header h1 { font-size: 20px; font-weight: 600; }
        .page-header .back-btn { color: #aad4f5; text-decoration: none; font-size: 13px; }
        .page-header .back-btn:hover { color: #fff; }

        .container { max-width: 1200px; margin: 0 auto; padding: 24px 20px; }

        /* ── Flash messages ── */
        .alert { padding: 12px 16px; border-radius: 6px; margin-bottom: 16px;
            font-size: 14px; display: flex; align-items: center; gap: 8px; }
        .alert-success { background: #d1fadf; color: #166534; border: 1px solid #bbf7d0; }
        .alert-error   { background: #fee2e2; color: #991b1b; border: 1px solid #fecaca; }
        .alert-db      { background: #fef3c7; color: #92400e; border: 1px solid #fde68a; }

        /* ── Toolbar ── */
        .toolbar { display: flex; gap: 12px; align-items: center; margin-bottom: 16px; flex-wrap: wrap; }
        .toolbar .search-box { flex: 1; min-width: 200px; display: flex; gap: 0; }
        .toolbar .search-box input {
            flex: 1; padding: 9px 14px; border: 1px solid #d1d5db; border-right: none;
            border-radius: 6px 0 0 6px; font-size: 14px; outline: none; }
        .toolbar .search-box input:focus { border-color: #3b82f6; }
        .toolbar .search-box button {
            padding: 9px 16px; background: #3b82f6; color: #fff; border: none;
            border-radius: 0 6px 6px 0; cursor: pointer; font-size: 14px; }
        .toolbar .search-box button:hover { background: #2563eb; }
        .btn { padding: 9px 18px; border-radius: 6px; border: none; cursor: pointer;
            font-size: 14px; font-weight: 500; transition: opacity .15s; }
        .btn:hover { opacity: .85; }
        .btn-primary { background: #3b82f6; color: #fff; }
        .btn-success { background: #22c55e; color: #fff; }
        .btn-warning { background: #f59e0b; color: #fff; }
        .btn-danger  { background: #ef4444; color: #fff; }
        .btn-sm { padding: 5px 12px; font-size: 12px; }
        .btn-outline { background: #fff; border: 1px solid #d1d5db; color: #374151; }

        /* ── Table ── */
        .card { background: #fff; border-radius: 10px; box-shadow: 0 1px 4px rgba(0,0,0,.08); overflow: hidden; }
        table { width: 100%; border-collapse: collapse; font-size: 14px; }
        thead { background: #f8fafc; }
        th { padding: 11px 14px; text-align: left; font-weight: 600; color: #4b5563;
            border-bottom: 2px solid #e5e7eb; white-space: nowrap; }
        td { padding: 11px 14px; border-bottom: 1px solid #f3f4f6; vertical-align: middle; }
        tr:last-child td { border-bottom: none; }
        tr:hover td { background: #f9fafb; }

        /* ── Badges ── */
        .badge { display: inline-block; padding: 2px 10px; border-radius: 20px;
            font-size: 12px; font-weight: 600; }
        .badge-active  { background: #dcfce7; color: #166534; }
        .badge-locked  { background: #fee2e2; color: #991b1b; }
        .badge-admin   { background: #ede9fe; color: #6d28d9; }
        .badge-user    { background: #e0f2fe; color: #0369a1; }
        .badge-google  { background: #fef9c3; color: #854d0e; }

        .actions-col { display: flex; gap: 6px; flex-wrap: wrap; }

        /* ── Pagination ── */
        .pagination { display: flex; gap: 6px; align-items: center;
            justify-content: center; padding: 16px; }
        .pagination a, .pagination span {
            padding: 6px 12px; border-radius: 6px; font-size: 13px;
            text-decoration: none; border: 1px solid #e5e7eb; color: #374151; }
        .pagination a:hover { background: #f3f4f6; }
        .pagination .active { background: #3b82f6; color: #fff; border-color: #3b82f6; }
        .pagination .disabled { color: #9ca3af; pointer-events: none; }

        /* ── Stat bar ── */
        .stat-bar { display: flex; gap: 16px; margin-bottom: 16px; flex-wrap: wrap; }
        .stat-card { background: #fff; border-radius: 8px; padding: 12px 20px;
            box-shadow: 0 1px 3px rgba(0,0,0,.07); flex: 1; min-width: 140px; }
        .stat-card .val { font-size: 24px; font-weight: 700; color: #1e3a5f; }
        .stat-card .lbl { font-size: 12px; color: #6b7280; margin-top: 2px; }

        .empty-state { text-align: center; padding: 48px; color: #9ca3af; }
        .empty-state svg { width: 48px; height: 48px; margin-bottom: 12px; opacity: .4; }

        /* ── Modal overlay ── */
        .modal-overlay {
            display: none; position: fixed; inset: 0;
            background: rgba(0,0,0,.5); z-index: 1000;
            align-items: center; justify-content: center; }
        .modal-overlay.open { display: flex; }
        .modal { background: #fff; border-radius: 12px; width: 100%; max-width: 540px;
            max-height: 90vh; overflow-y: auto;
            box-shadow: 0 20px 60px rgba(0,0,0,.2); }
        .modal-header { padding: 18px 22px; border-bottom: 1px solid #e5e7eb;
            display: flex; align-items: center; justify-content: space-between; }
        .modal-header h2 { font-size: 18px; font-weight: 600; }
        .modal-close { background: none; border: none; font-size: 22px;
            color: #6b7280; cursor: pointer; line-height: 1; }
        .modal-body { padding: 22px; }
        .modal-footer { padding: 14px 22px; border-top: 1px solid #e5e7eb;
            display: flex; gap: 10px; justify-content: flex-end; }

        /* ── Form fields ── */
        .form-row { display: grid; grid-template-columns: 1fr 1fr; gap: 14px; }
        .form-row.full { grid-template-columns: 1fr; }
        .form-group { margin-bottom: 14px; }
        .form-group label { display: block; font-size: 13px; font-weight: 500;
            color: #374151; margin-bottom: 5px; }
        .form-group input, .form-group select {
            width: 100%; padding: 9px 12px; border: 1px solid #d1d5db;
            border-radius: 6px; font-size: 14px; outline: none; }
        .form-group input:focus, .form-group select:focus { border-color: #3b82f6; }
        .form-group .hint { font-size: 11px; color: #9ca3af; margin-top: 3px; }

        /* ── Confirm modal ── */
        .confirm-icon { font-size: 40px; text-align: center; margin-bottom: 10px; }
        .confirm-info { background: #f8fafc; border-radius: 8px; padding: 12px 16px;
            margin: 12px 0; font-size: 14px; }
        .confirm-info strong { color: #1e3a5f; }
    </style>
</head>
<body>

<!-- ── Page header ── -->
<div class="page-header">
    <a href="${pageContext.request.contextPath}/admin/dashboard" class="back-btn">← Dashboard</a>
    <h1>🧑‍💼 Quản lý tài khoản người dùng</h1>
</div>

<div class="container">

    <%-- ── Flash messages ── --%>
    <% if (request.getAttribute("flashSuccess") != null) { %>
    <div class="alert alert-success">✅ <%= request.getAttribute("flashSuccess") %></div>
    <% } %>
    <% if (request.getAttribute("flashError") != null) { %>
    <div class="alert alert-error">❌ <%= request.getAttribute("flashError") %></div>
    <% } %>
    <% if (request.getAttribute("dbError") != null) { %>
    <%-- 8.0-E1: CSDL không kết nối được --%>
    <div class="alert alert-db">⚠️ <%= request.getAttribute("dbError") %></div>
    <% } %>

    <%-- ── Stat bar ── --%>
    <div class="stat-bar">
        <div class="stat-card">
            <div class="val"><%= request.getAttribute("totalUsers") != null ? request.getAttribute("totalUsers") : "—" %></div>
            <div class="lbl">Tổng người dùng</div>
        </div>
    </div>

    <%-- ── Toolbar: tìm kiếm + thêm mới ── --%>
    <%-- 8.5.1: ô tìm kiếm; 8.5.2: submit khi nhấn Enter hoặc nút tìm --%>
    <div class="toolbar">
        <form method="get" action="${pageContext.request.contextPath}/admin/users"
              class="search-box" id="searchForm">
            <input type="text" name="q" id="searchInput" placeholder="Tìm theo tên, email, username, SĐT…"
                   value="<%= request.getAttribute("keyword") != null ? request.getAttribute("keyword") : "" %>"
                   autocomplete="off">
            <button type="submit">🔍 Tìm</button>
        </form>
        <%-- Nút thêm mới — mở modal UC8.1 --%>
        <button class="btn btn-success" onclick="openCreateModal()">+ Thêm người dùng</button>
    </div>

    <%-- ── Bảng danh sách user (8.0.2 / 8.5.4) ── --%>
    <div class="card">
        <%
            @SuppressWarnings("unchecked")
            List<User> users = (List<User>) request.getAttribute("users");
            String keyword = (String) request.getAttribute("keyword");
            boolean hasKeyword = keyword != null && !keyword.isBlank();
        %>

        <% if (users == null || users.isEmpty()) { %>
        <div class="empty-state">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                <path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"/>
                <circle cx="9" cy="7" r="4"/>
                <path d="M23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/>
            </svg>
            <p>
                <% if (hasKeyword) { %>
                <%-- 8.5-A1: Không tìm thấy kết quả --%>
                Không tìm thấy người dùng phù hợp với "<strong><%= keyword %></strong>"
                <% } else { %>
                Chưa có người dùng nào trong hệ thống.
                <% } %>
            </p>
            <% if (hasKeyword) { %>
            <a href="${pageContext.request.contextPath}/admin/users"
               style="display:inline-block;margin-top:12px;color:#3b82f6;font-size:13px">
                ✕ Xoá bộ lọc, hiển thị tất cả
            </a>
            <% } %>
        </div>
        <% } else { %>
        <table>
            <thead>
            <tr>
                <th>#</th>
                <th>Họ và tên</th>
                <th>Username</th>
                <th>Email</th>
                <th>SĐT</th>
                <th>Vai trò</th>
                <th>Trạng thái</th>
                <th>Provider</th>
                <th>Ngày tạo</th>
                <th>Thao tác</th>
            </tr>
            </thead>
            <tbody>
            <% for (User u : users) { %>
            <tr>
                <td><%= u.getId() %></td>
                <td><strong><%= u.getFullName() != null ? u.getFullName() : "—" %></strong></td>
                <td><%= u.getUsername() != null ? u.getUsername() : "—" %></td>
                <td><%= u.getEmail() %></td>
                <td><%= u.getPhone() != null ? u.getPhone() : "—" %></td>
                <td>
                    <% if (u.getRole() == User.Role.ADMIN) { %>
                    <span class="badge badge-admin">ADMIN</span>
                    <% } else { %>
                    <span class="badge badge-user">USER</span>
                    <% } %>
                </td>
                <td>
                    <%-- 8.4.8: hiển thị trạng thái --%>
                    <% if (u.getStatus() == User.Status.LOCKED) { %>
                    <span class="badge badge-locked">Đã khóa</span>
                    <% } else { %>
                    <span class="badge badge-active">Hoạt động</span>
                    <% } %>
                </td>
                <td>
                    <% if (u.getAuthProvider() == User.AuthProvider.GOOGLE) { %>
                    <span class="badge badge-google">Google</span>
                    <% } else { %>
                    <span style="font-size:12px;color:#6b7280">Local</span>
                    <% } %>
                </td>
                <td style="font-size:12px;color:#6b7280">
                    <%= u.getCreatedAt() != null ? u.getCreatedAt().toLocalDate().toString() : "—" %>
                </td>
                <td>
                    <div class="actions-col">
                        <%-- 8.2.1: nút cập nhật --%>
                        <button class="btn btn-primary btn-sm"
                                onclick="openEditModal(<%= u.getId() %>, '<%= esc(u.getUsername()) %>',
                                        '<%= esc(u.getEmail()) %>', '<%= esc(u.getFullName()) %>',
                                        '<%= u.getPhone() != null ? esc(u.getPhone()) : "" %>',
                                        '<%= u.getRole() != null ? u.getRole().name() : "USER" %>',
                                        '<%= u.getStatus() != null ? u.getStatus().name() : "ACTIVE" %>',
                                        '<%= u.getDateOfBirth() != null ? u.getDateOfBirth().toString() : "" %>',
                                        '<%= u.getGender() != null ? u.getGender().name() : "" %>',
                                        '<%= esc(u.getAddressLine()) %>', '<%= esc(u.getCity()) %>',
                                        '<%= esc(u.getProvince()) %>', '<%= esc(u.getCountry()) %>')">
                            ✏️ Sửa
                        </button>

                        <%-- 8.4.1: nút khoá / mở khoá — 8.4-A2: label đổi theo trạng thái --%>
                        <button class="btn btn-sm <%= u.getStatus() == User.Status.LOCKED ? "btn-success" : "btn-warning" %>"
                                onclick="confirmToggleLock(<%= u.getId() %>, '<%= esc(u.getFullName()) %>',
                                        '<%= esc(u.getEmail()) %>',
                                    <%= u.getStatus() == User.Status.LOCKED %>)">
                            <%= u.getStatus() == User.Status.LOCKED ? "🔓 Mở khóa" : "🔒 Khóa" %>
                        </button>

                        <%-- 8.3.1: nút xoá — gọi dialog xác nhận NFR-03 --%>
                        <button class="btn btn-danger btn-sm"
                                onclick="confirmDelete(<%= u.getId() %>, '<%= esc(u.getFullName()) %>',
                                        '<%= esc(u.getUsername()) %>', '<%= esc(u.getEmail()) %>')">
                            🗑 Xoá
                        </button>
                    </div>
                </td>
            </tr>
            <% } %>
            </tbody>
        </table>

        <%-- ── Phân trang (NFR-02) ── --%>
        <%
            int totalPages  = request.getAttribute("totalPages")  != null ? (int) request.getAttribute("totalPages")  : 1;
            int currentPage = request.getAttribute("currentPage") != null ? (int) request.getAttribute("currentPage") : 1;
            String qParam   = hasKeyword ? "&q=" + java.net.URLEncoder.encode(keyword, "UTF-8") : "";
        %>
        <% if (totalPages > 1) { %>
        <div class="pagination">
            <% if (currentPage > 1) { %>
            <a href="?page=<%= currentPage - 1 %><%= qParam %>">‹ Trước</a>
            <% } else { %>
            <span class="disabled">‹ Trước</span>
            <% } %>

            <% for (int i = 1; i <= totalPages; i++) { %>
            <% if (i == currentPage) { %>
            <span class="active"><%= i %></span>
            <% } else { %>
            <a href="?page=<%= i %><%= qParam %>"><%= i %></a>
            <% } %>
            <% } %>

            <% if (currentPage < totalPages) { %>
            <a href="?page=<%= currentPage + 1 %><%= qParam %>">Sau ›</a>
            <% } else { %>
            <span class="disabled">Sau ›</span>
            <% } %>
        </div>
        <% } %>

        <% } /* end if users not empty */ %>
    </div>
</div>

<!-- ══════════════════════════════════════════════════════════════
     MODAL: THÊM NGƯỜI DÙNG (UC8.1)
     8.1.2: hệ thống hiển thị dialog nhập thông tin
════════════════════════════════════════════════════════════════ -->
<div class="modal-overlay" id="createModal">
    <div class="modal">
        <div class="modal-header">
            <h2>➕ Thêm người dùng mới</h2>
            <button class="modal-close" onclick="closeModal('createModal')">×</button>
        </div>
        <form method="post" action="${pageContext.request.contextPath}/admin/users" id="createForm">
            <input type="hidden" name="action" value="create">
            <div class="modal-body">
                <div class="form-row">
                    <div class="form-group">
                        <label>Họ và tên <span style="color:red">*</span></label>
                        <input type="text" name="fullName" placeholder="Nguyễn Văn A" required>
                    </div>
                    <div class="form-group">
                        <label>Username</label>
                        <input type="text" name="username" placeholder="nguyenvana">
                        <div class="hint">BR-03: phải duy nhất trong hệ thống</div>
                    </div>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label>Email <span style="color:red">*</span></label>
                        <input type="email" name="email" placeholder="user@example.com" required>
                    </div>
                    <div class="form-group">
                        <label>Số điện thoại</label>
                        <input type="text" name="phone" placeholder="0901234567">
                    </div>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label>Mật khẩu <span style="color:red">*</span></label>
                        <input type="password" name="password" placeholder="Tối thiểu 6 ký tự" required>
                        <div class="hint">BR-04: sẽ được mã hoá trước khi lưu</div>
                    </div>
                    <div class="form-group">
                        <label>Ngày sinh</label>
                        <input type="date" name="dateOfBirth">
                    </div>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label>Vai trò</label>
                        <select name="role">
                            <option value="USER" selected>USER</option>
                            <option value="ADMIN">ADMIN</option>
                        </select>
                    </div>
                    <div class="form-group">
                        <label>Giới tính</label>
                        <select name="gender">
                            <option value="">— Không chọn —</option>
                            <option value="MALE">Nam</option>
                            <option value="FEMALE">Nữ</option>
                            <option value="OTHER">Khác</option>
                        </select>
                    </div>
                </div>
                <div class="form-row full">
                    <div class="form-group">
                        <label>Địa chỉ</label>
                        <input type="text" name="addressLine" placeholder="Số nhà, đường…">
                    </div>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label>Thành phố</label>
                        <input type="text" name="city">
                    </div>
                    <div class="form-group">
                        <label>Tỉnh / Thành</label>
                        <input type="text" name="province">
                    </div>
                </div>
            </div>
            <div class="modal-footer">
                <%-- 8.1-A1: Huỷ → đóng modal, không lưu --%>
                <button type="button" class="btn btn-outline" onclick="closeModal('createModal')">Huỷ</button>
                <%-- 8.1.3: nút Thêm → submit form --%>
                <button type="submit" class="btn btn-success">✔ Thêm người dùng</button>
            </div>
        </form>
    </div>
</div>

<!-- ══════════════════════════════════════════════════════════════
     MODAL: CẬP NHẬT NGƯỜI DÙNG (UC8.2)
     8.2.2: thông tin hiện tại được điền sẵn vào dialog
════════════════════════════════════════════════════════════════ -->
<div class="modal-overlay" id="editModal">
    <div class="modal">
        <div class="modal-header">
            <h2>✏️ Cập nhật thông tin người dùng</h2>
            <button class="modal-close" onclick="closeModal('editModal')">×</button>
        </div>
        <form method="post" action="${pageContext.request.contextPath}/admin/users" id="editForm">
            <input type="hidden" name="action" value="update">
            <input type="hidden" name="userId" id="edit_userId">
            <div class="modal-body">
                <div class="form-row">
                    <div class="form-group">
                        <label>Họ và tên <span style="color:red">*</span></label>
                        <input type="text" name="fullName" id="edit_fullName" required>
                    </div>
                    <div class="form-group">
                        <label>Username</label>
                        <input type="text" name="username" id="edit_username">
                    </div>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label>Email <span style="color:red">*</span></label>
                        <input type="email" name="email" id="edit_email" required>
                    </div>
                    <div class="form-group">
                        <label>Số điện thoại</label>
                        <input type="text" name="phone" id="edit_phone">
                    </div>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label>Ngày sinh</label>
                        <input type="date" name="dateOfBirth" id="edit_dob">
                    </div>
                    <div class="form-group">
                        <label>Giới tính</label>
                        <select name="gender" id="edit_gender">
                            <option value="">— Không chọn —</option>
                            <option value="MALE">Nam</option>
                            <option value="FEMALE">Nữ</option>
                            <option value="OTHER">Khác</option>
                        </select>
                    </div>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label>Vai trò</label>
                        <select name="role" id="edit_role">
                            <option value="USER">USER</option>
                            <option value="ADMIN">ADMIN</option>
                        </select>
                    </div>
                    <div class="form-group">
                        <label>Trạng thái</label>
                        <select name="status" id="edit_status">
                            <option value="ACTIVE">Hoạt động</option>
                            <option value="LOCKED">Đã khóa</option>
                        </select>
                    </div>
                </div>
                <div class="form-row full">
                    <div class="form-group">
                        <label>Địa chỉ</label>
                        <input type="text" name="addressLine" id="edit_addressLine">
                    </div>
                </div>
                <div class="form-row">
                    <div class="form-group">
                        <label>Thành phố</label>
                        <input type="text" name="city" id="edit_city">
                    </div>
                    <div class="form-group">
                        <label>Tỉnh / Thành</label>
                        <input type="text" name="province" id="edit_province">
                    </div>
                </div>
            </div>
            <div class="modal-footer">
                <%-- 8.2-A1: Huỷ → đóng modal --%>
                <button type="button" class="btn btn-outline" onclick="closeModal('editModal')">Huỷ</button>
                <%-- 8.2.3: nút Lưu → submit form --%>
                <button type="submit" class="btn btn-primary">💾 Lưu thay đổi</button>
            </div>
        </form>
    </div>
</div>

<!-- ══════════════════════════════════════════════════════════════
     MODAL: XÁC NHẬN XOÁ (UC8.3 soft-delete)
     NFR-03: hiển thị rõ thông tin User trước khi xoá
════════════════════════════════════════════════════════════════ -->
<div class="modal-overlay" id="deleteModal">
    <div class="modal" style="max-width:420px">
        <div class="modal-header">
            <h2>🗑 Xoá người dùng</h2>
            <button class="modal-close" onclick="closeModal('deleteModal')">×</button>
        </div>
        <form method="post" action="${pageContext.request.contextPath}/admin/users">
            <input type="hidden" name="action" value="delete">
            <input type="hidden" name="userId" id="del_userId">
            <div class="modal-body">
                <div class="confirm-icon">⚠️</div>
                <%-- 8.3.2: dialog xác nhận với thông tin User --%>
                <p style="text-align:center;color:#374151">Bạn có chắc muốn xoá người dùng này?</p>
                <div class="confirm-info">
                    <div>Tên: <strong id="del_name">—</strong></div>
                    <div>Username: <strong id="del_username">—</strong></div>
                    <div>Email: <strong id="del_email">—</strong></div>
                </div>
                <p style="font-size:12px;color:#9ca3af;text-align:center;margin-top:8px">
                    Dữ liệu sẽ được ẩn (soft-delete) và không xoá khỏi CSDL.
                </p>
            </div>
            <div class="modal-footer">
                <%-- 8.3-A1: Huỷ → dialog đóng --%>
                <button type="button" class="btn btn-outline" onclick="closeModal('deleteModal')">Huỷ</button>
                <%-- 8.3.3: Xác nhận xoá --%>
                <button type="submit" class="btn btn-danger">🗑 Xác nhận Xoá</button>
            </div>
        </form>
    </div>
</div>

<!-- ══════════════════════════════════════════════════════════════
     MODAL: XÁC NHẬN KHÓA / MỞ KHÓA (UC8.4)
     NFR-03: hiển thị rõ thông tin User và hành động
════════════════════════════════════════════════════════════════ -->
<div class="modal-overlay" id="lockModal">
    <div class="modal" style="max-width:420px">
        <div class="modal-header">
            <h2 id="lock_title">🔒 Khóa tài khoản</h2>
            <button class="modal-close" onclick="closeModal('lockModal')">×</button>
        </div>
        <form method="post" action="${pageContext.request.contextPath}/admin/users">
            <input type="hidden" name="action" value="toggleLock">
            <input type="hidden" name="userId" id="lock_userId">
            <div class="modal-body">
                <div class="confirm-icon" id="lock_icon">🔒</div>
                <%-- 8.4.2: dialog xác nhận NFR-03 --%>
                <p style="text-align:center;color:#374151" id="lock_question">
                    Bạn có chắc muốn khóa tài khoản này?
                </p>
                <div class="confirm-info">
                    <div>Tên: <strong id="lock_name">—</strong></div>
                    <div>Email: <strong id="lock_email">—</strong></div>
                </div>
                <p style="font-size:12px;color:#9ca3af;text-align:center;margin-top:8px"
                   id="lock_note">Người dùng sẽ không thể đăng nhập cho đến khi được mở khóa.</p>
            </div>
            <div class="modal-footer">
                <%-- 8.4-A1: Huỷ → đóng --%>
                <button type="button" class="btn btn-outline" onclick="closeModal('lockModal')">Huỷ</button>
                <%-- 8.4.3: Xác nhận --%>
                <button type="submit" class="btn" id="lock_submitBtn">🔒 Xác nhận Khóa</button>
            </div>
        </form>
    </div>
</div>

<script>
    // ── Modal helpers ──────────────────────────────────────────────
    function openModal(id) { document.getElementById(id).classList.add('open'); }
    function closeModal(id) { document.getElementById(id).classList.remove('open'); }

    // Đóng modal khi click ra ngoài
    document.querySelectorAll('.modal-overlay').forEach(overlay => {
        overlay.addEventListener('click', e => {
            if (e.target === overlay) overlay.classList.remove('open');
        });
    });

    // ── UC8.1: Mở modal thêm mới ──────────────────────────────────
    function openCreateModal() {
        document.getElementById('createForm').reset();
        openModal('createModal');
    }

    // ── UC8.2: Mở modal cập nhật, điền sẵn thông tin (8.2.2) ─────
    function openEditModal(id, username, email, fullName, phone,
                           role, status, dob, gender,
                           addressLine, city, province, country) {
        document.getElementById('edit_userId').value    = id;
        document.getElementById('edit_username').value  = username;
        document.getElementById('edit_email').value     = email;
        document.getElementById('edit_fullName').value  = fullName;
        document.getElementById('edit_phone').value     = phone;
        document.getElementById('edit_dob').value       = dob;
        document.getElementById('edit_addressLine').value = addressLine;
        document.getElementById('edit_city').value      = city;
        document.getElementById('edit_province').value  = province;

        // Select role
        let roleEl = document.getElementById('edit_role');
        for (let o of roleEl.options) o.selected = (o.value === role);

        // Select status
        let statusEl = document.getElementById('edit_status');
        for (let o of statusEl.options) o.selected = (o.value === status);

        // Select gender
        let genderEl = document.getElementById('edit_gender');
        for (let o of genderEl.options) o.selected = (o.value === gender);

        openModal('editModal');
    }

    // ── UC8.3: Mở modal xác nhận xoá (8.3.2) ────────────────────
    // NFR-03: hiển thị tên, username, email trước khi xoá
    function confirmDelete(userId, fullName, username, email) {
        document.getElementById('del_userId').value   = userId;
        document.getElementById('del_name').textContent     = fullName || '—';
        document.getElementById('del_username').textContent = username || '—';
        document.getElementById('del_email').textContent    = email;
        openModal('deleteModal');
    }

    // ── UC8.4: Mở modal xác nhận khóa / mở khóa (8.4.2 + 8.4-A2)
    // isLocked = true  → đang bị khoá → hành động: MỞ KHOÁ
    // isLocked = false → đang hoạt động → hành động: KHOÁ
    function confirmToggleLock(userId, fullName, email, isLocked) {
        document.getElementById('lock_userId').value        = userId;
        document.getElementById('lock_name').textContent   = fullName || '—';
        document.getElementById('lock_email').textContent  = email;

        if (isLocked) {
            // 8.4-A2: user đang bị khoá → nút đổi thành "Mở khóa"
            document.getElementById('lock_title').textContent    = '🔓 Mở khóa tài khoản';
            document.getElementById('lock_icon').textContent     = '🔓';
            document.getElementById('lock_question').textContent = 'Bạn có chắc muốn mở khóa tài khoản này?';
            document.getElementById('lock_note').textContent     = 'Người dùng sẽ có thể đăng nhập trở lại.';
            let btn = document.getElementById('lock_submitBtn');
            btn.textContent = '🔓 Xác nhận Mở khóa';
            btn.className   = 'btn btn-success';
        } else {
            document.getElementById('lock_title').textContent    = '🔒 Khóa tài khoản';
            document.getElementById('lock_icon').textContent     = '🔒';
            document.getElementById('lock_question').textContent = 'Bạn có chắc muốn khóa tài khoản này?';
            document.getElementById('lock_note').textContent     = 'Người dùng sẽ không thể đăng nhập cho đến khi được mở khóa.';
            let btn = document.getElementById('lock_submitBtn');
            btn.textContent = '🔒 Xác nhận Khóa';
            btn.className   = 'btn btn-warning';
        }

        openModal('lockModal');
    }

    // ── 8.5: Auto-clear tìm kiếm khi xoá hết nội dung (8.5-A2) ──
    document.getElementById('searchInput').addEventListener('keydown', function(e) {
        if (e.key === 'Escape') {
            this.value = '';
            // 8.5-A2: xoá từ khoá → submit form để hiển thị lại toàn bộ
            document.getElementById('searchForm').submit();
        }
    });
</script>

<%!
    /** Helper thoát ký tự đặc biệt cho attribute onclick trong JSP. */
    private String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
%>

</body>
</html>
