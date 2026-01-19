(() => {
  const LS = {
    baseUrl: "zhixu_console_baseUrl",
    token: "zhixu_console_token",
    sessionId: "zhixu_console_sessionId",
    refreshToken: "zhixu_console_refreshToken"
  };

  const $ = (id) => document.getElementById(id);

  const el = {
    toast: $("toast"),
    baseUrlInput: $("baseUrlInput"),
    baseUrlSaveBtn: $("baseUrlSaveBtn"),
    authPillText: $("authPillText"),
    logoutBtn: $("logoutBtn"),

    authView: $("authView"),
    loginBtn: $("loginBtn"),
    loginUsername: $("loginUsername"),
    loginPassword: $("loginPassword"),

    registerBtn: $("registerBtn"),
    registerUsername: $("registerUsername"),
    registerPassword: $("registerPassword"),
    registerEmail: $("registerEmail"),
    registerEmailCode: $("registerEmailCode"),
    registerSendCodeBtn: $("registerSendCodeBtn"),

    verifyBtn: $("verifyBtn"),
    verifyEmail: $("verifyEmail"),
    verifyCode: $("verifyCode"),
    verifySendCodeBtn: $("verifySendCodeBtn"),

    consoleView: $("consoleView"),
    refreshOverviewBtn: $("refreshOverviewBtn"),

    meUsername: $("meUsername"),
    meEmail: $("meEmail"),
    meEmailStatus: $("meEmailStatus"),
    meStorageQuota: $("meStorageQuota"),

    storageUsed: $("storageUsed"),
    storageLimit: $("storageLimit"),
    storageRemaining: $("storageRemaining"),
    storageFileCount: $("storageFileCount"),
    storageLastUpdated: $("storageLastUpdated"),

    avatarImg: $("avatarImg"),
    avatarFallback: $("avatarFallback"),
    avatarFile: $("avatarFile"),
    uploadAvatarBtn: $("uploadAvatarBtn"),
    deleteAvatarBtn: $("deleteAvatarBtn"),

    emailInput: $("emailInput"),
    emailVerifyCode: $("emailVerifyCode"),
    emailSendVerifyCodeBtn: $("emailSendVerifyCodeBtn"),
    emailVerifyBtn: $("emailVerifyBtn"),
    saveEmailBtn: $("saveEmailBtn"),

    currentPassword: $("currentPassword"),
    newPassword: $("newPassword"),
    changePasswordBtn: $("changePasswordBtn"),

    exportZipBtn: $("exportZipBtn"),
    refreshStorageBtn: $("refreshStorageBtn"),
    storageSearch: $("storageSearch"),
    storageFilesTbody: $("storageFilesTbody"),
    storagePagerText: $("storagePagerText"),
    storagePrevBtn: $("storagePrevBtn"),
    storageNextBtn: $("storageNextBtn"),

    refreshDevicesBtn: $("refreshDevicesBtn"),
    devicesTbody: $("devicesTbody"),

    refreshSyncLogsBtn: $("refreshSyncLogsBtn"),
    syncLogsTbody: $("syncLogsTbody"),

    adminTabBtn: $("adminTabBtn"),
    adminEmailText: $("adminEmailText"),
    adminSmtpText: $("adminSmtpText"),
    adminSyncAllText: $("adminSyncAllText"),
    adminRefreshStatusBtn: $("adminRefreshStatusBtn"),
    adminToggleSyncAllBtn: $("adminToggleSyncAllBtn"),

    adminSyncDaysInput: $("adminSyncDaysInput"),
    adminRefreshSyncSummaryBtn: $("adminRefreshSyncSummaryBtn"),
    adminSyncChart: $("adminSyncChart"),
    adminSyncTopErrors: $("adminSyncTopErrors"),

    adminUsersSearch: $("adminUsersSearch"),
    adminUsersRefreshBtn: $("adminUsersRefreshBtn"),
    adminUsersSelectAll: $("adminUsersSelectAll"),
    adminUsersTbody: $("adminUsersTbody"),
    adminUsersPagerText: $("adminUsersPagerText"),
    adminUsersPrevBtn: $("adminUsersPrevBtn"),
    adminUsersNextBtn: $("adminUsersNextBtn"),

    adminMailSubject: $("adminMailSubject"),
    adminMailBody: $("adminMailBody"),
    adminSendMailBtn: $("adminSendMailBtn"),

    adminUserSyncTitle: $("adminUserSyncTitle"),
    adminUserSyncDaysInput: $("adminUserSyncDaysInput"),
    adminRefreshUserSyncBtn: $("adminRefreshUserSyncBtn"),
    adminUserSyncChart: $("adminUserSyncChart"),
    adminUserSyncTopErrors: $("adminUserSyncTopErrors")
  };

  const state = {
    baseUrl: "",
    token: "",
    sessionId: "",
    refreshToken: "",
    isAdmin: false,
    avatarObjectUrl: "",
    storage: {
      total: 0,
      offset: 0,
      limit: 50,
      files: []
    },
    admin: {
      syncDisabledAll: false,
      users: {
        total: 0,
        offset: 0,
        limit: 50,
        q: "",
        rows: []
      },
      selectedUserIds: new Set(),
      selectedUserId: 0,
      syncSummary: { days: 30, series: [], topErrorCodes: [] },
      userSyncSummary: { userId: 0, days: 30, series: [], topErrorCodes: [] }
    }
  };

  function defaultBaseUrl() {
    const origin = String(window.location.origin || "").trim();
    if (origin && origin !== "null" && (origin.startsWith("http://") || origin.startsWith("https://"))) return origin;
    return "https://zhixu.app";
  }

  function normalizeBaseUrl(baseUrl) {
    const clean = String(baseUrl || "").trim().replace(/\/+$/, "");
    return clean || defaultBaseUrl();
  }

  function joinUrl(baseUrl, path) {
    const left = normalizeBaseUrl(baseUrl);
    const right = String(path || "").trim();
    if (!right) return left;
    return right.startsWith("/") ? `${left}${right}` : `${left}/${right}`;
  }

  function showToast(message, kind = "info") {
    if (!el.toast) return;
    el.toast.hidden = false;
    el.toast.dataset.kind = kind;
    el.toast.textContent = String(message || "");
    window.clearTimeout(showToast._t);
    showToast._t = window.setTimeout(() => {
      el.toast.hidden = true;
      el.toast.textContent = "";
    }, 4000);
  }

  function setBusy(btn, busy) {
    if (!btn) return;
    btn.disabled = Boolean(busy);
    btn.dataset.busy = busy ? "1" : "0";
  }

  function saveState() {
    localStorage.setItem(LS.baseUrl, state.baseUrl);
    localStorage.setItem(LS.token, state.token);
    localStorage.setItem(LS.sessionId, state.sessionId);
    localStorage.setItem(LS.refreshToken, state.refreshToken);
  }

  function clearAuth() {
    state.token = "";
    state.sessionId = "";
    state.refreshToken = "";
    state.isAdmin = false;
    localStorage.removeItem(LS.token);
    localStorage.removeItem(LS.sessionId);
    localStorage.removeItem(LS.refreshToken);
  }

  function setAuthedUI(authed, label = "") {
    el.logoutBtn.hidden = !authed;
    el.authView.hidden = authed;
    el.consoleView.hidden = !authed;
    el.authPillText.textContent = authed ? label || "已登录" : "未登录";
  }

  async function refreshAuth() {
    if (!state.sessionId || !state.refreshToken) return false;
    try {
      const res = await fetch(joinUrl(state.baseUrl, "/api/auth/refresh"), {
        method: "POST",
        headers: { "Content-Type": "application/json; charset=utf-8" },
        body: JSON.stringify({ sessionId: state.sessionId, refreshToken: state.refreshToken })
      });
      if (!res.ok) return false;
      const obj = await res.json().catch(() => null);
      if (!obj || !obj.token) return false;
      state.token = String(obj.token || "");
      state.sessionId = String(obj.sessionId || state.sessionId);
      state.refreshToken = String(obj.refreshToken || state.refreshToken);
      if (Object.prototype.hasOwnProperty.call(obj, "isAdmin")) state.isAdmin = Boolean(obj.isAdmin);
      saveState();
      return true;
    } catch (_) {
      return false;
    }
  }

  async function apiFetch(path, init = {}, { auth = false, retry401 = true } = {}) {
    const url = joinUrl(state.baseUrl, path);
    const headers = new Headers(init.headers || {});
    if (auth && state.token) headers.set("Authorization", `Bearer ${state.token}`);
    init.headers = headers;

    const res = await fetch(url, init);
    if (auth && res.status === 401 && retry401) {
      const ok = await refreshAuth();
      if (ok) return apiFetch(path, init, { auth, retry401: false });
    }
    return res;
  }

  async function requestJson(path, { method = "GET", body, auth = false } = {}) {
    const init = { method, headers: {} };
    if (body !== undefined) {
      init.headers["Content-Type"] = "application/json; charset=utf-8";
      init.body = typeof body === "string" ? body : JSON.stringify(body);
    }

    const res = await apiFetch(path, init, { auth });
    const text = await res.text().catch(() => "");
    const obj = (() => {
      try {
        return text ? JSON.parse(text) : null;
      } catch {
        return null;
      }
    })();

    const looksLikeHtml = (s) => {
      const raw = String(s || "").trim().toLowerCase();
      if (!raw) return false;
      if (raw.startsWith("<!doctype html")) return true;
      if (raw.startsWith("<html")) return true;
      return raw.includes("<html") && raw.includes("</html>");
    };

    const formatHttpError = (response, responseText, parsedObj) => {
      const msg = String(parsedObj?.error || parsedObj?.message || responseText || `HTTP ${response.status}`).trim();
      const ct = String(response.headers.get("Content-Type") || "").toLowerCase();
      const html = ct.includes("text/html") || looksLikeHtml(msg);
      if (html) {
        const is1Panel = msg.includes("1Panel") || msg.includes("请求拦截") || msg.includes("恶意参数");
        return is1Panel ? "请求被 1Panel 防护拦截（WAF）。请在 1Panel 放行该接口或关闭相关拦截规则。" : "服务器返回了 HTML 页面（可能被反向代理/WAF 拦截）。";
      }
      return msg || `HTTP ${response.status}`;
    };

    if (!res.ok) {
      const msg = formatHttpError(res, text, obj);
      return { ok: false, status: res.status, error: msg, value: obj };
    }
    return { ok: true, status: res.status, value: obj, raw: text };
  }

  function formatBytes(bytes) {
    const n = Number(bytes) || 0;
    if (n <= 0) return "0 B";
    const units = ["B", "KB", "MB", "GB", "TB"];
    let v = n;
    let u = 0;
    while (v >= 1024 && u < units.length - 1) {
      v /= 1024;
      u += 1;
    }
    return `${v.toFixed(v < 10 && u > 0 ? 2 : 1)} ${units[u]}`;
  }

  function formatTime(ms) {
    const t = Number(ms) || 0;
    if (t <= 0) return "-";
    try {
      return new Date(t).toLocaleString();
    } catch {
      return String(t);
    }
  }

  function setAdminTabVisible(visible) {
    if (!el.adminTabBtn) return;
    el.adminTabBtn.hidden = !visible;

    const currentBtn = document.querySelector('.consoleTabs [data-console-tab][aria-selected="true"]');
    const current = String(currentBtn?.dataset?.consoleTab || "");
    if (!visible && current === "admin") switchConsoleTab("overview");
  }

  function formatErrorRate(rate) {
    const n = Number(rate);
    if (!Number.isFinite(n) || n <= 0) return "0%";
    const pct = Math.min(100, Math.max(0, n * 100));
    return `${pct.toFixed(pct < 10 ? 1 : 0)}%`;
  }

  function ensureCanvasResolution(canvas) {
    if (!canvas) return null;
    const rect = canvas.getBoundingClientRect();
    const cssW = Math.max(1, Math.floor(rect.width || canvas.width || 1));
    const cssH = Math.max(1, Math.floor(rect.height || canvas.height || 1));
    const dpr = Math.max(1, Math.min(3, window.devicePixelRatio || 1));
    canvas.width = Math.floor(cssW * dpr);
    canvas.height = Math.floor(cssH * dpr);
    const ctx = canvas.getContext("2d");
    if (!ctx) return null;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    return { ctx, width: cssW, height: cssH };
  }

  function drawSyncSummaryChart(canvas, series) {
    const g = ensureCanvasResolution(canvas);
    if (!g) return;
    const { ctx, width, height } = g;
    ctx.clearRect(0, 0, width, height);

    const data = Array.isArray(series) ? series : [];
    if (!data.length) {
      ctx.fillStyle = "rgba(120,120,120,0.9)";
      ctx.font = "14px system-ui, -apple-system, Segoe UI, Roboto, sans-serif";
      ctx.fillText("暂无数据", 10, 24);
      return;
    }

    const maxTotal = Math.max(1, ...data.map((d) => Number(d.total) || 0));
    const padX = 8;
    const padY = 18;
    const chartW = Math.max(1, width - padX * 2);
    const chartH = Math.max(1, height - padY * 2);
    const barW = chartW / data.length;

    ctx.fillStyle = "rgba(120,120,120,0.35)";
    ctx.fillRect(padX, padY + chartH, chartW, 1);

    for (let i = 0; i < data.length; i += 1) {
      const total = Math.max(0, Number(data[i]?.total) || 0);
      const errors = Math.max(0, Number(data[i]?.errors) || 0);

      const totalH = (total / maxTotal) * chartH;
      const errH = (errors / maxTotal) * chartH;
      const x = padX + i * barW;
      const w = Math.max(1, barW * 0.7);
      const x0 = x + (barW - w) / 2;

      ctx.fillStyle = "rgba(6, 106, 223, 0.55)";
      ctx.fillRect(x0, padY + chartH - totalH, w, totalH);

      if (errH > 0) {
        ctx.fillStyle = "rgba(217, 53, 53, 0.7)";
        ctx.fillRect(x0, padY + chartH - errH, w, errH);
      }
    }
  }

  function setButtonLoading(btn, loading, labelWhenIdle) {
    if (!btn) return;
    if (loading) {
      btn.disabled = true;
      btn.dataset._label = btn.textContent || "";
      btn.textContent = "处理中…";
    } else {
      btn.disabled = false;
      const prev = btn.dataset._label || labelWhenIdle || "";
      if (prev) btn.textContent = prev;
      delete btn.dataset._label;
    }
  }

  function clearChildren(node) {
    if (!node) return;
    while (node.firstChild) node.removeChild(node.firstChild);
  }

  function downloadBlob(blob, filename) {
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = filename || "download";
    a.style.display = "none";
    document.body.appendChild(a);
    a.click();
    a.remove();
    window.setTimeout(() => URL.revokeObjectURL(url), 1000);
  }

  function parseContentDispositionFilename(value) {
    const v = String(value || "");
    const m1 = v.match(/filename\\*=UTF-8''([^;]+)/i);
    if (m1 && m1[1]) {
      try {
        return decodeURIComponent(m1[1]);
      } catch {
        return m1[1];
      }
    }
    const m2 = v.match(/filename=\"([^\"]+)\"/i);
    if (m2 && m2[1]) return m2[1];
    const m3 = v.match(/filename=([^;]+)/i);
    if (m3 && m3[1]) return m3[1].trim();
    return "";
  }

  function switchAuthTab(tab) {
    const buttons = Array.from(document.querySelectorAll('.consoleTabs [data-tab]'));
    const panels = Array.from(document.querySelectorAll(".consolePanel[data-panel]"));
    for (const b of buttons) {
      const selected = String(b.dataset.tab || "") === String(tab || "");
      b.setAttribute("aria-selected", selected ? "true" : "false");
    }
    for (const p of panels) {
      const selected = String(p.dataset.panel || "") === String(tab || "");
      p.hidden = !selected;
    }
  }

  function switchConsoleTab(tab) {
    const buttons = Array.from(document.querySelectorAll('.consoleTabs [data-console-tab]'));
    const panels = Array.from(document.querySelectorAll(".consoleSection[data-console-panel]"));
    for (const b of buttons) {
      const selected = String(b.dataset.consoleTab || "") === String(tab || "");
      b.setAttribute("aria-selected", selected ? "true" : "false");
    }
    for (const p of panels) {
      const selected = String(p.dataset.consolePanel || "") === String(tab || "");
      p.hidden = !selected;
    }
  }

  async function doLogin() {
    const username = String(el.loginUsername.value || "").trim();
    const password = String(el.loginPassword.value || "");
    if (!username || !password) {
      showToast("请输入账号与密码", "error");
      return;
    }

    setButtonLoading(el.loginBtn, true);
    try {
      const deviceName = `Zhixu-Web (${navigator.platform || "Web"})`;
      const r = await requestJson("/api/auth/login", { method: "POST", body: { username, password, deviceName }, auth: false });
      if (!r.ok) {
        showToast(r.error || "登录失败", "error");
        return;
      }
      state.token = String(r.value?.token || "");
      state.sessionId = String(r.value?.sessionId || "");
      state.refreshToken = String(r.value?.refreshToken || "");
      state.isAdmin = false;
      saveState();
      await enterConsole();
      showToast("登录成功");
    } finally {
      setButtonLoading(el.loginBtn, false);
    }
  }

  async function doRegister() {
    const username = String(el.registerUsername.value || "").trim();
    const password = String(el.registerPassword.value || "");
    const email = String(el.registerEmail.value || "").trim();
    const emailCode = String(el.registerEmailCode.value || "").trim();
    if (!username || !password || !email || !emailCode) {
      showToast("请输入用户名、密码、邮箱与邮箱验证码", "error");
      return;
    }

    setButtonLoading(el.registerBtn, true);
    try {
      const body = { username, password, email, emailCode };
      const r = await requestJson("/api/auth/register", { method: "POST", body, auth: false });
      if (!r.ok) {
        showToast(r.error || "注册失败", "error");
        return;
      }
      showToast("注册成功，请登录");
      switchAuthTab("login");
      el.loginUsername.value = username;
      el.loginPassword.value = "";
    } finally {
      setButtonLoading(el.registerBtn, false);
    }
  }

  async function doSendEmailCode(purpose, emailInputEl, btnEl) {
    const email = String(emailInputEl.value || "").trim();
    if (!email) {
      showToast("请输入邮箱", "error");
      return;
    }
    setButtonLoading(btnEl, true);
    try {
      const r = await requestJson("/api/auth/email/code", { method: "POST", body: { email, purpose }, auth: false });
      if (!r.ok) {
        showToast(r.error || "发送失败", "error");
        return;
      }
      showToast("验证码已发送（若服务器已配置 SMTP）");
    } finally {
      setButtonLoading(btnEl, false);
    }
  }

  async function doEmailLogin() {
    const email = String(el.verifyEmail.value || "").trim();
    const code = String(el.verifyCode.value || "").trim();
    if (!email || !code) {
      showToast("请输入邮箱与验证码", "error");
      return;
    }

    setButtonLoading(el.verifyBtn, true);
    try {
      const deviceName = `Zhixu-Web (${navigator.platform || "Web"})`;
      const r = await requestJson("/api/auth/email/login", { method: "POST", body: { email, code, deviceName }, auth: false });
      if (!r.ok) {
        showToast(r.error || "邮箱登录失败", "error");
        return;
      }
      state.token = String(r.value?.token || "");
      state.sessionId = String(r.value?.sessionId || "");
      state.refreshToken = String(r.value?.refreshToken || "");
      state.isAdmin = Boolean(r.value?.isAdmin);
      saveState();
      await enterConsole();
      showToast(state.isAdmin ? "管理员邮箱登录成功" : "登录成功");
    } finally {
      setButtonLoading(el.verifyBtn, false);
    }
  }

  async function doVerifyEmailInline(email, code, btnEl) {
    const safeEmail = String(email || "").trim();
    const safeCode = String(code || "").trim();
    if (!safeEmail || !safeCode) {
      showToast("请输入邮箱与验证码", "error");
      return;
    }
    setButtonLoading(btnEl, true);
    try {
      const r = await requestJson("/api/auth/email/verify", { method: "POST", body: { email: safeEmail, code: safeCode }, auth: false });
      if (!r.ok) {
        showToast(r.error || "验证失败", "error");
        return;
      }
      showToast(r.value?.alreadyVerified ? "邮箱已验证" : "验证成功");
      el.emailVerifyCode.value = "";
      if (state.token) await loadMe();
    } finally {
      setButtonLoading(btnEl, false);
    }
  }

  async function doLogout() {
    setBusy(el.logoutBtn, true);
    try {
      if (state.token) {
        await requestJson("/api/auth/logout", { method: "POST", body: {}, auth: true });
      }
    } catch (_) {
      // ignore
    } finally {
      setBusy(el.logoutBtn, false);
    }

    clearAuth();
    clearAvatar();
    setAuthedUI(false, "");
    showToast("已退出");
  }

  async function loadMe() {
    const r = await requestJson("/api/account/me", { method: "GET", auth: true });
    if (!r.ok) {
      showToast(r.error || "加载账号失败", "error");
      return null;
    }
    const me = r.value || {};
    el.meUsername.textContent = String(me.username || "-");
    el.meEmail.textContent = String(me.email || "-");
    el.meEmailStatus.textContent = me.emailVerified ? "已验证" : me.email ? "未验证" : "未设置";

    const usedBytes = Number(me.storage?.usedBytes) || 0;
    const limitBytes = Number(me.storage?.limitBytes) || 0;
    el.meStorageQuota.textContent = limitBytes > 0 ? `${formatBytes(usedBytes)} / ${formatBytes(limitBytes)}` : "-";

    el.emailInput.value = String(me.email || "");

    const initial = String(me.username || "Z")
      .slice(0, 1)
      .toUpperCase();
    el.avatarFallback.textContent = initial || "Z";

    if (me.avatar?.hasAvatar) {
      await loadAvatar();
    } else {
      clearAvatar();
    }

    el.authPillText.textContent = `已登录：${String(me.username || "") || "账号"}`;
    return me;
  }

  function clearAvatar() {
    if (state.avatarObjectUrl) {
      try {
        URL.revokeObjectURL(state.avatarObjectUrl);
      } catch (_) {
        // ignore
      }
      state.avatarObjectUrl = "";
    }
    el.avatarImg.hidden = true;
    el.avatarImg.removeAttribute("src");
    el.avatarFallback.hidden = false;
  }

  async function loadAvatar() {
    try {
      const res = await apiFetch("/api/account/avatar", { method: "GET" }, { auth: true });
      if (!res.ok) {
        clearAvatar();
        return;
      }
      const blob = await res.blob();
      if (!blob || blob.size <= 0) {
        clearAvatar();
        return;
      }
      clearAvatar();
      const url = URL.createObjectURL(blob);
      state.avatarObjectUrl = url;
      el.avatarImg.src = url;
      el.avatarImg.hidden = false;
      el.avatarFallback.hidden = true;
    } catch (_) {
      clearAvatar();
    }
  }

  async function saveEmail() {
    const email = String(el.emailInput.value || "").trim();
    setButtonLoading(el.saveEmailBtn, true);
    try {
      const r = await requestJson("/api/account/email", { method: "POST", body: { email }, auth: true });
      if (!r.ok) {
        showToast(r.error || "保存失败", "error");
        return;
      }
      showToast(r.value?.verificationSent ? "已保存，并发送验证码" : "已保存（若需验证，请发送验证码）");
      await loadMe();
    } finally {
      setButtonLoading(el.saveEmailBtn, false);
    }
  }

  async function changePassword() {
    const currentPassword = String(el.currentPassword.value || "");
    const newPassword = String(el.newPassword.value || "");
    if (!currentPassword || !newPassword) {
      showToast("请输入当前密码与新密码", "error");
      return;
    }
    setButtonLoading(el.changePasswordBtn, true);
    try {
      const r = await requestJson("/api/account/password", { method: "POST", body: { currentPassword, newPassword }, auth: true });
      if (!r.ok) {
        showToast(r.error || "修改失败", "error");
        return;
      }
      el.currentPassword.value = "";
      el.newPassword.value = "";
      showToast("密码已修改，其他设备已退出");
    } finally {
      setButtonLoading(el.changePasswordBtn, false);
    }
  }

  async function uploadAvatar() {
    const file = el.avatarFile.files && el.avatarFile.files[0] ? el.avatarFile.files[0] : null;
    if (!file) {
      showToast("请选择图片文件", "error");
      return;
    }
    const mime = String(file.type || "")
      .trim()
      .toLowerCase();
    const allowed = new Set(["image/png", "image/jpeg", "image/webp", "image/gif"]);
    if (!allowed.has(mime)) {
      showToast("仅支持 PNG / JPG / WebP / GIF", "error");
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      showToast("图片过大（最大 5MB）", "error");
      return;
    }
    setButtonLoading(el.uploadAvatarBtn, true);
    try {
      const res = await apiFetch(
        "/api/account/avatar",
        {
          method: "PUT",
          headers: { "Content-Type": "application/octet-stream", "X-Zhixu-Avatar-Mime": mime },
          body: file
        },
        { auth: true }
      );
      const text = await res.text().catch(() => "");
      const obj = (() => {
        try {
          return text ? JSON.parse(text) : null;
        } catch {
          return null;
        }
      })();
      if (!res.ok) {
        const msg = (() => {
          const raw = String(obj?.error || obj?.message || text || `HTTP ${res.status}`).trim();
          const ct = String(res.headers.get("Content-Type") || "").toLowerCase();
          const lower = raw.toLowerCase();
          const isHtml = ct.includes("text/html") || lower.startsWith("<!doctype html") || lower.includes("<html");
          if (!isHtml) return raw || `HTTP ${res.status}`;
          const is1Panel = raw.includes("1Panel") || raw.includes("请求拦截") || raw.includes("恶意参数");
          return is1Panel ? "请求被 1Panel 防护拦截（WAF）。请在 1Panel 放行该接口或关闭相关拦截规则。" : "上传失败：服务器返回了 HTML 页面（可能被反向代理/WAF 拦截）。";
        })();
        showToast(msg || "上传失败", "error");
        return;
      }
      showToast("头像已更新");
      await loadMe();
    } finally {
      setButtonLoading(el.uploadAvatarBtn, false);
    }
  }

  async function deleteAvatar() {
    setButtonLoading(el.deleteAvatarBtn, true);
    try {
      const r = await requestJson("/api/account/avatar", { method: "DELETE", auth: true });
      if (!r.ok) {
        showToast(r.error || "移除失败", "error");
        return;
      }
      clearAvatar();
      showToast("头像已移除");
      await loadMe();
    } finally {
      setButtonLoading(el.deleteAvatarBtn, false);
    }
  }

  async function loadStorageStats() {
    const r = await requestJson("/api/storage/stats", { method: "GET", auth: true });
    if (!r.ok) {
      showToast(r.error || "加载存储失败", "error");
      return null;
    }
    const s = r.value || {};
    el.storageUsed.textContent = formatBytes(s.usedBytes || 0);
    el.storageLimit.textContent = formatBytes(s.limitBytes || 0);
    el.storageRemaining.textContent = formatBytes(s.remainingBytes || Math.max(0, (Number(s.limitBytes) || 0) - (Number(s.usedBytes) || 0)));
    el.storageFileCount.textContent = String(s.fileCount ?? "-");
    el.storageLastUpdated.textContent = formatTime(s.lastUpdatedAtMs || 0);
    return s;
  }

  function renderStorageFiles() {
    const q = String(el.storageSearch.value || "").trim().toLowerCase();
    const rows = q ? state.storage.files.filter((f) => String(f.path || "").toLowerCase().includes(q)) : state.storage.files;
    clearChildren(el.storageFilesTbody);

    for (const f of rows) {
      const tr = document.createElement("tr");

      const tdPath = document.createElement("td");
      tdPath.textContent = String(f.path || "");
      tdPath.style.wordBreak = "break-word";

      const tdSize = document.createElement("td");
      tdSize.textContent = formatBytes(f.size || 0);
      tdSize.style.whiteSpace = "nowrap";

      const tdUpdated = document.createElement("td");
      tdUpdated.textContent = formatTime(f.updatedAt || 0);
      tdUpdated.style.whiteSpace = "nowrap";

      const tdActions = document.createElement("td");
      tdActions.style.whiteSpace = "nowrap";
      const btnDl = document.createElement("button");
      btnDl.type = "button";
      btnDl.className = "button";
      btnDl.textContent = "下载";
      btnDl.addEventListener("click", () => void downloadVaultFile(String(f.path || "")));

      const btnDel = document.createElement("button");
      btnDel.type = "button";
      btnDel.className = "button";
      btnDel.textContent = "删除";
      btnDel.addEventListener("click", () => void deleteVaultFile(String(f.path || ""), Number(f.rev) || 0));

      tdActions.appendChild(btnDl);
      tdActions.appendChild(document.createTextNode(" "));
      tdActions.appendChild(btnDel);

      tr.appendChild(tdPath);
      tr.appendChild(tdSize);
      tr.appendChild(tdUpdated);
      tr.appendChild(tdActions);
      el.storageFilesTbody.appendChild(tr);
    }

    const start = state.storage.total ? state.storage.offset + 1 : 0;
    const end = Math.min(state.storage.total, state.storage.offset + state.storage.limit);
    el.storagePagerText.textContent = state.storage.total ? `${start}-${end} / ${state.storage.total}` : "-";
    el.storagePrevBtn.disabled = state.storage.offset <= 0;
    el.storageNextBtn.disabled = state.storage.offset + state.storage.limit >= state.storage.total;
  }

  async function loadStorageFilesPage() {
    const q = `?limit=${encodeURIComponent(String(state.storage.limit))}&offset=${encodeURIComponent(String(state.storage.offset))}`;
    const r = await requestJson(`/api/storage/files${q}`, { method: "GET", auth: true });
    if (!r.ok) {
      showToast(r.error || "加载列表失败", "error");
      return;
    }
    state.storage.total = Number(r.value?.total) || 0;
    state.storage.files = Array.isArray(r.value?.files) ? r.value.files : [];
    renderStorageFiles();
  }

  async function downloadVaultFile(path) {
    const safePath = String(path || "");
    if (!safePath) return;
    setBusy(el.storageFilesTbody, true);
    try {
      const res = await apiFetch(`/api/v2/vault/file?path=${encodeURIComponent(safePath)}`, { method: "GET" }, { auth: true });
      if (!res.ok) {
        const msg = await res.text().catch(() => "");
        showToast(msg || `下载失败（HTTP ${res.status}）`, "error");
        return;
      }
      const blob = await res.blob();
      const name = safePath.split("/").filter(Boolean).pop() || "vault-file";
      downloadBlob(blob, name);
    } catch (_) {
      showToast("下载失败", "error");
    } finally {
      setBusy(el.storageFilesTbody, false);
    }
  }

  async function deleteVaultFile(path, rev) {
    const safePath = String(path || "");
    const baseRev = Number(rev) || 0;
    if (!safePath) return;
    if (!window.confirm(`确认删除：${safePath} ?`)) return;

    try {
      const res = await apiFetch(
        `/api/v2/vault/file?path=${encodeURIComponent(safePath)}&baseRev=${encodeURIComponent(String(Math.max(0, baseRev)))}`,
        { method: "DELETE" },
        { auth: true }
      );
      const text = await res.text().catch(() => "");
      const obj = (() => {
        try {
          return text ? JSON.parse(text) : null;
        } catch {
          return null;
        }
      })();
      if (!res.ok) {
        showToast(String(obj?.error || text || `HTTP ${res.status}`).trim() || "删除失败", "error");
        return;
      }
      showToast("已删除");
      await refreshStorage();
    } catch (_) {
      showToast("删除失败", "error");
    }
  }

  async function exportZip() {
    setButtonLoading(el.exportZipBtn, true);
    try {
      const res = await apiFetch("/api/storage/export", { method: "GET" }, { auth: true });
      if (!res.ok) {
        const msg = await res.text().catch(() => "");
        showToast(msg || `导出失败（HTTP ${res.status}）`, "error");
        return;
      }
      const cd = res.headers.get("content-disposition") || "";
      const filename = parseContentDispositionFilename(cd) || `zhixu-vault-export.zip`;
      const blob = await res.blob();
      downloadBlob(blob, filename);
      showToast("导出已开始");
    } catch (_) {
      showToast("导出失败", "error");
    } finally {
      setButtonLoading(el.exportZipBtn, false);
    }
  }

  async function loadDevices() {
    const r = await requestJson("/api/account/sessions", { method: "GET", auth: true });
    if (!r.ok) {
      showToast(r.error || "加载设备失败", "error");
      return;
    }
    const sessions = Array.isArray(r.value?.sessions) ? r.value.sessions : [];
    clearChildren(el.devicesTbody);
    for (const s of sessions) {
      const tr = document.createElement("tr");

      const tdName = document.createElement("td");
      tdName.textContent = String(s.deviceName || s.name || s.client || "");
      tdName.style.wordBreak = "break-word";

      const tdIp = document.createElement("td");
      tdIp.textContent = String(s.ip || "-");
      tdIp.style.whiteSpace = "nowrap";

      const tdSeen = document.createElement("td");
      tdSeen.textContent = String(s.lastSeenText || s.lastSeenAt || "-");
      tdSeen.style.whiteSpace = "nowrap";

      const tdAct = document.createElement("td");
      tdAct.style.whiteSpace = "nowrap";
      if (s.isCurrent) {
        tdAct.textContent = "当前";
      } else {
        const btn = document.createElement("button");
        btn.type = "button";
        btn.className = "button";
        btn.textContent = "撤销";
        btn.addEventListener("click", () => void revokeSession(String(s.sessionId || s.id || "")));
        tdAct.appendChild(btn);
      }

      tr.appendChild(tdName);
      tr.appendChild(tdIp);
      tr.appendChild(tdSeen);
      tr.appendChild(tdAct);
      el.devicesTbody.appendChild(tr);
    }
  }

  async function revokeSession(sessionId) {
    const sid = String(sessionId || "").trim();
    if (!sid) return;
    if (!window.confirm("确认撤销该设备会话？")) return;
    const r = await requestJson("/api/account/sessions/revoke", { method: "POST", body: { sessionId: sid }, auth: true });
    if (!r.ok) {
      showToast(r.error || "撤销失败", "error");
      return;
    }
    showToast("已撤销");
    await loadDevices();
  }

  function formatAction(action) {
    const a = String(action || "");
    if (a === "changes_snapshot") return "changes(快照)";
    if (a === "changes_delta") return "changes(增量)";
    if (a === "file_get") return "file(下载)";
    if (a === "file_put") return "file(上传)";
    if (a === "file_delete") return "file(删除)";
    return a || "-";
  }

  async function loadSyncLogs() {
    const r = await requestJson("/api/account/sync/logs?limit=200", { method: "GET", auth: true });
    if (!r.ok) {
      showToast(r.error || "加载同步记录失败", "error");
      return;
    }
    const logs = Array.isArray(r.value?.logs) ? r.value.logs : [];
    clearChildren(el.syncLogsTbody);
    for (const l of logs) {
      const tr = document.createElement("tr");

      const tdTime = document.createElement("td");
      tdTime.textContent = formatTime(l.createdAtMs || 0);
      tdTime.style.whiteSpace = "nowrap";

      const tdAction = document.createElement("td");
      tdAction.textContent = formatAction(l.action);
      tdAction.style.whiteSpace = "nowrap";

      const tdPath = document.createElement("td");
      tdPath.textContent = String(l.path || "-");
      tdPath.style.wordBreak = "break-word";

      const tdDev = document.createElement("td");
      tdDev.textContent = String(l.deviceName || l.sessionId || "-");
      tdDev.style.wordBreak = "break-word";

      const tdIp = document.createElement("td");
      tdIp.textContent = String(l.ip || "-");
      tdIp.style.whiteSpace = "nowrap";

      tr.appendChild(tdTime);
      tr.appendChild(tdAction);
      tr.appendChild(tdPath);
      tr.appendChild(tdDev);
      tr.appendChild(tdIp);
      el.syncLogsTbody.appendChild(tr);
    }
  }

  async function refreshOverview() {
    await loadMe();
    await loadStorageStats();
  }

  async function refreshStorage() {
    await loadStorageStats();
    await loadStorageFilesPage();
  }

  async function refreshAdminStatus({ silent = false } = {}) {
    if (!state.token) {
      state.isAdmin = false;
      setAdminTabVisible(false);
      return false;
    }

    const r = await requestJson("/api/admin/status", { method: "GET", auth: true });
    if (!r.ok) {
      state.isAdmin = false;
      setAdminTabVisible(false);
      if (!silent && r.status !== 401 && r.status !== 403) showToast(r.error || "管理员状态获取失败", "error");
      return false;
    }

    state.isAdmin = true;
    setAdminTabVisible(true);

    const status = r.value || {};
    state.admin.syncDisabledAll = Boolean(status.syncDisabledAll);

    if (el.adminEmailText) el.adminEmailText.textContent = String(status.adminEmail || "-") || "-";
    if (el.adminSmtpText) el.adminSmtpText.textContent = status.smtpConfigured ? "已配置" : "未配置";
    if (el.adminSyncAllText) el.adminSyncAllText.textContent = state.admin.syncDisabledAll ? "已禁用" : "已启用";
    if (el.adminToggleSyncAllBtn) el.adminToggleSyncAllBtn.textContent = state.admin.syncDisabledAll ? "启用全体同步" : "禁用全体同步";
    return true;
  }

  async function loadAdminSyncSummary() {
    if (!state.isAdmin) return;
    const daysRaw = Number(el.adminSyncDaysInput?.value || 30);
    const days = Number.isFinite(daysRaw) ? Math.min(Math.max(Math.floor(daysRaw), 1), 365) : 30;

    setButtonLoading(el.adminRefreshSyncSummaryBtn, true);
    try {
      const r = await requestJson(`/api/admin/sync/summary?days=${encodeURIComponent(String(days))}`, { method: "GET", auth: true });
      if (!r.ok) {
        showToast(r.error || "同步概览获取失败", "error");
        return;
      }
      const v = r.value || {};
      state.admin.syncSummary = { days, series: v.series || [], topErrorCodes: v.topErrorCodes || [] };
      drawSyncSummaryChart(el.adminSyncChart, state.admin.syncSummary.series);

      if (el.adminSyncTopErrors) {
        const list = Array.isArray(state.admin.syncSummary.topErrorCodes) ? state.admin.syncSummary.topErrorCodes : [];
        el.adminSyncTopErrors.textContent = list.length
          ? list
              .slice(0, 8)
              .map((x) => `${String(x.code || "") || "?"}(${Number(x.count) || 0})`)
              .join(", ")
          : "-";
      }
    } finally {
      setButtonLoading(el.adminRefreshSyncSummaryBtn, false);
    }
  }

  function updateAdminUsersSelectAllCheckbox() {
    if (!el.adminUsersSelectAll) return;
    const rows = Array.isArray(state.admin.users.rows) ? state.admin.users.rows : [];
    if (!rows.length) {
      el.adminUsersSelectAll.checked = false;
      el.adminUsersSelectAll.indeterminate = false;
      return;
    }
    const selectedCount = rows.filter((u) => state.admin.selectedUserIds.has(Number(u.userId) || 0)).length;
    el.adminUsersSelectAll.checked = selectedCount === rows.length;
    el.adminUsersSelectAll.indeterminate = selectedCount > 0 && selectedCount < rows.length;
  }

  function renderAdminUsers() {
    if (!el.adminUsersTbody) return;
    el.adminUsersTbody.textContent = "";

    const rows = Array.isArray(state.admin.users.rows) ? state.admin.users.rows : [];
    for (const u of rows) {
      const userId = Number(u.userId) || 0;

      const tr = document.createElement("tr");

      const tdSel = document.createElement("td");
      const cb = document.createElement("input");
      cb.type = "checkbox";
      cb.checked = state.admin.selectedUserIds.has(userId);
      cb.addEventListener("change", () => {
        if (cb.checked) state.admin.selectedUserIds.add(userId);
        else state.admin.selectedUserIds.delete(userId);
        updateAdminUsersSelectAllCheckbox();
      });
      tdSel.appendChild(cb);

      const tdId = document.createElement("td");
      tdId.textContent = String(userId || "-");
      tdId.style.whiteSpace = "nowrap";

      const tdUser = document.createElement("td");
      tdUser.className = "consoleCell--ellipsis";
      tdUser.textContent = String(u.username || "-");
      tdUser.title = String(u.username || "");

      const tdEmail = document.createElement("td");
      const email = String(u.email || "");
      tdEmail.className = "consoleCell--ellipsis";
      tdEmail.textContent = email ? `${email}${u.emailVerified ? "（已验证）" : "（未验证）"}` : "-";
      tdEmail.title = email;

      const tdStorage = document.createElement("td");
      tdStorage.textContent = formatBytes(Number(u.storage?.usedBytes) || 0);
      tdStorage.style.whiteSpace = "nowrap";

      const tdFiles = document.createElement("td");
      tdFiles.textContent = String(Number(u.storage?.fileCount) || 0);
      tdFiles.style.whiteSpace = "nowrap";

      const tdSync = document.createElement("td");
      const lastSync = Number(u.sync?.lastSyncAtMs) || 0;
      const total = Number(u.sync?.totalCount) || 0;
      tdSync.textContent = total ? `${formatTime(lastSync)} / ${total}` : "-";
      tdSync.style.whiteSpace = "nowrap";

      const tdErr = document.createElement("td");
      const errRate = Number(u.sync?.errorRate) || 0;
      const errCount = Number(u.sync?.errorCount) || 0;
      tdErr.textContent = total ? `${formatErrorRate(errRate)} (${errCount}/${total})` : "-";
      tdErr.style.whiteSpace = "nowrap";

      const tdActions = document.createElement("td");
      tdActions.style.whiteSpace = "nowrap";

      const btnDetail = document.createElement("button");
      btnDetail.type = "button";
      btnDetail.className = "button";
      btnDetail.textContent = "详情";
      btnDetail.addEventListener("click", () => void loadAdminUserSyncSummary(userId));

      const btnToggleSync = document.createElement("button");
      btnToggleSync.type = "button";
      btnToggleSync.className = "button";
      btnToggleSync.textContent = u.syncDisabled ? "启用同步" : "禁用同步";
      btnToggleSync.addEventListener("click", () => void adminSetUserSyncDisabled(userId, !u.syncDisabled));

      const btnDelete = document.createElement("button");
      btnDelete.type = "button";
      btnDelete.className = "button";
      btnDelete.textContent = "删除";
      btnDelete.addEventListener("click", () => {
        const ok = window.confirm(`删除用户 #${userId} 并清空数据/存储？此操作不可恢复。`);
        if (ok) void adminDeleteUser(userId);
      });

      tdActions.appendChild(btnDetail);
      tdActions.appendChild(document.createTextNode(" "));
      tdActions.appendChild(btnToggleSync);
      tdActions.appendChild(document.createTextNode(" "));
      tdActions.appendChild(btnDelete);

      tr.appendChild(tdSel);
      tr.appendChild(tdId);
      tr.appendChild(tdUser);
      tr.appendChild(tdEmail);
      tr.appendChild(tdStorage);
      tr.appendChild(tdFiles);
      tr.appendChild(tdSync);
      tr.appendChild(tdErr);
      tr.appendChild(tdActions);

      el.adminUsersTbody.appendChild(tr);
    }

    updateAdminUsersSelectAllCheckbox();
  }

  async function loadAdminUsersPage({ resetOffset = false } = {}) {
    if (!state.isAdmin) return;

    const q = String(el.adminUsersSearch?.value ?? state.admin.users.q ?? "").trim();
    state.admin.users.q = q;
    if (resetOffset) state.admin.users.offset = 0;

    const limit = state.admin.users.limit || 50;
    const offset = state.admin.users.offset || 0;
    const url = `/api/admin/users?limit=${encodeURIComponent(String(limit))}&offset=${encodeURIComponent(String(offset))}${q ? `&q=${encodeURIComponent(q)}` : ""}`;

    setButtonLoading(el.adminUsersRefreshBtn, true);
    try {
      const r = await requestJson(url, { method: "GET", auth: true });
      if (!r.ok) {
        showToast(r.error || "用户列表获取失败", "error");
        return;
      }
      state.admin.users.total = Number(r.value?.total) || 0;
      state.admin.users.offset = Number(r.value?.offset) || 0;
      state.admin.users.limit = Number(r.value?.limit) || limit;
      state.admin.users.rows = Array.isArray(r.value?.users) ? r.value.users : [];
      renderAdminUsers();

      if (el.adminUsersPagerText) {
        const total = state.admin.users.total;
        const start = total ? state.admin.users.offset + 1 : 0;
        const end = Math.min(state.admin.users.offset + state.admin.users.limit, total);
        el.adminUsersPagerText.textContent = total ? `${start}-${end} / ${total}` : "0 / 0";
      }

      if (el.adminUsersPrevBtn) el.adminUsersPrevBtn.disabled = state.admin.users.offset <= 0;
      if (el.adminUsersNextBtn) el.adminUsersNextBtn.disabled = state.admin.users.offset + state.admin.users.limit >= state.admin.users.total;
    } finally {
      setButtonLoading(el.adminUsersRefreshBtn, false);
    }
  }

  async function adminToggleSyncAll() {
    if (!state.isAdmin) return;
    const next = !state.admin.syncDisabledAll;
    setButtonLoading(el.adminToggleSyncAllBtn, true);
    try {
      const r = await requestJson("/api/admin/sync/disableAll", { method: "POST", body: { disabled: next }, auth: true });
      if (!r.ok) {
        showToast(r.error || "操作失败", "error");
        return;
      }
      state.admin.syncDisabledAll = Boolean(r.value?.syncDisabledAll);
      await refreshAdminStatus({ silent: true });
      showToast(state.admin.syncDisabledAll ? "已禁用全体同步" : "已启用全体同步");
    } finally {
      setButtonLoading(el.adminToggleSyncAllBtn, false);
    }
  }

  async function adminSetUserSyncDisabled(userId, disabled) {
    if (!state.isAdmin) return;
    const safeId = Number(userId);
    if (!Number.isFinite(safeId) || safeId <= 0) return;

    const r = await requestJson(`/api/admin/users/${encodeURIComponent(String(safeId))}/sync`, {
      method: "POST",
      body: { disabled },
      auth: true
    });
    if (!r.ok) {
      showToast(r.error || "操作失败", "error");
      return;
    }
    showToast(disabled ? `已禁用用户 #${safeId} 同步` : `已启用用户 #${safeId} 同步`);
    await loadAdminUsersPage();
  }

  async function adminDeleteUser(userId) {
    if (!state.isAdmin) return;
    const safeId = Number(userId);
    if (!Number.isFinite(safeId) || safeId <= 0) return;

    const r = await requestJson(`/api/admin/users/${encodeURIComponent(String(safeId))}`, { method: "DELETE", auth: true });
    if (!r.ok) {
      showToast(r.error || "删除失败", "error");
      return;
    }
    state.admin.selectedUserIds.delete(safeId);
    if (state.admin.selectedUserId === safeId) {
      state.admin.selectedUserId = 0;
      if (el.adminUserSyncTitle) el.adminUserSyncTitle.textContent = "用户同步详情";
      if (el.adminUserSyncTopErrors) el.adminUserSyncTopErrors.textContent = "-";
      drawSyncSummaryChart(el.adminUserSyncChart, []);
    }
    showToast(`已删除用户 #${safeId}`);
    await loadAdminUsersPage({ resetOffset: true });
  }

  async function loadAdminUserSyncSummary(userId) {
    if (!state.isAdmin) return;
    const safeId = Number(userId);
    if (!Number.isFinite(safeId) || safeId <= 0) return;
    state.admin.selectedUserId = safeId;
    if (el.adminUserSyncTitle) el.adminUserSyncTitle.textContent = `用户同步详情：#${safeId}`;

    const daysRaw = Number(el.adminUserSyncDaysInput?.value || 30);
    const days = Number.isFinite(daysRaw) ? Math.min(Math.max(Math.floor(daysRaw), 1), 365) : 30;

    setButtonLoading(el.adminRefreshUserSyncBtn, true);
    try {
      const r = await requestJson(`/api/admin/users/${encodeURIComponent(String(safeId))}/sync/summary?days=${encodeURIComponent(String(days))}`, {
        method: "GET",
        auth: true
      });
      if (!r.ok) {
        showToast(r.error || "用户同步详情获取失败", "error");
        return;
      }
      const v = r.value || {};
      state.admin.userSyncSummary = { userId: safeId, days, series: v.series || [], topErrorCodes: v.topErrorCodes || [] };
      drawSyncSummaryChart(el.adminUserSyncChart, state.admin.userSyncSummary.series);

      if (el.adminUserSyncTopErrors) {
        const list = Array.isArray(state.admin.userSyncSummary.topErrorCodes) ? state.admin.userSyncSummary.topErrorCodes : [];
        el.adminUserSyncTopErrors.textContent = list.length
          ? list
              .slice(0, 8)
              .map((x) => `${String(x.code || "") || "?"}(${Number(x.count) || 0})`)
              .join(", ")
          : "-";
      }
    } finally {
      setButtonLoading(el.adminRefreshUserSyncBtn, false);
    }
  }

  async function adminSendBroadcast() {
    if (!state.isAdmin) return;
    const userIds = Array.from(state.admin.selectedUserIds);
    if (!userIds.length) {
      showToast("请先在用户列表中勾选收件人", "error");
      return;
    }

    const subject = String(el.adminMailSubject?.value || "").trim();
    const body = String(el.adminMailBody?.value || "").trim();
    if (!subject || !body) {
      showToast("请输入邮件主题与正文", "error");
      return;
    }

    setButtonLoading(el.adminSendMailBtn, true);
    try {
      const r = await requestJson("/api/admin/email/broadcast", { method: "POST", body: { userIds, subject, text: body }, auth: true });
      if (!r.ok) {
        showToast(r.error || "发送失败", "error");
        return;
      }
      showToast(`发送完成：成功 ${Number(r.value?.sentCount) || 0}，失败 ${Number(r.value?.failedCount) || 0}`);
    } finally {
      setButtonLoading(el.adminSendMailBtn, false);
    }
  }

  async function loadAdminDashboard() {
    const ok = await refreshAdminStatus({ silent: true });
    if (!ok) return false;
    await loadAdminSyncSummary();
    await loadAdminUsersPage({ resetOffset: true });
    if (state.admin.selectedUserId) await loadAdminUserSyncSummary(state.admin.selectedUserId);
    return true;
  }

  async function enterConsole() {
    const me = await loadMe();
    if (!me) {
      clearAuth();
      setAuthedUI(false, "");
      return;
    }

    setAuthedUI(true, `已登录：${String(me.username || "") || "账号"}`);

    const adminOk = await refreshAdminStatus({ silent: true });
    if (adminOk) {
      switchConsoleTab("admin");
      await loadAdminDashboard();
      return;
    }

    switchConsoleTab("overview");
    await loadStorageStats();
  }

  function initBaseUrl() {
    const saved = localStorage.getItem(LS.baseUrl);
    state.baseUrl = normalizeBaseUrl(saved || defaultBaseUrl());
    el.baseUrlInput.value = state.baseUrl;
  }

  function initAuthFromStorage() {
    state.token = localStorage.getItem(LS.token) || "";
    state.sessionId = localStorage.getItem(LS.sessionId) || "";
    state.refreshToken = localStorage.getItem(LS.refreshToken) || "";
  }

  async function saveBaseUrl() {
    const raw = String(el.baseUrlInput.value || "").trim();
    const next = normalizeBaseUrl(raw);
    const changed = next !== state.baseUrl;
    state.baseUrl = next;
    localStorage.setItem(LS.baseUrl, state.baseUrl);
    el.baseUrlInput.value = state.baseUrl;
    if (changed) {
      clearAuth();
      setAuthedUI(false, "");
      showToast("已切换服务地址，请重新登录");
    } else {
      showToast("已保存");
    }
  }

  function initHandlers() {
    document.querySelectorAll('.consoleTabs [data-tab]').forEach((btn) => {
      btn.addEventListener("click", () => switchAuthTab(btn.dataset.tab));
    });

    document.querySelectorAll('.consoleTabs [data-console-tab]').forEach((btn) => {
      btn.addEventListener("click", async () => {
        const tab = btn.dataset.consoleTab;
        switchConsoleTab(tab);
        if (tab === "overview") await refreshOverview();
        if (tab === "storage") await refreshStorage();
        if (tab === "devices") await loadDevices();
        if (tab === "sync") await loadSyncLogs();
        if (tab === "account") await loadMe();
        if (tab === "admin") {
          const ok = await loadAdminDashboard();
          if (!ok) {
            showToast("无管理员权限（需：管理员邮箱 + 邮箱登录）", "error");
            switchConsoleTab("overview");
          }
        }
      });
    });

    el.baseUrlSaveBtn.addEventListener("click", () => void saveBaseUrl());
    el.logoutBtn.addEventListener("click", () => void doLogout());

    el.loginBtn.addEventListener("click", () => void doLogin());
    el.registerBtn.addEventListener("click", () => void doRegister());
    el.registerSendCodeBtn.addEventListener("click", () => void doSendEmailCode("register", el.registerEmail, el.registerSendCodeBtn));

    el.verifySendCodeBtn.addEventListener("click", () => void doSendEmailCode("login", el.verifyEmail, el.verifySendCodeBtn));
    el.verifyBtn.addEventListener("click", () => void doEmailLogin());

    el.refreshOverviewBtn.addEventListener("click", () => void refreshOverview());

    el.saveEmailBtn.addEventListener("click", () => void saveEmail());
    el.emailSendVerifyCodeBtn.addEventListener("click", () => void doSendEmailCode("verify", el.emailInput, el.emailSendVerifyCodeBtn));
    el.emailVerifyBtn.addEventListener("click", () => void doVerifyEmailInline(el.emailInput.value, el.emailVerifyCode.value, el.emailVerifyBtn));

    el.changePasswordBtn.addEventListener("click", () => void changePassword());

    el.uploadAvatarBtn.addEventListener("click", () => void uploadAvatar());
    el.deleteAvatarBtn.addEventListener("click", () => void deleteAvatar());

    el.exportZipBtn.addEventListener("click", () => void exportZip());
    el.refreshStorageBtn.addEventListener("click", () => void refreshStorage());
    el.storageSearch.addEventListener("input", () => renderStorageFiles());

    el.storagePrevBtn.addEventListener("click", () => {
      state.storage.offset = Math.max(0, state.storage.offset - state.storage.limit);
      void loadStorageFilesPage();
    });
    el.storageNextBtn.addEventListener("click", () => {
      state.storage.offset = state.storage.offset + state.storage.limit;
      void loadStorageFilesPage();
    });

    el.refreshDevicesBtn.addEventListener("click", () => void loadDevices());
    el.refreshSyncLogsBtn.addEventListener("click", () => void loadSyncLogs());

    el.adminRefreshStatusBtn?.addEventListener("click", () => void refreshAdminStatus());
    el.adminToggleSyncAllBtn?.addEventListener("click", () => void adminToggleSyncAll());
    el.adminRefreshSyncSummaryBtn?.addEventListener("click", () => void loadAdminSyncSummary());

    el.adminUsersRefreshBtn?.addEventListener("click", () => void loadAdminUsersPage({ resetOffset: true }));
    el.adminUsersSearch?.addEventListener("keydown", (e) => {
      if (e.key === "Enter") void loadAdminUsersPage({ resetOffset: true });
    });
    el.adminUsersPrevBtn?.addEventListener("click", () => {
      state.admin.users.offset = Math.max(0, (Number(state.admin.users.offset) || 0) - (Number(state.admin.users.limit) || 50));
      void loadAdminUsersPage();
    });
    el.adminUsersNextBtn?.addEventListener("click", () => {
      state.admin.users.offset = (Number(state.admin.users.offset) || 0) + (Number(state.admin.users.limit) || 50);
      void loadAdminUsersPage();
    });
    el.adminUsersSelectAll?.addEventListener("change", () => {
      const checked = Boolean(el.adminUsersSelectAll.checked);
      const rows = Array.isArray(state.admin.users.rows) ? state.admin.users.rows : [];
      for (const u of rows) {
        const id = Number(u.userId) || 0;
        if (!id) continue;
        if (checked) state.admin.selectedUserIds.add(id);
        else state.admin.selectedUserIds.delete(id);
      }
      renderAdminUsers();
    });

    el.adminSendMailBtn?.addEventListener("click", () => void adminSendBroadcast());
    el.adminRefreshUserSyncBtn?.addEventListener("click", () => {
      if (state.admin.selectedUserId) void loadAdminUserSyncSummary(state.admin.selectedUserId);
    });
  }

  async function boot() {
    initBaseUrl();
    initAuthFromStorage();
    initHandlers();
    switchAuthTab("login");

    if (!state.token) {
      setAuthedUI(false, "");
      return;
    }

    await enterConsole();
  }

  void boot();
})();
