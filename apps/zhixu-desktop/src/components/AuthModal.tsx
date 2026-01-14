import React, { useEffect, useMemo, useRef, useState } from "react";
import { IconClose } from "./icons";
import { login, me, register } from "../lib/sync/officialClient";
import type { SyncServerMe } from "../lib/sync/officialClient";

export type AuthModalMode = "login" | "register";

export type OfficialAuthState = {
  token: string;
  sessionId?: string;
  refreshToken?: string;
  me?: SyncServerMe | null;
};

type Props = {
  mode: AuthModalMode;
  baseUrl: string;
  onBaseUrlChange: (next: string) => void;
  onClose: () => void;
  onAuth: (auth: OfficialAuthState) => void;
};

function normalizeBaseUrl(baseUrl: string): string {
  return String(baseUrl || "").trim().replace(/\/+$/, "");
}

export function AuthModal({ mode, baseUrl, onBaseUrlChange, onClose, onAuth }: Props) {
  const [active, setActive] = useState<AuthModalMode>(mode);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [password2, setPassword2] = useState("");

  const submitBtnLabel = useMemo(() => (active === "login" ? "登录" : "注册并登录"), [active]);
  const usernameLabel = useMemo(() => (active === "login" ? "用户名 / 邮箱" : "用户名"), [active]);

  const usernameRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    setActive(mode);
  }, [mode]);

  useEffect(() => {
    setError(null);
  }, [active]);

  useEffect(() => {
    const id = window.setTimeout(() => usernameRef.current?.focus(), 0);
    return () => window.clearTimeout(id);
  }, []);

  const submit = async () => {
    if (submitting) return;
    setError(null);

    const cleanBaseUrl = normalizeBaseUrl(baseUrl);
    if (!cleanBaseUrl) {
      setError("请输入服务器地址");
      return;
    }

    const u = username.trim();
    if (!u) {
      setError("请输入用户名");
      return;
    }
    if (!password) {
      setError("请输入密码");
      return;
    }
    if (active === "register") {
      if (password.length < 6) {
        setError("密码至少 6 位");
        return;
      }
      if (password2 !== password) {
        setError("两次输入的密码不一致");
        return;
      }
    }

    setSubmitting(true);
    try {
      if (active === "register") {
        const r = await register(cleanBaseUrl, u, password, email.trim() || undefined);
        if (!r.ok) {
          setError(r.errorMessage || "注册失败");
          return;
        }
      }

      const l = await login(cleanBaseUrl, u, password);
      if (!l.ok || !l.value?.token) {
        setError(l.errorMessage || "登录失败");
        return;
      }

      const token = l.value.token;
      const m = await me(cleanBaseUrl, token);
      onAuth({
        token,
        sessionId: l.value.sessionId,
        refreshToken: l.value.refreshToken,
        me: m.ok ? m.value : null,
      });
      onClose();
    } catch (e) {
      setError(String(e instanceof Error ? e.message : e));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      className="modalBackdrop noDrag"
      data-no-drag="true"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
      role="dialog"
      aria-modal="true"
      aria-label="账号登录/注册"
      onKeyDown={(e) => {
        if (e.key === "Escape") {
          e.preventDefault();
          e.stopPropagation();
          onClose();
          return;
        }
        if (e.key === "Enter") {
          e.preventDefault();
          void submit();
          return;
        }
      }}
    >
      <div className="modalPanel authModal" data-no-drag="true">
        <div className="modalHeader">
          <div className="modalTitle">账号</div>
          <button className="iconBtn" type="button" data-no-drag="true" aria-label="关闭" onClick={onClose}>
            <IconClose size={16} />
          </button>
        </div>

        <div className="modalBody authBody">
          <div className="authTabs" role="tablist" aria-label="登录注册">
            <button type="button" className={`authTab${active === "login" ? " active" : ""}`} onClick={() => setActive("login")}>
              登录
            </button>
            <button
              type="button"
              className={`authTab${active === "register" ? " active" : ""}`}
              onClick={() => setActive("register")}
            >
              注册
            </button>
          </div>

          <div className="authForm">
            <label className="authField">
              <div className="authLabel">服务器</div>
              <input
                className="textInput"
                value={baseUrl}
                onChange={(e) => onBaseUrlChange(e.target.value)}
                placeholder="https://zhixu.app"
                spellCheck={false}
              />
            </label>

            <label className="authField">
              <div className="authLabel">{usernameLabel}</div>
              <input
                ref={usernameRef}
                className="textInput"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder={active === "login" ? "username@example.com" : "username"}
                autoComplete="username"
                spellCheck={false}
              />
            </label>

            {active === "register" ? (
              <label className="authField">
                <div className="authLabel">邮箱（可选）</div>
                <input
                  className="textInput"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  placeholder="user@example.com"
                  autoComplete="email"
                  spellCheck={false}
                />
              </label>
            ) : null}

            <label className="authField">
              <div className="authLabel">密码</div>
              <input
                className="textInput"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete={active === "login" ? "current-password" : "new-password"}
              />
            </label>

            {active === "register" ? (
              <label className="authField">
                <div className="authLabel">确认密码</div>
                <input
                  className="textInput"
                  type="password"
                  value={password2}
                  onChange={(e) => setPassword2(e.target.value)}
                  autoComplete="new-password"
                />
              </label>
            ) : null}

            {error ? <div className="authError">{error}</div> : null}

            <div className="authActions">
              <button type="button" className="settingsBtn authSubmitBtn" onClick={() => void submit()} disabled={submitting}>
                {submitting ? "处理中…" : submitBtnLabel}
              </button>
              <button
                type="button"
                className="settingsBtn authCancelBtn"
                onClick={onClose}
                disabled={submitting}
              >
                取消
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}

