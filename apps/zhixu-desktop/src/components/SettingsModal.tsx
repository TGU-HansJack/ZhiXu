import React, { useEffect, useMemo, useState } from "react";
import type { OfficialAuthState } from "./AuthModal";
import { logout } from "../lib/sync/officialClient";
import { syncOfficialVault, type OfficialVaultSyncSummary } from "../lib/sync/officialSyncEngine";

export type SettingsSectionId = "pro" | "sync" | "editor" | "ui" | "ai" | "about" | "logs";

type SettingsSection = {
  id: SettingsSectionId;
  label: string;
  description: string;
};

const SECTIONS: SettingsSection[] = [
  { id: "pro", label: "知序 PRO", description: "订阅与高级功能管理。" },
  { id: "sync", label: "同步", description: "同步与设备间数据一致性设置。" },
  { id: "editor", label: "编辑器", description: "编辑体验、自动保存与默认行为设置。" },
  { id: "ui", label: "用户界面", description: "主题、布局与显示相关设置。" },
  { id: "ai", label: "AI", description: "AI 功能入口与偏好设置。" },
  { id: "about", label: "关于", description: "版本信息与相关链接。" },
  { id: "logs", label: "日志", description: "查看与导出运行日志。" },
];

function Toggle({ checked, onChange }: { checked: boolean; onChange: (next: boolean) => void }) {
  return (
    <button
      type="button"
      className={`toggle${checked ? " on" : ""}`}
      role="switch"
      aria-checked={checked}
      data-no-drag="true"
      onClick={() => onChange(!checked)}
    />
  );
}

function SettingsRow({
  title,
  description,
  control,
}: {
  title: string;
  description?: string;
  control?: React.ReactNode;
}) {
  return (
    <div className="settingsRow">
      <div className="settingsRowMain">
        <div className="settingsRowTitle">{title}</div>
        {description ? <div className="settingsRowDesc">{description}</div> : null}
      </div>
      {control ? <div className="settingsRowControl">{control}</div> : null}
    </div>
  );
}

type Props = {
  initialSection?: SettingsSectionId;
  cloudSyncEnabled: boolean;
  onCloudSyncEnabledChange: (next: boolean) => void;
  vaultRoot: string | null;
  officialBaseUrl: string;
  onOfficialBaseUrlChange: (next: string) => void;
  officialAuth: OfficialAuthState | null;
  onOfficialAuthChange: (next: OfficialAuthState | null) => void;
  onOpenAuth: (mode: "login" | "register") => void;
};

export function SettingsModal({
  initialSection = "pro",
  cloudSyncEnabled,
  onCloudSyncEnabledChange,
  vaultRoot,
  officialBaseUrl,
  onOfficialBaseUrlChange,
  officialAuth,
  onOfficialAuthChange,
  onOpenAuth,
}: Props) {
  const [active, setActive] = useState<SettingsSectionId>(initialSection);

  const [aiEnabled, setAiEnabled] = useState(false);
  const [compactMode, setCompactMode] = useState(false);
  const [autoSave, setAutoSave] = useState(true);
  const [syncing, setSyncing] = useState(false);
  const [syncSummary, setSyncSummary] = useState<OfficialVaultSyncSummary | null>(null);
  const [syncError, setSyncError] = useState<string | null>(null);
  const [syncFinishedAt, setSyncFinishedAt] = useState<number | null>(null);
  const [loggingOut, setLoggingOut] = useState(false);

  useEffect(() => {
    setActive(initialSection);
  }, [initialSection]);

  const activeSection = useMemo(() => SECTIONS.find((s) => s.id === active) ?? SECTIONS[0], [active]);

  const runSyncNow = async () => {
    if (syncing) return;
    setSyncError(null);

    if (!cloudSyncEnabled) {
      setSyncError("请先开启“云同步”");
      return;
    }
    if (!vaultRoot) {
      setSyncError("请先打开一个库（Vault）");
      return;
    }
    if (!officialAuth?.token) {
      setSyncError("请先登录账号");
      return;
    }

    setSyncing(true);
    try {
      const summary = await syncOfficialVault({ vaultRoot, baseUrl: officialBaseUrl, token: officialAuth.token });
      setSyncSummary(summary);
      setSyncFinishedAt(Date.now());
    } catch (e) {
      setSyncError(String(e instanceof Error ? e.message : e));
    } finally {
      setSyncing(false);
    }
  };

  const doLogout = async () => {
    if (!officialAuth?.token) {
      onOfficialAuthChange(null);
      return;
    }
    if (loggingOut) return;

    setLoggingOut(true);
    try {
      await logout(officialBaseUrl, officialAuth.token);
    } catch {
      // ignore
    } finally {
      setLoggingOut(false);
      onOfficialAuthChange(null);
    }
  };

  return (
    <div className="settingsModalBody">
      <nav className="settingsNav" aria-label="设置分类">
        <div className="settingsNavList">
          {SECTIONS.map((s) => (
            <button
              key={s.id}
              type="button"
              className={`settingsNavItem${active === s.id ? " active" : ""}`}
              onClick={() => setActive(s.id)}
              data-no-drag="true"
            >
              {s.label}
            </button>
          ))}
        </div>
      </nav>

      <main className="settingsContent">
        <div className="settingsPage">
          <div className="settingsPageHeader">
            <div className="settingsPageTitle">{activeSection.label}</div>
            <div className="settingsPageDesc">{activeSection.description}</div>
          </div>

          {active === "pro" ? (
            <div className="settingsCard">
              <SettingsRow
                title="账号"
                description={officialAuth?.token ? `已登录：${officialAuth.me?.username || "—"}` : "登录后可使用官方同步与订阅能力。"}
                control={
                  officialAuth?.token ? (
                    <button type="button" className="settingsBtn" data-no-drag="true" onClick={() => void doLogout()} disabled={loggingOut}>
                      {loggingOut ? "退出中…" : "退出登录"}
                    </button>
                  ) : (
                    <button type="button" className="settingsBtn" data-no-drag="true" onClick={() => onOpenAuth("login")}>
                      登录/注册
                    </button>
                  )
                }
              />
              <SettingsRow
                title="订阅"
                description={officialAuth?.token ? (officialAuth.me?.plan?.name ? officialAuth.me.plan.name : "未订阅") : "—"}
                control={
                  <button type="button" className="settingsBtn" data-no-drag="true">
                    查看
                  </button>
                }
              />
            </div>
          ) : null}

          {active === "sync" ? (
            <div className="settingsCard">
              <SettingsRow
                title="云同步"
                description="开启或关闭跨端云同步能力。"
                control={<Toggle checked={cloudSyncEnabled} onChange={onCloudSyncEnabledChange} />}
              />

              <div className="syncConfig">
                <div className="syncConfigTitle">官方同步</div>

                <label className="syncField">
                  <div className="syncLabel">服务器</div>
                  <input
                    className="textInput"
                    value={officialBaseUrl}
                    onChange={(e) => onOfficialBaseUrlChange(e.target.value)}
                    placeholder="https://zhixu.app"
                    spellCheck={false}
                  />
                </label>

                <div className="syncAccountRow">
                  <div className="syncAccountInfo">
                    <div className="syncLabel">账号</div>
                    <div className="syncAccountText">
                      {officialAuth?.token ? (
                        <span>
                          已登录：{officialAuth.me?.username || "—"}
                          {officialAuth.me?.plan?.code ? <span className="syncPlan">（{officialAuth.me.plan.code}）</span> : null}
                        </span>
                      ) : (
                        <span className="syncMuted">未登录</span>
                      )}
                    </div>
                  </div>
                  <div className="syncAccountActions">
                    {officialAuth?.token ? (
                      <button type="button" className="settingsBtn" data-no-drag="true" onClick={() => void doLogout()} disabled={loggingOut}>
                        {loggingOut ? "退出中…" : "退出登录"}
                      </button>
                    ) : (
                      <button type="button" className="settingsBtn" data-no-drag="true" onClick={() => onOpenAuth("login")}>
                        登录/注册
                      </button>
                    )}
                  </div>
                </div>

                <div className="syncActionsRow">
                  <button
                    type="button"
                    className="settingsBtn syncNowBtn"
                    data-no-drag="true"
                    onClick={() => void runSyncNow()}
                    disabled={!cloudSyncEnabled || syncing || !vaultRoot || !officialAuth?.token}
                  >
                    {syncing ? "同步中…" : "立即同步"}
                  </button>
                  {!cloudSyncEnabled ? <span className="syncMuted">开启“云同步”后可执行</span> : null}
                </div>

                {syncError ? <div className="syncError">{syncError}</div> : null}

                {syncSummary ? (
                  <div className="syncSummary">
                    <div className="syncSummaryTitle">上次同步</div>
                    <div className="syncSummaryGrid">
                      <div>上传</div>
                      <div>{syncSummary.uploaded}</div>
                      <div>下载</div>
                      <div>{syncSummary.downloaded}</div>
                      <div>远端删除</div>
                      <div>{syncSummary.deletedRemote}</div>
                      <div>本地删除</div>
                      <div>{syncSummary.deletedLocal}</div>
                      <div>冲突</div>
                      <div>{syncSummary.conflicts}</div>
                      <div>失败</div>
                      <div>{syncSummary.failed}</div>
                    </div>
                    {syncFinishedAt ? <div className="syncMuted">完成时间：{new Date(syncFinishedAt).toLocaleString()}</div> : null}
                  </div>
                ) : null}
              </div>
            </div>
          ) : null}

          {active === "editor" ? (
            <div className="settingsCard">
              <SettingsRow
                title="自动保存"
                description="编辑内容时自动保存到本地库。"
                control={<Toggle checked={autoSave} onChange={setAutoSave} />}
              />
              <SettingsRow
                title="默认模式"
                description="选择打开 Markdown 的默认编辑模式。"
                control={
                  <select className="select" data-no-drag="true" defaultValue="live">
                    <option value="live">所见即所得</option>
                    <option value="source">源码</option>
                  </select>
                }
              />
            </div>
          ) : null}

          {active === "ui" ? (
            <div className="settingsCard">
              <SettingsRow
                title="主题"
                description="选择浅色/深色主题（当前仅样式预览）。"
                control={
                  <select className="select" data-no-drag="true" defaultValue="light">
                    <option value="light">浅色</option>
                    <option value="dark">深色</option>
                  </select>
                }
              />
              <SettingsRow
                title="紧凑模式"
                description="减少间距以显示更多内容。"
                control={<Toggle checked={compactMode} onChange={setCompactMode} />}
              />
            </div>
          ) : null}

          {active === "ai" ? (
            <div className="settingsCard">
              <SettingsRow
                title="启用 AI"
                description="开启 AI 相关入口与能力。"
                control={<Toggle checked={aiEnabled} onChange={setAiEnabled} />}
              />
              <SettingsRow
                title="默认模型"
                description="选择用于对话与写作辅助的模型。"
                control={
                  <select className="select" data-no-drag="true" defaultValue="default">
                    <option value="default">默认</option>
                    <option value="custom">自定义</option>
                  </select>
                }
              />
            </div>
          ) : null}

          {active === "about" ? (
            <div className="settingsCard">
              <SettingsRow title="Zhixu Desktop" description="知序桌面端设置界面（预览）。" />
              <SettingsRow
                title="版本"
                description="功能开发中，后续将展示构建信息与变更日志。"
                control={<span className="settingsMeta">0.0.0</span>}
              />
            </div>
          ) : null}

          {active === "logs" ? (
            <div className="settingsCard">
              <SettingsRow
                title="日志输出"
                description="用于排查问题的运行日志（暂为占位）。"
                control={
                  <button type="button" className="settingsBtn" data-no-drag="true">
                    导出
                  </button>
                }
              />
              <textarea className="settingsTextarea" data-no-drag="true" readOnly value="（日志功能开发中）" />
            </div>
          ) : null}
        </div>
      </main>
    </div>
  );
}
