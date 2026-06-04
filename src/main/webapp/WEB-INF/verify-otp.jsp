<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Xác thực mã OTP — NLU System</title>
    <link rel="stylesheet" href="${pageContext.request.contextPath}/css/login.css">
    <style>
        .otp-input {
            text-align: center;
            font-size: 2rem;
            font-weight: 700;
            letter-spacing: 12px;
            max-width: 220px;
            margin: 0 auto;
            display: block;
            border: 2px solid #d1fae5;
            border-radius: 10px;
            padding: 12px;
        }
        .otp-input:focus { border-color: #1a7a4a; outline: none; }
        .countdown-bar {
            height: 4px;
            background: #d1fae5;
            border-radius: 2px;
            margin: 12px 0;
        }
        .countdown-bar-fill {
            height: 100%;
            background: #1a7a4a;
            border-radius: 2px;
            transition: width 1s linear;
        }
        .resend-section {
            text-align: center;
            margin-top: 16px;
            font-size: .9rem;
            color: #6b7280;
        }
        .btn-resend {
            background: none;
            border: none;
            color: #1a7a4a;
            font-weight: 600;
            cursor: pointer;
            padding: 0;
            font-size: .9rem;
        }
        .btn-resend:disabled { color: #9ca3af; cursor: not-allowed; }
    </style>
</head>
<body>
<div class="login-wrapper">
    <div class="login-card">

        <%-- ─── Header ──────────────────────────────────────────────── --%>
        <div class="login-header">
            <h1>🌿 NLU System</h1>
            <p>Xác thực mã OTP</p>
        </div>

        <%-- ─── Thông báo lỗi (3.0-E5: OTP sai/hết hạn; 3.0-E3/E4: khóa) --%>
        <% if (request.getAttribute("error") != null) { %>
            <div class="alert alert-error">
                ⚠️ <%= request.getAttribute("error") %>
            </div>
        <% } %>

        <%-- ─── Thông báo gửi lại thành công (UC03.1) ──────────────── --%>
        <% if (request.getAttribute("success") != null) { %>
            <div class="alert" style="background:#f0fdf4;color:#15803d;border:1px solid #bbf7d0">
                ✅ <%= request.getAttribute("success") %>
            </div>
        <% } %>

        <%-- ─── Thông báo cooldown chưa đủ giờ gửi lại (3.1.2) ──────── --%>
        <% if (request.getAttribute("cooldownSeconds") != null) { %>
            <div class="alert" style="background:#fffbeb;color:#92400e;border:1px solid #fde68a">
                ⏳ Vui lòng chờ thêm
                <strong><%= request.getAttribute("cooldownSeconds") %> giây</strong>
                trước khi gửi lại mã.
            </div>
        <% } %>

        <%-- ─── Thông tin gửi đến email ─────────────────────────────── --%>
        <p style="text-align:center;color:#6b7280;font-size:.9rem;margin-bottom:20px">
            Mã xác thực đã được gửi nếu Email tồn tại trên hệ thống.<br>
            <% if (request.getAttribute("maskedEmail") != null) { %>
                Kiểm tra hộp thư: <strong><%= request.getAttribute("maskedEmail") %></strong>
            <% } %>
        </p>

        <%-- ═══ 3.0.11 — Form nhập mã OTP ═══════════════════════════ --%>
        <form action="${pageContext.request.contextPath}/verify-otp"
              method="post" novalidate id="verifyForm">
            <input type="hidden" name="action" value="verify">

            <div class="form-group">
                <label for="otp" style="text-align:center;display:block">
                    Nhập mã xác thực (6 chữ số)
                </label>

                <%-- Input OTP — maxlength=6, pattern số ────────────── --%>
                <input type="text" id="otp" name="otp"
                       class="otp-input"
                       maxlength="6"
                       inputmode="numeric"
                       pattern="[0-9]{6}"
                       placeholder="_ _ _ _ _ _"
                       autocomplete="one-time-code">
            </div>

            <%-- BR-02: Thanh đếm ngược 120 giây hiệu lực OTP ──────── --%>
            <div style="margin:8px 0 4px;font-size:.8rem;color:#6b7280;text-align:right">
                Hiệu lực: <span id="otpTimer">2:00</span>
            </div>
            <div class="countdown-bar">
                <div class="countdown-bar-fill" id="timerBar" style="width:100%"></div>
            </div>

            <%-- 3.0.11 — Nút "Xác nhận" ────────────────────────────── --%>
            <button type="submit" class="btn btn-primary" id="submitBtn">
                Xác nhận
            </button>
        </form>

        <%-- ═══ UC03.1 — Gửi lại mã OTP ════════════════════════════ --%>
        <div class="resend-section">
            Không nhận được mã?
            <form action="${pageContext.request.contextPath}/verify-otp"
                  method="post" style="display:inline">
                <input type="hidden" name="action" value="resend">
                <%-- 3.1.2: Nút bị disable trong thời gian cooldown ── --%>
                <button type="submit" class="btn-resend" id="resendBtn" disabled>
                    Gửi lại mã (<span id="resendCountdown">60</span>s)
                </button>
            </form>
        </div>

        <div style="text-align:center;margin-top:20px">
            <a href="${pageContext.request.contextPath}/forgot-password"
               style="color:#1a7a4a;font-size:.9rem;text-decoration:none">
                ← Nhập lại email khác
            </a>
        </div>

    </div>
</div>

<%-- ─── Script đếm ngược OTP timer và cooldown gửi lại ─────────── --%>
<script>
    // Thời điểm gửi OTP (milliseconds) — từ server
    const otpSentAtMs = <%= request.getAttribute("otpSentAt") != null
                           ? request.getAttribute("otpSentAt") : 0 %>;
    const OTP_EXPIRY_MS   = 120 * 1000; // BR-02: 120 giây
    const RESEND_COOL_MS  = 60  * 1000; // Cooldown gửi lại: 60 giây

    const timerEl    = document.getElementById('otpTimer');
    const timerBar   = document.getElementById('timerBar');
    const submitBtn  = document.getElementById('submitBtn');
    const resendBtn  = document.getElementById('resendBtn');
    const resendCdEl = document.getElementById('resendCountdown');

    function updateTimers() {
        const now = Date.now();

        // ── OTP expiry countdown (BR-02) ─────────────────────────────
        const elapsedMs  = now - otpSentAtMs;
        const remainOtp  = Math.max(0, OTP_EXPIRY_MS - elapsedMs);
        const secOtp     = Math.ceil(remainOtp / 1000);
        const minPart    = Math.floor(secOtp / 60);
        const secPart    = secOtp % 60;
        timerEl.textContent  = minPart + ':' + String(secPart).padStart(2, '0');
        timerBar.style.width = (remainOtp / OTP_EXPIRY_MS * 100).toFixed(1) + '%';

        // Khi OTP hết hạn → vô hiệu hóa nút xác nhận
        if (remainOtp <= 0) {
            timerEl.textContent   = 'Hết hạn';
            timerBar.style.width  = '0%';
            timerBar.style.background = '#dc2626';
            submitBtn.disabled    = true;
            submitBtn.textContent = 'Mã đã hết hạn';
        }

        // ── Resend cooldown (3.1.2) ──────────────────────────────────
        const remainResend = Math.max(0, RESEND_COOL_MS - elapsedMs);
        const secResend    = Math.ceil(remainResend / 1000);

        if (remainResend > 0) {
            resendBtn.disabled            = true;
            resendCdEl.textContent        = secResend;
        } else {
            resendBtn.disabled            = false;
            resendBtn.textContent         = 'Gửi lại mã';
        }
    }

    // Chạy ngay và lặp mỗi giây
    updateTimers();
    const timerInterval = setInterval(updateTimers, 1000);

    // Auto-focus vào ô nhập OTP
    document.getElementById('otp').focus();

    // Chỉ cho nhập số vào ô OTP
    document.getElementById('otp').addEventListener('input', function () {
        this.value = this.value.replace(/[^0-9]/g, '');
    });
</script>
</body>
</html>
