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
    mePlan: $("mePlan"),

    storageUsed: $("storageUsed"),
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
    syncLogsTbody: $("syncLogsTbody")
  };

  const state = {
    baseUrl: "",
    token: "",
    sessionId: "",
    refreshToken: "",
    avatarObjectUrl: "",
    storage: {
      total: 0,
      offset: 0,
      limit: 50,
      files: []
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

    if (!res.ok) {
      const msg = String(obj?.error || obj?.message || text || `HTTP ${res.status}`).trim();
      return { ok: false, status: res.status, error: msg || `HTTP ${res.status}`, value: obj };
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
    if (!username || !password) {
      showToast("请输入用户名与密码", "error");
      return;
    }

    setButtonLoading(el.registerBtn, true);
    try {
      const body = { username, password };
      if (email) body.email = email;
      if (emailCode) body.emailCode = emailCode;
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

  async function doVerifyEmail() {
    const email = String(el.verifyEmail.value || "").trim();
    const code = String(el.verifyCode.value || "").trim();
    if (!email || !code) {
      showToast("请输入邮箱与验证码", "error");
      return;
    }

    setButtonLoading(el.verifyBtn, true);
    try {
      const r = await requestJson("/api/auth/email/verify", { method: "POST", body: { email, code }, auth: false });
      if (!r.ok) {
        showToast(r.error || "验证失败", "error");
        return;
      }
      showToast(r.value?.alreadyVerified ? "邮箱已验证" : "验证成功");
      if (state.token) await loadMe();
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
    el.mePlan.textContent = me.plan ? `${String(me.plan.name || me.plan.code || "")} (${formatBytes(Number(me.plan.storageBytes) || 0)})` : "-";

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
    setButtonLoading(el.uploadAvatarBtn, true);
    try {
      const res = await apiFetch(
        "/api/account/avatar",
        {
          method: "PUT",
          headers: { "Content-Type": file.type || "application/octet-stream" },
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
        showToast(String(obj?.error || text || `HTTP ${res.status}`).trim() || "上传失败", "error");
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

  async function enterConsole() {
    const me = await loadMe();
    if (!me) {
      clearAuth();
      setAuthedUI(false, "");
      return;
    }
    setAuthedUI(true, `已登录：${String(me.username || "") || "账号"}`);
    switchConsoleTab("overview");
    await refreshOverview();
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
      });
    });

    el.baseUrlSaveBtn.addEventListener("click", () => void saveBaseUrl());
    el.logoutBtn.addEventListener("click", () => void doLogout());

    el.loginBtn.addEventListener("click", () => void doLogin());
    el.registerBtn.addEventListener("click", () => void doRegister());
    el.registerSendCodeBtn.addEventListener("click", () => void doSendEmailCode("register", el.registerEmail, el.registerSendCodeBtn));

    el.verifySendCodeBtn.addEventListener("click", () => void doSendEmailCode("verify", el.verifyEmail, el.verifySendCodeBtn));
    el.verifyBtn.addEventListener("click", () => void doVerifyEmail());

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
