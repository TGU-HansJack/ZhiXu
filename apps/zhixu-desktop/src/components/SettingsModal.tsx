import React, { useMemo, useState } from "react";

type SettingsSectionId = "pro" | "sync" | "editor" | "ui" | "ai" | "about" | "logs";

type SettingsSection = {
  id: SettingsSectionId;
  label: string;
  description: string;
};

const SECTIONS: SettingsSection[] = [
  { id: "pro", label: "知序 PRO", description: "订阅与高级功能管理。" },
  { id: "sync", label: "同步", description: "同步与设备间数据一致性设置。" },
  { id: "editor", label: "编辑器", description: "编辑体验、自动保存与默认行为。" },
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

export function SettingsModal() {
  const [active, setActive] = useState<SettingsSectionId>("pro");

  const [autoSync, setAutoSync] = useState(true);
  const [aiEnabled, setAiEnabled] = useState(false);
  const [compactMode, setCompactMode] = useState(false);
  const [autoSave, setAutoSave] = useState(true);

  const activeSection = useMemo(() => SECTIONS.find((s) => s.id === active) ?? SECTIONS[0], [active]);

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
                title="订阅状态"
                description="管理你的知序 PRO 订阅与权益。"
                control={
                  <button type="button" className="settingsBtn" data-no-drag="true">
                    查看
                  </button>
                }
              />
              <SettingsRow
                title="高级功能"
                description="解锁跨端同步与高级编辑能力。"
                control={
                  <button type="button" className="settingsBtn" data-no-drag="true">
                    了解
                  </button>
                }
              />
            </div>
          ) : null}

          {active === "sync" ? (
            <div className="settingsCard">
              <SettingsRow
                title="自动同步"
                description="在内容变更后自动触发同步。"
                control={<Toggle checked={autoSync} onChange={setAutoSync} />}
              />
              <SettingsRow
                title="同步策略"
                description="选择同步触发与冲突处理方式。"
                control={
                  <select className="select" data-no-drag="true" defaultValue="smart">
                    <option value="smart">智能</option>
                    <option value="manual">手动</option>
                  </select>
                }
              />
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

