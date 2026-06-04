<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Đặt lại mật khẩu — NLU System</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/login.css">
    <style>
        .password-strength {
            height: 4px;
            border-radius: 2px;
            background: #e5e7eb;
            margin-top: 6px;
            overflow: hidden;
        }
        .password-strength-bar {
            height: 100%;
            border-radius: 2px;
            width: 0;
            transition: width .3s, background .3s;
        }
        .strength-weak   { background: #dc2626; width: 33%; }
        .strength-medium { background: #f59e0b; width: 66%; }
        .strength-strong { background: #16a34a; width: 100%; }
        .strength-label  { font-size:.75rem; margin-top:4px; color:#6b7280; }
        .toggle-pw { cursor:pointer; font-size:.8rem; color:#1a7a4a; float:right; margin-top:-28px; margin-right:8px; }
    </style>
</head>
<body>
<div class="login-wrapper">
    <div class="login-card">

        <%-- ─── Header ──────────────────────────────────────────────── --%>
        <div class="login-header">
            <h1>🌿 NLU System</h1>
            <p>Đặt lại mật khẩu mới</p>
        </div>

        <%-- ─── Thông báo lỗi (3.0-E6: mật khẩu không hợp lệ/không khớp) --%>
        <% if (request.getAttribute("error") != null) { %>
            <div class="alert alert-error">
                ⚠️ <%= request.getAttribute("error") %>
            </div>
        <% } %>

        <%-- ═══ 3.0.13 — Form "Đặt lại mật khẩu mới" ════════════════ --%>
        <form action="${pageContext.request.contextPath}/reset-password"
              method="post" novalidate id="resetForm">

            <%-- NFR-01: Hint mật khẩu mạnh ────────────────────────── --%>
            <p style="font-size:.82rem;color:#6b7280;margin-bottom:16px;background:#f9fafb;padding:10px;border-radius:8px">
                📋 Mật khẩu phải có ít nhất <strong>8 ký tự</strong>,
                gồm <strong>chữ cái</strong>, <strong>chữ số</strong>
                và <strong>ký tự đặc biệt</strong> (vd: @, #, !, $).
            </p>

            <%-- 3.0.14 — Trường "Mật khẩu mới" ────────────────────── --%>
            <div class="form-group">
                <label for="newPassword">Mật khẩu mới</label>
                <input type="password" id="newPassword" name="newPassword"
                       placeholder="Nhập mật khẩu mới"
                       autocomplete="new-password">
                <span class="toggle-pw" onclick="togglePw('newPassword', this)">Hiện</span>
                <%-- Thanh đánh giá độ mạnh mật khẩu ─────────────────── --%>
                <div class="password-strength">
                    <div class="password-strength-bar" id="strengthBar"></div>
                </div>
                <div class="strength-label" id="strengthLabel"></div>
            </div>

            <%-- 3.0.14 — Trường "Nhập lại mật khẩu mới" ─────────────── --%>
            <div class="form-group">
                <label for="confirmPassword">Nhập lại mật khẩu mới</label>
                <input type="password" id="confirmPassword" name="confirmPassword"
                       placeholder="Nhập lại mật khẩu"
                       autocomplete="new-password">
                <span class="toggle-pw" onclick="togglePw('confirmPassword', this)">Hiện</span>
                <div id="matchMsg" style="font-size:.8rem;margin-top:4px"></div>
            </div>

            <%-- 3.0.14 — Nút "Hoàn thành" ─────────────────────────── --%>
            <%-- 3.0-E6: Nút bị disable client-side nếu form chưa hợp lệ --%>
            <button type="submit" class="btn btn-primary" id="submitBtn" disabled>
                Hoàn thành
            </button>
        </form>

    </div>
</div>

<%-- ─── Script validate client-side và đánh giá độ mạnh mật khẩu ── --%>
<script>
    const newPwEl    = document.getElementById('newPassword');
    const confirmEl  = document.getElementById('confirmPassword');
    const strengthBar = document.getElementById('strengthBar');
    const strengthLabel = document.getElementById('strengthLabel');
    const matchMsg   = document.getElementById('matchMsg');
    const submitBtn  = document.getElementById('submitBtn');

    // Đánh giá độ mạnh mật khẩu (NFR-01)
    function evaluateStrength(pw) {
        let score = 0;
        if (pw.length >= 8)               score++;
        if (/[a-zA-Z]/.test(pw))          score++;
        if (/[0-9]/.test(pw))             score++;
        if (/[^a-zA-Z0-9]/.test(pw))      score++;
        return score; // 0-4
    }

    function updateStrengthUI(pw) {
        const score = evaluateStrength(pw);
        strengthBar.className = 'password-strength-bar';
        if (pw.length === 0) {
            strengthBar.style.width = '0';
            strengthLabel.textContent = '';
        } else if (score <= 2) {
            strengthBar.classList.add('strength-weak');
            strengthLabel.textContent = '⚠️ Yếu';
        } else if (score === 3) {
            strengthBar.classList.add('strength-medium');
            strengthLabel.textContent = '🔶 Trung bình';
        } else {
            strengthBar.classList.add('strength-strong');
            strengthLabel.textContent = '✅ Mạnh';
        }
    }

    function checkMatch() {
        const pw  = newPwEl.value;
        const cpw = confirmEl.value;
        if (cpw.length === 0) {
            matchMsg.textContent = '';
            return false;
        }
        if (pw === cpw) {
            matchMsg.style.color = '#16a34a';
            matchMsg.textContent = '✅ Mật khẩu trùng khớp';
            return true;
        } else {
            matchMsg.style.color = '#dc2626';
            matchMsg.textContent = '❌ Mật khẩu không khớp';
            return false;
        }
    }

    function updateSubmitBtn() {
        // Chỉ bật nút khi: độ mạnh >= medium VÀ hai trường khớp nhau
        const score   = evaluateStrength(newPwEl.value);
        const matches = checkMatch();
        submitBtn.disabled = !(score >= 3 && matches);
    }

    newPwEl.addEventListener('input', function () {
        updateStrengthUI(this.value);
        updateSubmitBtn();
    });
    confirmEl.addEventListener('input', updateSubmitBtn);

    // Toggle hiện/ẩn mật khẩu
    function togglePw(fieldId, btn) {
        const field = document.getElementById(fieldId);
        if (field.type === 'password') {
            field.type = 'text';
            btn.textContent = 'Ẩn';
        } else {
            field.type = 'password';
            btn.textContent = 'Hiện';
        }
    }
</script>
</body>
</html>
