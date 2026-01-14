import React, { Suspense, useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { cursorPosition, getCurrentWindow } from "@tauri-apps/api/window";
import { emitTo } from "@tauri-apps/api/event";
import { WebviewWindow } from "@tauri-apps/api/webviewWindow";
import { message } from "@tauri-apps/plugin-dialog";
import type { CodeMirrorSelection } from "./components/CodeMirrorEditor";
import type { MarkdownEditorMode } from "./components/ZhixuMarkdownEditor";
import { FileTree, type TreeNode } from "./components/FileTree";
import { AuthModal, type AuthModalMode, type OfficialAuthState } from "./components/AuthModal";
import { PluginDeveloperWindow } from "./components/PluginDeveloperWindow";
import { Popover } from "./components/Popover";
import { WorkshopEditor } from "./components/WorkshopEditor";
import { WorkshopSidebar } from "./components/WorkshopSidebar";
import { SettingsModal, type SettingsSectionId } from "./components/SettingsModal";
import { Tooltip, type TooltipPlacement } from "./components/Tooltip";
import {
  IconChevronsUpDown,
  IconClose,
  IconFolderPlus,
  IconFolderPlusLucide,
  IconLucideBrush,
  IconLucideCircleUserRound,
  IconLucidePictureInPicture,
  IconLucidePin,
  IconLucideRefreshCw,
  IconLucideRefreshCwOff,
  IconLucideSettings,
  IconMaximize,
  IconMinimize,
  IconPlus,
  IconRefresh,
  IconRename,
  IconSave,
  IconSidebarClose,
  IconSidebarOpen,
  IconSpace,
  IconTrash,
  IconWorkshop,
} from "./components/icons";
import { basename, dirname, join } from "./lib/path";
import { getFileTypeLabel, isTextFile, isZhixuDrawFile, stripExtension } from "./lib/fileType";
import type { InstalledPlugin, PluginIndexItem } from "./lib/plugins/types";
import { runInstalledPluginAction } from "./lib/plugins/runtime";
import { fetchOfficialIndex, listInstalledPlugins } from "./lib/plugins/workshop";
import { logout as officialLogout, me as officialMe } from "./lib/sync/officialClient";
import {
  DEFAULT_EDITOR_DISPLAY_SETTINGS,
  loadEditorDisplaySettings,
  saveEditorDisplaySettings,
  type EditorDisplaySettings,
} from "./lib/editorDisplaySettings";
import {
  createDir,
  createFile,
  deleteEntry,
  getPersistedState,
  listDir,
  readDrawDocument,
  readTextFile,
  renameEntry,
  selectVault,
  setVaultRoot,
  writeDrawDocument,
  writeTextFile,
  type PersistedState,
  type VaultEntry,
} from "./lib/vaultApi";
import {
  createRafLatencyTracker,
  devPerfMark,
  initDevPerfLogging,
  isDevPerfEnabled,
  recordSpanMs,
  runDevPerfOnce,
  startStatsReporter,
  withDevPerfSpan,
} from "./lib/perf";
import type { DrawDocument, DrawViewMode } from "./draw/types";

const LazyCodeMirrorEditor = React.lazy(() =>
  import("./components/CodeMirrorEditor").then((m) => ({ default: m.CodeMirrorEditor })),
) as React.LazyExoticComponent<typeof import("./components/CodeMirrorEditor").CodeMirrorEditor>;

const LazyZhixuDrawEditor = React.lazy(() =>
  import("./components/ZhixuDrawEditor").then((m) => ({ default: m.ZhixuDrawEditor })),
) as React.LazyExoticComponent<typeof import("./components/ZhixuDrawEditor").ZhixuDrawEditor>;

const LazyZhixuMarkdownEditor = React.lazy(() =>
  import("./components/ZhixuMarkdownEditor").then((m) => ({ default: m.ZhixuMarkdownEditor })),
) as React.LazyExoticComponent<typeof import("./components/ZhixuMarkdownEditor").ZhixuMarkdownEditor>;

type PluginActivityId = `plugin:${string}:${string}`;
type Activity = "space" | "workshop" | PluginActivityId;

type NewTab = {
  path: string;
  name: string;
  kind: "newtab";
  locked: boolean;
  content: string;
  savedContent: string;
  dirty: false;
  selection: CodeMirrorSelection;
};

type TextTab = {
  path: string;
  name: string;
  kind: "text";
  locked: boolean;
  content: string;
  savedContent: string;
  dirty: boolean;
  selection: CodeMirrorSelection;
};

type BinaryTab = {
  path: string;
  name: string;
  kind: "binary";
  locked: boolean;
  content: string;
  savedContent: string;
  dirty: false;
  selection: CodeMirrorSelection;
};

type DrawingTab = {
  path: string;
  name: string;
  kind: "drawing";
  locked: boolean;
  doc: DrawDocument | null;
  savedDoc: DrawDocument | null;
  dirty: boolean;
  viewMode: DrawViewMode;
  selection: CodeMirrorSelection;
};

type PluginTab = {
  path: string;
  name: string;
  kind: "plugin";
  locked: boolean;
  pluginId: string;
  actionId: string;
  html: string;
  dirty: false;
  selection: CodeMirrorSelection;
};

type Tab = NewTab | TextTab | BinaryTab | DrawingTab | PluginTab;

type TabTransferPayload = {
  tab: Tab;
  vaultRoot: string | null;
  sourceWindowLabel: string | null;
};

function makeNewTab(id: number): Tab {
  const path = `__newtab__${id}`;
  return {
    path,
    name: "新标签页",
    kind: "newtab",
    locked: false,
    content: "",
    savedContent: "",
    dirty: false,
    selection: { anchor: 0, head: 0 },
  };
}

function formatPathForDisplay(p: string): string {
  if (p.startsWith("\\\\?\\UNC\\")) return `\\\\${p.slice("\\\\?\\UNC\\".length)}`;
  if (p.startsWith("\\\\?\\")) return p.slice("\\\\?\\".length);
  return p;
}

function normalizeVaultRoot(p: string): string {
  const display = formatPathForDisplay(p).replaceAll("/", "\\");
  let trimmed = display;
  while (trimmed.length > 3 && (trimmed.endsWith("\\") || trimmed.endsWith("/"))) trimmed = trimmed.slice(0, -1);
  return trimmed.toLowerCase();
}

function sortEntries(entries: VaultEntry[]): VaultEntry[] {
  return [...entries].sort((a, b) => {
    if (a.isDir !== b.isDir) return a.isDir ? -1 : 1;
    return a.name.localeCompare(b.name, undefined, { numeric: true, sensitivity: "base" });
  });
}

function IconButton({
  title,
  tooltipPlacement,
  active,
  disabled,
  className,
  buttonRef,
  onClick,
  onPointerDown,
  onPointerMove,
  onPointerUp,
  onPointerCancel,
  children,
}: React.PropsWithChildren<{
  title: string;
  tooltipPlacement?: TooltipPlacement;
  active?: boolean;
  disabled?: boolean;
  className?: string;
  buttonRef?: React.Ref<HTMLButtonElement>;
  onClick?: React.MouseEventHandler<HTMLButtonElement>;
  onPointerDown?: React.PointerEventHandler<HTMLButtonElement>;
  onPointerMove?: React.PointerEventHandler<HTMLButtonElement>;
  onPointerUp?: React.PointerEventHandler<HTMLButtonElement>;
  onPointerCancel?: React.PointerEventHandler<HTMLButtonElement>;
}>) {
  return (
    <Tooltip label={title} placement={tooltipPlacement}>
      <button
        className={`iconBtn${active ? " active" : ""}${className ? ` ${className}` : ""}`}
        aria-label={title}
        data-no-drag="true"
        ref={buttonRef}
        onClick={onClick}
        onPointerDown={onPointerDown}
        onPointerMove={onPointerMove}
        onPointerUp={onPointerUp}
        onPointerCancel={onPointerCancel}
        disabled={disabled}
        type="button"
      >
        {children}
      </button>
    </Tooltip>
  );
}

function normalizePluginPlace(place: string | undefined): string {
  return String(place || "")
    .trim()
    .toLowerCase()
    .replaceAll("-", "")
    .replaceAll("_", "");
}

function parsePluginActivityId(id: string): { pluginId: string; actionId: string } | null {
  const raw = String(id || "");
  if (!raw.startsWith("plugin:")) return null;
  const parts = raw.split(":");
  if (parts.length < 3) return null;
  const pluginId = parts[1] || "";
  const actionId = parts.slice(2).join(":");
  if (!pluginId || !actionId) return null;
  return { pluginId, actionId };
}

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
  } catch (_) {
    return fallback;
  }
}

function buildPluginSidebarSrcDoc(opts: {
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

function PluginIcon({ icon, fallback }: { icon?: string; fallback?: string }) {
  const raw = String(icon || "").trim();
  if (raw && raw.includes("<svg")) {
    return <span className="pluginSvgIcon" aria-hidden="true" dangerouslySetInnerHTML={{ __html: raw }} />;
  }
  if (raw) return <span className="pluginTextIcon" aria-hidden="true">{raw.slice(0, 1)}</span>;
  if (fallback) return <span className="pluginTextIcon" aria-hidden="true">{fallback.slice(0, 1)}</span>;
  return null;
}

export function App() {
  const appWindow = useMemo(() => getCurrentWindow(), []);
  const urlParams = useMemo(() => new URLSearchParams(window.location.search), []);
  const isDetached = useMemo(() => urlParams.get("view") === "tab", [urlParams]);
  const initialVaultParam = useMemo(() => urlParams.get("vaultRoot"), [urlParams]);
  const transferId = useMemo(() => urlParams.get("transferId"), [urlParams]);

  useLayoutEffect(() => {
    if (!isDevPerfEnabled()) return;
    runDevPerfOnce("mark:zhixu:app:layout", () => devPerfMark("zhixu:app:layout"));
  }, []);

  useEffect(() => {
    window.__zhixuBoot?.log("$ app: mounted");
    window.__zhixuBoot?.step("Ready");

    const splash = document.getElementById("zhixu-boot-splash");
    if (splash && splash.getAttribute("data-hidden") !== "true") {
      splash.setAttribute("data-hidden", "true");
      window.setTimeout(() => splash.remove(), 240);
    }

    if (!isDevPerfEnabled()) return;
    initDevPerfLogging();
    runDevPerfOnce("mark:zhixu:app:mounted", () => devPerfMark("zhixu:app:mounted"));
  }, []);

  useEffect(() => {
    if (!isDevPerfEnabled()) return;
    const cleanups = [
      startStatsReporter("editor:input->frame", { label: "编辑：输入 -> 下一帧", minNewSamples: 20 }),
      startStatsReporter("vault:listDir", { label: "文件树：listDir", minNewSamples: 1 }),
      startStatsReporter("vault:readTextFile", { label: "打开文件：readTextFile", minNewSamples: 1 }),
      startStatsReporter("vault:writeTextFile", { label: "保存：writeTextFile", minNewSamples: 1 }),
      startStatsReporter("md-editor:iframe-load", { label: "Markdown编辑器：iframe加载", minNewSamples: 1 }),
      startStatsReporter("startup:getPersistedState", { label: "启动：getPersistedState", minNewSamples: 1 }),
      startStatsReporter("startup:lcp", { label: "启动：LCP", minNewSamples: 1 }),
      startStatsReporter("vault:setVaultRoot", { label: "vault:setVaultRoot", minNewSamples: 1 }),
    ];
    return () => {
      for (const fn of cleanups) fn();
    };
  }, []);
  const draggingTabPathRef = useRef<string | null>(null);
  const draggingTransferIdRef = useRef<string | null>(null);
  const transferCleanupByPathRef = useRef<Map<string, string>>(new Map());
  const dragBroadcastTimerRef = useRef<number | null>(null);
  const dragBroadcastTargetLabelRef = useRef<string | null>(null);
  const tabStripRef = useRef<HTMLDivElement | null>(null);
  const tabElByPathRef = useRef<Map<string, HTMLDivElement>>(new Map());
  const dragPreviewElRef = useRef<HTMLDivElement | null>(null);
  const appShellRef = useRef<HTMLDivElement | null>(null);

  const [vaultRoot, setVaultRootState] = useState<string | null>(null);
  const [persisted, setPersisted] = useState<PersistedState>({ lastVault: null, recentVaults: [] });
  const [vaultPickerOpen, setVaultPickerOpen] = useState(false);
  const vaultPickerRef = useRef<HTMLDivElement | null>(null);

  const [settingsOpen, setSettingsOpen] = useState(false);
  const [settingsInitialSection, setSettingsInitialSection] = useState<SettingsSectionId>("pro");
  const [editorDisplaySettings, setEditorDisplaySettings] = useState<EditorDisplaySettings>(() => {
    try {
      return loadEditorDisplaySettings();
    } catch {
      return { ...DEFAULT_EDITOR_DISPLAY_SETTINGS };
    }
  });
  useEffect(() => {
    saveEditorDisplaySettings(editorDisplaySettings);
  }, [editorDisplaySettings]);
  const [cloudSyncEnabled, setCloudSyncEnabled] = useState<boolean>(() => {
    try {
      const raw = localStorage.getItem("zhixu:cloudSyncEnabled");
      if (raw == null) return false;
      return JSON.parse(raw) === true;
    } catch {
      return false;
    }
  });
  useEffect(() => {
    try {
      localStorage.setItem("zhixu:cloudSyncEnabled", JSON.stringify(cloudSyncEnabled));
    } catch {
      // ignore
    }
  }, [cloudSyncEnabled]);

  const [officialSyncBaseUrl, setOfficialSyncBaseUrl] = useState<string>(() => {
    try {
      return localStorage.getItem("zhixu:officialSyncBaseUrl") || "https://zhixu.app";
    } catch {
      return "https://zhixu.app";
    }
  });
  useEffect(() => {
    try {
      localStorage.setItem("zhixu:officialSyncBaseUrl", officialSyncBaseUrl);
    } catch {
      // ignore
    }
  }, [officialSyncBaseUrl]);

  const [officialAuth, setOfficialAuth] = useState<OfficialAuthState | null>(() => {
    try {
      const raw = localStorage.getItem("zhixu:officialAuth");
      if (!raw) return null;
      const obj = JSON.parse(raw) as OfficialAuthState;
      if (!obj || typeof obj !== "object" || !obj.token) return null;
      return obj;
    } catch {
      return null;
    }
  });
  useEffect(() => {
    try {
      if (officialAuth?.token) localStorage.setItem("zhixu:officialAuth", JSON.stringify(officialAuth));
      else localStorage.removeItem("zhixu:officialAuth");
    } catch {
      // ignore
    }
  }, [officialAuth]);

  useEffect(() => {
    if (!officialAuth?.token) return;
    if (officialAuth.me?.username) return;
    void (async () => {
      try {
        const r = await officialMe(officialSyncBaseUrl, officialAuth.token);
        if (r.ok && r.value) {
          setOfficialAuth((prev) => {
            if (!prev?.token || prev.token !== officialAuth.token) return prev;
            return { ...prev, me: r.value };
          });
        }
      } catch {
        // ignore
      }
    })();
  }, [officialAuth?.token, officialAuth?.me?.username, officialSyncBaseUrl]);

  const [authModalOpen, setAuthModalOpen] = useState(false);
  const [authModalMode, setAuthModalMode] = useState<AuthModalMode>("login");
  const pendingEnableCloudSyncRef = useRef(false);

  const openAuthModal = useCallback(
    (mode: AuthModalMode = "login") => {
      setAuthModalMode(mode);
      setAuthModalOpen(true);
    },
    [setAuthModalMode, setAuthModalOpen],
  );

  const closeAuthModal = useCallback(() => {
    setAuthModalOpen(false);
    pendingEnableCloudSyncRef.current = false;
  }, []);

  const handleAuth = useCallback(
    (auth: OfficialAuthState) => {
      setOfficialAuth(auth);
      if (pendingEnableCloudSyncRef.current) {
        pendingEnableCloudSyncRef.current = false;
        setCloudSyncEnabled(true);
      }
    },
    [setOfficialAuth, setCloudSyncEnabled],
  );

  const handleCloudSyncEnabledChange = useCallback(
    (next: boolean) => {
      if (next && !officialAuth?.token) {
        pendingEnableCloudSyncRef.current = true;
        openAuthModal("login");
        return;
      }
      pendingEnableCloudSyncRef.current = false;
      setCloudSyncEnabled(next);
    },
    [officialAuth?.token, openAuthModal, setCloudSyncEnabled],
  );

  const doLogout = useCallback(async () => {
    const token = officialAuth?.token || "";
    setOfficialAuth(null);
    setCloudSyncEnabled(false);
    if (!token) return;
    try {
      await officialLogout(officialSyncBaseUrl, token);
    } catch {
      // ignore
    }
  }, [officialAuth?.token, officialSyncBaseUrl]);

  const [workshopBaseUrl, setWorkshopBaseUrl] = useState<string>(() => {
    try {
      return localStorage.getItem("zhixu:workshopBaseUrl") || "https://zhixu.app/plugins";
    } catch {
      return "https://zhixu.app/plugins";
    }
  });
  useEffect(() => {
    try {
      localStorage.setItem("zhixu:workshopBaseUrl", workshopBaseUrl);
    } catch {
      // ignore
    }
  }, [workshopBaseUrl]);

  const [workshopSearch, setWorkshopSearch] = useState<string>("");
  const [workshopSelectedId, setWorkshopSelectedId] = useState<string | null>(null);
  const [workshopOfficial, setWorkshopOfficial] = useState<PluginIndexItem[]>([]);
  const [workshopOfficialLoading, setWorkshopOfficialLoading] = useState(false);
  const [workshopOfficialError, setWorkshopOfficialError] = useState<string | null>(null);
  const [installedPlugins, setInstalledPlugins] = useState<InstalledPlugin[]>([]);
  const [installedPluginsLoading, setInstalledPluginsLoading] = useState(false);

  const editWorkshopBaseUrl = useCallback(() => {
    const next = window.prompt("官方插件源（Base URL）", workshopBaseUrl);
    if (next == null) return;
    const clean = next.trim().replace(/\/+$/, "");
    if (!clean) return;
    setWorkshopBaseUrl(clean);
  }, [workshopBaseUrl]);

  const reloadInstalledPlugins = useCallback(() => {
    if (!vaultRoot) {
      setInstalledPlugins([]);
      setInstalledPluginsLoading(false);
      return;
    }
    setInstalledPluginsLoading(true);
    void (async () => {
      try {
        const next = await listInstalledPlugins(vaultRoot);
        setInstalledPlugins(next);
      } catch {
        setInstalledPlugins([]);
      } finally {
        setInstalledPluginsLoading(false);
      }
    })();
  }, [vaultRoot]);

  useEffect(() => {
    reloadInstalledPlugins();
  }, [reloadInstalledPlugins]);

  const reloadWorkshop = useCallback(() => {
    setWorkshopOfficialLoading(true);
    setWorkshopOfficialError(null);
    void (async () => {
      try {
        const next = await fetchOfficialIndex(workshopBaseUrl);
        setWorkshopOfficial(next);
      } catch (e) {
        setWorkshopOfficialError(String(e instanceof Error ? e.message : e));
      } finally {
        setWorkshopOfficialLoading(false);
      }
    })();
    reloadInstalledPlugins();
  }, [reloadInstalledPlugins, workshopBaseUrl]);
  const [dirCache, setDirCache] = useState<Record<string, VaultEntry[]>>({});
  const [expanded, setExpanded] = useState<Record<string, boolean>>({});
  const [loadingDir, setLoadingDir] = useState<Record<string, boolean>>({});
  const [selectedDir, setSelectedDir] = useState<string>("");

  const [activity, setActivity] = useState<Activity>("space");
  const [sidebarOpen, setSidebarOpen] = useState<boolean>(() => !isDetached);

  const pluginSidebarViews = useMemo(() => {
    const out: Array<{
      activityId: PluginActivityId;
      pluginId: string;
      actionId: string;
      label: string;
      icon?: string;
      ringIndex?: number;
    }> = [];

    for (const p of installedPlugins) {
      if (!p.enabled) continue;
      for (const a of p.manifest.actions || []) {
        const place = normalizePluginPlace(a.place);
        if (place !== "mainsidebar" && place !== "sidebar") continue;
        const actionId = String(a.id || "").trim();
        if (!actionId) continue;
        const label = String(a.label || a.id || p.manifest.name || p.manifest.id || "Plugin").trim();
        out.push({
          activityId: `plugin:${p.manifest.id}:${actionId}`,
          pluginId: p.manifest.id,
          actionId,
          label,
          icon: a.icon,
          ringIndex: a.ringIndex,
        });
      }
    }

    out.sort((a, b) => {
      const ai = Number.isFinite(a.ringIndex) ? Number(a.ringIndex) : 0;
      const bi = Number.isFinite(b.ringIndex) ? Number(b.ringIndex) : 0;
      if (ai !== bi) return ai - bi;
      return a.label.localeCompare(b.label, undefined, { numeric: true, sensitivity: "base" });
    });

    return out;
  }, [installedPlugins]);

  const pluginSidebarViewsByActivityId = useMemo(() => {
    const m = new Map<string, (typeof pluginSidebarViews)[number]>();
    for (const v of pluginSidebarViews) m.set(v.activityId, v);
    return m;
  }, [pluginSidebarViews]);

  const pluginFunctionActions = useMemo(() => {
    const out: Array<{
      id: string;
      pluginId: string;
      actionId: string;
      label: string;
      icon?: string;
      ringIndex?: number;
    }> = [];

    for (const p of installedPlugins) {
      if (!p.enabled) continue;
      for (const a of p.manifest.actions || []) {
        const place = normalizePluginPlace(a.place);
        if (place !== "functionarea" && place !== "activitybar") continue;
        const actionId = String(a.id || "").trim();
        if (!actionId) continue;
        const label = String(a.label || a.id || p.manifest.name || p.manifest.id || "Plugin").trim();
        out.push({
          id: `action:${p.manifest.id}:${actionId}`,
          pluginId: p.manifest.id,
          actionId,
          label,
          icon: a.icon,
          ringIndex: a.ringIndex,
        });
      }
    }

    out.sort((a, b) => {
      const ai = Number.isFinite(a.ringIndex) ? Number(a.ringIndex) : 0;
      const bi = Number.isFinite(b.ringIndex) ? Number(b.ringIndex) : 0;
      if (ai !== bi) return ai - bi;
      return a.label.localeCompare(b.label, undefined, { numeric: true, sensitivity: "base" });
    });

    return out;
  }, [installedPlugins]);

  const runPluginAction = useCallback(
    (pluginId: string, actionId: string) => {
      if (!vaultRoot) return;
      const plugin = installedPlugins.find((p) => p.enabled && p.manifest.id === pluginId);
      if (!plugin) return;
      void runInstalledPluginAction({ vaultRoot, plugin, actionId }).catch((e) => console.error(e));
    },
    [installedPlugins, vaultRoot],
  );

  const sidebarTitle = useMemo(() => {
    if (activity === "workshop") return "工坊";
    const v = pluginSidebarViewsByActivityId.get(activity);
    if (v) return v.label;
    return "";
  }, [activity, pluginSidebarViewsByActivityId]);

  const pluginSidebarTheme = useMemo(() => {
    return {
      bg: getRootCssVar("--panel", "rgba(245, 245, 245, 0.75)"),
      text: getRootCssVar("--text", "rgba(0, 0, 0, 0.88)"),
      muted: getRootCssVar("--muted", "rgba(0, 0, 0, 0.55)"),
      accent: getRootCssVar("--accent", "#2f6feb"),
      border: getRootCssVar("--border", "rgba(0, 0, 0, 0.08)"),
    };
  }, []);

  const [pluginSidebarState, setPluginSidebarState] = useState<{
    activityId: PluginActivityId;
    pluginId: string;
    actionId: string;
    title: string;
    html: string;
    editorTitle: string;
    editorHtml: string;
    loading: boolean;
    error: string | null;
  } | null>(null);

  useEffect(() => {
    const v = pluginSidebarViewsByActivityId.get(activity);
    if (!v) {
      setPluginSidebarState(null);
      return;
    }

    if (!vaultRoot) {
      setPluginSidebarState({
        activityId: v.activityId,
        pluginId: v.pluginId,
        actionId: v.actionId,
        title: v.label,
        html: "",
        editorTitle: "",
        editorHtml: "",
        loading: false,
        error: "请先选择一个库（Vault）。",
      });
      return;
    }

    const plugin = installedPlugins.find((p) => p.enabled && p.manifest.id === v.pluginId);
    if (!plugin) {
      setActivity("space");
      return;
    }

    let cancelled = false;
    setPluginSidebarState({
      activityId: v.activityId,
      pluginId: v.pluginId,
      actionId: v.actionId,
      title: v.label,
      html: "",
      editorTitle: "",
      editorHtml: "",
      loading: true,
      error: null,
    });
    void (async () => {
      try {
        const { result } = await runInstalledPluginAction({ vaultRoot, plugin, actionId: v.actionId });
        if (cancelled) return;

        let title = v.label;
        let html = "";
        let editorTitle = "";
        let editorHtml = "";

        if (result && typeof result === "object") {
          const anyRes = result as any;
          if (typeof anyRes.title === "string" && anyRes.title.trim()) title = anyRes.title.trim();
          if (typeof anyRes.html === "string") html = anyRes.html;
          else if (typeof anyRes.message === "string" && anyRes.message.trim()) html = `<pre>${escapeHtml(anyRes.message)}</pre>`;
          if (typeof anyRes.editorTitle === "string" && anyRes.editorTitle.trim()) editorTitle = anyRes.editorTitle.trim();
          if (typeof anyRes.editorHtml === "string") editorHtml = anyRes.editorHtml;
        } else if (typeof result === "string") {
          const trimmed = result.trim();
          html = trimmed.startsWith("<") ? result : `<pre>${escapeHtml(result)}</pre>`;
        }

        if (!html.trim()) html = `<div style="color: var(--muted);">暂无内容</div>`;
        setPluginSidebarState({
          activityId: v.activityId,
          pluginId: v.pluginId,
          actionId: v.actionId,
          title,
          html,
          editorTitle,
          editorHtml,
          loading: false,
          error: null,
        });
      } catch (e) {
        if (cancelled) return;
        setPluginSidebarState({
          activityId: v.activityId,
          pluginId: v.pluginId,
          actionId: v.actionId,
          title: v.label,
          html: "",
          editorTitle: "",
          editorHtml: "",
          loading: false,
          error: String(e instanceof Error ? e.message : e),
        });
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [activity, installedPlugins, pluginSidebarViewsByActivityId, vaultRoot]);

  const pluginSidebarSrcDoc = useMemo(() => {
    const state = pluginSidebarState;
    if (!state || state.loading || state.error) return "";
    return buildPluginSidebarSrcDoc({
      title: state.title,
      html: state.html,
      pluginId: state.pluginId,
      viewActionId: state.actionId,
      ...pluginSidebarTheme,
    });
  }, [pluginSidebarState, pluginSidebarTheme]);

  const pluginTabFrameRef = useRef<HTMLIFrameElement | null>(null);

  const pluginSidebarFrameRef = useRef<HTMLIFrameElement | null>(null);
  const openFileRef = useRef<(path: string, opts?: { lineIndex?: number | null }) => Promise<void> | void>(() => {});

  const installedPluginsRef = useRef<InstalledPlugin[]>([]);
  installedPluginsRef.current = installedPlugins;
  const vaultRootRef = useRef<string | null>(null);
  vaultRootRef.current = vaultRoot;
  const pluginSidebarStateRef = useRef<typeof pluginSidebarState>(null);
  pluginSidebarStateRef.current = pluginSidebarState;
  const activeTabRef = useRef<Tab | null>(null);

  useEffect(() => {
    function onMessage(ev: MessageEvent) {
      const msg = ev.data as any;
      if (!msg || typeof msg !== "object") return;
      if (msg.__zhixuPlugin !== true) return;

      const source = ev.source as Window | null;
      const sidebarWin = pluginSidebarFrameRef.current?.contentWindow ?? null;
      const tabWin = pluginTabFrameRef.current?.contentWindow ?? null;
      if (!source || (source !== sidebarWin && source !== tabWin)) return;
      const src = source;

      const requestId = String(msg.id || "");
      const kind = String(msg.kind || "");
      const pluginId = String(msg.pluginId || "");

      function respond(ok: boolean, payload: { result?: unknown; error?: string }) {
        try {
          src.postMessage(
            { __zhixuPlugin: true, kind: "response", id: requestId, ok, result: payload.result, error: payload.error },
            "*",
          );
        } catch {
          // ignore
        }
      }

      const sidebarState = pluginSidebarStateRef.current;
      const tab = activeTabRef.current;
      const expectedPluginId =
        src === sidebarWin ? (sidebarState?.pluginId ?? null) : tab && tab.kind === "plugin" ? tab.pluginId : null;
      if (!expectedPluginId || expectedPluginId !== pluginId) {
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
        void Promise.resolve(openFileRef.current(p, { lineIndex }))
          .then(() => {
            respond(true, { result: { ok: true } });
          })
          .catch((e) => respond(false, { error: String(e instanceof Error ? e.message : e) }));
        return;
      }

      if (kind === "runAction") {
        const actionId = String(msg.payload?.actionId || "");
        const input = msg.payload?.input;
        if (!actionId) {
          respond(false, { error: "Missing actionId" });
          return;
        }
        const vault = vaultRootRef.current;
        if (!vault) {
          respond(false, { error: "No vault selected" });
          return;
        }
        const plugin = installedPluginsRef.current.find((p) => p.enabled && p.manifest.id === pluginId);
        if (!plugin) {
          respond(false, { error: "Plugin not installed or disabled" });
          return;
        }
        void runInstalledPluginAction({ vaultRoot: vault, plugin, actionId, input })
          .then(({ result }) => respond(true, { result }))
          .catch((e) => respond(false, { error: String(e instanceof Error ? e.message : e) }));
        return;
      }

      respond(false, { error: "Unknown request" });
    }

    window.addEventListener("message", onMessage);
    return () => window.removeEventListener("message", onMessage);
  }, []);

  type FunctionAreaItem =
    | {
        id: string;
        kind: "activity";
        title: string;
        activityId: Activity;
        active: boolean;
        disabled?: boolean;
        icon: React.ReactNode;
      }
    | {
        id: string;
        kind: "action";
        title: string;
        pluginId: string;
        actionId: string;
        disabled?: boolean;
        icon: React.ReactNode;
      };

  const functionAreaItems = useMemo<FunctionAreaItem[]>(() => {
    const items: FunctionAreaItem[] = [];

    items.push({
      id: "space",
      kind: "activity",
      title: "空间",
      activityId: "space",
      active: activity === "space",
      icon: <IconSpace />,
    });

    for (const v of pluginSidebarViews) {
      items.push({
        id: v.activityId,
        kind: "activity",
        title: v.label,
        activityId: v.activityId,
        active: activity === v.activityId,
        disabled: !vaultRoot,
        icon: <PluginIcon icon={v.icon} fallback={v.label} />,
      });
    }

    for (const a of pluginFunctionActions) {
      items.push({
        id: a.id,
        kind: "action",
        title: a.label,
        pluginId: a.pluginId,
        actionId: a.actionId,
        disabled: !vaultRoot,
        icon: <PluginIcon icon={a.icon} fallback={a.label} />,
      });
    }

    items.push({
      id: "workshop",
      kind: "activity",
      title: "工坊",
      activityId: "workshop",
      active: activity === "workshop",
      icon: <IconWorkshop />,
    });

    return items;
  }, [activity, pluginFunctionActions, pluginSidebarViews, vaultRoot]);

  function arrayEqual(a: string[], b: string[]): boolean {
    if (a === b) return true;
    if (a.length !== b.length) return false;
    for (let i = 0; i < a.length; i++) if (a[i] !== b[i]) return false;
    return true;
  }

  const functionAreaOrderStorageKey = "zhixu:functionAreaOrder";
  const [functionAreaOrder, setFunctionAreaOrder] = useState<string[]>(() => {
    try {
      const raw = localStorage.getItem(functionAreaOrderStorageKey);
      const parsed = raw ? (JSON.parse(raw) as unknown) : [];
      if (Array.isArray(parsed) && parsed.every((v) => typeof v === "string")) return parsed as string[];
    } catch {
      // ignore
    }
    return [];
  });

  useEffect(() => {
    try {
      localStorage.setItem(functionAreaOrderStorageKey, JSON.stringify(functionAreaOrder));
    } catch {
      // ignore
    }
  }, [functionAreaOrder]);

  useEffect(() => {
    const ids = functionAreaItems.map((i) => i.id);
    setFunctionAreaOrder((prev) => {
      const filtered = prev.filter((id) => ids.includes(id));
      const missing = ids.filter((id) => !filtered.includes(id));
      const next = [...filtered, ...missing];
      return arrayEqual(prev, next) ? prev : next;
    });
  }, [functionAreaItems]);

  const functionAreaRef = useRef<HTMLDivElement | null>(null);
  const functionAreaBtnByIdRef = useRef<Map<string, HTMLButtonElement>>(new Map());

  const orderedFunctionAreaItems = useMemo(() => {
    const byId = new Map<string, FunctionAreaItem>();
    for (const it of functionAreaItems) byId.set(it.id, it);

    const orderedIds: string[] = [];
    for (const id of functionAreaOrder) {
      if (byId.has(id)) orderedIds.push(id);
    }
    for (const it of functionAreaItems) {
      if (!orderedIds.includes(it.id)) orderedIds.push(it.id);
    }

    return orderedIds.map((id) => byId.get(id)!).filter(Boolean);
  }, [functionAreaItems, functionAreaOrder]);

  const orderedFunctionAreaIdsRef = useRef<string[]>([]);
  useEffect(() => {
    orderedFunctionAreaIdsRef.current = orderedFunctionAreaItems.map((it) => it.id);
  }, [orderedFunctionAreaItems]);

  const [functionAreaDraggingId, setFunctionAreaDraggingId] = useState<string | null>(null);
  const [functionAreaInsertIndicator, setFunctionAreaInsertIndicator] = useState<{ index: number; top: number } | null>(null);
  const functionAreaInsertIndicatorRef = useRef<typeof functionAreaInsertIndicator>(null);
  useEffect(() => {
    functionAreaInsertIndicatorRef.current = functionAreaInsertIndicator;
  }, [functionAreaInsertIndicator]);

  const functionAreaDragSessionRef = useRef<{
    id: string;
    pointerId: number;
    startX: number;
    startY: number;
    timer: number | null;
    started: boolean;
    el: HTMLButtonElement;
  } | null>(null);

  const suppressClickIdRef = useRef<string | null>(null);
  const suppressClickTimerRef = useRef<number | null>(null);

  const clearFunctionAreaDrag = useCallback(() => {
    const s = functionAreaDragSessionRef.current;
    functionAreaDragSessionRef.current = null;
    if (s?.timer) window.clearTimeout(s.timer);
    setFunctionAreaDraggingId(null);
    setFunctionAreaInsertIndicator(null);
  }, []);

  const computeFunctionAreaInsert = useCallback((clientY: number) => {
    const host = functionAreaRef.current;
    if (!host) return null;
    const hostRect = host.getBoundingClientRect();

    const session = functionAreaDragSessionRef.current;
    const dragId = session?.started ? session.id : null;
    if (!dragId) return null;

    const ids = orderedFunctionAreaIdsRef.current.filter((id) => id !== dragId);
    if (!ids.length) return { index: 0, top: Math.max(0, clientY - hostRect.top) };

    let index = ids.length;
    for (let i = 0; i < ids.length; i++) {
      const rect = functionAreaBtnByIdRef.current.get(ids[i])?.getBoundingClientRect();
      if (!rect) continue;
      const center = rect.top + rect.height / 2;
      if (clientY < center) {
        index = i;
        break;
      }
    }

    let top: number | null = null;
    if (index === 0) {
      for (const id of ids) {
        const rect = functionAreaBtnByIdRef.current.get(id)?.getBoundingClientRect();
        if (rect) {
          top = rect.top - hostRect.top;
          break;
        }
      }
    } else if (index >= ids.length) {
      for (let i = ids.length - 1; i >= 0; i--) {
        const rect = functionAreaBtnByIdRef.current.get(ids[i])?.getBoundingClientRect();
        if (rect) {
          top = rect.bottom - hostRect.top;
          break;
        }
      }
    } else {
      const rect = functionAreaBtnByIdRef.current.get(ids[index])?.getBoundingClientRect();
      if (rect) top = rect.top - hostRect.top;
    }

    if (top == null) return null;
    return { index, top: Math.max(0, top) };
  }, []);

  const onFunctionAreaItemPointerDown = useCallback(
    (id: string, ev: React.PointerEvent<HTMLButtonElement>) => {
      if (ev.button !== 0) return;
      const el = ev.currentTarget;

      clearFunctionAreaDrag();

      const pointerId = ev.pointerId;
      const startX = ev.clientX;
      const startY = ev.clientY;

      const session = { id, pointerId, startX, startY, timer: null as number | null, started: false, el };
      session.timer = window.setTimeout(() => {
        const s = functionAreaDragSessionRef.current;
        if (!s || s.id !== id || s.pointerId !== pointerId) return;
        s.started = true;
        suppressClickIdRef.current = id;
        if (suppressClickTimerRef.current) window.clearTimeout(suppressClickTimerRef.current);
        suppressClickTimerRef.current = window.setTimeout(() => {
          if (suppressClickIdRef.current === id) suppressClickIdRef.current = null;
        }, 650);

        try {
          s.el.setPointerCapture(pointerId);
        } catch {
          // ignore
        }
        setFunctionAreaDraggingId(id);
        const insert = computeFunctionAreaInsert(startY);
        if (insert) setFunctionAreaInsertIndicator(insert);
      }, 220);

      functionAreaDragSessionRef.current = session;
    },
    [clearFunctionAreaDrag, computeFunctionAreaInsert],
  );

  const onFunctionAreaItemPointerMove = useCallback(
    (ev: React.PointerEvent<HTMLButtonElement>) => {
      const s = functionAreaDragSessionRef.current;
      if (!s) return;
      if (ev.pointerId !== s.pointerId) return;

      if (!s.started) {
        const dx = ev.clientX - s.startX;
        const dy = ev.clientY - s.startY;
        if (Math.hypot(dx, dy) > 6) {
          if (s.timer) window.clearTimeout(s.timer);
          functionAreaDragSessionRef.current = null;
        }
        return;
      }

      try {
        ev.preventDefault();
      } catch {
        // ignore
      }

      const insert = computeFunctionAreaInsert(ev.clientY);
      if (insert) setFunctionAreaInsertIndicator(insert);
    },
    [computeFunctionAreaInsert],
  );

  const onFunctionAreaItemPointerUp = useCallback((ev: React.PointerEvent<HTMLButtonElement>) => {
    const s = functionAreaDragSessionRef.current;
    if (!s) return;
    if (ev.pointerId !== s.pointerId) return;
    if (s.timer) window.clearTimeout(s.timer);
    s.timer = null;

    if (s.started) {
      const insert = functionAreaInsertIndicatorRef.current;
      const current = orderedFunctionAreaIdsRef.current;
      const dragId = s.id;
      const remaining = current.filter((x) => x !== dragId);
      const insertIndexRaw = insert?.index ?? remaining.length;
      const insertIndex = Math.max(0, Math.min(remaining.length, insertIndexRaw));
      const next = [...remaining.slice(0, insertIndex), dragId, ...remaining.slice(insertIndex)];
      setFunctionAreaOrder((prev) => (arrayEqual(prev, next) ? prev : next));
    }

    try {
      s.el.releasePointerCapture(s.pointerId);
    } catch {
      // ignore
    }

    clearFunctionAreaDrag();
  }, [clearFunctionAreaDrag]);

  const onFunctionAreaItemPointerCancel = useCallback((ev: React.PointerEvent<HTMLButtonElement>) => {
    const s = functionAreaDragSessionRef.current;
    if (!s) return;
    if (ev.pointerId !== s.pointerId) return;
    clearFunctionAreaDrag();
  }, [clearFunctionAreaDrag]);

  useEffect(() => {
    if (activity !== "workshop") return;
    reloadWorkshop();
  }, [activity, reloadWorkshop]);

  const [mainSidebarWidth, setMainSidebarWidth] = useState<number>(280);
  const mainSidebarWidthRef = useRef(mainSidebarWidth);
  const defaultMainSidebarWidthRef = useRef(mainSidebarWidth);

  const sidebarResizeSessionRef = useRef<{ startClientX: number; startWidth: number } | null>(null);
  const [sidebarResizing, setSidebarResizing] = useState(false);
  const [sidebarResizeHover, setSidebarResizeHover] = useState(false);
  const sidebarResizeActive = sidebarOpen && (sidebarResizing || sidebarResizeHover);

  useEffect(() => {
    mainSidebarWidthRef.current = mainSidebarWidth;
  }, [mainSidebarWidth]);

  useEffect(() => {
    document.body.classList.toggle("sidebarResizing", sidebarResizing);
    return () => document.body.classList.remove("sidebarResizing");
  }, [sidebarResizing]);

  useEffect(() => {
    if (sidebarOpen) return;
    setSidebarResizing(false);
    setSidebarResizeHover(false);
    sidebarResizeSessionRef.current = null;
  }, [sidebarOpen]);

  const onSidebarResizerPointerDown = useCallback(
    (ev: React.PointerEvent<HTMLDivElement>) => {
      if (!sidebarOpen) return;
      if (ev.button !== 0) return;

      ev.preventDefault();
      ev.stopPropagation();

      ev.currentTarget.setPointerCapture(ev.pointerId);
      sidebarResizeSessionRef.current = { startClientX: ev.clientX, startWidth: mainSidebarWidthRef.current };
      setSidebarResizing(true);
    },
    [sidebarOpen],
  );

  const onSidebarResizerPointerMove = useCallback(
    (ev: React.PointerEvent<HTMLDivElement>) => {
      const session = sidebarResizeSessionRef.current;
      if (!sidebarResizing || !session) return;

      const baseWidth = defaultMainSidebarWidthRef.current;
      const minWidth = baseWidth * 0.8;
      const maxWidth = baseWidth * 1.5;
      const closeThreshold = minWidth * 0.5;

      const delta = ev.clientX - session.startClientX;
      const raw = session.startWidth + delta;

      if (raw <= closeThreshold) {
        setSidebarOpen(false);
        setSidebarResizing(false);
        setSidebarResizeHover(false);
        sidebarResizeSessionRef.current = null;
        return;
      }

      const next = Math.min(maxWidth, Math.max(minWidth, raw));
      if (next === mainSidebarWidthRef.current) return;
      mainSidebarWidthRef.current = next;
      setMainSidebarWidth(next);
    },
    [sidebarResizing],
  );

  const onSidebarResizerPointerUp = useCallback(() => {
    if (!sidebarResizing) return;
    setSidebarResizing(false);
    sidebarResizeSessionRef.current = null;
  }, [sidebarResizing]);

  const onSidebarResizerPointerCancel = useCallback(() => {
    if (!sidebarResizing) return;
    setSidebarResizing(false);
    sidebarResizeSessionRef.current = null;
  }, [sidebarResizing]);

  const [editorMode, setEditorMode] = useState<MarkdownEditorMode>("live");
  const [shortcutsOpen, setShortcutsOpen] = useState(false);
  const [pluginDevOpen, setPluginDevOpen] = useState(false);
  const [pendingPluginTabOpen, setPendingPluginTabOpen] = useState<PluginActivityId | null>(null);

  const newTabIdRef = useRef(isDetached && transferId ? 1 : 2);
  const [tabs, setTabs] = useState<Tab[]>(() => (isDetached && transferId ? [] : [makeNewTab(1)]));
  const [activePath, setActivePath] = useState<string | null>(() => (isDetached && transferId ? null : "__newtab__1"));
  const tabsRef = useRef<Tab[]>(tabs);
  const latestTextByPathRef = useRef<Map<string, string>>(new Map());
  const trackEditorInputToFrame = useMemo(
    () => createRafLatencyTracker("editor:input->frame", { label: "编辑：输入 -> 下一帧" }),
    [],
  );
  const [tabInsertIndicator, setTabInsertIndicator] = useState<{ index: number; left: number } | null>(null);
  const [recentlyInsertedPath, setRecentlyInsertedPath] = useState<string | null>(null);
  useEffect(() => {
    tabsRef.current = tabs;
    let maxNewTabId = 0;
    for (const t of tabs) {
      if (!t.path.startsWith("__newtab__")) continue;
      const raw = t.path.slice("__newtab__".length);
      const parsed = Number.parseInt(raw, 10);
      if (!Number.isFinite(parsed)) continue;
      if (parsed > maxNewTabId) maxNewTabId = parsed;
    }
    if (maxNewTabId + 1 > newTabIdRef.current) newTabIdRef.current = maxNewTabId + 1;
  }, [tabs]);
  const detachedHadTabRef = useRef(false);
  useEffect(() => {
    if (tabs.length > 0) detachedHadTabRef.current = true;
    if (!isDetached) return;
    if (!detachedHadTabRef.current) return;
    if (tabs.length === 0) void appWindow.close();
  }, [appWindow, isDetached, tabs.length]);
  const activeTab = useMemo(() => tabs.find((t) => t.path === activePath) ?? null, [tabs, activePath]);
  activeTabRef.current = activeTab;
  const tabCount = Math.max(1, tabs.length);
  const tabsDensity = tabCount >= 14 ? "separators" : tabCount >= 8 ? "dense" : "normal";

  const tabContextAnchorRef = useRef<HTMLSpanElement | null>(null);
  const [tabContextOpen, setTabContextOpen] = useState(false);
  const [tabContextPath, setTabContextPath] = useState<string | null>(null);
  const [tabContextPoint, setTabContextPoint] = useState<{ x: number; y: number }>({ x: 0, y: 0 });
  const tabContextTab = useMemo(() => (tabContextPath ? tabs.find((t) => t.path === tabContextPath) ?? null : null), [tabContextPath, tabs]);

  const accountMenuAnchorRef = useRef<HTMLButtonElement | null>(null);
  const [accountMenuOpen, setAccountMenuOpen] = useState(false);

  const savingRef = useRef(false);

  const recentKey = useCallback((root: string) => `zhixu.desktop.recentFiles:${root}`, []);
  const [recentFiles, setRecentFiles] = useState<string[]>([]);
  const [allFiles, setAllFiles] = useState<string[]>([]);

  const [filePickerOpen, setFilePickerOpen] = useState(false);
  const [filePickerQuery, setFilePickerQuery] = useState("");
  const [filePickerMode, setFilePickerMode] = useState<"all" | "recent">("all");
  const [filePickerLoading, setFilePickerLoading] = useState(false);
  const [filePickerActiveIndex, setFilePickerActiveIndex] = useState(0);
  const filePickerSeqRef = useRef(0);
  const filePickerInputRef = useRef<HTMLInputElement | null>(null);

  const sameVaultRoot = useCallback((a: string | null, b: string | null) => {
    if (!a && !b) return true;
    if (!a || !b) return false;
    return normalizeVaultRoot(a) === normalizeVaultRoot(b);
  }, []);

  const transferStorageKey = useCallback((id: string) => `zhixu.desktop.tabTransfer:${id}`, []);

  const writeTabTransfer = useCallback(
    (id: string, payload: TabTransferPayload): boolean => {
      try {
        localStorage.setItem(transferStorageKey(id), JSON.stringify(payload));
        return true;
      } catch (e) {
        console.error(e);
        return false;
      }
    },
    [transferStorageKey],
  );

  const readTabTransfer = useCallback(
    (id: string): TabTransferPayload | null => {
      try {
        const raw = localStorage.getItem(transferStorageKey(id));
        if (!raw) return null;
        const parsed = JSON.parse(raw) as Partial<TabTransferPayload> | null;
        if (!parsed || typeof parsed !== "object") return null;
        if (!parsed.tab || typeof parsed.tab !== "object") return null;
        if (typeof (parsed.tab as any).path !== "string") return null;
        return {
          tab: parsed.tab as Tab,
          vaultRoot: typeof parsed.vaultRoot === "string" || parsed.vaultRoot === null ? parsed.vaultRoot : null,
          sourceWindowLabel: typeof parsed.sourceWindowLabel === "string" || parsed.sourceWindowLabel === null ? parsed.sourceWindowLabel : null,
        };
      } catch (e) {
        console.error(e);
        return null;
      }
    },
    [transferStorageKey],
  );

  const clearTabTransfer = useCallback(
    (id: string) => {
      try {
        localStorage.removeItem(transferStorageKey(id));
      } catch (e) {
        console.error(e);
      }
    },
    [transferStorageKey],
  );

  const resetForVault = useCallback(async (root: string, options?: { createNewTab?: boolean; keepTabs?: boolean }) => {
    setVaultRootState(root);
    const keepTabs = options?.keepTabs ?? false;
    const createNewTab = options?.createNewTab ?? true;
    if (!keepTabs) {
      if (createNewTab) {
        const tab = makeNewTab(newTabIdRef.current++);
        setTabs([tab]);
        setActivePath(tab.path);
      } else {
        setTabs([]);
        setActivePath(null);
      }
    }
    setSelectedDir("");
    setDirCache({});
    setExpanded({});
    setLoadingDir({});
    const t0 = isDevPerfEnabled() ? performance.now() : 0;
    const entries = sortEntries(await listDir(""));
    if (t0) recordSpanMs("vault:listDir", t0, performance.now() - t0, { dir: "", ctx: "resetForVault" });
    setDirCache({ "": entries });
  }, []);

  useEffect(() => {
    if (!isDetached) return;
    let cancelled = false;
    void (async () => {
      try {
        if (initialVaultParam && !sameVaultRoot(vaultRoot, initialVaultParam)) {
          const resolved = await withDevPerfSpan("vault:setVaultRoot", () => setVaultRoot(initialVaultParam), { ctx: "initialVaultParam" });
          if (cancelled) return;
          await resetForVault(resolved, { createNewTab: !(isDetached && transferId) });
          if (cancelled) return;
          setPersisted(await getPersistedState());
        }
      } catch (e) {
        console.error(e);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [initialVaultParam, isDetached, resetForVault, sameVaultRoot, transferId, vaultRoot]);

  const openFolder = useCallback(async (): Promise<string | null> => {
    try {
      const root = await selectVault();
      await resetForVault(root);
      setPersisted(await getPersistedState());
      setVaultPickerOpen(false);
      return root;
    } catch (e) {
      console.error(e);
      return null;
    }
  }, [resetForVault]);

  const openRecent = useCallback(
    async (root: string) => {
      try {
        const resolved = await withDevPerfSpan("vault:setVaultRoot", () => setVaultRoot(root), { ctx: "openRecent" });
        await resetForVault(resolved);
        setPersisted(await getPersistedState());
        setVaultPickerOpen(false);
      } catch (e) {
        console.error(e);
        window.alert(String(e));
      }
    },
    [resetForVault],
  );

  const reloadDir = useCallback(async (path: string) => {
    try {
      const t0 = isDevPerfEnabled() ? performance.now() : 0;
      const entries = sortEntries(await listDir(path));
      if (t0) recordSpanMs("vault:listDir", t0, performance.now() - t0, { dir: path, ctx: "reloadDir" });
      setDirCache((m) => ({ ...m, [path]: entries }));
    } catch (e) {
      console.error(e);
    }
  }, []);

  const toggleDir = useCallback(
    async (path: string) => {
      setSelectedDir(path);
      const next = !expanded[path];
      setExpanded((m) => ({ ...m, [path]: next }));
      if (!next) return;
      if (dirCache[path]) return;
      setLoadingDir((m) => ({ ...m, [path]: true }));
      try {
        const t0 = isDevPerfEnabled() ? performance.now() : 0;
        const entries = sortEntries(await listDir(path));
        if (t0) recordSpanMs("vault:listDir", t0, performance.now() - t0, { dir: path, ctx: "toggleDir" });
        setDirCache((m) => ({ ...m, [path]: entries }));
      } finally {
        setLoadingDir((m) => ({ ...m, [path]: false }));
      }
    },
    [expanded, dirCache],
  );

  const pushRecentFile = useCallback(
    (path: string) => {
      if (!vaultRoot) return;
      const key = recentKey(vaultRoot);
      setRecentFiles((prev) => {
        const next = [path, ...prev.filter((p) => p !== path)].slice(0, 50);
        try {
          localStorage.setItem(key, JSON.stringify(next));
        } catch (_) {}
        return next;
      });
    },
    [recentKey, vaultRoot],
  );

  const openFile = useCallback(async (path: string, opts?: { lineIndex?: number | null }) => {
    pushRecentFile(path);
    setActivePath(path);
    setSelectedDir(dirname(path));
    setActivity("space");
    setSidebarOpen(true);

    const fileName = basename(path);
    const tabName = stripExtension(fileName);
    const textFile = isTextFile(fileName);
    const drawFile = isZhixuDrawFile(fileName);
    const binaryLabel = getFileTypeLabel(fileName) ?? "文件";
    const binaryPlaceholder = `该文件类型暂不支持在应用内打开：${binaryLabel}\n\n路径：${path}`;

    setTabs((prev) => {
      const existing = prev.find((t) => t.path === path);
      if (existing) return prev;
      return [
        ...prev,
        textFile
          ? { path, name: tabName, kind: "text", locked: false, content: "", savedContent: "", dirty: false, selection: { anchor: 0, head: 0 } }
          : drawFile
            ? {
                path,
                name: tabName,
                kind: "drawing",
                locked: false,
                doc: null,
                savedDoc: null,
                dirty: false,
                viewMode: "writing",
                selection: { anchor: 0, head: 0 },
              }
            : {
                path,
                name: tabName,
                kind: "binary",
                locked: false,
                content: binaryPlaceholder,
                savedContent: binaryPlaceholder,
                dirty: false,
                selection: { anchor: 0, head: 0 },
              },
      ];
    });

    if (textFile) {
      try {
        const t0 = isDevPerfEnabled() ? performance.now() : 0;
        const content = await readTextFile(path);
        if (t0) recordSpanMs("vault:readTextFile", t0, performance.now() - t0, { path, chars: content.length, ctx: "openFile" });

        const lineIndex = opts?.lineIndex;
        let nextSelection: CodeMirrorSelection | null = null;
        if (typeof lineIndex === "number" && Number.isFinite(lineIndex)) {
          const target = Math.max(0, Math.floor(lineIndex));
          let pos = 0;
          for (let i = 0; i < target && pos < content.length; i++) {
            const nl = content.indexOf("\n", pos);
            if (nl < 0) {
              pos = content.length;
              break;
            }
            pos = nl + 1;
          }
          nextSelection = { anchor: pos, head: pos };
        }

        setTabs((prev) =>
          prev.map((t) =>
            t.path === path && t.kind === "text"
              ? { ...t, content, savedContent: content, dirty: false, selection: nextSelection ?? t.selection }
              : t,
          ),
        );
      } catch (e) {
        console.error(e);
      }
      return;
    }

    if (drawFile) {
      try {
        const t0 = isDevPerfEnabled() ? performance.now() : 0;
        const doc = await readDrawDocument(path);
        if (t0) recordSpanMs("vault:readDrawDocument", t0, performance.now() - t0, { path, ctx: "openFile" });
        setTabs((prev) =>
          prev.map((t) => (t.path === path && t.kind === "drawing" ? { ...t, doc, savedDoc: doc, dirty: false } : t)),
        );
      } catch (e) {
        console.error(e);
      }
    }
  }, [pushRecentFile]);
  openFileRef.current = openFile;

  const revealFolderInSidebar = useCallback(
    async (path: string) => {
      if (!vaultRoot) return;
      const norm = String(path || "")
        .replace(/\\/g, "/")
        .replace(/^\/+/, "")
        .replace(/\/+$/, "");

      setActivity("space");
      setSidebarOpen(true);
      setSelectedDir(norm);

      if (!norm) return;
      const parts = norm.split("/").filter(Boolean);
      let current = "";
      for (const part of parts) {
        current = current ? `${current}/${part}` : part;
        if (!expanded[current]) await toggleDir(current);
      }
    },
    [expanded, toggleDir, vaultRoot],
  );

  const saveActive = useCallback(async () => {
    if (!activeTab) return;
    if (savingRef.current) return;
    savingRef.current = true;
    try {
      if (activeTab.kind === "text") {
        const latest = latestTextByPathRef.current.get(activeTab.path);
        const content = latest ?? activeTab.content;
        if (content === activeTab.savedContent) return;

        const t0 = isDevPerfEnabled() ? performance.now() : 0;
        await writeTextFile(activeTab.path, content);
        if (t0) recordSpanMs("vault:writeTextFile", t0, performance.now() - t0, { path: activeTab.path, chars: content.length, ctx: "saveActive" });
        setTabs((prev) =>
          prev.map((t) =>
            t.path === activeTab.path && t.kind === "text" ? { ...t, savedContent: content, dirty: false } : t,
          ),
        );
      } else if (activeTab.kind === "drawing") {
        if (!activeTab.dirty) return;
        if (!activeTab.doc) return;
        const t0 = isDevPerfEnabled() ? performance.now() : 0;
        await writeDrawDocument(activeTab.path, activeTab.doc);
        if (t0) recordSpanMs("vault:writeDrawDocument", t0, performance.now() - t0, { path: activeTab.path, ctx: "saveActive" });
        setTabs((prev) =>
          prev.map((t) =>
            t.path === activeTab.path && t.kind === "drawing" ? { ...t, savedDoc: activeTab.doc, dirty: false } : t,
          ),
        );
      }
    } catch (e) {
      console.error(e);
    } finally {
      savingRef.current = false;
    }
  }, [activeTab]);

  const newFile = useCallback(async () => {
    if (!vaultRoot) return;
    const name = window.prompt("新建文件名（相对当前选中文件夹）：", "note.md");
    if (!name) return;
    const rel = join(selectedDir, name);
    try {
      await createFile(rel);
      await reloadDir(selectedDir);
      await openFile(rel);
    } catch (e) {
      console.error(e);
      window.alert(String(e));
    }
  }, [vaultRoot, selectedDir, reloadDir, openFile]);

  const newCanvas = useCallback(async () => {
    if (!vaultRoot) return;

    const dir = selectedDir;
    try {
      const entries = sortEntries(await listDir(dir));
      const existing = new Set(entries.filter((e) => !e.isDir).map((e) => e.name));
      let max = 0;
      for (const name of existing) {
        const m = /^未命名\\s+(\\d+)\\.zhixu$/i.exec(name);
        if (m) max = Math.max(max, Number(m[1]) || 0);
      }
      let n = max + 1;
      let fileName = `未命名 ${n}.zhixu`;
      while (existing.has(fileName)) {
        n += 1;
        fileName = `未命名 ${n}.zhixu`;
      }
      const rel = join(dir, fileName);

      await createFile(rel);
      await reloadDir(dir);
      await openFile(rel);
    } catch (e) {
      console.error(e);
      window.alert(String(e));
    }
  }, [vaultRoot, selectedDir, reloadDir, openFile]);

  const createUntitledInRoot = useCallback(async () => {
    let root = vaultRoot;
    if (!root) {
      root = await openFolder();
      if (!root) return;
    }

    try {
      const entries = sortEntries(await listDir(""));
      const existing = new Set(entries.filter((e) => !e.isDir).map((e) => e.name));
      let max = 0;
      for (const name of existing) {
        const m = /^未命名\s+(\d+)\.md$/i.exec(name);
        if (m) max = Math.max(max, Number(m[1]) || 0);
      }
      let n = max + 1;
      let fileName = `未命名 ${n}.md`;
      while (existing.has(fileName)) {
        n += 1;
        fileName = `未命名 ${n}.md`;
      }

      await createFile(fileName);
      await reloadDir("");
      await openFile(fileName);
    } catch (e) {
      console.error(e);
      window.alert(String(e));
    }
  }, [openFile, openFolder, reloadDir, vaultRoot]);

  const buildAllFiles = useCallback(async () => {
    const seq = ++filePickerSeqRef.current;
    const perfT0 = isDevPerfEnabled() ? performance.now() : 0;
    setFilePickerLoading(true);
    try {
      const out: string[] = [];
      const stack: string[] = [""];
      while (stack.length) {
        const dir = stack.pop()!;
        const entries = sortEntries(await listDir(dir));
        for (const e of entries) {
          if (e.isDir) stack.push(e.path);
          else out.push(e.path);
        }
        if (seq !== filePickerSeqRef.current) return;
      }
      if (seq !== filePickerSeqRef.current) return;
      if (perfT0) recordSpanMs("filePicker:buildAllFiles", perfT0, performance.now() - perfT0, { files: out.length });
      setAllFiles(out);
    } finally {
      if (seq === filePickerSeqRef.current) setFilePickerLoading(false);
    }
  }, [vaultRoot]);

  const openFilePicker = useCallback(
    async (mode: "all" | "recent" = "all") => {
      let root = vaultRoot;
      if (!root) {
        root = await openFolder();
        if (!root) return;
      }
      setFilePickerMode(mode);
      setFilePickerQuery("");
      setFilePickerActiveIndex(0);
      setFilePickerOpen(true);
      setTimeout(() => filePickerInputRef.current?.focus(), 0);
    },
    [openFolder, vaultRoot],
  );

  const addNewTab = useCallback(() => {
    const tab = makeNewTab(newTabIdRef.current++);
    setTabs((prev) => [...prev, tab]);
    setActivePath(tab.path);
  }, []);

  const newFolder = useCallback(async () => {
    if (!vaultRoot) return;
    const name = window.prompt("新建文件夹名称（相对当前选中文件夹）：", "新建文件夹");
    if (!name) return;
    const rel = join(selectedDir, name);
    try {
      await createDir(rel);
      setExpanded((m) => ({ ...m, [selectedDir]: true }));
      await reloadDir(selectedDir);
    } catch (e) {
      console.error(e);
      window.alert(String(e));
    }
  }, [vaultRoot, selectedDir, reloadDir]);

  const renameTabPath = useCallback(
    async (oldPath: string, next: string): Promise<boolean> => {
      if (!next || next === oldPath) return true;

      const nextFileName = basename(next);
      const nextName = stripExtension(nextFileName);
      const nextText = isTextFile(nextFileName);
      const nextDraw = isZhixuDrawFile(nextFileName);
      const nextBinaryLabel = getFileTypeLabel(nextFileName) ?? "文件";
      const nextBinaryPlaceholder = `该文件类型暂不支持在应用内打开：${nextBinaryLabel}\n\n路径：${next}`;

      try {
        await renameEntry(oldPath, next);
        setTabs((prev) =>
          prev.map((t) => {
            if (t.path !== oldPath) return t;
            if (nextDraw) {
              return {
                path: next,
                name: nextName,
                kind: "drawing",
                locked: t.locked,
                doc: t.kind === "drawing" ? t.doc : null,
                savedDoc: t.kind === "drawing" ? t.savedDoc : null,
                dirty: t.kind === "drawing" ? t.dirty : false,
                viewMode: t.kind === "drawing" ? t.viewMode : "writing",
                selection: { anchor: 0, head: 0 },
              };
            }
            if (!nextText) {
              return {
                path: next,
                name: nextName,
                kind: "binary",
                locked: t.locked,
                content: nextBinaryPlaceholder,
                savedContent: nextBinaryPlaceholder,
                dirty: false,
                selection: { anchor: 0, head: 0 },
              };
            }
            return {
              path: next,
              name: nextName,
              kind: "text",
              locked: t.locked,
              content: t.kind === "text" ? t.content : "",
              savedContent: t.kind === "text" ? t.savedContent : "",
              dirty: t.kind === "text" ? t.dirty : false,
              selection: { anchor: 0, head: 0 },
            };
          }),
        );
        setActivePath((prev) => (prev === oldPath ? next : prev));
        await reloadDir(dirname(oldPath));
        await reloadDir(dirname(next));
        if (nextText) {
          try {
            const content = await readTextFile(next);
            setTabs((prev) =>
              prev.map((t) => (t.path === next && t.kind === "text" ? { ...t, content, savedContent: content, dirty: false } : t)),
            );
          } catch (e) {
            console.error(e);
          }
        } else if (nextDraw) {
          try {
            const doc = await readDrawDocument(next);
            setTabs((prev) =>
              prev.map((t) => (t.path === next && t.kind === "drawing" ? { ...t, doc, savedDoc: doc, dirty: false } : t)),
            );
          } catch (e) {
            console.error(e);
          }
        }
        return true;
      } catch (e) {
        console.error(e);
        window.alert(String(e));
        return false;
      }
    },
    [reloadDir],
  );

  const renameActive = useCallback(async () => {
    if (!activeTab) return;
    if (activeTab.kind === "plugin" || activeTab.kind === "newtab") return;
    const next = window.prompt("重命名为（库内相对路径）：", activeTab.path);
    if (!next || next === activeTab.path) return;

    const nextFileName = basename(next);
    const nextName = stripExtension(nextFileName);
    const nextText = isTextFile(nextFileName);
    const nextDraw = isZhixuDrawFile(nextFileName);
    const nextBinaryLabel = getFileTypeLabel(nextFileName) ?? "文件";
    const nextBinaryPlaceholder = `该文件类型暂不支持在应用内打开：${nextBinaryLabel}\n\n路径：${next}`;

    try {
      await renameEntry(activeTab.path, next);
      setTabs((prev) =>
        prev.map((t) => {
          if (t.path !== activeTab.path) return t;
          if (nextDraw) {
            return {
              path: next,
              name: nextName,
              kind: "drawing",
              locked: t.locked,
              doc: t.kind === "drawing" ? t.doc : null,
              savedDoc: t.kind === "drawing" ? t.savedDoc : null,
              dirty: t.kind === "drawing" ? t.dirty : false,
              viewMode: t.kind === "drawing" ? t.viewMode : "writing",
              selection: { anchor: 0, head: 0 },
            };
          }
          if (!nextText) {
            return {
              path: next,
              name: nextName,
              kind: "binary",
              locked: t.locked,
              content: nextBinaryPlaceholder,
              savedContent: nextBinaryPlaceholder,
              dirty: false,
              selection: { anchor: 0, head: 0 },
            };
          }
          return {
            path: next,
            name: nextName,
            kind: "text",
            locked: t.locked,
            content: t.kind === "text" ? t.content : "",
            savedContent: t.kind === "text" ? t.savedContent : "",
            dirty: t.kind === "text" ? t.dirty : false,
            selection: { anchor: 0, head: 0 },
          };
        }),
      );
      setActivePath(next);
      await reloadDir(dirname(activeTab.path));
      await reloadDir(dirname(next));
      if (nextText) {
        try {
          const content = await readTextFile(next);
          setTabs((prev) => prev.map((t) => (t.path === next && t.kind === "text" ? { ...t, content, savedContent: content, dirty: false } : t)));
        } catch (e) {
          console.error(e);
        }
      } else if (nextDraw) {
        try {
          const doc = await readDrawDocument(next);
          setTabs((prev) => prev.map((t) => (t.path === next && t.kind === "drawing" ? { ...t, doc, savedDoc: doc, dirty: false } : t)));
        } catch (e) {
          console.error(e);
        }
      }
    } catch (e) {
      console.error(e);
      window.alert(String(e));
    }
  }, [activeTab, reloadDir]);

  const deleteActive = useCallback(async () => {
    if (!activePath) return;
    const tab = tabs.find((t) => t.path === activePath) ?? null;
    if (tab?.kind === "plugin" || tab?.kind === "newtab") return;
    const ok = window.confirm(`确认删除？\n\n${activePath}`);
    if (!ok) return;
    try {
      await deleteEntry(activePath);
      setTabs((prev) => prev.filter((t) => t.path !== activePath));
      setActivePath((prev) => (prev === activePath ? null : prev));
      await reloadDir(dirname(activePath));
    } catch (e) {
      console.error(e);
      window.alert(String(e));
    }
  }, [activePath, reloadDir, tabs]);

  const closeTab = useCallback(
    (path: string) => {
      const tab = tabs.find((t) => t.path === path);
      if (tab?.locked) {
        void message("该标签页已锁定，请先点击图钉解锁后再关闭。", { title: "标签页已锁定", kind: "info" });
        return;
      }
      if (tab?.dirty) {
        const ok = window.confirm(`该标签页有未保存的更改，确定关闭？\n\n${tab.path}`);
        if (!ok) return;
      }
      const remaining = tabs.filter((t) => t.path !== path);
      if (remaining.length === 0) {
        if (isDetached) {
          setTabs([]);
          setActivePath(null);
          return;
        }
        const next = makeNewTab(newTabIdRef.current++);
        setTabs([next]);
        setActivePath(next.path);
        return;
      }
      setTabs(remaining);
      if (activePath === path) setActivePath(remaining[remaining.length - 1]!.path);
    },
    [activePath, isDetached, tabs],
  );

  const removeTabSilently = useCallback((path: string) => {
    setTabs((prev) => {
      const remaining = prev.filter((t) => t.path !== path);
      if (remaining.length === 0) {
        if (isDetached) {
          setActivePath(null);
          return [];
        }
        const next = makeNewTab(newTabIdRef.current++);
        setActivePath(next.path);
        return [next];
      }
      setActivePath((prevActive) => (prevActive === path ? remaining[remaining.length - 1]!.path : prevActive));
      return remaining;
    });
  }, [isDetached]);

  const closeManyTabs = useCallback(
    (toClose: string[], confirmLabel: string) => {
      if (toClose.length === 0) return;
      const locked = tabs.filter((t) => toClose.includes(t.path) && t.locked);
      if (locked.length) {
        void message(`已跳过 ${locked.length} 个锁定标签页（需先解锁才能关闭）。`, { title: "标签页已锁定", kind: "info" });
        toClose = toClose.filter((p) => !locked.some((t) => t.path === p));
      }
      if (toClose.length === 0) return;
      const dirty = tabs.filter((t) => toClose.includes(t.path) && t.dirty);
      if (dirty.length) {
        const preview = dirty
          .slice(0, 5)
          .map((t) => t.path)
          .join("\n");
        const more = dirty.length > 5 ? `\n...（还有 ${dirty.length - 5} 个）` : "";
        const ok = window.confirm(`${confirmLabel}（其中 ${dirty.length} 个有未保存更改），确定继续？\n\n${preview}${more}`);
        if (!ok) return;
      }
      const remaining = tabs.filter((t) => !toClose.includes(t.path));
      if (remaining.length === 0) {
        if (isDetached) {
          setTabs([]);
          setActivePath(null);
          return;
        }
        const next = makeNewTab(newTabIdRef.current++);
        setTabs([next]);
        setActivePath(next.path);
        return;
      }
      setTabs(remaining);
      if (activePath && toClose.includes(activePath)) setActivePath(remaining[remaining.length - 1]!.path);
    },
    [activePath, isDetached, tabs],
  );

  const closeOtherTabs = useCallback(
    (keepPath: string) => closeManyTabs(tabs.filter((t) => t.path !== keepPath).map((t) => t.path), "关闭其他标签页"),
    [closeManyTabs, tabs],
  );

  const closeRightTabs = useCallback(
    (keepPath: string) => {
      const index = tabs.findIndex((t) => t.path === keepPath);
      if (index < 0) return;
      closeManyTabs(tabs.slice(index + 1).map((t) => t.path), "关闭右侧标签页");
    },
    [closeManyTabs, tabs],
  );

  const closeAllTabs = useCallback(() => closeManyTabs(tabs.map((t) => t.path), "全部关闭"), [closeManyTabs, tabs]);

  const openTabContextMenu = useCallback((ev: React.MouseEvent, path: string) => {
    ev.preventDefault();
    ev.stopPropagation();
    setActivePath(path);
    setTabContextPath(path);
    setTabContextPoint({ x: ev.clientX, y: ev.clientY });
    setTabContextOpen(true);
  }, []);

  const closeTabContextMenu = useCallback(() => setTabContextOpen(false), []);

  const toggleTabLock = useCallback((path: string, next?: boolean) => {
    setTabs((prev) => prev.map((t) => (t.path === path ? { ...t, locked: next ?? !t.locked } : t)));
  }, []);

  const cleanupDragPreview = useCallback(() => {
    const el = dragPreviewElRef.current;
    dragPreviewElRef.current = null;
    if (!el) return;
    el.remove();
  }, []);

  const setTabDragImage = useCallback(
    (ev: React.DragEvent, title: string) => {
      try {
        cleanupDragPreview();
        const el = document.createElement("div");
        el.className = "tabDragPreview";
        const label = document.createElement("div");
        label.className = "tabDragPreviewLabel";
        label.textContent = title;
        el.appendChild(label);
        document.body.appendChild(el);
        dragPreviewElRef.current = el;
        const rect = el.getBoundingClientRect();
        ev.dataTransfer.setDragImage(el, Math.round(rect.width / 2), Math.round(rect.height / 2));
      } catch (e) {
        console.error(e);
      }
    },
    [cleanupDragPreview],
  );

  const computeTabInsertFromClientPoint = useCallback((clientX: number, clientY: number) => {
    const host = tabStripRef.current;
    if (!host) return null;
    const hostRect = host.getBoundingClientRect();
    if (clientY < hostRect.top || clientY > hostRect.bottom) return null;

    const currentTabs = tabsRef.current;
    if (!currentTabs.length) return { index: 0, left: 0 };

    const tabRects: Array<DOMRect | null> = currentTabs.map(
      (t) => tabElByPathRef.current.get(t.path)?.getBoundingClientRect() ?? null,
    );

    let index = currentTabs.length;
    for (let i = 0; i < currentTabs.length; i++) {
      const rect = tabRects[i];
      if (!rect) continue;
      const center = rect.left + rect.width / 2;
      if (clientX < center) {
        index = i;
        break;
      }
    }

    let targetRect: DOMRect | null = null;
    if (index === 0) {
      for (const r of tabRects) {
        if (r) {
          targetRect = r;
          break;
        }
      }
      if (!targetRect) return null;
      return { index, left: targetRect.left - hostRect.left };
    }

    if (index === currentTabs.length) {
      for (let i = tabRects.length - 1; i >= 0; i--) {
        const r = tabRects[i];
        if (r) {
          targetRect = r;
          break;
        }
      }
      if (!targetRect) return null;
      return { index, left: targetRect.right - hostRect.left };
    }

    targetRect = tabRects[index];
    if (!targetRect) return null;
    return { index, left: targetRect.left - hostRect.left };
  }, []);

  useEffect(() => {
    if (!recentlyInsertedPath) return;
    const id = window.setTimeout(() => setRecentlyInsertedPath(null), 260);
    return () => window.clearTimeout(id);
  }, [recentlyInsertedPath]);

  const applyTabTransfer = useCallback(
    async (payload: TabTransferPayload, options?: { insertIndex?: number }) => {
      try {
        const nextRoot = payload.vaultRoot;
        if (nextRoot && !sameVaultRoot(nextRoot, vaultRoot)) {
          const resolved = await withDevPerfSpan("vault:setVaultRoot", () => setVaultRoot(nextRoot), { ctx: "tabTransfer" });
          await resetForVault(resolved, { createNewTab: false, keepTabs: true });
          setPersisted(await getPersistedState());
        }
      } catch (e) {
        console.error(e);
      }

      const insertIndex = options?.insertIndex;
      setTabs((prev) => {
        const fromIndex = prev.findIndex((t) => t.path === payload.tab.path);
        const sameWindow = payload.sourceWindowLabel === appWindow.label;
        if (sameWindow && fromIndex >= 0) {
          const tab = prev[fromIndex]!;
          const without = prev.filter((t) => t.path !== payload.tab.path);
          const clamped = insertIndex == null ? without.length : Math.max(0, Math.min(insertIndex, without.length));
          const adjusted = insertIndex != null && fromIndex < insertIndex ? Math.max(0, clamped - 1) : clamped;
          return [...without.slice(0, adjusted), tab, ...without.slice(adjusted)];
        }

        const without = prev.filter((t) => t.path !== payload.tab.path);
        const at = insertIndex == null ? without.length : Math.max(0, Math.min(insertIndex, without.length));
        return [...without.slice(0, at), payload.tab, ...without.slice(at)];
      });

      setActivePath(payload.tab.path);
      setRecentlyInsertedPath(payload.tab.path);

      if (payload.sourceWindowLabel && payload.sourceWindowLabel !== appWindow.label) {
        await emitTo(payload.sourceWindowLabel, "zhixu:tab-remove", { path: payload.tab.path });
      }
    },
    [appWindow.label, resetForVault, sameVaultRoot, vaultRoot],
  );

  const readTabTransferFromEvent = useCallback(
    (ev: React.DragEvent): { id: string; payload: TabTransferPayload } | null => {
      const raw =
        ev.dataTransfer.getData("application/x-zhixu-tab-transfer") ||
        ev.dataTransfer.getData("text/plain") ||
        ev.dataTransfer.getData("application/x-zhixu-tab");
      if (!raw) return null;

      let id = raw.trim();
      const prefix = "zhixu-tab-transfer:";
      if (id.startsWith(prefix)) id = id.slice(prefix.length).trim();
      if (!id) return null;

      const payload = readTabTransfer(id);
      if (!payload) return null;

      return { id, payload };
    },
    [readTabTransfer],
  );

  useEffect(() => {
    if (!isDetached) return;
    if (!transferId) return;
    const payload = readTabTransfer(transferId);
    if (!payload) return;
    void (async () => {
      try {
        await applyTabTransfer(payload);
      } finally {
        clearTabTransfer(transferId);
      }
    })();
  }, [applyTabTransfer, clearTabTransfer, isDetached, readTabTransfer, transferId]);

  const findOtherWindowUnderCursorLabel = useCallback(async (): Promise<string | null> => {
    try {
      const point = await cursorPosition();
      const windows = await WebviewWindow.getAll();
      let best: { label: string; area: number } | null = null;

      for (const w of windows) {
        if (w.label === appWindow.label) continue;
        const [pos, size] = await Promise.all([w.outerPosition(), w.outerSize()]);
        const inside =
          point.x >= pos.x &&
          point.y >= pos.y &&
          point.x <= pos.x + size.width &&
          point.y <= pos.y + size.height;
        if (!inside) continue;
        const area = Math.max(1, size.width) * Math.max(1, size.height);
        if (!best || area < best.area) best = { label: w.label, area };
      }

      return best?.label ?? null;
    } catch (e) {
      console.error(e);
      return null;
    }
  }, [appWindow.label]);

  const stopTabDragBroadcast = useCallback(async (transferId: string) => {
    const timerId = dragBroadcastTimerRef.current;
    dragBroadcastTimerRef.current = null;
    if (timerId != null) window.clearInterval(timerId);

    const last = dragBroadcastTargetLabelRef.current;
    dragBroadcastTargetLabelRef.current = null;
    if (!last) return;
    try {
      await emitTo(last, "zhixu:tab-drag-leave", { transferId });
    } catch (e) {
      console.error(e);
    }
  }, []);

  const startTabDragBroadcast = useCallback(
    (transferId: string) => {
      const timerId = dragBroadcastTimerRef.current;
      if (timerId != null) window.clearInterval(timerId);
      dragBroadcastTimerRef.current = null;

      dragBroadcastTargetLabelRef.current = null;

      let busy = false;
      dragBroadcastTimerRef.current = window.setInterval(() => {
        if (busy) return;
        busy = true;
        void (async () => {
          try {
            const targetLabel = await findOtherWindowUnderCursorLabel();
            const prev = dragBroadcastTargetLabelRef.current;
            if (prev && prev !== targetLabel) {
              try {
                await emitTo(prev, "zhixu:tab-drag-leave", { transferId });
              } catch (e) {
                console.error(e);
              }
            }
            dragBroadcastTargetLabelRef.current = targetLabel;
            if (targetLabel) {
              try {
                await emitTo(targetLabel, "zhixu:tab-drag-over", { transferId });
              } catch (e) {
                console.error(e);
              }
            }
          } finally {
            busy = false;
          }
        })();
      }, 70);
    },
    [findOtherWindowUnderCursorLabel],
  );

  const isCursorInsideAppWindow = useCallback(async (): Promise<boolean> => {
    try {
      const point = await cursorPosition();
      const [pos, size] = await Promise.all([appWindow.outerPosition(), appWindow.outerSize()]);
      return (
        point.x >= pos.x &&
        point.y >= pos.y &&
        point.x <= pos.x + size.width &&
        point.y <= pos.y + size.height
      );
    } catch (e) {
      console.error(e);
      return true;
    }
  }, [appWindow]);

  const getClientCursorPosition = useCallback(async (): Promise<{ x: number; y: number }> => {
    const point = await cursorPosition();
    const [pos, scale] = await Promise.all([appWindow.outerPosition(), appWindow.scaleFactor()]);
    return { x: (point.x - pos.x) / scale, y: (point.y - pos.y) / scale };
  }, [appWindow]);

  const openTabTransferWindow = useCallback(
    async (payload: { transferId: string; title: string; vaultRoot: string | null }) => {
      const label = `tab-${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
      try {
        const url = new URL(window.location.href);
        url.searchParams.set("view", "tab");
        url.searchParams.set("transferId", payload.transferId);
        if (payload.vaultRoot) url.searchParams.set("vaultRoot", payload.vaultRoot);
        const win = new WebviewWindow(label, {
          url: url.toString(),
          title: payload.title,
          width: 980,
          height: 720,
          decorations: false,
        });
        void win.once("tauri://error", async (e) => {
          clearTabTransfer(payload.transferId);
          await message(String(e), { title: "无法新建窗口", kind: "error" });
        });
      } catch (e) {
        clearTabTransfer(payload.transferId);
        console.error(e);
        await message(String(e), { title: "无法新建窗口", kind: "error" });
      }
    },
    [clearTabTransfer],
  );

  const moveTabToNewWindow = useCallback(
    async (path: string) => {
      const tab = tabs.find((t) => t.path === path);
      if (!tab) return;
      const nextTransferId = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
      const ok = writeTabTransfer(nextTransferId, { tab, vaultRoot, sourceWindowLabel: appWindow.label });
      if (!ok) {
        await message("无法保存要转移的标签页数据，请重试。", { title: "移动到新窗口失败", kind: "error" });
        return;
      }
      await openTabTransferWindow({ transferId: nextTransferId, title: tab.name, vaultRoot });
    },
    [appWindow.label, openTabTransferWindow, tabs, vaultRoot, writeTabTransfer],
  );

  const onAppKeyDown = useCallback(
    (ev: KeyboardEvent) => {
      if (ev.key === "Escape") {
        if (settingsOpen) {
          ev.preventDefault();
          ev.stopPropagation();
          setSettingsOpen(false);
          return;
        }
        if (shortcutsOpen) {
          ev.preventDefault();
          ev.stopPropagation();
          setShortcutsOpen(false);
          return;
        }
        if (pluginDevOpen) {
          ev.preventDefault();
          ev.stopPropagation();
          setPluginDevOpen(false);
          return;
        }
        if (vaultPickerOpen) {
          setVaultPickerOpen(false);
          return;
        }
        return;
      }

      const mod = ev.ctrlKey || ev.metaKey;
      if (!mod) return;

      const key = ev.key.toLowerCase();
      if (key === "p" && ev.shiftKey) {
        ev.preventDefault();
        ev.stopPropagation();
        setPluginDevOpen(true);
        return;
      }
      if (key === "n") {
        ev.preventDefault();
        ev.stopPropagation();
        void createUntitledInRoot();
        return;
      }
      if (key === "o") {
        ev.preventDefault();
        ev.stopPropagation();
        void openFilePicker("all");
        return;
      }
      if (key === "s") {
        ev.preventDefault();
        ev.stopPropagation();
        void saveActive();
        return;
      }
      if (key === "e") {
        ev.preventDefault();
        ev.stopPropagation();
        setEditorMode((m) => (m === "live" ? "source" : "live"));
        return;
      }
      if (key === "k") {
        ev.preventDefault();
        ev.stopPropagation();
        setShortcutsOpen((v) => !v);
      }
    },
    [createUntitledInRoot, openFilePicker, pluginDevOpen, saveActive, settingsOpen, shortcutsOpen, vaultPickerOpen],
  );

  useEffect(() => {
    window.addEventListener("keydown", onAppKeyDown, true);
    return () => window.removeEventListener("keydown", onAppKeyDown, true);
  }, [onAppKeyDown]);

  useEffect(() => {
    void (async () => {
      try {
        const t0 = isDevPerfEnabled() ? performance.now() : 0;
        const next = await getPersistedState();
        if (t0) recordSpanMs("startup:getPersistedState", t0, performance.now() - t0);
        setPersisted(next);
      } catch (e) {
        console.error(e);
      }
    })();
  }, []);

  useEffect(() => {
    let unlistenTransfer: null | (() => void) = null;
    let unlistenRemove: null | (() => void) = null;
    let unlistenRemoteDragOver: null | (() => void) = null;
    let unlistenRemoteDragLeave: null | (() => void) = null;
    const remoteDragTransferIdRef = { current: null as string | null };
    const remoteDragHideTimerRef = { current: null as number | null };
    void (async () => {
      unlistenTransfer = await appWindow.listen<TabTransferPayload>("zhixu:tab-transfer", async (event) => {
        try {
          const p = await getClientCursorPosition();
          const insert = computeTabInsertFromClientPoint(p.x, p.y);
          if (insert) {
            setTabInsertIndicator(insert);
            window.setTimeout(() => setTabInsertIndicator(null), 220);
            await applyTabTransfer(event.payload, { insertIndex: insert.index });
            return;
          }
        } catch (e) {
          console.error(e);
        }
        await applyTabTransfer(event.payload);
      });

      unlistenRemove = await appWindow.listen<{ path: string }>("zhixu:tab-remove", (event) => {
        const path = event.payload.path;
        const transferId = transferCleanupByPathRef.current.get(path);
        if (transferId) {
          transferCleanupByPathRef.current.delete(path);
          clearTabTransfer(transferId);
        }
        removeTabSilently(path);
      });

      unlistenRemoteDragOver = await appWindow.listen<{ transferId: string }>("zhixu:tab-drag-over", async (event) => {
        try {
          remoteDragTransferIdRef.current = event.payload.transferId;
          if (remoteDragHideTimerRef.current != null) window.clearTimeout(remoteDragHideTimerRef.current);
          const p = await getClientCursorPosition();
          const insert = computeTabInsertFromClientPoint(p.x, p.y);
          if (insert) setTabInsertIndicator(insert);
          remoteDragHideTimerRef.current = window.setTimeout(() => {
            if (remoteDragTransferIdRef.current === event.payload.transferId) {
              remoteDragTransferIdRef.current = null;
              setTabInsertIndicator(null);
            }
          }, 160);
        } catch (e) {
          console.error(e);
        }
      });

      unlistenRemoteDragLeave = await appWindow.listen<{ transferId: string }>("zhixu:tab-drag-leave", (event) => {
        if (remoteDragTransferIdRef.current !== event.payload.transferId) return;
        remoteDragTransferIdRef.current = null;
        if (remoteDragHideTimerRef.current != null) window.clearTimeout(remoteDragHideTimerRef.current);
        remoteDragHideTimerRef.current = null;
        setTabInsertIndicator(null);
      });
    })();

    return () => {
      unlistenTransfer?.();
      unlistenRemove?.();
      unlistenRemoteDragOver?.();
      unlistenRemoteDragLeave?.();
      const timerId = dragBroadcastTimerRef.current;
      dragBroadcastTimerRef.current = null;
      if (timerId != null) window.clearInterval(timerId);
    };
  }, [appWindow, applyTabTransfer, clearTabTransfer, computeTabInsertFromClientPoint, getClientCursorPosition, removeTabSilently]);

  useEffect(() => {
    if (!vaultRoot) {
      setRecentFiles([]);
      setAllFiles([]);
      return;
    }
    try {
      const raw = localStorage.getItem(recentKey(vaultRoot));
      const parsed = raw ? (JSON.parse(raw) as unknown) : [];
      setRecentFiles(Array.isArray(parsed) ? (parsed.filter((p) => typeof p === "string") as string[]) : []);
    } catch (_) {
      setRecentFiles([]);
    }
    setAllFiles([]);
  }, [recentKey, vaultRoot]);

  useEffect(() => {
    if (!filePickerOpen) return;
    if (filePickerMode !== "all") return;
    if (!filePickerQuery.trim()) return;
    if (allFiles.length) return;
    if (filePickerLoading) return;
    void buildAllFiles();
  }, [allFiles.length, buildAllFiles, filePickerLoading, filePickerMode, filePickerOpen, filePickerQuery]);

  const filePickerItems = useMemo(() => {
    const q = filePickerQuery.trim().toLowerCase();
    const base = filePickerMode === "recent" ? recentFiles : q ? allFiles : recentFiles;
    if (!q) return base;
    return base.filter((p) => p.toLowerCase().includes(q) || basename(p).toLowerCase().includes(q));
  }, [allFiles, filePickerMode, filePickerQuery, recentFiles]);

  const filePickerVisibleItems = useMemo(() => filePickerItems.slice(0, 10), [filePickerItems]);

  useEffect(() => {
    if (!filePickerOpen) return;
    setFilePickerActiveIndex((i) => Math.min(i, Math.max(0, filePickerVisibleItems.length - 1)));
  }, [filePickerOpen, filePickerVisibleItems.length]);

  const flattenedNodes: TreeNode[] = useMemo(() => {
    const out: TreeNode[] = [];
    const walk = (parentPath: string, depth: number) => {
      const entries = dirCache[parentPath] ?? [];
      for (const e of entries) {
        const node: TreeNode = {
          ...e,
          depth,
          expanded: e.isDir ? Boolean(expanded[e.path]) : undefined,
          loading: e.isDir ? Boolean(loadingDir[e.path]) : undefined,
        };
        out.push(node);
        if (e.isDir && expanded[e.path]) walk(e.path, depth + 1);
      }
    };
    walk("", 0);
    return out;
  }, [dirCache, expanded, loadingDir]);

  const rootLabel = useMemo(() => (vaultRoot ? basename(vaultRoot) : "未选择库"), [vaultRoot]);

  const vaultOptions = useMemo(() => {
    const merged: string[] = [];
    if (vaultRoot) merged.push(vaultRoot);
    for (const p of persisted.recentVaults) merged.push(p);
    return [...new Set(merged)];
  }, [vaultRoot, persisted.recentVaults]);

  useEffect(() => {
    if (!vaultPickerOpen) return;
    const onMouseDown = (ev: MouseEvent) => {
      const host = vaultPickerRef.current;
      if (!host) return;
      if (ev.target instanceof Node && host.contains(ev.target)) return;
      setVaultPickerOpen(false);
    };
    const onKeyDown = (ev: KeyboardEvent) => {
      if (ev.key === "Escape") setVaultPickerOpen(false);
    };
    window.addEventListener("mousedown", onMouseDown);
    window.addEventListener("keydown", onKeyDown);
    return () => {
      window.removeEventListener("mousedown", onMouseDown);
      window.removeEventListener("keydown", onKeyDown);
    };
  }, [vaultPickerOpen]);

  const editorPlaceholder = useMemo(() => {
    if (!vaultRoot) return "请选择一个库开始…";
    if (!activeTab) return "从主侧栏打开一个 Markdown 或绘图（.zhixu）文件…";
    return "";
  }, [vaultRoot, activeTab]);

  const pluginTabPath = useCallback((pluginId: string, actionId: string) => {
    return `__plugin__:${pluginId}:${actionId}`;
  }, []);

  const openPluginTab = useCallback(
    (opts: { pluginId: string; actionId: string; title: string; html: string }) => {
      const pluginId = String(opts.pluginId || "").trim();
      const actionId = String(opts.actionId || "").trim();
      if (!pluginId || !actionId) return;
      const tabPath = pluginTabPath(pluginId, actionId);
      const name = String(opts.title || "").trim() || "插件";
      const html = String(opts.html || "");

      setTabs((prev) => {
        const existing = prev.find((t) => t.path === tabPath);
        if (existing && existing.kind === "plugin") {
          return prev.map((t) => (t.path === tabPath && t.kind === "plugin" ? { ...t, name, html } : t));
        }
        return [
          ...prev,
          {
            path: tabPath,
            name,
            kind: "plugin",
            locked: false,
            pluginId,
            actionId,
            html,
            dirty: false,
            selection: { anchor: 0, head: 0 },
          } satisfies PluginTab,
        ];
      });
      setActivePath(tabPath);
    },
    [pluginTabPath],
  );

  useEffect(() => {
    const state = pluginSidebarState;
    if (!pendingPluginTabOpen) return;
    if (!state) return;
    if (pendingPluginTabOpen !== state.activityId) return;

    if (state.error) {
      setPendingPluginTabOpen(null);
      return;
    }
    if (state.loading) return;

    const title = String(state.editorTitle || state.title || "").trim();
    const html = String(state.editorHtml || state.html || "").trim();
    if (html) openPluginTab({ pluginId: state.pluginId, actionId: state.actionId, title: title || "插件", html });
    setPendingPluginTabOpen(null);
  }, [openPluginTab, pendingPluginTabOpen, pluginSidebarState]);

  const openActivity = useCallback((next: Activity) => {
    const parsed = parsePluginActivityId(next);
    if (parsed) {
      setActivity(next);
      setSidebarOpen(true);
      setPendingPluginTabOpen(next as PluginActivityId);
      return;
    }
    setActivity(next);
    setSidebarOpen(true);
    setPendingPluginTabOpen(null);
  }, []);

  const startDraggingIfAllowed = useCallback(
    (ev: React.MouseEvent) => {
      if (ev.button !== 0) return;
      const target = ev.target as HTMLElement | null;
      if (!target) return;
      if (target.closest('[data-no-drag="true"]')) return;
      void appWindow.startDragging();
    },
    [appWindow],
  );

  return (
    <div
      ref={appShellRef}
      className={`appShell${isDetached ? " detached" : ""}${sidebarOpen ? "" : " sidebarClosed"}${
        sidebarResizeActive ? " sidebarResizeActive" : ""
      }`}
      style={{ ["--mainSidebarWidth" as any]: `${isDetached || !sidebarOpen ? 0 : mainSidebarWidth}px` } as React.CSSProperties}
      onDragOver={(e) => {
        // Allow dropping a tab anywhere inside the window.
        e.preventDefault();
        e.dataTransfer.dropEffect = "move";
      }}
      onDrop={(e) => {
        const transfer = readTabTransferFromEvent(e);
        if (!transfer) return;
        e.preventDefault();
        clearTabTransfer(transfer.id);
        void applyTabTransfer(transfer.payload, { insertIndex: tabsRef.current.length });
      }}
    >
      <span
        ref={tabContextAnchorRef}
        style={{ position: "fixed", left: tabContextPoint.x, top: tabContextPoint.y, width: 1, height: 1, pointerEvents: "none" }}
        aria-hidden="true"
      />
      {/* 菜单栏（Menubar）：展开/收起固定按钮、主侧栏顶部按钮、标签页、窗口按钮 */}
      <div className="topbar menubar" onMouseDown={startDraggingIfAllowed}>
        <div className="menubarLeft">
          {/* 展开/收起图标按钮固定 */}
          <IconButton
            title={sidebarOpen ? "收起" : "展开"}
            tooltipPlacement="right"
            onClick={() => setSidebarOpen((v) => !v)}
            className="sidebarToggleBtn"
          >
            {sidebarOpen ? <IconSidebarClose size={22} /> : <IconSidebarOpen size={22} />}
          </IconButton>
          {/* 主侧栏顶部按钮：空间 */}
          {sidebarOpen ? (
            <>
              <IconButton title="空间" tooltipPlacement="bottom" active={activity === "space"} onClick={() => openActivity("space")}>
                <IconSpace />
              </IconButton>
            </>
          ) : null}
        </div>

        {/* 标签页显示 */}
        <div className="menubarTabs">
          <div
            className="tabs"
            ref={tabStripRef}
            data-density={tabsDensity}
            style={{ ["--tab-count" as any]: tabCount } as React.CSSProperties}
            role="tablist"
            onDragOver={(e) => {
              e.preventDefault();
              e.dataTransfer.dropEffect = "move";
              const transfer = readTabTransferFromEvent(e);
              if (!transfer && !draggingTabPathRef.current) return;
              const insert = computeTabInsertFromClientPoint(e.clientX, e.clientY);
              if (insert) setTabInsertIndicator(insert);
            }}
            onDragLeave={(e) => {
              const next = e.relatedTarget as Node | null;
              if (next && e.currentTarget.contains(next)) return;
              setTabInsertIndicator(null);
            }}
            onDrop={(e) => {
              const transfer = readTabTransferFromEvent(e);
              if (!transfer) return;
              e.preventDefault();
              e.stopPropagation();
              clearTabTransfer(transfer.id);
              const insertIndex = tabInsertIndicator?.index ?? tabs.length;
              setTabInsertIndicator(null);
              void applyTabTransfer(transfer.payload, { insertIndex });
            }}
            aria-label="标签页"
          >
            <div
              className={`tabInsertIndicator${tabInsertIndicator ? " visible" : ""}`}
              aria-hidden="true"
              style={tabInsertIndicator ? ({ left: tabInsertIndicator.left } as React.CSSProperties) : undefined}
            />
            {tabs.map((t, index) => (
              <div
                key={t.path}
                className={`tab${t.path === activePath ? " active" : ""}`}
                ref={(el) => {
                  if (el) tabElByPathRef.current.set(t.path, el);
                  else tabElByPathRef.current.delete(t.path);
                }}
                role="tab"
                aria-selected={t.path === activePath}
                data-dirty={t.dirty ? "true" : "false"}
                data-no-drag="true"
                data-inserted={t.path === recentlyInsertedPath ? "true" : "false"}
                onClick={() => setActivePath(t.path)}
                onContextMenu={(e) => openTabContextMenu(e, t.path)}
                draggable
                onDragStart={(e) => {
                  const transferId = `${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;
                  const ok = writeTabTransfer(transferId, { tab: t, vaultRoot, sourceWindowLabel: appWindow.label });
                  if (!ok) return;
                  e.dataTransfer.setData("application/x-zhixu-tab-transfer", transferId);
                  e.dataTransfer.setData("text/plain", `zhixu-tab-transfer:${transferId}`);
                  e.dataTransfer.effectAllowed = "move";
                  draggingTabPathRef.current = t.path;
                  draggingTransferIdRef.current = transferId;
                  setTabDragImage(e, t.name);
                  startTabDragBroadcast(transferId);
                }}
                onDragEnd={(e) => {
                  const id = draggingTransferIdRef.current;
                  const path = draggingTabPathRef.current;
                  draggingTransferIdRef.current = null;
                  draggingTabPathRef.current = null;
                  cleanupDragPreview();
                  setTabInsertIndicator(null);
                  if (!id || !path) return;
                  void stopTabDragBroadcast(id);
                  if (e.dataTransfer.dropEffect !== "none") return;
                  void (async () => {
                    const pending = readTabTransfer(id);
                    if (!pending) return;

                    const inside = await isCursorInsideAppWindow();
                    if (inside) {
                      clearTabTransfer(id);
                      return;
                    }

                    const targetLabel = await findOtherWindowUnderCursorLabel();
                    if (targetLabel) {
                      try {
                        transferCleanupByPathRef.current.set(path, id);
                        await emitTo<TabTransferPayload>(targetLabel, "zhixu:tab-transfer", pending);
                        return;
                      } catch (err) {
                        console.error(err);
                      }
                    }

                    transferCleanupByPathRef.current.set(path, id);
                    await openTabTransferWindow({ transferId: id, title: pending.tab.name, vaultRoot: pending.vaultRoot });
                  })();
                }}
              >
                <div className="tabInner">
                  <Tooltip label={t.kind === "newtab" ? "新标签页" : t.path} placement="bottom">
                    <span className="tabLabel">{t.name}</span>
                  </Tooltip>
                  <button
                    type="button"
                    className={`tabClose${t.locked ? " locked" : ""}`}
                    aria-label={t.locked ? "解锁标签页" : "关闭标签页"}
                    data-no-drag="true"
                    onClick={(e) => {
                      e.stopPropagation();
                      if (t.locked) {
                        toggleTabLock(t.path, false);
                        return;
                      }
                      closeTab(t.path);
                    }}
                  >
                    <Tooltip label={t.locked ? "解锁" : "关闭"} placement="bottom">
                      {t.locked ? (
                        <span className="tabCloseGlyph" aria-hidden="true">
                          <IconLucidePin size={14} />
                        </span>
                      ) : (
                        <span className="tabCloseGlyph" aria-hidden="true">
                          <span className="tabCloseDot" />
                          <span className="tabCloseX">
                            <IconClose size={14} />
                          </span>
                        </span>
                      )}
                    </Tooltip>
                  </button>
                </div>
              </div>
            ))}

            <div className="tab tabAdd" role="tab" aria-selected="false" data-no-drag="true" onClick={addNewTab}>
              <div className="tabInner">
                <Tooltip label="新标签页" placement="bottom">
                  <span className="tabAddIcon" aria-hidden="true">
                    <svg
                      xmlns="http://www.w3.org/2000/svg"
                      fill="none"
                      viewBox="0 0 24 24"
                      strokeWidth="1.5"
                      stroke="currentColor"
                      className="tabAddSvg"
                    >
                      <path strokeLinecap="round" strokeLinejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
                    </svg>
                  </span>
                </Tooltip>
              </div>
            </div>
          </div>
        </div>

        {/* 最大化/最小化/关闭按钮 */}
        <Popover
          open={tabContextOpen}
          anchorEl={tabContextAnchorRef.current}
          placement="bottom-start"
          onClose={closeTabContextMenu}
          className="tabContextMenu"
        >
          <div className="menu">
            <button
              type="button"
              className="menuItem"
              onClick={() => {
                if (tabContextPath) closeTab(tabContextPath);
                closeTabContextMenu();
              }}
            >
              <span className="menuIcon" aria-hidden="true">
                <IconClose size={16} />
              </span>
              <span className="menuLabel">关闭</span>
            </button>
            <button
              type="button"
              className="menuItem"
              onClick={() => {
                if (tabContextPath) closeOtherTabs(tabContextPath);
                closeTabContextMenu();
              }}
            >
              <span className="menuIcon" aria-hidden="true">
                <IconClose size={16} />
              </span>
              <span className="menuLabel">关闭其他标签页</span>
            </button>
            <button
              type="button"
              className="menuItem"
              onClick={() => {
                if (tabContextPath) closeRightTabs(tabContextPath);
                closeTabContextMenu();
              }}
            >
              <span className="menuIcon" aria-hidden="true">
                <IconClose size={16} />
              </span>
              <span className="menuLabel">关闭右侧标签页</span>
            </button>
            <button
              type="button"
              className="menuItem"
              onClick={() => {
                closeAllTabs();
                closeTabContextMenu();
              }}
            >
              <span className="menuIcon" aria-hidden="true">
                <IconClose size={16} />
              </span>
              <span className="menuLabel">全部关闭</span>
            </button>

            <div className="menuSeparator" role="separator" />

            <button
              type="button"
              className="menuItem"
              onClick={() => {
                if (tabContextPath) toggleTabLock(tabContextPath);
                closeTabContextMenu();
              }}
              disabled={!tabContextPath}
            >
              <span className="menuIcon" aria-hidden="true">
                <IconLucidePin size={16} />
              </span>
              <span className="menuLabel">{tabContextTab?.locked ? "解锁" : "锁定"}</span>
            </button>

            <button
              type="button"
              className="menuItem"
              onClick={() => {
                if (tabContextPath) void moveTabToNewWindow(tabContextPath);
                closeTabContextMenu();
              }}
              disabled={!tabContextPath}
            >
              <span className="menuIcon" aria-hidden="true">
                <IconLucidePictureInPicture size={16} />
              </span>
              <span className="menuLabel">移动到新窗口</span>
            </button>
          </div>
        </Popover>

        <div className="menubarWindowControls">
          <IconButton title="最小化" tooltipPlacement="bottom" onClick={() => void appWindow.minimize()} className="winBtn">
            <IconMinimize size={16} />
          </IconButton>
          <IconButton title="最大化" tooltipPlacement="bottom" onClick={() => void appWindow.toggleMaximize()} className="winBtn">
            <IconMaximize size={16} />
          </IconButton>
          <IconButton title="关闭" tooltipPlacement="bottom" onClick={() => void appWindow.close()} className="winBtn winClose">
            <IconClose size={16} />
          </IconButton>
        </div>

      </div>

      <div className={`workbench${sidebarOpen ? "" : " sidebarClosed"}`}>
        {/* 功能区（左边按钮边框区） */}
        <div
          className={`activitybar functionArea noDrag${functionAreaDraggingId ? " abDragging" : ""}`}
          role="navigation"
          aria-label="功能区"
          ref={functionAreaRef}
        >
          <div
            className={`abInsertIndicator${functionAreaInsertIndicator ? " visible" : ""}`}
            aria-hidden="true"
            style={
              functionAreaInsertIndicator ? ({ top: functionAreaInsertIndicator.top } as React.CSSProperties) : undefined
            }
          />
          {orderedFunctionAreaItems.map((it) => {
            const isDragging = functionAreaDraggingId === it.id;
            return (
              <IconButton
                key={it.id}
                title={it.title}
                tooltipPlacement="right"
                active={it.kind === "activity" ? it.active : false}
                disabled={it.disabled}
                className={`abBtn abDraggable${isDragging ? " dragging" : ""}`}
                buttonRef={(node) => {
                  const m = functionAreaBtnByIdRef.current;
                  if (node) m.set(it.id, node);
                  else m.delete(it.id);
                }}
                onClick={(ev) => {
                  if (suppressClickIdRef.current === it.id) {
                    suppressClickIdRef.current = null;
                    if (suppressClickTimerRef.current) window.clearTimeout(suppressClickTimerRef.current);
                    suppressClickTimerRef.current = null;
                    try {
                      ev.preventDefault();
                      ev.stopPropagation();
                    } catch {
                      // ignore
                    }
                    return;
                  }

                  if (it.kind === "activity") openActivity(it.activityId);
                  else runPluginAction(it.pluginId, it.actionId);
                }}
                onPointerDown={(ev) => onFunctionAreaItemPointerDown(it.id, ev)}
                onPointerMove={onFunctionAreaItemPointerMove}
                onPointerUp={onFunctionAreaItemPointerUp}
                onPointerCancel={onFunctionAreaItemPointerCancel}
              >
                {it.icon}
              </IconButton>
            );
          })}

          <div className="abSpacer" aria-hidden="true" />

          <IconButton
            title="账号管理"
            tooltipPlacement="right"
            buttonRef={accountMenuAnchorRef}
            onClick={() => setAccountMenuOpen((v) => !v)}
            className="abBtn"
          >
            <IconLucideCircleUserRound size={20} />
          </IconButton>
          <IconButton
            title="设置"
            tooltipPlacement="right"
            onClick={() => {
              setAccountMenuOpen(false);
              setSettingsInitialSection("pro");
              setSettingsOpen(true);
            }}
            className="abBtn"
          >
            <IconLucideSettings size={20} />
          </IconButton>
        </div>

        <Popover
          open={accountMenuOpen}
          anchorEl={accountMenuAnchorRef.current}
          placement="right-end"
          onClose={() => setAccountMenuOpen(false)}
          className="tabContextMenu accountMenu"
        >
          <div className="menu">
            <button
              type="button"
              className="menuItem"
              onClick={() => {
                setAccountMenuOpen(false);
                if (!officialAuth?.token) {
                  openAuthModal("login");
                  return;
                }
                setSettingsInitialSection("pro");
                setSettingsOpen(true);
              }}
            >
              <span className="menuIcon" aria-hidden="true">
                <IconLucideCircleUserRound size={16} />
              </span>
              <span className="menuLabel">账号信息</span>
            </button>

            <button
              type="button"
              className="menuItem"
              onClick={() => {
                setAccountMenuOpen(false);
                setSettingsInitialSection("sync");
                setSettingsOpen(true);
              }}
            >
              <span className="menuIcon" aria-hidden="true">
                <IconRefresh size={16} />
              </span>
              <span className="menuLabel">同步配置</span>
            </button>

            <div className="menuSeparator" role="separator" />

            <button
              type="button"
              className="menuItem"
              onClick={() => {
                setAccountMenuOpen(false);
                void doLogout();
              }}
            >
              <span className="menuIcon" aria-hidden="true">
                <IconClose size={16} />
              </span>
              <span className="menuLabel">注销</span>
            </button>
          </div>
        </Popover>

        {sidebarOpen ? (
          /* 主侧栏（点击打开的侧栏） */
          <div className="sidebar mainSidebar">
            <div className={`sidebarHeader${activity === "space" ? " spaceHeader" : ""}`}>
              <div className="sidebarHeaderLeft">
                {false ? (
                  <div className="vaultPicker" ref={vaultPickerRef}>
                    <Tooltip label={vaultRoot ? formatPathForDisplay(vaultRoot!) : ""} placement="right">
                      <button
                        type="button"
                        className="vaultPickerBtn"
                        data-no-drag="true"
                        onClick={() => {
                          if (!vaultRoot) {
                            void openFolder();
                            return;
                          }
                          setVaultPickerOpen((v) => !v);
                        }}
                        aria-haspopup="dialog"
                        aria-expanded={vaultPickerOpen}
                      >
                        <span className="vaultPickerIcon" aria-hidden="true">
                          <IconChevronsUpDown size={18} />
                        </span>
                        <span className="vaultPickerLabel">{rootLabel}</span>
                      </button>
                    </Tooltip>

                    {vaultPickerOpen ? (
                      <div className="vaultPickerMenu" role="dialog" aria-label="选择库">
                        <div className="vaultPickerList" role="list">
                          {vaultOptions.map((p) => {
                            const isActive = p === vaultRoot;
                            const name = basename(p);
                            return (
                              <Tooltip key={p} label={formatPathForDisplay(p)} placement="right">
                                <button
                                  type="button"
                                  className={`vaultPickerItem${isActive ? " active" : ""}`}
                                  onClick={() => {
                                    if (isActive) return;
                                    void openRecent(p);
                                  }}
                                >
                                  <span className="vaultPickerItemText">
                                    <span className="vaultPickerItemName">{name}</span>
                                  </span>
                                  <span className="vaultPickerCheck" aria-hidden="true">
                                    {isActive ? "✓" : ""}
                                  </span>
                                </button>
                              </Tooltip>
                            );
                          })}
                        </div>
                        <div className="vaultPickerFooter">
                          <button
                            type="button"
                            className="vaultPickerAdd"
                            onClick={() => {
                              setVaultPickerOpen(false);
                              void openFolder();
                            }}
                          >
                            <span className="vaultPickerAddIcon" aria-hidden="true">
                              <IconFolderPlusLucide size={18} />
                            </span>
                            添加新库
                          </button>
                        </div>
                      </div>
                    ) : null}
                  </div>
                ) : (
                  <div className="sidebarTitle">
                    {sidebarTitle}
                  </div>
                )}
              </div>
              <div className="sidebarActions">
                {activity === "space" ? (
                  <>
                    <IconButton title="新建画布" onClick={() => void newCanvas()} disabled={!vaultRoot} className="toolBtn">
                      <IconLucideBrush size={16} />
                    </IconButton>
                    <IconButton title="新建文件" onClick={newFile} disabled={!vaultRoot} className="toolBtn">
                      <IconPlus size={16} />
                    </IconButton>
                    <IconButton title="新建文件夹" onClick={newFolder} disabled={!vaultRoot} className="toolBtn">
                      <IconFolderPlus size={16} />
                    </IconButton>
                    <IconButton
                      title="刷新"
                      onClick={() => void reloadDir(selectedDir)}
                      disabled={!vaultRoot}
                      className="toolBtn"
                    >
                      <IconRefresh size={16} />
                    </IconButton>
                  </>
                ) : null}
              </div>
            </div>

            {activity === "space" ? (
              vaultRoot ? (
                <>
                  <FileTree
                    nodes={flattenedNodes}
                    activePath={activePath}
                    onToggleDir={toggleDir}
                    onOpenFile={openFile}
                  />
                </>
              ) : (
                <div className="emptyState">请选择一个库文件夹开始。</div>
              )
            ) : activity === "workshop" ? (
              <WorkshopSidebar
                vaultRoot={vaultRoot}
                baseUrl={workshopBaseUrl}
                search={workshopSearch}
                onSearchChange={setWorkshopSearch}
                official={workshopOfficial}
                officialLoading={workshopOfficialLoading}
                officialError={workshopOfficialError}
                installed={installedPlugins}
                installedLoading={installedPluginsLoading}
                selectedId={workshopSelectedId}
                onSelect={(id) => setWorkshopSelectedId(id)}
                onRefresh={reloadWorkshop}
                onEditBaseUrl={editWorkshopBaseUrl}
              />
            ) : pluginSidebarViewsByActivityId.has(activity) ? (
              pluginSidebarState && pluginSidebarState.activityId === activity ? (
                pluginSidebarState.loading ? (
                  <div className="emptyState">正在加载…</div>
                ) : pluginSidebarState.error ? (
                  <div className="emptyState error">{pluginSidebarState.error}</div>
                ) : (
                  <iframe
                    className="pluginSidebarFrame"
                    title={pluginSidebarState.title || "Plugin"}
                    sandbox="allow-scripts"
                    ref={pluginSidebarFrameRef}
                    srcDoc={pluginSidebarSrcDoc}
                  />
                )
              ) : (
                <div className="emptyState">正在加载…</div>
              )
            ) : (
              <div className="emptyState">敬请期待。</div>
            )}

            {activity === "space" ? (
              <div className="sidebarFooter">
                <div className="vaultPicker" ref={vaultPickerRef}>
                  <button
                    type="button"
                    className="vaultPickerBtn"
                    data-no-drag="true"
                    onClick={() => {
                      if (!vaultRoot) {
                        void openFolder();
                        return;
                      }
                      setVaultPickerOpen((v) => !v);
                    }}
                    aria-haspopup="dialog"
                    aria-expanded={vaultPickerOpen}
                  >
                    <span className="vaultPickerIcon" aria-hidden="true">
                      <IconChevronsUpDown size={18} />
                    </span>
                    <span className="vaultPickerLabel">{rootLabel}</span>
                  </button>

                  {vaultPickerOpen ? (
                    <div className="vaultPickerMenu" role="dialog" aria-label="选择库">
                      <div className="vaultPickerList" role="list">
                        {vaultOptions.map((p) => {
                          const isActive = p === vaultRoot;
                          const name = basename(p);
                          return (
                            <Tooltip
                              key={p}
                              label=""
                              placement="right"
                              boundarySelector=".sidebar.mainSidebar"
                              gap={8}
                            >
                              <button
                                type="button"
                                className={`vaultPickerItem${isActive ? " active" : ""}`}
                                onClick={() => {
                                  if (isActive) return;
                                  void openRecent(p);
                                }}
                              >
                                <span className="vaultPickerItemText">
                                  <span className="vaultPickerItemName">{name}</span>
                                </span>
                                <span className="vaultPickerCheck" aria-hidden="true">
                                  {isActive ? "✓" : ""}
                                </span>
                              </button>
                            </Tooltip>
                          );
                        })}
                      </div>
                      <div className="vaultPickerFooter">
                        <button
                          type="button"
                          className="vaultPickerAdd"
                          onClick={() => {
                            setVaultPickerOpen(false);
                            void openFolder();
                          }}
                        >
                          <span className="vaultPickerAddIcon" aria-hidden="true">
                            <IconFolderPlusLucide size={18} />
                          </span>
                          添加新库
                        </button>
                      </div>
                    </div>
                  ) : null}
                </div>

                <Tooltip
                  label={cloudSyncEnabled ? "云同步：已开启" : "云同步：未开启"}
                  placement="right"
                  boundarySelector=".sidebar.mainSidebar"
                  gap={8}
                >
                  <button
                    type="button"
                    className={`iconBtn sidebarSyncBtn${cloudSyncEnabled ? "" : " off"}`}
                    data-no-drag="true"
                    onClick={() => {
                      setSettingsInitialSection("sync");
                      setSettingsOpen(true);
                    }}
                    aria-label={cloudSyncEnabled ? "云同步已开启" : "云同步未开启"}
                  >
                    {cloudSyncEnabled ? <IconLucideRefreshCw size={20} /> : <IconLucideRefreshCwOff size={20} />}
                  </button>
                </Tooltip>
              </div>
            ) : null}

            <div
              className="sidebarResizer"
              role="separator"
              aria-orientation="vertical"
              aria-label="Resize sidebar"
              data-no-drag="true"
              onPointerDown={onSidebarResizerPointerDown}
              onPointerMove={onSidebarResizerPointerMove}
              onPointerUp={onSidebarResizerPointerUp}
              onPointerCancel={onSidebarResizerPointerCancel}
              onPointerEnter={() => setSidebarResizeHover(true)}
              onPointerLeave={() => setSidebarResizeHover(false)}
            />
          </div>
        ) : null}

        <div className="editorArea">
          {activity === "workshop" ? (
            <WorkshopEditor
              vaultRoot={vaultRoot}
              baseUrl={workshopBaseUrl}
              selectedId={workshopSelectedId}
              official={workshopOfficial}
              installed={installedPlugins}
              onInstalledReload={reloadWorkshop}
            />
          ) : activeTab ? (
            activeTab.kind === "newtab" ? (
              <div className="newTabPage">
                <div className="newTabActions">
                  <button type="button" className="newTabTextBtn" onClick={() => void createUntitledInRoot()}>
                    创建新文件（Ctrl+N)
                  </button>
                  <button type="button" className="newTabTextBtn" onClick={() => void openFilePicker("all")}>
                    打开文件(Ctrl + O)
                  </button>
                  <button type="button" className="newTabTextBtn" onClick={() => void openFilePicker("recent")}>
                    查看近期文件(Ctrl+O)
                  </button>
                  <button
                    type="button"
                    className="newTabTextBtn"
                    onClick={() => {
                      if (!activePath) return;
                      closeTab(activePath);
                    }}
                  >
                    关闭标签页
                  </button>
                </div>
              </div>
            ) : activeTab.kind === "plugin" ? (
              <iframe
                className="pluginTabFrame"
                title={activeTab.name || "Plugin"}
                sandbox="allow-scripts"
                ref={pluginTabFrameRef}
                srcDoc={buildPluginSidebarSrcDoc({
                  title: activeTab.name || "Plugin",
                  html: activeTab.html || "",
                  pluginId: activeTab.pluginId,
                  viewActionId: activeTab.actionId,
                  ...pluginSidebarTheme,
                })}
              />
            ) : activeTab.kind === "text" ? (
              <Suspense fallback={<div className="emptyState">加载编辑器…</div>}>
                <LazyZhixuMarkdownEditor
                  path={activeTab.path}
                  value={activeTab.content}
                  selection={activeTab.selection}
                  placeholder={editorPlaceholder}
                  mode={editorMode}
                  displaySettings={editorDisplaySettings}
                  onHeaderAction={(info) => {
                    if (!activeTab) return;
                    if (info.action === "revealFolder") {
                      const folder = typeof info.detail.path === "string" ? info.detail.path : "";
                      void revealFolderInSidebar(folder);
                      return;
                    }
                    if (info.action === "renameFile") {
                      const nextName = typeof info.detail.name === "string" ? info.detail.name : "";
                      const fileName = basename(nextName);
                      if (!fileName) return false;
                      const nextPath = join(dirname(activeTab.path), fileName);
                      return renameTabPath(activeTab.path, nextPath);
                    }
                  }}
                  onKeyDownCapture={onAppKeyDown}
                  onChange={(next) => {
                    trackEditorInputToFrame({ path: activeTab.path, mode: editorMode });
                    latestTextByPathRef.current.set(activeTab.path, next);
                    setTabs((prev) =>
                      prev.map((t) => {
                        if (t.path !== activeTab.path) return t;
                        if (t.kind !== "text") return t;
                        if (t.content === next) return t;
                        return { ...t, content: next, dirty: next !== t.savedContent };
                      }),
                    );
                  }}
                  onSelectionChange={(nextSel) => {
                    setTabs((prev) =>
                      prev.map((t) => {
                        if (t.path !== activeTab.path) return t;
                        return { ...t, selection: nextSel };
                      }),
                    );
                  }}
                />
              </Suspense>
            ) : activeTab.kind === "drawing" ? (
              <Suspense fallback={<div className="emptyState">加载画布编辑器…</div>}>
                <LazyZhixuDrawEditor
                  key={activeTab.path}
                  path={activeTab.path}
                  doc={activeTab.doc}
                  savedDoc={activeTab.savedDoc}
                  dirty={activeTab.dirty}
                  viewMode={activeTab.viewMode}
                  onBack={() => closeTab(activeTab.path)}
                  onSave={() => void saveActive()}
                  onDeleteFile={() => void deleteActive()}
                  onOpenFile={(path) => void openFile(path)}
                  onUpdate={(patch) => {
                    setTabs((prev) =>
                      prev.map((t) => (t.path === activeTab.path && t.kind === "drawing" ? { ...t, ...patch } : t)),
                    );
                  }}
                />
              </Suspense>
            ) : (
              <Suspense fallback={<div className="emptyState">加载预览…</div>}>
                <LazyCodeMirrorEditor
                  value={activeTab.content}
                  selection={activeTab.selection}
                  placeholder={editorPlaceholder}
                  readOnly={true}
                  onChange={() => {}}
                  onSelectionChange={(nextSel) => {
                    setTabs((prev) =>
                      prev.map((t) => {
                        if (t.path !== activeTab.path) return t;
                        return { ...t, selection: nextSel };
                      }),
                    );
                  }}
                />
              </Suspense>
            )
          ) : (
            <div className="emptyState">
              从主侧栏打开一个 Markdown 或绘图（.zhixu）文件。使用 <span className="kbd">Ctrl+S</span> 保存，<span className="kbd">Ctrl+E</span>{" "}
              切换源码/实时预览，<span className="kbd">Ctrl+K</span> 查看快捷键。
            </div>
          )}
        </div>
      </div>

      {/* 状态栏：重命名/删除/保存/文件名称等 */}
      <div className="statusbar statusBar noDrag">
        <div className="statusLeft">
          {activeTab ? (activeTab.kind === "plugin" ? `插件：${activeTab.name}` : activeTab.path) : "未打开文件"}
        </div>
        <div className="statusRight">
          {activeTab?.kind === "text" ? <div className="statusPill">{editorMode === "live" ? "实时预览" : "源码模式"}</div> : null}
          <IconButton title="保存" tooltipPlacement="top" onClick={() => void saveActive()} disabled={!activeTab?.dirty}>
            <IconSave size={16} />
          </IconButton>
          <IconButton
            title="重命名"
            tooltipPlacement="top"
            onClick={() => void renameActive()}
            disabled={!activeTab || activeTab.kind === "newtab" || activeTab.kind === "plugin"}
          >
            <IconRename size={16} />
          </IconButton>
          <IconButton
            title="删除"
            tooltipPlacement="top"
            onClick={() => void deleteActive()}
            disabled={!activePath || activeTab?.kind === "newtab" || activeTab?.kind === "plugin"}
            className="danger"
          >
            <IconTrash size={16} />
          </IconButton>
          <div className="statusPill">{activeTab?.dirty ? "未保存" : "已保存"}</div>
        </div>
      </div>

      {settingsOpen ? (
        <div
          className="modalBackdrop noDrag settingsBackdrop"
          data-no-drag="true"
          onMouseDown={(e) => {
            if (e.target === e.currentTarget) setSettingsOpen(false);
          }}
          role="dialog"
          aria-modal="true"
          aria-label="设置"
        >
          <div className="modalPanel settingsModal" data-no-drag="true">
            <div className="modalHeader">
              <div className="modalTitle">设置</div>
              <button className="iconBtn" type="button" data-no-drag="true" aria-label="关闭" onClick={() => setSettingsOpen(false)}>
                <IconClose size={16} />
              </button>
            </div>
            <div className="settingsModalContent">
              <SettingsModal
                initialSection={settingsInitialSection}
                cloudSyncEnabled={cloudSyncEnabled}
                onCloudSyncEnabledChange={handleCloudSyncEnabledChange}
                vaultRoot={vaultRoot}
                editorDisplaySettings={editorDisplaySettings}
                onEditorDisplaySettingsChange={setEditorDisplaySettings}
                officialBaseUrl={officialSyncBaseUrl}
                onOfficialBaseUrlChange={setOfficialSyncBaseUrl}
                officialAuth={officialAuth}
                onOfficialAuthChange={(next) => {
                  setOfficialAuth(next);
                  if (!next?.token) setCloudSyncEnabled(false);
                }}
                onOpenAuth={openAuthModal}
              />
            </div>
          </div>
        </div>
      ) : null}

      {shortcutsOpen ? (
        <div
          className="modalBackdrop noDrag"
          data-no-drag="true"
          onMouseDown={(e) => {
            if (e.target === e.currentTarget) setShortcutsOpen(false);
          }}
          role="dialog"
          aria-modal="true"
          aria-label="快捷键"
        >
          <div className="modalPanel shortcutModal" data-no-drag="true">
            <div className="modalHeader">
              <div className="modalTitle">快捷键</div>
              <button className="iconBtn" type="button" data-no-drag="true" aria-label="关闭" onClick={() => setShortcutsOpen(false)}>
                <IconClose size={16} />
              </button>
            </div>
            <div className="modalBody">
              <div className="shortcutGrid">
                <span className="kbd">Ctrl+S</span>
                <div>保存当前文件</div>
                <span className="kbd">Ctrl+E</span>
                <div>切换源码模式 / 实时预览模式</div>
                <span className="kbd">Ctrl+K</span>
                <div>打开/关闭快捷键列表</div>
                <span className="kbd">Ctrl+Z</span>
                <div>撤销</div>
                <div className="shortcutKeyGroup">
                  <span className="kbd">Ctrl+Y</span>
                  <span className="kbd">Ctrl+Shift+Z</span>
                </div>
                <div>重做</div>
                <span className="kbd">Ctrl+F</span>
                <div>查找</div>
                <span className="kbd">Ctrl+G</span>
                <div>查找下一个</div>
                <span className="kbd">Ctrl+Shift+G</span>
                <div>查找上一个</div>
                <span className="kbd">Esc</span>
                <div>关闭弹窗/搜索面板</div>
              </div>
            </div>
          </div>
        </div>
      ) : null}

      {pluginDevOpen ? <PluginDeveloperWindow onClose={() => setPluginDevOpen(false)} /> : null}

      {authModalOpen ? (
        <AuthModal
          mode={authModalMode}
          baseUrl={officialSyncBaseUrl}
          onBaseUrlChange={setOfficialSyncBaseUrl}
          onClose={closeAuthModal}
          onAuth={handleAuth}
        />
      ) : null}

      {filePickerOpen ? (
        <div
          className="modalBackdrop noDrag"
          data-no-drag="true"
          onMouseDown={(e) => {
            if (e.target === e.currentTarget) setFilePickerOpen(false);
          }}
          role="dialog"
          aria-modal="true"
          aria-label="文件搜索"
          onKeyDown={(e) => {
            if (e.key === "Escape") {
              e.preventDefault();
              e.stopPropagation();
              setFilePickerOpen(false);
              return;
            }
            if (e.key === "ArrowDown") {
              e.preventDefault();
              setFilePickerActiveIndex((i) => Math.min(i + 1, Math.max(0, filePickerVisibleItems.length - 1)));
              return;
            }
            if (e.key === "ArrowUp") {
              e.preventDefault();
              setFilePickerActiveIndex((i) => Math.max(0, i - 1));
              return;
            }
            if (e.key === "Enter") {
              e.preventDefault();
              const chosen = filePickerVisibleItems[filePickerActiveIndex];
              if (!chosen) return;
              setFilePickerOpen(false);
              void openFile(chosen);
              return;
            }
          }}
        >
          <div className="modalPanel filePickerModal" data-no-drag="true">
            <div className="filePickerHeader">
              <input
                ref={filePickerInputRef}
                className="filePickerInput"
                placeholder="输入以切换或创建文件...."
                value={filePickerQuery}
                onChange={(e) => {
                  setFilePickerQuery(e.target.value);
                  setFilePickerActiveIndex(0);
                }}
              />
            </div>

            <div className="filePickerBody">
              {filePickerLoading ? <div className="filePickerHint">正在加载…</div> : null}
              {!filePickerLoading && filePickerVisibleItems.length === 0 ? <div className="filePickerHint">没有匹配结果</div> : null}
              <div className="filePickerList" role="list">
                {filePickerVisibleItems.map((p, idx) => {
                  const name = stripExtension(basename(p));
                  const active = idx === filePickerActiveIndex;
                  return (
                    <button
                      key={p}
                      type="button"
                      className={`filePickerItem${active ? " active" : ""}`}
                      onMouseEnter={() => setFilePickerActiveIndex(idx)}
                      onClick={() => {
                        setFilePickerOpen(false);
                        void openFile(p);
                      }}
                    >
                      <div className="filePickerName">{name}</div>
                    </button>
                  );
                })}
              </div>
            </div>

            <div className="filePickerFooter">
              <span className="filePickerFooterKey">↑↓</span> 导航
              <span className="filePickerFooterKey">↵</span> 打开
              <span className="filePickerFooterKey">Esc</span> 退出
            </div>
          </div>
        </div>
      ) : null}
    </div>
  );
}
