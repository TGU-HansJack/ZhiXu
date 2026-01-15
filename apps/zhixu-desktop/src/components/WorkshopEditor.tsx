import React, { useEffect, useMemo, useState } from "react";
import type { InstalledPlugin, PluginIndexItem, PluginManifest } from "../lib/plugins/types";
import { runInstalledPluginAction } from "../lib/plugins/runtime";
import { closeDesktopWidget, isDesktopWidgetEnabled, openDesktopWidget } from "../lib/widgets";
import { MarkdownRendererFrame } from "./MarkdownRendererFrame";
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

  const [configView, setConfigView] = useState<"visual" | "json">("visual");
  const [configJsonDraft, setConfigJsonDraft] = useState("");
  const [configObjDraft, setConfigObjDraft] = useState<Record<string, any>>({});
  const [configError, setConfigError] = useState<string | null>(null);
  const [configSaving, setConfigSaving] = useState(false);
  const [widgetEnabled, setWidgetEnabled] = useState(false);

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
      setConfigView("visual");
      setConfigJsonDraft("");
      setConfigObjDraft({});
      setConfigError(null);
      return;
    }
    const raw = (installedPlugin.configText ?? "{\n}\n").trim() ? (installedPlugin.configText ?? "{\n}\n") : "{\n}\n";
    const nextJson = raw.trimEnd() + "\n";
    setConfigJsonDraft(nextJson);
    try {
      const parsed = JSON.parse(nextJson) as unknown;
      if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
        setConfigView("json");
        setConfigObjDraft({});
        setConfigError("config.json 必须是对象。");
      } else {
        setConfigView("visual");
        setConfigObjDraft(parsed as Record<string, any>);
        setConfigError(null);
      }
    } catch (e) {
      setConfigView("json");
      setConfigObjDraft({});
      setConfigError(String(e instanceof Error ? e.message : e));
    }
  }, [installedPlugin]);

  const displayManifest = installedPlugin?.manifest || remoteManifest;
  const displayName = displayManifest?.name || officialItem?.name || selectedId || "";
  const description = displayManifest?.description || officialItem?.description || "";

  const updateAvailable = useMemo(() => {
    if (!installedPlugin || !remoteManifest) return false;
    return compareVersion(remoteManifest.version, installedPlugin.manifest.version) > 0;
  }, [installedPlugin, remoteManifest]);

  const readme = installedPlugin?.readmeText ?? remoteReadme;

  const widgetAction = useMemo(() => {
    if (!installedPlugin?.manifest.actions) return null;
    const normalizePlace = (place: string | undefined) => String(place || "").replace(/[\s_-]/g, "").toLowerCase();
    return installedPlugin.manifest.actions.find((a) => normalizePlace(a.place) === "mainsidebar") || null;
  }, [installedPlugin]);

  useEffect(() => {
    setWidgetEnabled(selectedId ? isDesktopWidgetEnabled(selectedId) : false);
  }, [selectedId]);

  useEffect(() => {
    const onStorage = (e: StorageEvent) => {
      if (e.key !== "zhixu:desktopWidgets") return;
      setWidgetEnabled(selectedId ? isDesktopWidgetEnabled(selectedId) : false);
    };
    window.addEventListener("storage", onStorage);
    return () => window.removeEventListener("storage", onStorage);
  }, [selectedId]);

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
            {readme ? (
              <MarkdownRendererFrame markdown={readme} className="workshopReadmeFrame" />
            ) : (
              <div className="workshopHint">暂无 README。</div>
            )}
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

              <div className="workshopSectionTitle">桌面</div>
              <div className="workshopConfigGrid">
                <div className="workshopConfigRow">
                  <div className="workshopConfigKey">桌面小组件</div>
                  <div className="workshopConfigControl">
                    <button
                      type="button"
                      className={`toggle${widgetEnabled ? " on" : ""}`}
                      aria-label="桌面小组件"
                      aria-checked={widgetEnabled ? "true" : "false"}
                      role="switch"
                      disabled={!vaultRoot || !installedPlugin.enabled || !widgetAction}
                      onClick={() => {
                        if (!vaultRoot) return;
                        if (!widgetAction) return;
                        setActionError(null);
                        void (async () => {
                          try {
                            if (widgetEnabled) await closeDesktopWidget(installedPlugin.manifest.id);
                            else {
                              await openDesktopWidget({
                                pluginId: installedPlugin.manifest.id,
                                actionId: widgetAction.id,
                                vaultRoot,
                                title: widgetAction.label || installedPlugin.manifest.name,
                              });
                            }
                            setWidgetEnabled(!widgetEnabled);
                          } catch (e) {
                            setActionError(String(e instanceof Error ? e.message : e));
                          }
                        })();
                      }}
                    />
                  </div>
                </div>
              </div>

              <div className="workshopSectionHeader">
                <div className="workshopSectionTitle">配置（config.json）</div>
                <button
                  type="button"
                  className="workshopBtn"
                  disabled={!installedPlugin.enabled || configSaving}
                  onClick={() => {
                    setActionError(null);
                    setConfigError(null);
                    if (configView === "visual") {
                      setConfigJsonDraft(JSON.stringify(configObjDraft || {}, null, 2) + "\n");
                      setConfigView("json");
                      return;
                    }
                    try {
                      const parsed = JSON.parse(configJsonDraft.trim() ? configJsonDraft : "{\n}\n") as unknown;
                      if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
                        setConfigError("config.json 必须是对象。");
                        return;
                      }
                      setConfigObjDraft(parsed as Record<string, any>);
                      setConfigView("visual");
                    } catch (e) {
                      setConfigError(String(e instanceof Error ? e.message : e));
                    }
                  }}
                >
                  {configView === "visual" ? "高级（JSON）" : "可视化"}
                </button>
              </div>

              {configError ? <div className="workshopHint error">{configError}</div> : null}

              {configView === "json" ? (
                <textarea
                  className="workshopConfigEditor"
                  value={configJsonDraft}
                  onChange={(e) => setConfigJsonDraft(e.target.value)}
                  spellCheck={false}
                  data-no-drag="true"
                  disabled={!installedPlugin.enabled || configSaving}
                />
              ) : (
                <div className="workshopConfigGrid">
                  {Object.keys(configObjDraft || {}).length ? (
                    Object.entries(configObjDraft || {}).map(([key, value]) => {
                      const type = Array.isArray(value) ? "array" : value === null ? "null" : typeof value;
                      const setValue = (next: any) => setConfigObjDraft((prev) => ({ ...(prev || {}), [key]: next }));

                      if (type === "boolean") {
                        return (
                          <div key={key} className="workshopConfigRow">
                            <div className="workshopConfigKey">{key}</div>
                            <div className="workshopConfigControl">
                              <button
                                type="button"
                                className={`toggle${value ? " on" : ""}`}
                                aria-label={key}
                                aria-checked={value ? "true" : "false"}
                                role="switch"
                                disabled={!installedPlugin.enabled || configSaving}
                                onClick={() => setValue(!value)}
                              />
                            </div>
                          </div>
                        );
                      }

                      if (type === "number" || type === "null") {
                        return (
                          <label key={key} className="workshopConfigRow">
                            <div className="workshopConfigKey">{key}</div>
                            <div className="workshopConfigControl">
                              <input
                                className="textInput"
                                type="number"
                                inputMode="decimal"
                                value={typeof value === "number" ? String(value) : ""}
                                disabled={!installedPlugin.enabled || configSaving}
                                onChange={(e) => {
                                  const raw = e.target.value;
                                  if (raw === "") setValue(null);
                                  else {
                                    const n = Number(raw);
                                    if (Number.isFinite(n)) setValue(n);
                                  }
                                }}
                              />
                            </div>
                          </label>
                        );
                      }

                      if (type === "string") {
                        return (
                          <label key={key} className="workshopConfigRow">
                            <div className="workshopConfigKey">{key}</div>
                            <div className="workshopConfigControl">
                              <input
                                className="textInput"
                                type="text"
                                value={String(value)}
                                disabled={!installedPlugin.enabled || configSaving}
                                onChange={(e) => setValue(e.target.value)}
                              />
                            </div>
                          </label>
                        );
                      }

                      if (type === "array" && Array.isArray(value) && value.every((v) => typeof v === "string")) {
                        return (
                          <label key={key} className="workshopConfigRow">
                            <div className="workshopConfigKey">{key}</div>
                            <div className="workshopConfigControl">
                              <textarea
                                className="textInput workshopConfigTextarea"
                                rows={Math.min(6, Math.max(2, value.length || 2))}
                                value={value.join("\n")}
                                disabled={!installedPlugin.enabled || configSaving}
                                onChange={(e) => {
                                  const lines = String(e.target.value || "")
                                    .split(/\r?\n|,/g)
                                    .map((s) => s.trim())
                                    .filter(Boolean);
                                  setValue(lines);
                                }}
                              />
                            </div>
                          </label>
                        );
                      }

                      return (
                        <div key={key} className="workshopConfigRow">
                          <div className="workshopConfigKey">{key}</div>
                          <div className="workshopConfigControl">
                            <div className="workshopHint">该字段类型暂不支持可视化编辑，请使用“高级（JSON）”。</div>
                          </div>
                        </div>
                      );
                    })
                  ) : (
                    <div className="workshopHint">该插件暂无配置项。</div>
                  )}
                </div>
              )}
              <div className="workshopActionRow">
                <button
                  type="button"
                  className="workshopBtn primary"
                  disabled={!installedPlugin.enabled || !vaultRoot || configSaving}
                  onClick={() => {
                    if (!vaultRoot) return;
                    setConfigSaving(true);
                    setActionError(null);
                    setConfigError(null);
                    void (async () => {
                      try {
                        const nextObj =
                          configView === "json"
                            ? (JSON.parse(configJsonDraft.trim() ? configJsonDraft : "{\n}\n") as unknown)
                            : (configObjDraft as unknown);
                        if (!nextObj || typeof nextObj !== "object" || Array.isArray(nextObj)) {
                          throw new Error("config.json 必须是对象。");
                        }
                        const next = JSON.stringify(nextObj, null, 2) + "\n";
                        await savePluginConfig(vaultRoot, installedPlugin.manifest.id, next);
                        setConfigJsonDraft(next);
                        setConfigObjDraft(nextObj as Record<string, any>);
                        setConfigView("visual");
                        onInstalledReload();
                      } catch (e) {
                        setConfigError(String(e instanceof Error ? e.message : e));
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
