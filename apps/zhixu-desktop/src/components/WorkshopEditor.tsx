import React, { useEffect, useMemo, useState } from "react";
import type { InstalledPlugin, PluginIndexItem, PluginManifest } from "../lib/plugins/types";
import { runInstalledPluginAction } from "../lib/plugins/runtime";
import {
  fetchOfficialManifest,
  fetchOfficialReadme,
  installPluginFromOfficial,
  savePluginConfig,
  setPluginEnabled,
  uninstallPlugin,
} from "../lib/plugins/workshop";

function compareVersion(a: string, b: string): number {
  const as = String(a || "").split(".");
  const bs = String(b || "").split(".");
  const len = Math.max(as.length, bs.length);
  for (let i = 0; i < len; i++) {
    const av = parseInt(as[i] || "0", 10);
    const bv = parseInt(bs[i] || "0", 10);
    if (!Number.isFinite(av) || !Number.isFinite(bv)) {
      const sa = as[i] || "";
      const sb = bs[i] || "";
      if (sa === sb) continue;
      return sa > sb ? 1 : -1;
    }
    if (av === bv) continue;
    return av > bv ? 1 : -1;
  }
  return 0;
}

type Props = {
  vaultRoot: string | null;
  baseUrl: string;
  selectedId: string | null;
  official: PluginIndexItem[];
  installed: InstalledPlugin[];
  onInstalledReload: () => void;
};

export function WorkshopEditor({ vaultRoot, baseUrl, selectedId, official, installed, onInstalledReload }: Props) {
  const installedPlugin = useMemo(() => installed.find((p) => p.manifest.id === selectedId) || null, [installed, selectedId]);
  const officialItem = useMemo(() => official.find((p) => p.id === selectedId) || null, [official, selectedId]);

  const [remoteManifest, setRemoteManifest] = useState<PluginManifest | null>(null);
  const [remoteReadme, setRemoteReadme] = useState<string | null>(null);
  const [remoteLoading, setRemoteLoading] = useState(false);
  const [remoteError, setRemoteError] = useState<string | null>(null);

  const [configDraft, setConfigDraft] = useState("");
  const [configSaving, setConfigSaving] = useState(false);

  const [running, setRunning] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [logs, setLogs] = useState<string[]>([]);

  useEffect(() => {
    setLogs([]);
    setActionError(null);
    setRunning(null);
  }, [selectedId]);

  useEffect(() => {
    if (!selectedId) return;
    setRemoteError(null);
    setRemoteLoading(true);
    void (async () => {
      try {
        const m = await fetchOfficialManifest(baseUrl, selectedId);
        const r = await fetchOfficialReadme(baseUrl, selectedId);
        setRemoteManifest(m);
        setRemoteReadme(r);
      } catch (e) {
        setRemoteManifest(null);
        setRemoteReadme(null);
        setRemoteError(String(e instanceof Error ? e.message : e));
      } finally {
        setRemoteLoading(false);
      }
    })();
  }, [baseUrl, selectedId]);

  useEffect(() => {
    if (!installedPlugin) {
      setConfigDraft("");
      return;
    }
    const raw = installedPlugin.configText ?? "{\n}\n";
    setConfigDraft(raw.trimEnd() + "\n");
  }, [installedPlugin]);

  const displayManifest = installedPlugin?.manifest || remoteManifest;
  const displayName = displayManifest?.name || officialItem?.name || selectedId || "";
  const description = displayManifest?.description || officialItem?.description || "";

  const updateAvailable = useMemo(() => {
    if (!installedPlugin || !remoteManifest) return false;
    return compareVersion(remoteManifest.version, installedPlugin.manifest.version) > 0;
  }, [installedPlugin, remoteManifest]);

  const readme = installedPlugin?.readmeText ?? remoteReadme;

  if (!selectedId) {
    return (
      <div className="workshopEditor">
        <div className="workshopEmpty">
          <div className="workshopEmptyTitle">创意工坊</div>
          <div className="workshopEmptyHint">从左侧选择一个插件查看详情。</div>
          <div className="workshopEmptyHint muted">快捷键：Ctrl + Shift + P 打开插件开发者窗口。</div>
        </div>
      </div>
    );
  }

  return (
    <div className="workshopEditor">
      <div className="workshopEditorHeader">
        <div className="workshopHeaderMain">
          <div className="workshopHeaderTitle">{displayName}</div>
          <div className="workshopHeaderDesc">{description || "—"}</div>
        </div>

        <div className="workshopHeaderActions">
          {!vaultRoot ? (
            <button type="button" className="workshopBtn primary" disabled>
              请选择库
            </button>
          ) : installedPlugin ? (
            <>
              {updateAvailable ? (
                <button
                  type="button"
                  className="workshopBtn primary"
                  disabled={running !== null}
                  onClick={() => {
                    if (!vaultRoot) return;
                    setRunning("update");
                    setActionError(null);
                    void (async () => {
                      try {
                        await installPluginFromOfficial({ vaultRoot, baseUrl, pluginId: installedPlugin.manifest.id, preserveConfig: true });
                        onInstalledReload();
                      } catch (e) {
                        setActionError(String(e instanceof Error ? e.message : e));
                      } finally {
                        setRunning(null);
                      }
                    })();
                  }}
                >
                  更新
                </button>
              ) : null}

              <button
                type="button"
                className={`workshopBtn${installedPlugin.enabled ? "" : " primary"}`}
                disabled={running !== null}
                onClick={() => {
                  if (!vaultRoot) return;
                  setRunning(installedPlugin.enabled ? "disable" : "enable");
                  setActionError(null);
                  void (async () => {
                    try {
                      await setPluginEnabled(vaultRoot, installedPlugin.manifest.id, !installedPlugin.enabled);
                      onInstalledReload();
                    } catch (e) {
                      setActionError(String(e instanceof Error ? e.message : e));
                    } finally {
                      setRunning(null);
                    }
                  })();
                }}
              >
                {installedPlugin.enabled ? "禁用" : "启用"}
              </button>

              <button
                type="button"
                className="workshopBtn danger"
                disabled={running !== null}
                onClick={() => {
                  if (!vaultRoot) return;
                  if (!confirm(`确定卸载插件「${displayName}」吗？`)) return;
                  setRunning("uninstall");
                  setActionError(null);
                  void (async () => {
                    try {
                      await uninstallPlugin(vaultRoot, installedPlugin.manifest.id);
                      onInstalledReload();
                    } catch (e) {
                      setActionError(String(e instanceof Error ? e.message : e));
                    } finally {
                      setRunning(null);
                    }
                  })();
                }}
              >
                卸载
              </button>
            </>
          ) : (
            <button
              type="button"
              className="workshopBtn primary"
              disabled={!vaultRoot || running !== null || remoteLoading}
              onClick={() => {
                if (!vaultRoot) return;
                setRunning("install");
                setActionError(null);
                void (async () => {
                  try {
                    await installPluginFromOfficial({ vaultRoot, baseUrl, pluginId: selectedId, preserveConfig: true });
                    onInstalledReload();
                  } catch (e) {
                    setActionError(String(e instanceof Error ? e.message : e));
                  } finally {
                    setRunning(null);
                  }
                })();
              }}
            >
              安装
            </button>
          )}
        </div>
      </div>

      <div className="workshopEditorBody">
        <div className="workshopEditorMain">
          {remoteLoading && !installedPlugin ? <div className="workshopHint">正在加载插件信息…</div> : null}
          {remoteError ? <div className="workshopHint error">{remoteError}</div> : null}
          {actionError ? <div className="workshopHint error">{actionError}</div> : null}

          <div className="workshopTabs" role="tablist" aria-label="插件信息">
            <button type="button" className="workshopTab active" role="tab" aria-selected="true">
              细节
            </button>
          </div>

          <div className="workshopReadme">
            {readme ? <pre className="workshopReadmePre">{readme}</pre> : <div className="workshopHint">暂无 README。</div>}
          </div>

          {installedPlugin ? (
            <>
              <div className="workshopSectionTitle">插件操作</div>
              {installedPlugin.manifest.actions && installedPlugin.manifest.actions.length ? (
                <div className="workshopActionRow">
                  {installedPlugin.manifest.actions.map((a) => (
                    <button
                      key={a.id}
                      type="button"
                      className="workshopBtn"
                      disabled={!installedPlugin.enabled || running !== null}
                      onClick={() => {
                        if (!vaultRoot) return;
                        setRunning(a.id);
                        setActionError(null);
                        setLogs([]);
                        void (async () => {
                          try {
                            const { result } = await runInstalledPluginAction({
                              vaultRoot,
                              plugin: installedPlugin,
                              actionId: a.id,
                              log: (line) => setLogs((prev) => [...prev, line]),
                            });
                            if (typeof result === "string") setLogs((prev) => [...prev, result]);
                            else if (result && typeof result === "object" && "message" in result && result.message) {
                              setLogs((prev) => [...prev, String(result.message)]);
                            }
                          } catch (e) {
                            setActionError(String(e instanceof Error ? e.message : e));
                          } finally {
                            setRunning(null);
                          }
                        })();
                      }}
                    >
                      {a.label || a.id}
                    </button>
                  ))}
                </div>
              ) : (
                <div className="workshopHint">该插件没有声明 actions。</div>
              )}

              <div className="workshopSectionTitle">配置（config.json）</div>
              <textarea
                className="workshopConfigEditor"
                value={configDraft}
                onChange={(e) => setConfigDraft(e.target.value)}
                spellCheck={false}
                data-no-drag="true"
                disabled={!installedPlugin.enabled || configSaving}
              />
              <div className="workshopActionRow">
                <button
                  type="button"
                  className="workshopBtn primary"
                  disabled={!installedPlugin.enabled || !vaultRoot || configSaving}
                  onClick={() => {
                    if (!vaultRoot) return;
                    setConfigSaving(true);
                    setActionError(null);
                    void (async () => {
                      try {
                        const next = configDraft.trim() ? configDraft : "{\n}\n";
                        JSON.parse(next);
                        await savePluginConfig(vaultRoot, installedPlugin.manifest.id, next);
                        onInstalledReload();
                      } catch (e) {
                        setActionError(String(e instanceof Error ? e.message : e));
                      } finally {
                        setConfigSaving(false);
                      }
                    })();
                  }}
                >
                  保存配置
                </button>
              </div>
            </>
          ) : null}

          {logs.length ? (
            <>
              <div className="workshopSectionTitle">输出</div>
              <pre className="workshopLogs">{logs.join("\n")}</pre>
            </>
          ) : null}
        </div>

        <aside className="workshopEditorAside">
          <div className="workshopMetaCard">
            <div className="workshopMetaRow">
              <div className="workshopMetaKey">标识符</div>
              <div className="workshopMetaVal">{displayManifest?.id || selectedId}</div>
            </div>
            <div className="workshopMetaRow">
              <div className="workshopMetaKey">版本</div>
              <div className="workshopMetaVal">{displayManifest?.version || officialItem?.version || "—"}</div>
            </div>
            <div className="workshopMetaRow">
              <div className="workshopMetaKey">状态</div>
              <div className="workshopMetaVal">
                {installedPlugin ? (installedPlugin.enabled ? "已启用" : "已禁用") : "未安装"}
                {updateAvailable ? "（可更新）" : ""}
              </div>
            </div>
            <div className="workshopMetaRow">
              <div className="workshopMetaKey">来源</div>
              <div className="workshopMetaVal">官方</div>
            </div>
          </div>
        </aside>
      </div>
    </div>
  );
}

