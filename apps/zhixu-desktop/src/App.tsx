import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { getCurrentWindow } from "@tauri-apps/api/window";
import { CodeMirrorEditor, type CodeMirrorSelection } from "./components/CodeMirrorEditor";
import { ZhixuDrawEditor } from "./components/ZhixuDrawEditor";
import { ZhixuMarkdownEditor, type MarkdownEditorMode } from "./components/ZhixuMarkdownEditor";
import { FileTree, type TreeNode } from "./components/FileTree";
import { Tooltip, type TooltipPlacement } from "./components/Tooltip";
import {
  IconCalendar,
  IconChevronsUpDown,
  IconClose,
  IconFolderPlus,
  IconFolderPlusLucide,
  IconLucideBrush,
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
  content: string;
  savedContent: string;
  dirty: false;
  selection: CodeMirrorSelection;
};

type TextTab = {
  path: string;
  name: string;
  kind: "text";
  content: string;
  savedContent: string;
  dirty: boolean;
  selection: CodeMirrorSelection;
};

type BinaryTab = {
  path: string;
  name: string;
  kind: "binary";
  content: string;
  savedContent: string;
  dirty: false;
  selection: CodeMirrorSelection;
};

type DrawingTab = {
  path: string;
  name: string;
  kind: "drawing";
  doc: DrawDocument | null;
  savedDoc: DrawDocument | null;
  dirty: boolean;
  viewMode: DrawViewMode;
  selection: CodeMirrorSelection;
};

type Tab = NewTab | TextTab | BinaryTab | DrawingTab;

function makeNewTab(id: number): Tab {
  const path = `__newtab__${id}`;
  return {
    path,
    name: "新标签页",
    kind: "newtab",
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

  const [vaultRoot, setVaultRootState] = useState<string | null>(null);
  const [persisted, setPersisted] = useState<PersistedState>({ lastVault: null, recentVaults: [] });
  const [vaultPickerOpen, setVaultPickerOpen] = useState(false);
  const vaultPickerRef = useRef<HTMLDivElement | null>(null);

  const [dirCache, setDirCache] = useState<Record<string, VaultEntry[]>>({});
  const [expanded, setExpanded] = useState<Record<string, boolean>>({});
  const [loadingDir, setLoadingDir] = useState<Record<string, boolean>>({});
  const [selectedDir, setSelectedDir] = useState<string>("");

  const [activity, setActivity] = useState<Activity>("space");
  const [sidebarOpen, setSidebarOpen] = useState<boolean>(true);

  const [editorMode, setEditorMode] = useState<MarkdownEditorMode>("live");
  const [shortcutsOpen, setShortcutsOpen] = useState(false);

  const newTabIdRef = useRef(2);
  const [tabs, setTabs] = useState<Tab[]>(() => [makeNewTab(1)]);
  const [activePath, setActivePath] = useState<string | null>(() => "__newtab__1");
  const activeTab = useMemo(() => tabs.find((t) => t.path === activePath) ?? null, [tabs, activePath]);
  const tabCount = Math.max(1, tabs.length);
  const tabsDensity = tabCount >= 14 ? "separators" : tabCount >= 8 ? "dense" : "normal";

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

  const resetForVault = useCallback(async (root: string) => {
    setVaultRootState(root);
    const tab = makeNewTab(newTabIdRef.current++);
    setTabs([tab]);
    setActivePath(tab.path);
    setSelectedDir("");
    setDirCache({});
    setExpanded({});
    setLoadingDir({});
    const entries = sortEntries(await listDir(""));
    setDirCache({ "": entries });
  }, []);

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
          ? { path, name: tabName, kind: "text", content: "", savedContent: "", dirty: false, selection: { anchor: 0, head: 0 } }
          : drawFile
            ? { path, name: tabName, kind: "drawing", doc: null, savedDoc: null, dirty: false, viewMode: "writing", selection: { anchor: 0, head: 0 } }
            : {
                path,
                name: tabName,
                kind: "binary",
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
    if (!activeTab || !activeTab.dirty) return;
    if (savingRef.current) return;
    savingRef.current = true;
    try {
      if (activeTab.kind === "text") {
        await writeTextFile(activeTab.path, activeTab.content);
        setTabs((prev) =>
          prev.map((t) =>
            t.path === activeTab.path && t.kind === "text" ? { ...t, savedContent: activeTab.content, dirty: false } : t,
          ),
        );
      } else if (activeTab.kind === "drawing") {
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
      if (tab?.dirty) {
        const ok = window.confirm(`该标签页有未保存的更改，确定关闭？\n\n${tab.path}`);
        if (!ok) return;
      }
      const remaining = tabs.filter((t) => t.path !== path);
      if (remaining.length === 0) {
        const next = makeNewTab(newTabIdRef.current++);
        setTabs([next]);
        setActivePath(next.path);
        return;
      }
      setTabs(remaining);
      if (activePath === path) setActivePath(remaining[remaining.length - 1]!.path);
    },
    [activePath, tabs],
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
    <div className={`appShell${sidebarOpen ? "" : " sidebarClosed"}`}>
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
            data-density={tabsDensity}
            style={{ ["--tab-count" as any]: tabCount } as React.CSSProperties}
            role="tablist"
            aria-label="标签页"
          >
            {tabs.map((t) => (
              <div
                key={t.path}
                className={`tab${t.path === activePath ? " active" : ""}`}
                role="tab"
                aria-selected={t.path === activePath}
                data-dirty={t.dirty ? "true" : "false"}
                data-no-drag="true"
                onClick={() => setActivePath(t.path)}
              >
                <div className="tabInner">
                  <Tooltip label={t.kind === "newtab" ? "新标签页" : t.path} placement="bottom">
                    <span className="tabLabel">{t.name}</span>
                  </Tooltip>
                  <button
                    type="button"
                    className="tabClose"
                    aria-label="关闭标签"
                    data-no-drag="true"
                    onClick={(e) => {
                      e.stopPropagation();
                      closeTab(t.path);
                    }}
                  >
                    <Tooltip label="关闭标签" placement="bottom">
                      <span className="tabCloseGlyph" aria-hidden="true">
                        <span className="tabCloseDot" />
                        <span className="tabCloseX">
                          <IconClose size={14} />
                        </span>
                      </span>
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
