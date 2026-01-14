import React from "react";
import { IconClose } from "./icons";

type Props = {
  onClose: () => void;
};

export function PluginDeveloperWindow({ onClose }: Props) {
  return (
    <div
      className="modalBackdrop noDrag"
      data-no-drag="true"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
      role="dialog"
      aria-modal="true"
      aria-label="插件开发者"
    >
      <div className="modalPanel pluginDevModal" data-no-drag="true">
        <div className="modalHeader">
          <div className="modalTitle">插件开发者</div>
          <button className="iconBtn" type="button" data-no-drag="true" aria-label="关闭" onClick={onClose}>
            <IconClose size={16} />
          </button>
        </div>
        <div className="modalBody pluginDevBody">
          <div className="pluginDevIntro">
            这里是桌面端（Tauri）插件接口说明与入门教程。插件以文件夹形式安装到当前 Vault 的{" "}
            <code>.zhixu/plugins/&lt;pluginId&gt;/</code>。
          </div>

          <h3 className="pluginDevH3">目录结构</h3>
          <pre className="pluginDevPre">
            <code>{`my-plugin/
  manifest.json
  main.js
  README.md        # 可选：用于“详情”显示
  config.json      # 可选：插件配置（JSON）
`}</code>
          </pre>

          <h3 className="pluginDevH3">manifest.json</h3>
          <pre className="pluginDevPre">
            <code>{`{
  "id": "webdav-zhixu",
  "name": "WebDAV 同步",
  "version": "1.0.0",
  "description": "在桌面端对 Vault 进行 WebDAV 同步",
  "entry": "main.js",
  "files": ["config.json"],
  "actions": [
    { "id": "testConnection", "label": "测试连接" },
    { "id": "syncNow", "label": "立即同步" }
  ]
}`}</code>
          </pre>

          <h3 className="pluginDevH3">功能区 / 主侧栏（UI）</h3>
          <div className="pluginDevNote">
            通过在 <code>actions</code> 中声明 <code>place</code> + <code>icon</code>，插件可以把按钮图标添加到左侧“功能区”，并支持在主侧栏显示内容。
          </div>
          <pre className="pluginDevPre">
            <code>{`{
  "actions": [
    {
      "id": "myPanel",
      "label": "我的面板",
      "place": "mainSidebar",
      "icon": "<svg ...></svg>",
      "ringIndex": 20
    },
    {
      "id": "quickAction",
      "label": "一键操作",
      "place": "functionArea",
      "icon": "<svg ...></svg>",
      "ringIndex": 30
    }
  ]
}`}</code>
          </pre>
          <ul className="pluginDevList">
            <li>
              <code>place: "mainSidebar"</code>：显示为功能区图标，点击后打开主侧栏，并执行对应 action 获取内容。
            </li>
            <li>
              <code>place: "functionArea"</code>：显示为功能区图标，点击后直接执行 action。
            </li>
            <li>
              <code>icon</code>：建议传入一段 <code>&lt;svg ... stroke="currentColor" ...&gt;</code> 字符串。
            </li>
            <li>
              <code>ringIndex</code>：默认排序（用户也可在功能区长按拖拽重新排序）。
            </li>
          </ul>

          <h3 className="pluginDevH3">入口文件（CommonJS）</h3>
          <div className="pluginDevNote">桌面端 actions 支持 async（返回 Promise）。</div>
          <pre className="pluginDevPre">
            <code>{`async function hello(ctx, api) {
  api.log("hello", ctx.plugin.id);
  return { ok: true, message: "Hello from plugin!" };
}

module.exports = {
  actions: { hello },
};`}</code>
          </pre>
          <div className="pluginDevNote">
            当 action 用于 <code>place: "mainSidebar"</code> 时，可返回 <code>&#123; title, html &#125;</code> 来渲染侧栏内容：
          </div>
          <pre className="pluginDevPre">
            <code>{`async function myPanel(ctx, api) {
  const entries = await api.vault.listDir(".");
  const html = "<h3>Vault</h3><pre>" + entries.map(e => e.name).join("\\n") + "</pre>";
  return { title: ctx.plugin.name, html };
}

module.exports = { actions: { myPanel } };`}</code>
          </pre>

          <h3 className="pluginDevH3">context（ctx）</h3>
          <ul className="pluginDevList">
            <li>
              <code>ctx.plugin</code>：当前插件 manifest（id/name/version/...）
            </li>
            <li>
              <code>ctx.config</code>：插件配置对象（来自 <code>config.json</code>）
            </li>
            <li>
              <code>ctx.vault.root</code>：当前 Vault 的绝对路径
            </li>
            <li>
              <code>ctx.app.platform</code>：固定为 <code>"desktop"</code>
            </li>
          </ul>

          <h3 className="pluginDevH3">API（api）</h3>
          <ul className="pluginDevList">
            <li>
              <code>api.log(...)</code>：输出日志（会显示在“输出”面板）
            </li>
            <li>
              <code>api.http(method, url, body?, contentType?, headers?, timeoutMs?)</code>：HTTP 文本请求（非 2xx 抛错）
            </li>
            <li>
              <code>api.httpRaw(&#123; method, url, headers?, body?, timeoutMs? &#125;)</code>：HTTP 二进制请求（返回{" "}
              <code>&#123; status, ok, headers, bytes &#125;</code>）
            </li>
            <li>
              <code>api.vault.listDir(relPath)</code>：列出目录（返回 path/name/isDir）
            </li>
            <li>
              <code>api.vault.readBytes(relPath)</code> / <code>api.vault.writeBytes(relPath, bytes)</code>：读写二进制
            </li>
            <li>
              <code>api.vault.readText(relPath)</code> / <code>api.vault.writeText(relPath, text)</code>：读写 Markdown 文本
            </li>
          </ul>

          <h3 className="pluginDevH3">发布建议（企业级）</h3>
          <ul className="pluginDevList">
            <li>为每个版本提供稳定的 manifest + README，避免破坏性变更。</li>
            <li>配置默认值向后兼容，升级时不要覆盖用户 config。</li>
            <li>长耗时任务（如同步）提供分阶段日志与可恢复状态（写入 .zhixu/sync/）。</li>
          </ul>
        </div>
      </div>
    </div>
  );
}
