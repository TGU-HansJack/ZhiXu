import React, { useEffect, useMemo, useRef, useState } from "react";
import { emitTo } from "@tauri-apps/api/event";
import { getCurrentWindow } from "@tauri-apps/api/window";
import type { InstalledPlugin } from "./lib/plugins/types";
import { runInstalledPluginAction } from "./lib/plugins/runtime";
import { listInstalledPlugins } from "./lib/plugins/workshop";
import { setVaultRoot } from "./lib/vaultApi";
import { IconClose, IconLucidePin } from "./components/icons";
import { getDesktopWidgetState, setDesktopWidgetState, type DesktopWidgetState } from "./lib/widgets";

function escapeHtml(text: string): string {
  return String(text || "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function getRootCssVar(name: string, fallback: string): string {
  try {
    const v = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
    return v || fallback;
  } catch {
    return fallback;
  }
}

function buildPluginSrcDoc(opts: {
  title: string;
  html: string;
  bg: string;
  text: string;
  muted: string;
  accent: string;
  border: string;
  pluginId?: string;
  viewActionId?: string;
}) {
  const title = escapeHtml(opts.title || "Plugin");
  const html = String(opts.html || "");
  const pluginId = String(opts.pluginId || "");
  const viewActionId = String(opts.viewActionId || "");

  return `<!doctype html>
<html>
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>${title}</title>
    <style>
      :root {
        --bg: ${opts.bg};
        --text: ${opts.text};
        --muted: ${opts.muted};
        --accent: ${opts.accent};
        --border: ${opts.border};
        color-scheme: light dark;
      }
      html, body {
        height: 100%;
        margin: 0;
        background: transparent;
        color: var(--text);
        font: 13px/1.4 system-ui, -apple-system, "Segoe UI", Roboto, Arial, sans-serif;
      }
      body { padding: 10px; box-sizing: border-box; }
      a { color: var(--accent); }
      pre, code { font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace; }
      pre { white-space: pre-wrap; word-break: break-word; }
      hr { border: 0; border-top: 1px solid var(--border); margin: 10px 0; }
      * { scrollbar-width: thin; scrollbar-color: rgba(0, 0, 0, 0.18) transparent; }
      *::-webkit-scrollbar { width: 6px; height: 6px; }
      *::-webkit-scrollbar-track { background: transparent; }
      *::-webkit-scrollbar-thumb { background: rgba(0, 0, 0, 0.18); border-radius: 999px; }
      *::-webkit-scrollbar-thumb:hover { background: rgba(0, 0, 0, 0.28); }
      *::-webkit-scrollbar-corner { background: transparent; }
      *::-webkit-scrollbar-button,
      *::-webkit-scrollbar-button:single-button,
      *::-webkit-scrollbar-button:single-button:vertical:decrement,
      *::-webkit-scrollbar-button:single-button:vertical:increment,
      *::-webkit-scrollbar-button:single-button:horizontal:decrement,
      *::-webkit-scrollbar-button:single-button:horizontal:increment,
      *::-webkit-scrollbar-button:start:decrement,
      *::-webkit-scrollbar-button:end:increment,
      *::-webkit-scrollbar-button:vertical:start:decrement,
      *::-webkit-scrollbar-button:vertical:end:increment,
      *::-webkit-scrollbar-button:horizontal:start:decrement,
      *::-webkit-scrollbar-button:horizontal:end:increment {
        width: 0 !important;
        height: 0 !important;
        display: none !important;
        background: transparent !important;
        -webkit-appearance: none !important;
        appearance: none !important;
      }
    </style>
    <script>
      (function () {
        const pluginId = ${JSON.stringify(pluginId)};
        const viewActionId = ${JSON.stringify(viewActionId)};
        const pending = new Map();

        function makeId() {
          return Math.random().toString(36).slice(2) + Date.now().toString(36);
        }

        function request(kind, payload, timeoutMs) {
          const id = makeId();
          const tm = typeof timeoutMs === "number" && timeoutMs > 0 ? timeoutMs : 30000;
          return new Promise(function (resolve, reject) {
            const timer = window.setTimeout(function () {
              pending.delete(id);
              reject(new Error("Request timeout"));
            }, tm);

            pending.set(id, { resolve: resolve, reject: reject, timer: timer });

            try {
              (window.parent || window.top).postMessage({ __zhixuPlugin: true, id: id, kind: kind, pluginId: pluginId, payload: payload }, "*");
            } catch (e) {
              window.clearTimeout(timer);
              pending.delete(id);
              reject(e);
            }
          });
        }

        window.addEventListener("message", function (ev) {
          const msg = ev && ev.data;
          if (!msg || typeof msg !== "object") return;
          if (msg.__zhixuPlugin !== true) return;
          if (msg.kind !== "response") return;
          const entry = pending.get(msg.id);
          if (!entry) return;
          pending.delete(msg.id);
          window.clearTimeout(entry.timer);
          if (msg.ok) entry.resolve(msg.result);
          else entry.reject(new Error(String(msg.error || "Request failed")));
        });

        window.ZhixuPlugin = {
          pluginId: pluginId,
          viewActionId: viewActionId,
          request: request,
          runAction: function (actionId, input, timeoutMs) {
            return request("runAction", { actionId: actionId, input: input }, timeoutMs);
          },
          openFile: function (path, lineIndex) {
            return request("openFile", { path: path, lineIndex: lineIndex }, 15000);
          },
        };
      })();
    </script>
  </head>
  <body data-zhixu-plugin-id="${escapeHtml(pluginId)}" data-zhixu-view-action="${escapeHtml(viewActionId)}">
    ${html}
  </body>
</html>`;
}

function clamp(n: number, lo: number, hi: number) {
  return Math.min(hi, Math.max(lo, n));
}

export function WidgetWindow() {
  const appWindow = useMemo(() => getCurrentWindow(), []);
  const urlParams = useMemo(() => new URLSearchParams(window.location.search), []);
  const pluginId = useMemo(() => String(urlParams.get("pluginId") || "").trim(), [urlParams]);
  const actionId = useMemo(() => String(urlParams.get("actionId") || "").trim(), [urlParams]);
  const initialVaultParam = useMemo(() => urlParams.get("vaultRoot"), [urlParams]);

  const [vaultRoot, setVaultRootState] = useState<string | null>(null);
  const [installed, setInstalled] = useState<InstalledPlugin[]>([]);
  const [plugin, setPlugin] = useState<InstalledPlugin | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [locked, setLocked] = useState<boolean>(() => {
    const state = pluginId ? getDesktopWidgetState(pluginId) : null;
    return state?.locked === true;
  });

  const frameRef = useRef<HTMLIFrameElement | null>(null);
  const latestRef = useRef({ pluginId, actionId });
  latestRef.current = { pluginId, actionId };

  const theme = useMemo(() => {
    return {
      bg: getRootCssVar("--panel", "rgba(245, 245, 245, 0.75)"),
      text: getRootCssVar("--text", "rgba(0, 0, 0, 0.88)"),
      muted: getRootCssVar("--muted", "rgba(0, 0, 0, 0.55)"),
      accent: getRootCssVar("--accent", "#2f6feb"),
      border: getRootCssVar("--border", "rgba(0, 0, 0, 0.08)"),
    };
  }, []);

  useEffect(() => {
    if (!pluginId || !actionId) {
      setError("缺少 pluginId/actionId");
      setLoading(false);
      return;
    }

    let cancelled = false;
    setLoading(true);
    setError(null);
    void (async () => {
      try {
        const resolvedVault = initialVaultParam ? await setVaultRoot(initialVaultParam) : null;
        if (cancelled) return;
        if (!resolvedVault) {
          setError("未选择库（Vault）");
          setLoading(false);
          return;
        }
        setVaultRootState(resolvedVault);

        const plugins = await listInstalledPlugins(resolvedVault);
        if (cancelled) return;
        setInstalled(plugins);

        const found = plugins.find((p) => p.enabled && p.manifest.id === pluginId) || null;
        if (!found) {
          setError("插件未安装或已禁用");
          setLoading(false);
          return;
        }
        setPlugin(found);
        setLoading(false);
      } catch (e) {
        if (cancelled) return;
        setError(String(e instanceof Error ? e.message : e));
        setLoading(false);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [actionId, initialVaultParam, pluginId]);

  const [view, setView] = useState<{ title: string; html: string } | null>(null);
  useEffect(() => {
    if (!vaultRoot || !plugin || !pluginId || !actionId) {
      setView(null);
      return;
    }
    let cancelled = false;
    void (async () => {
      try {
        const { result } = await runInstalledPluginAction({ vaultRoot, plugin, actionId });
        if (cancelled) return;
        let title = plugin.manifest.name || pluginId;
        let html = "";
        if (result && typeof result === "object") {
          const anyRes = result as any;
          if (typeof anyRes.title === "string" && anyRes.title.trim()) title = anyRes.title.trim();
          if (typeof anyRes.html === "string") html = anyRes.html;
          else if (typeof anyRes.message === "string" && anyRes.message.trim()) html = `<pre>${escapeHtml(anyRes.message)}</pre>`;
        } else if (typeof result === "string") {
          const trimmed = result.trim();
          html = trimmed.startsWith("<") ? result : `<pre>${escapeHtml(result)}</pre>`;
        }
        if (!html.trim()) html = `<div style="color: var(--muted);">暂无内容</div>`;
        setView({ title, html });
      } catch (e) {
        if (cancelled) return;
        setView({ title: plugin.manifest.name || pluginId, html: `<pre>${escapeHtml(String(e instanceof Error ? e.message : e))}</pre>` });
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [actionId, plugin, pluginId, vaultRoot]);

  const srcDoc = useMemo(() => {
    if (!view) return "";
    return buildPluginSrcDoc({
      title: view.title,
      html: view.html,
      pluginId,
      viewActionId: actionId,
      ...theme,
    });
  }, [actionId, pluginId, theme, view]);

  useEffect(() => {
    function onMessage(ev: MessageEvent) {
      const msg = ev.data as any;
      if (!msg || typeof msg !== "object") return;
      if (msg.__zhixuPlugin !== true) return;

      const source = ev.source as Window | null;
      const frameWin = frameRef.current?.contentWindow ?? null;
      if (!source || source !== frameWin) return;

      const requestId = String(msg.id || "");
      const kind = String(msg.kind || "");
      const msgPluginId = String(msg.pluginId || "");
      const { pluginId: expectedPluginId, actionId: expectedActionId } = latestRef.current;

      function respond(ok: boolean, payload: { result?: unknown; error?: string }) {
        try {
          source.postMessage(
            { __zhixuPlugin: true, kind: "response", id: requestId, ok, result: payload.result, error: payload.error },
            "*",
          );
        } catch {
          // ignore
        }
      }

      if (!expectedPluginId || msgPluginId !== expectedPluginId) {
        respond(false, { error: "Plugin context mismatch" });
        return;
      }

      if (kind === "openFile") {
        const p = String(msg.payload?.path || "");
        const lineIndexRaw = msg.payload?.lineIndex;
        const lineIndex = typeof lineIndexRaw === "number" && Number.isFinite(lineIndexRaw) ? Math.max(0, Math.floor(lineIndexRaw)) : null;
        if (!p) {
          respond(false, { error: "Missing path" });
          return;
        }
        void emitTo("main", "zhixu:open-file", { path: p, lineIndex })
          .then(() => respond(true, { result: { ok: true } }))
          .catch((e) => respond(false, { error: String(e instanceof Error ? e.message : e) }));
        return;
      }

      if (kind === "runAction") {
        const runActionId = String(msg.payload?.actionId || "");
        const input = msg.payload?.input;
        if (!runActionId) {
          respond(false, { error: "Missing actionId" });
          return;
        }
        if (!vaultRoot) {
          respond(false, { error: "No vault selected" });
          return;
        }
        const found = installed.find((p) => p.enabled && p.manifest.id === expectedPluginId);
        if (!found) {
          respond(false, { error: "Plugin not installed or disabled" });
          return;
        }
        void runInstalledPluginAction({ vaultRoot, plugin: found, actionId: runActionId, input })
          .then(({ result }) => respond(true, { result }))
          .catch((e) => respond(false, { error: String(e instanceof Error ? e.message : e) }));
        return;
      }

      respond(false, { error: "Unknown request" });
    }

    window.addEventListener("message", onMessage);
    return () => window.removeEventListener("message", onMessage);
  }, [installed, vaultRoot]);

  useEffect(() => {
    if (!pluginId) return;
    const key = pluginId;
    const next: DesktopWidgetState = {
      ...(getDesktopWidgetState(pluginId) || { pluginId, actionId }),
      pluginId,
      actionId,
      enabled: true,
      locked,
    };
    setDesktopWidgetState(key, next);
  }, [actionId, locked, pluginId]);

  useEffect(() => {
    if (!pluginId) return;
    let unlistenMoved: null | (() => void) = null;
    let unlistenResized: null | (() => void) = null;
    let unlistenClose: null | (() => void) = null;
    let timer: number | null = null;

    const persistBounds = () => {
      if (timer != null) window.clearTimeout(timer);
      timer = window.setTimeout(() => {
        void (async () => {
          try {
            const [pos, size, scale] = await Promise.all([appWindow.outerPosition(), appWindow.outerSize(), appWindow.scaleFactor()]);
            const x = Math.round(pos.x / scale);
            const y = Math.round(pos.y / scale);
            const width = Math.round(size.width / scale);
            const height = Math.round(size.height / scale);
            const prev = getDesktopWidgetState(pluginId);
            if (!prev) return;
            setDesktopWidgetState(pluginId, {
              ...prev,
              x,
              y,
              width: clamp(width, 200, 1200),
              height: clamp(height, 140, 1200),
            });
          } catch {
            // ignore
          }
        })();
      }, 180);
    };

    void (async () => {
      unlistenMoved = await appWindow.onMoved(() => persistBounds());
      unlistenResized = await appWindow.onResized(() => persistBounds());
      unlistenClose = await appWindow.onCloseRequested(() => {
        const prev = getDesktopWidgetState(pluginId);
        if (prev) setDesktopWidgetState(pluginId, { ...prev, enabled: false });
      });
    })();

    return () => {
      unlistenMoved?.();
      unlistenResized?.();
      unlistenClose?.();
      if (timer != null) window.clearTimeout(timer);
    };
  }, [appWindow, pluginId]);

  const title = view?.title || plugin?.manifest?.name || "小组件";

  return (
    <div className="widgetRoot" data-locked={locked ? "true" : "false"}>
      <div
        className="widgetHeader"
        onPointerDown={(e) => {
          if (locked) return;
          if ((e.target as HTMLElement | null)?.closest?.("button")) return;
          void appWindow.startDragging();
        }}
      >
        <div className="widgetTitle" title={title}>
          {title}
        </div>
        <div className="widgetHeaderActions">
          <button
            type="button"
            className={`iconBtn widgetIconBtn${locked ? " active" : ""}`}
            data-no-drag="true"
            aria-label={locked ? "解锁" : "锁定"}
            onClick={() => setLocked((v) => !v)}
          >
            <IconLucidePin size={16} />
          </button>
          <button
            type="button"
            className="iconBtn widgetIconBtn"
            data-no-drag="true"
            aria-label="关闭"
            onClick={() => {
              if (pluginId) {
                const prev = getDesktopWidgetState(pluginId);
                if (prev) setDesktopWidgetState(pluginId, { ...prev, enabled: false });
              }
              void appWindow.close();
            }}
          >
            <IconClose size={16} />
          </button>
        </div>
      </div>

      <div className="widgetBody">
        {loading ? <div className="widgetHint">正在加载…</div> : null}
        {error ? <div className="widgetHint error">{error}</div> : null}
        {!loading && !error ? (
          <iframe ref={frameRef} className="widgetFrame" title={title} sandbox="allow-scripts" srcDoc={srcDoc} />
        ) : null}
      </div>

      <div
        className="widgetResizeHandle"
        role="presentation"
        aria-hidden="true"
        onPointerDown={(e) => {
          e.preventDefault();
          e.stopPropagation();
          if (locked) return;
          void appWindow.startResizeDragging("SouthEast");
        }}
      />
    </div>
  );
}
