import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { FileTree, type TreeNode } from "./components/FileTree";
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

type Tab = {
  path: string;
  name: string;
  content: string;
  dirty: boolean;
};

function sortEntries(entries: VaultEntry[]): VaultEntry[] {
  return [...entries].sort((a, b) => {
    if (a.isDir !== b.isDir) return a.isDir ? -1 : 1;
    return a.name.localeCompare(b.name, undefined, { numeric: true, sensitivity: "base" });
  });
}

export function App() {
  const [vaultRoot, setVaultRootState] = useState<string | null>(null);
  const [persisted, setPersisted] = useState<PersistedState>({ lastVault: null, recentVaults: [] });

  const [dirCache, setDirCache] = useState<Record<string, VaultEntry[]>>({});
  const [expanded, setExpanded] = useState<Record<string, boolean>>({});
  const [loadingDir, setLoadingDir] = useState<Record<string, boolean>>({});
  const [selectedDir, setSelectedDir] = useState<string>("");

  const [tabs, setTabs] = useState<Tab[]>([]);
  const [activePath, setActivePath] = useState<string | null>(null);
  const activeTab = useMemo(() => tabs.find((t) => t.path === activePath) ?? null, [tabs, activePath]);

  const [showPreview, setShowPreview] = useState<boolean>(true);
  const savingRef = useRef(false);
  const previewRef = useRef<HTMLIFrameElement | null>(null);

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
    setTabs((prev) => {
      const existing = prev.find((t) => t.path === path);
      if (existing) return prev;
      return [...prev, { path, name: basename(path), content: "", dirty: false }];
    });
    try {
      const content = await readTextFile(path);
      setTabs((prev) => prev.map((t) => (t.path === path ? { ...t, content, dirty: false } : t)));
    } catch (e) {
      console.error(e);
    }
  }, []);

  const updateActiveContent = useCallback(
    (next: string) => {
      setTabs((prev) =>
        prev.map((t) => {
          if (t.path !== activePath) return t;
          return { ...t, content: next, dirty: true };
        }),
      );
    },
    [activePath],
  );

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

  const pushPreview = useCallback(() => {
    if (!showPreview) return;
    const win = previewRef.current?.contentWindow as any;
    if (!win) return;
    try {
      if (typeof win.__setVaultRoot === "function") win.__setVaultRoot(vaultRoot ?? "");
      if (typeof win.__setMarkdown === "function") win.__setMarkdown(activeTab?.content ?? "");
    } catch {
      // ignore iframe timing issues
    }
  }, [showPreview, vaultRoot, activeTab?.content]);

  useEffect(() => {
    pushPreview();
  }, [pushPreview]);

  const rootLabel = useMemo(() => (vaultRoot ? basename(vaultRoot) : "No vault"), [vaultRoot]);

  return (
    <div className="app">
      <div className="titlebar">
        <div className="brand">Zhixu</div>
        <button className="btn primary" onClick={openFolder}>
          Open Folder
        </button>
        {!vaultRoot && persisted.lastVault ? (
          <button className="btn" onClick={() => openRecent(persisted.lastVault!)}>
            Reopen Last
          </button>
        ) : null}
        {persisted.recentVaults.length > 0 ? (
          <select
            className="select"
            value=""
            onChange={(e) => {
              const v = e.target.value;
              if (!v) return;
              void openRecent(v);
              e.target.value = "";
            }}
            title="Recent vaults"
          >
            <option value="">Recent…</option>
            {persisted.recentVaults.map((p) => (
              <option key={p} value={p}>
                {p}
              </option>
            ))}
          </select>
        ) : null}
        <button className="btn" onClick={newFile} disabled={!vaultRoot}>
          New File
        </button>
        <button className="btn" onClick={newFolder} disabled={!vaultRoot}>
          New Folder
        </button>
        <button className="btn" onClick={() => reloadDir(selectedDir)} disabled={!vaultRoot}>
          Refresh
        </button>
        <button className="btn" onClick={renameActive} disabled={!activeTab}>
          Rename
        </button>
        <button className="btn" onClick={deleteActive} disabled={!activePath}>
          Delete
        </button>
        <div className="spacer" />
        <button className="btn" onClick={() => setShowPreview((v) => !v)} disabled={!activeTab}>
          {showPreview ? "Hide Preview" : "Show Preview"}
        </button>
        {vaultRoot ? <span className="pill">Vault: {vaultRoot}</span> : <span className="pill">No vault</span>}
      </div>

      <div className="main">
        <div className="sidebar">
          <div className="sidebarHeader">Explorer</div>
          {vaultRoot ? (
            <FileTree
              rootLabel={rootLabel}
              nodes={flattenedNodes}
              activePath={activePath}
              onToggleDir={toggleDir}
              onOpenFile={openFile}
            />
          ) : (
            <div className="emptyState">Select a vault folder to start.</div>
          )}
        </div>

        <div className="editor">
          <div className="tabs">
            {tabs.length === 0 ? (
              <div className="tab active" style={{ minWidth: 240 }}>
                <span className="label">Welcome</span>
              </div>
            ) : (
              tabs.map((t) => (
                <div
                  key={t.path}
                  className={`tab${t.path === activePath ? " active" : ""}`}
                  onClick={() => setActivePath(t.path)}
                  title={t.path}
                >
                  <span className={`dirty${t.dirty ? " on" : ""}`} />
                  <span className="label">{t.name}</span>
                </div>
              ))
            )}
          </div>

          <div className="pane">
            {activeTab ? (
              showPreview ? (
                <div className="split">
                  <textarea
                    className="textarea"
                    value={activeTab.content}
                    onChange={(e) => updateActiveContent(e.target.value)}
                    spellCheck={false}
                  />
                  <iframe
                    ref={previewRef}
                    className="preview"
                    src="/markdown-preview/index.html"
                    title="Preview"
                    sandbox="allow-scripts allow-same-origin"
                    onLoad={pushPreview}
                  />
                </div>
              ) : (
                <textarea
                  className="textarea"
                  value={activeTab.content}
                  onChange={(e) => updateActiveContent(e.target.value)}
                  spellCheck={false}
                />
              )
            ) : (
              <div className="emptyState">
                Open a Markdown file from the Explorer. Save with <span className="pill">Ctrl+S</span>.
              </div>
            )}
          </div>
        </div>
      </div>

      <div className="statusbar">
        <div className="path">{activeTab ? activeTab.path : "No file"}</div>
        <div className="hint">{activeTab?.dirty ? "Unsaved changes" : "Saved"}</div>
        <div className="pill">Ctrl+S</div>
      </div>
    </div>
  );
}

