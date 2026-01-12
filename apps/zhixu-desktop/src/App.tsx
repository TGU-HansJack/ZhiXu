import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { cursorPosition, getCurrentWindow } from "@tauri-apps/api/window";
import { emitTo } from "@tauri-apps/api/event";
import { WebviewWindow } from "@tauri-apps/api/webviewWindow";
import { message } from "@tauri-apps/plugin-dialog";
import { CodeMirrorEditor, type CodeMirrorSelection } from "./components/CodeMirrorEditor";
import { ZhixuDrawEditor } from "./components/ZhixuDrawEditor";
import { ZhixuMarkdownEditor, type MarkdownEditorMode } from "./components/ZhixuMarkdownEditor";
import { FileTree, type TreeNode } from "./components/FileTree";
import { Popover } from "./components/Popover";
import { Tooltip, type TooltipPlacement } from "./components/Tooltip";
import {
  IconCalendar,
  IconChevronsUpDown,
  IconClose,
  IconFolderPlus,
  IconFolderPlusLucide,
  IconLucideBrush,
  IconLucidePictureInPicture,
  IconLucidePin,
  IconMaximize,
  IconMinimize,
  IconPlus,
  IconQuadrant,
  IconRefresh,
  IconRename,
  IconSave,
  IconSearch,
  IconSidebarClose,
  IconSidebarOpen,
  IconSpace,
  IconTasks,
  IconTrash,
  IconWorkshop,
} from "./components/icons";
import { basename, dirname, join } from "./lib/path";
import { getFileTypeLabel, isTextFile, isZhixuDrawFile, stripExtension } from "./lib/fileType";
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
import type { DrawDocument, DrawViewMode } from "./draw/types";

type Activity = "space" | "tasks" | "calendar" | "quadrant" | "workshop" | "search";

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

type Tab = NewTab | TextTab | BinaryTab | DrawingTab;

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
  onClick,
  children,
}: React.PropsWithChildren<{
  title: string;
  tooltipPlacement?: TooltipPlacement;
  active?: boolean;
  disabled?: boolean;
  className?: string;
  onClick?: () => void;
}>) {
  return (
    <Tooltip label={title} placement={tooltipPlacement}>
      <button
        className={`iconBtn${active ? " active" : ""}${className ? ` ${className}` : ""}`}
        aria-label={title}
        data-no-drag="true"
        onClick={onClick}
        disabled={disabled}
        type="button"
      >
        {children}
      </button>
    </Tooltip>
  );
}

export function App() {
  const appWindow = useMemo(() => getCurrentWindow(), []);
  const urlParams = useMemo(() => new URLSearchParams(window.location.search), []);
  const isDetached = useMemo(() => urlParams.get("view") === "tab", [urlParams]);
  const initialVaultParam = useMemo(() => urlParams.get("vaultRoot"), [urlParams]);
  const transferId = useMemo(() => urlParams.get("transferId"), [urlParams]);
  const draggingTabPathRef = useRef<string | null>(null);
  const draggingTransferIdRef = useRef<string | null>(null);
  const transferCleanupByPathRef = useRef<Map<string, string>>(new Map());
  const dragBroadcastTimerRef = useRef<number | null>(null);
  const dragBroadcastTargetLabelRef = useRef<string | null>(null);
  const tabStripRef = useRef<HTMLDivElement | null>(null);
  const tabElByPathRef = useRef<Map<string, HTMLDivElement>>(new Map());
  const dragPreviewElRef = useRef<HTMLDivElement | null>(null);

  const [vaultRoot, setVaultRootState] = useState<string | null>(null);
  const [persisted, setPersisted] = useState<PersistedState>({ lastVault: null, recentVaults: [] });
  const [vaultPickerOpen, setVaultPickerOpen] = useState(false);
  const vaultPickerRef = useRef<HTMLDivElement | null>(null);

  const [dirCache, setDirCache] = useState<Record<string, VaultEntry[]>>({});
  const [expanded, setExpanded] = useState<Record<string, boolean>>({});
  const [loadingDir, setLoadingDir] = useState<Record<string, boolean>>({});
  const [selectedDir, setSelectedDir] = useState<string>("");

  const [activity, setActivity] = useState<Activity>("space");
  const [sidebarOpen, setSidebarOpen] = useState<boolean>(() => !isDetached);

  const [editorMode, setEditorMode] = useState<MarkdownEditorMode>("live");
  const [shortcutsOpen, setShortcutsOpen] = useState(false);

  const newTabIdRef = useRef(isDetached && transferId ? 1 : 2);
  const [tabs, setTabs] = useState<Tab[]>(() => (isDetached && transferId ? [] : [makeNewTab(1)]));
  const [activePath, setActivePath] = useState<string | null>(() => (isDetached && transferId ? null : "__newtab__1"));
  const tabsRef = useRef<Tab[]>(tabs);
  const latestTextByPathRef = useRef<Map<string, string>>(new Map());
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
  const tabCount = Math.max(1, tabs.length);
  const tabsDensity = tabCount >= 14 ? "separators" : tabCount >= 8 ? "dense" : "normal";

  const tabContextAnchorRef = useRef<HTMLSpanElement | null>(null);
  const [tabContextOpen, setTabContextOpen] = useState(false);
  const [tabContextPath, setTabContextPath] = useState<string | null>(null);
  const [tabContextPoint, setTabContextPoint] = useState<{ x: number; y: number }>({ x: 0, y: 0 });
  const tabContextTab = useMemo(() => (tabContextPath ? tabs.find((t) => t.path === tabContextPath) ?? null : null), [tabContextPath, tabs]);

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
    const entries = sortEntries(await listDir(""));
    setDirCache({ "": entries });
  }, []);

  useEffect(() => {
    if (!isDetached) return;
    let cancelled = false;
    void (async () => {
      try {
        if (initialVaultParam && !sameVaultRoot(vaultRoot, initialVaultParam)) {
          const resolved = await setVaultRoot(initialVaultParam);
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
        const resolved = await setVaultRoot(root);
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
      const entries = sortEntries(await listDir(path));
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
        const entries = sortEntries(await listDir(path));
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

  const openFile = useCallback(async (path: string) => {
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
        const content = await readTextFile(path);
        setTabs((prev) =>
          prev.map((t) =>
            t.path === path && t.kind === "text" ? { ...t, content, savedContent: content, dirty: false } : t,
          ),
        );
      } catch (e) {
        console.error(e);
      }
      return;
    }

    if (drawFile) {
      try {
        const doc = await readDrawDocument(path);
        setTabs((prev) =>
          prev.map((t) => (t.path === path && t.kind === "drawing" ? { ...t, doc, savedDoc: doc, dirty: false } : t)),
        );
      } catch (e) {
        console.error(e);
      }
    }
  }, [pushRecentFile]);

  const saveActive = useCallback(async () => {
    if (!activeTab) return;
    if (savingRef.current) return;
    savingRef.current = true;
    try {
      if (activeTab.kind === "text") {
        const latest = latestTextByPathRef.current.get(activeTab.path);
        const content = latest ?? activeTab.content;
        if (content === activeTab.savedContent) return;

        await writeTextFile(activeTab.path, content);
        setTabs((prev) =>
          prev.map((t) =>
            t.path === activeTab.path && t.kind === "text" ? { ...t, savedContent: content, dirty: false } : t,
          ),
        );
      } else if (activeTab.kind === "drawing") {
        if (!activeTab.dirty) return;
        if (!activeTab.doc) return;
        await writeDrawDocument(activeTab.path, activeTab.doc);
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

  const renameActive = useCallback(async () => {
    if (!activeTab) return;
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
  }, [activePath, reloadDir]);

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
        const needsVaultSwitch = payload.vaultRoot && !sameVaultRoot(payload.vaultRoot, vaultRoot);
        if (needsVaultSwitch) {
          const resolved = await setVaultRoot(payload.vaultRoot);
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
        if (shortcutsOpen) {
          ev.preventDefault();
          ev.stopPropagation();
          setShortcutsOpen(false);
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
    [createUntitledInRoot, openFilePicker, saveActive, shortcutsOpen, vaultPickerOpen],
  );

  useEffect(() => {
    window.addEventListener("keydown", onAppKeyDown, true);
    return () => window.removeEventListener("keydown", onAppKeyDown, true);
  }, [onAppKeyDown]);

  useEffect(() => {
    void (async () => {
      try {
        setPersisted(await getPersistedState());
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

  const openActivity = useCallback((next: Activity) => {
    setActivity(next);
    setSidebarOpen(true);
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
      className={`appShell${isDetached ? " detached" : ""}${sidebarOpen ? "" : " sidebarClosed"}`}
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
          {/* 主侧栏顶部按钮：空间、搜索 */}
          {sidebarOpen ? (
            <>
              <IconButton title="空间" tooltipPlacement="bottom" active={activity === "space"} onClick={() => openActivity("space")}>
                <IconSpace />
              </IconButton>
              <IconButton title="搜索" tooltipPlacement="bottom" active={activity === "search"} onClick={() => openActivity("search")}>
                <IconSearch />
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
        <div className="activitybar functionArea noDrag" role="navigation" aria-label="功能区">
          <IconButton
            title="空间"
            tooltipPlacement="right"
            active={activity === "space"}
            onClick={() => openActivity("space")}
            className="abBtn"
          >
            <IconSpace />
          </IconButton>
          <IconButton
            title="任务"
            tooltipPlacement="right"
            active={activity === "tasks"}
            onClick={() => openActivity("tasks")}
            className="abBtn"
          >
            <IconTasks />
          </IconButton>
          <IconButton
            title="日历"
            tooltipPlacement="right"
            active={activity === "calendar"}
            onClick={() => openActivity("calendar")}
            className="abBtn"
          >
            <IconCalendar />
          </IconButton>
          <IconButton
            title="象限"
            tooltipPlacement="right"
            active={activity === "quadrant"}
            onClick={() => openActivity("quadrant")}
            className="abBtn"
          >
            <IconQuadrant />
          </IconButton>
          <IconButton
            title="工坊"
            tooltipPlacement="right"
            active={activity === "workshop"}
            onClick={() => openActivity("workshop")}
            className="abBtn"
          >
            <IconWorkshop />
          </IconButton>
          <IconButton
            title="搜索"
            tooltipPlacement="right"
            active={activity === "search"}
            onClick={() => openActivity("search")}
            className="abBtn"
          >
            <IconSearch />
          </IconButton>
        </div>

        {sidebarOpen ? (
          /* 主侧栏（点击打开的侧栏） */
          <div className="sidebar mainSidebar">
            <div className="sidebarHeader">
              <div className="sidebarHeaderLeft">
                {activity === "space" ? (
                  <div className="vaultPicker" ref={vaultPickerRef}>
                    <Tooltip label={vaultRoot ? formatPathForDisplay(vaultRoot) : ""} placement="right">
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
                    {activity === "tasks"
                      ? "任务"
                      : activity === "calendar"
                        ? "日历"
                        : activity === "quadrant"
                          ? "象限"
                          : activity === "workshop"
                            ? "工坊"
                            : "搜索"}
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
                    rootLabel={rootLabel}
                    nodes={flattenedNodes}
                    activePath={activePath}
                    onToggleDir={toggleDir}
                    onOpenFile={openFile}
                  />
                </>
              ) : (
                <div className="emptyState">请选择一个库文件夹开始。</div>
              )
            ) : (
              <div className="emptyState">敬请期待。</div>
            )}
          </div>
        ) : null}

        <div className="editorArea">
          {activeTab ? (
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
            ) : activeTab.kind === "text" ? (
              <ZhixuMarkdownEditor
                value={activeTab.content}
                selection={activeTab.selection}
                placeholder={editorPlaceholder}
                mode={editorMode}
                onKeyDownCapture={onAppKeyDown}
                onChange={(next) => {
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
            ) : activeTab.kind === "drawing" ? (
              <ZhixuDrawEditor
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
            ) : (
              <CodeMirrorEditor
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
        <div className="statusLeft">{activeTab ? activeTab.path : "未打开文件"}</div>
        <div className="statusRight">
          {activeTab?.kind === "text" ? <div className="statusPill">{editorMode === "live" ? "实时预览" : "源码模式"}</div> : null}
          <IconButton title="保存" tooltipPlacement="top" onClick={() => void saveActive()} disabled={!activeTab?.dirty}>
            <IconSave size={16} />
          </IconButton>
          <IconButton title="重命名" tooltipPlacement="top" onClick={() => void renameActive()} disabled={!activeTab}>
            <IconRename size={16} />
          </IconButton>
          <IconButton
            title="删除"
            tooltipPlacement="top"
            onClick={() => void deleteActive()}
            disabled={!activePath}
            className="danger"
          >
            <IconTrash size={16} />
          </IconButton>
          <div className="statusPill">{activeTab?.dirty ? "未保存" : "已保存"}</div>
        </div>
      </div>

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
