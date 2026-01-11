import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { getCurrentWindow } from "@tauri-apps/api/window";
import { CodeMirrorEditor, type CodeMirrorSelection } from "./components/CodeMirrorEditor";
import { ZhixuMarkdownEditor, type MarkdownEditorMode } from "./components/ZhixuMarkdownEditor";
import { FileTree, type TreeNode } from "./components/FileTree";
import { Tooltip, type TooltipPlacement } from "./components/Tooltip";
import {
  IconCalendar,
  IconChevronsUpDown,
  IconClose,
  IconFolderPlus,
  IconFolderPlusLucide,
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
import { getFileTypeLabel, isTextFile, stripExtension } from "./lib/fileType";
import {
  createDir,
  createFile,
  deleteEntry,
  getPersistedState,
  listDir,
  readTextFile,
  renameEntry,
  selectVault,
  setVaultRoot,
  writeTextFile,
  type PersistedState,
  type VaultEntry,
} from "./lib/vaultApi";

type Activity = "space" | "tasks" | "calendar" | "quadrant" | "workshop" | "search";

type TabKind = "text" | "binary";

type Tab = {
  path: string;
  name: string;
  kind: TabKind;
  content: string;
  savedContent: string;
  dirty: boolean;
  selection: CodeMirrorSelection;
};

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

  const [tabs, setTabs] = useState<Tab[]>([]);
  const [activePath, setActivePath] = useState<string | null>(null);
  const activeTab = useMemo(() => tabs.find((t) => t.path === activePath) ?? null, [tabs, activePath]);
  const tabCount = Math.max(1, tabs.length);
  const tabsDensity = tabCount >= 14 ? "separators" : tabCount >= 8 ? "dense" : "normal";

  const savingRef = useRef(false);

  const resetForVault = useCallback(async (root: string) => {
    setVaultRootState(root);
    setTabs([]);
    setActivePath(null);
    setSelectedDir("");
    setDirCache({});
    setExpanded({});
    setLoadingDir({});
    const entries = sortEntries(await listDir(""));
    setDirCache({ "": entries });
  }, []);

  const openFolder = useCallback(async () => {
    try {
      const root = await selectVault();
      await resetForVault(root);
      setPersisted(await getPersistedState());
      setVaultPickerOpen(false);
    } catch (e) {
      console.error(e);
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

  const openFile = useCallback(async (path: string) => {
    setActivePath(path);
    setSelectedDir(dirname(path));
    setActivity("space");
    setSidebarOpen(true);

    const fileName = basename(path);
    const tabName = stripExtension(fileName);
    const textFile = isTextFile(fileName);
    const binaryLabel = getFileTypeLabel(fileName) ?? "文件";
    const binaryPlaceholder = `该文件类型暂不支持在应用内打开：${binaryLabel}\n\n路径：${path}`;

    setTabs((prev) => {
      const existing = prev.find((t) => t.path === path);
      if (existing) return prev;
      return [
        ...prev,
        textFile
          ? { path, name: tabName, kind: "text", content: "", savedContent: "", dirty: false, selection: { anchor: 0, head: 0 } }
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

    if (!textFile) return;
    try {
      const content = await readTextFile(path);
      setTabs((prev) =>
        prev.map((t) =>
          t.path === path
            ? { ...t, kind: "text", content, savedContent: content, dirty: false, selection: { anchor: 0, head: 0 } }
            : t,
        ),
      );
    } catch (e) {
      console.error(e);
    }
  }, []);

  const saveActive = useCallback(async () => {
    if (!activeTab || activeTab.kind !== "text" || !activeTab.dirty) return;
    if (savingRef.current) return;
    savingRef.current = true;
    try {
      await writeTextFile(activeTab.path, activeTab.content);
      setTabs((prev) =>
        prev.map((t) =>
          t.path === activeTab.path ? { ...t, savedContent: activeTab.content, dirty: false } : t,
        ),
      );
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
    const nextBinaryLabel = getFileTypeLabel(nextFileName) ?? "文件";
    const nextBinaryPlaceholder = `该文件类型暂不支持在应用内打开：${nextBinaryLabel}\n\n路径：${next}`;

    try {
      await renameEntry(activeTab.path, next);
      setTabs((prev) =>
        prev.map((t) => {
          if (t.path !== activeTab.path) return t;
          if (!nextText) {
            return {
              ...t,
              path: next,
              name: nextName,
              kind: "binary",
              content: nextBinaryPlaceholder,
              savedContent: nextBinaryPlaceholder,
              dirty: false,
              selection: { anchor: 0, head: 0 },
            };
          }
          return { ...t, path: next, name: nextName, kind: "text" };
        }),
      );
      setActivePath(next);
      await reloadDir(dirname(activeTab.path));
      await reloadDir(dirname(next));
      if (nextText) {
        try {
          const content = await readTextFile(next);
          setTabs((prev) =>
            prev.map((t) =>
              t.path === next
                ? { ...t, kind: "text", content, savedContent: content, dirty: false, selection: { anchor: 0, head: 0 } }
                : t,
            ),
          );
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
      setTabs((prev) => prev.filter((t) => t.path !== path));
      setActivePath((prev) => {
        if (prev !== path) return prev;
        const remaining = tabs.filter((t) => t.path !== path);
        return remaining.length ? remaining[remaining.length - 1]!.path : null;
      });
    },
    [tabs],
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
    [saveActive, shortcutsOpen, vaultPickerOpen],
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
    if (!activeTab) return "从主侧栏打开一个 Markdown 或 Zhixu 文件…";
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
            {tabs.length === 0 ? (
              <div className="tab active" role="tab" aria-selected="true" data-no-drag="true">
                <div className="tabInner">
                  <span className="tabLabel">欢迎</span>
                </div>
              </div>
            ) : (
              tabs.map((t) => (
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
                    <Tooltip label={t.path} placement="bottom">
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
              ))
            )}
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
                    <IconButton title="选择库" onClick={openFolder} className="toolBtn">
                      <IconSpace size={16} />
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
            activeTab.kind === "text" ? (
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
              从主侧栏打开一个 Markdown 或 Zhixu 文件。使用 <span className="kbd">Ctrl+S</span> 保存，<span className="kbd">Ctrl+E</span>{" "}
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
    </div>
  );
}
