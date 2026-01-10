import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { getCurrentWindow } from "@tauri-apps/api/window";
import { CodeMirrorEditor, type CodeMirrorSelection } from "./components/CodeMirrorEditor";
import { FileTree, type TreeNode } from "./components/FileTree";
import { Tooltip, type TooltipPlacement } from "./components/Tooltip";
import {
  IconCalendar,
  IconClose,
  IconFolderPlus,
  IconMaximize,
  IconMinimize,
  IconPlus,
  IconQuadrant,
  IconRefresh,
  IconRename,
  IconSave,
  IconSearch,
  IconSidebar,
  IconSpace,
  IconTasks,
  IconTrash,
  IconWorkshop,
} from "./components/icons";
import { basename, dirname, join } from "./lib/path";
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

type Tab = {
  path: string;
  name: string;
  content: string;
  dirty: boolean;
  selection: CodeMirrorSelection;
};

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

  const [dirCache, setDirCache] = useState<Record<string, VaultEntry[]>>({});
  const [expanded, setExpanded] = useState<Record<string, boolean>>({});
  const [loadingDir, setLoadingDir] = useState<Record<string, boolean>>({});
  const [selectedDir, setSelectedDir] = useState<string>("");

  const [activity, setActivity] = useState<Activity>("space");
  const [sidebarOpen, setSidebarOpen] = useState<boolean>(true);

  const [tabs, setTabs] = useState<Tab[]>([]);
  const [activePath, setActivePath] = useState<string | null>(null);
  const activeTab = useMemo(() => tabs.find((t) => t.path === activePath) ?? null, [tabs, activePath]);

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
    setTabs((prev) => {
      const existing = prev.find((t) => t.path === path);
      if (existing) return prev;
      return [...prev, { path, name: basename(path), content: "", dirty: false, selection: { anchor: 0, head: 0 } }];
    });
    try {
      const content = await readTextFile(path);
      setTabs((prev) =>
        prev.map((t) => (t.path === path ? { ...t, content, dirty: false, selection: { anchor: 0, head: 0 } } : t)),
      );
    } catch (e) {
      console.error(e);
    }
  }, []);

  const saveActive = useCallback(async () => {
    if (!activeTab || !activeTab.dirty) return;
    if (savingRef.current) return;
    savingRef.current = true;
    try {
      await writeTextFile(activeTab.path, activeTab.content);
      setTabs((prev) => prev.map((t) => (t.path === activeTab.path ? { ...t, dirty: false } : t)));
    } catch (e) {
      console.error(e);
    } finally {
      savingRef.current = false;
    }
  }, [activeTab]);

  const newFile = useCallback(async () => {
    if (!vaultRoot) return;
    const name = window.prompt("New file name (relative to selected folder):", "note.md");
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
    const name = window.prompt("New folder name (relative to selected folder):", "New Folder");
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
    const next = window.prompt("Rename to (vault-relative path):", activeTab.path);
    if (!next || next === activeTab.path) return;
    try {
      await renameEntry(activeTab.path, next);
      const nextName = basename(next);
      setTabs((prev) => prev.map((t) => (t.path === activeTab.path ? { ...t, path: next, name: nextName } : t)));
      setActivePath(next);
      await reloadDir(dirname(activeTab.path));
      await reloadDir(dirname(next));
    } catch (e) {
      console.error(e);
      window.alert(String(e));
    }
  }, [activeTab, reloadDir]);

  const deleteActive = useCallback(async () => {
    if (!activePath) return;
    const ok = window.confirm(`Delete?\n\n${activePath}`);
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
        const ok = window.confirm(`Close tab with unsaved changes?\n\n${tab.path}`);
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

  useEffect(() => {
    const onKeyDown = (ev: KeyboardEvent) => {
      const isSave = (ev.ctrlKey || ev.metaKey) && ev.key.toLowerCase() === "s";
      if (!isSave) return;
      ev.preventDefault();
      void saveActive();
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [saveActive]);

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

  const rootLabel = useMemo(() => (vaultRoot ? basename(vaultRoot) : "No vault"), [vaultRoot]);

  const editorPlaceholder = useMemo(() => {
    if (!vaultRoot) return "Select a vault to start…";
    if (!activeTab) return "Open a Markdown file from the sidebar…";
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
    <div className="appShell">
      <div className="topbar" onMouseDown={startDraggingIfAllowed}>
        <div className="topbarLeft">
          <IconButton
            title={sidebarOpen ? "收起" : "展开"}
            tooltipPlacement="right"
            onClick={() => setSidebarOpen((v) => !v)}
          >
            <IconSidebar />
          </IconButton>
          <IconButton title="空间" tooltipPlacement="bottom" active={activity === "space"} onClick={() => openActivity("space")}>
            <IconSpace />
          </IconButton>
          <IconButton title="搜索" tooltipPlacement="bottom" active={activity === "search"} onClick={() => openActivity("search")}>
            <IconSearch />
          </IconButton>
        </div>

        <div className="topbarCenter">
          <div className="tabs" role="tablist" aria-label="Tabs">
            {tabs.length === 0 ? (
              <div className="tab active" role="tab" aria-selected="true" data-no-drag="true">
                <span className="tabLabel">Welcome</span>
              </div>
            ) : (
              tabs.map((t) => (
                <Tooltip key={t.path} label={t.path} placement="bottom">
                  <div
                    className={`tab${t.path === activePath ? " active" : ""}`}
                    role="tab"
                    aria-selected={t.path === activePath}
                    data-no-drag="true"
                    onClick={() => setActivePath(t.path)}
                  >
                    <span className={`dirty${t.dirty ? " on" : ""}`} aria-hidden="true" />
                    <span className="tabLabel">{t.name}</span>
                    <button
                      type="button"
                      className="tabClose"
                      aria-label="Close tab"
                      data-no-drag="true"
                      onClick={(e) => {
                        e.stopPropagation();
                        closeTab(t.path);
                      }}
                    >
                      <Tooltip label="关闭标签" placement="bottom">
                        <span aria-hidden="true">
                          <IconClose size={14} />
                        </span>
                      </Tooltip>
                    </button>
                  </div>
                </Tooltip>
              ))
            )}
          </div>
        </div>

        <div className="topbarRight">
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
          <div className="activitybar noDrag" role="navigation" aria-label="Activity bar">
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
          <div className="sidebar">
            <div className="sidebarHeader">
              <div className="sidebarTitle">
                {activity === "space"
                  ? "Space"
                  : activity === "tasks"
                    ? "Tasks"
                    : activity === "calendar"
                      ? "Calendar"
                      : activity === "quadrant"
                        ? "Quadrant"
                        : activity === "workshop"
                          ? "Workshop"
                          : "Search"}
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
                  {persisted.recentVaults.length > 0 ? (
                    <div className="sidebarSubHeader">
                      <Tooltip label="最近使用的库" placement="right">
                        <select
                          className="select"
                          value=""
                          data-no-drag="true"
                          onChange={(e) => {
                            const v = e.target.value;
                            if (!v) return;
                            void openRecent(v);
                            e.target.value = "";
                          }}
                        >
                          <option value="">Recent…</option>
                          {persisted.recentVaults.map((p) => (
                            <option key={p} value={p}>
                              {p}
                            </option>
                          ))}
                        </select>
                      </Tooltip>
                    </div>
                  ) : null}
                  <FileTree
                    rootLabel={rootLabel}
                    nodes={flattenedNodes}
                    activePath={activePath}
                    onToggleDir={toggleDir}
                    onOpenFile={openFile}
                  />
                </>
              ) : (
                <div className="emptyState">Select a vault folder to start.</div>
              )
            ) : (
              <div className="emptyState">Coming soon.</div>
            )}
          </div>
        ) : null}

        <div className="editorArea">
          {activeTab ? (
            <CodeMirrorEditor
              value={activeTab.content}
              selection={activeTab.selection}
              placeholder={editorPlaceholder}
              onChange={(next) => {
                setTabs((prev) =>
                  prev.map((t) => {
                    if (t.path !== activeTab.path) return t;
                    return { ...t, content: next, dirty: true };
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
            <div className="emptyState">
              Open a Markdown file from the sidebar. Save with <span className="kbd">Ctrl+S</span>.
            </div>
          )}
        </div>
      </div>

      <div className="statusbar noDrag">
        <div className="statusLeft">{activeTab ? activeTab.path : "No file"}</div>
        <div className="statusRight">
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
          <div className="statusPill">{activeTab?.dirty ? "Unsaved" : "Saved"}</div>
        </div>
      </div>
    </div>
  );
}
