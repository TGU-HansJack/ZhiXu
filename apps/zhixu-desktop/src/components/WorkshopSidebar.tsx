import React, { useMemo } from "react";
import type { InstalledPlugin, PluginIndexItem } from "../lib/plugins/types";
import { IconLucideSettings, IconRefresh, IconSearch } from "./icons";
import { Tooltip } from "./Tooltip";

type Props = {
  vaultRoot: string | null;
  baseUrl: string;
  search: string;
  onSearchChange: (next: string) => void;
  official: PluginIndexItem[];
  officialLoading: boolean;
  officialError: string | null;
  installed: InstalledPlugin[];
  installedLoading: boolean;
  selectedId: string | null;
  onSelect: (id: string) => void;
  onRefresh: () => void;
  onEditBaseUrl: () => void;
};

function isDesktopCompatible(p: PluginIndexItem): boolean {
  if (!p.platforms || p.platforms.length === 0) return true;
  return p.platforms.includes("desktop");
}

export function WorkshopSidebar({
  vaultRoot,
  baseUrl,
  search,
  onSearchChange,
  official,
  officialLoading,
  officialError,
  installed,
  installedLoading,
  selectedId,
  onSelect,
  onRefresh,
  onEditBaseUrl,
}: Props) {
  const installedById = useMemo(() => new Map(installed.map((p) => [p.manifest.id, p])), [installed]);
  const filteredOfficial = useMemo(() => {
    const q = search.trim().toLowerCase();
    return official
      .filter(isDesktopCompatible)
      .filter((p) => {
        if (!q) return true;
        const hay = `${p.id} ${p.name || ""} ${p.description || ""}`.toLowerCase();
        return hay.includes(q);
      })
      .sort((a, b) => (a.name || a.id).localeCompare(b.name || b.id, undefined, { numeric: true, sensitivity: "base" }));
  }, [official, search]);

  const filteredInstalled = useMemo(() => {
    const q = search.trim().toLowerCase();
    return installed
      .filter((p) => {
        if (!q) return true;
        const hay = `${p.manifest.id} ${p.manifest.name} ${p.manifest.description || ""}`.toLowerCase();
        return hay.includes(q);
      })
      .sort((a, b) => a.manifest.name.localeCompare(b.manifest.name, undefined, { numeric: true, sensitivity: "base" }));
  }, [installed, search]);

  return (
    <div className="workshopSidebar">
      <div className="sidebarSubHeader">
        <div className="workshopSearch">
          <span className="workshopSearchIcon" aria-hidden="true">
            <IconSearch size={16} />
          </span>
          <input
            className="workshopSearchInput"
            value={search}
            onChange={(e) => onSearchChange(e.target.value)}
            placeholder="搜索插件"
            type="text"
            spellCheck={false}
            data-no-drag="true"
          />
          <Tooltip label="刷新" placement="right">
            <button type="button" className="iconBtn workshopRefreshBtn" onClick={onRefresh} data-no-drag="true">
              <IconRefresh size={18} />
            </button>
          </Tooltip>
          <Tooltip label={`插件源：${baseUrl}`} placement="right">
            <button type="button" className="iconBtn workshopRefreshBtn" onClick={onEditBaseUrl} data-no-drag="true">
              <IconLucideSettings size={18} />
            </button>
          </Tooltip>
        </div>
      </div>

      {!vaultRoot ? <div className="emptyState">请先选择一个库（Vault）后再管理插件。</div> : null}

      <div className="workshopSection">
        <div className="workshopSectionTitle">已安装</div>
        {installedLoading ? (
          <div className="workshopHint">正在加载…</div>
        ) : filteredInstalled.length ? (
          <div className="workshopList" role="list">
            {filteredInstalled.map((p) => (
              <button
                key={p.manifest.id}
                type="button"
                className={`workshopItem${selectedId === p.manifest.id ? " active" : ""}`}
                onClick={() => onSelect(p.manifest.id)}
                data-no-drag="true"
              >
                <div className="workshopItemTop">
                  <div className="workshopItemName">{p.manifest.name}</div>
                  <div className={`workshopItemBadge${p.enabled ? "" : " off"}`}>{p.enabled ? "已启用" : "已禁用"}</div>
                </div>
                <div className="workshopItemDesc">{p.manifest.description || "—"}</div>
                <div className="workshopItemMeta">
                  <span className="muted">{p.manifest.id}</span>
                  <span className="muted">v{p.manifest.version}</span>
                </div>
              </button>
            ))}
          </div>
        ) : (
          <div className="workshopHint">暂无已安装插件</div>
        )}
      </div>

      <div className="workshopSection">
        <div className="workshopSectionTitle">官方插件</div>
        {officialLoading ? (
          <div className="workshopHint">正在获取官方插件…</div>
        ) : officialError ? (
          <div className="workshopHint error">{officialError}</div>
        ) : filteredOfficial.length ? (
          <div className="workshopList" role="list">
            {filteredOfficial.map((p) => {
              const installed = installedById.get(p.id);
              const displayName = p.name || p.id;
              return (
                <button
                  key={p.id}
                  type="button"
                  className={`workshopItem${selectedId === p.id ? " active" : ""}`}
                  onClick={() => onSelect(p.id)}
                  data-no-drag="true"
                >
                  <div className="workshopItemTop">
                    <div className="workshopItemName">{displayName}</div>
                    <div className={`workshopItemBadge${installed ? "" : " ghost"}`}>{installed ? "已安装" : "未安装"}</div>
                  </div>
                  <div className="workshopItemDesc">{p.description || "—"}</div>
                  <div className="workshopItemMeta">
                    <span className="muted">{p.id}</span>
                    <span className="muted">{p.version ? `v${p.version}` : ""}</span>
                  </div>
                </button>
              );
            })}
          </div>
        ) : (
          <div className="workshopHint">暂无可用插件</div>
        )}
      </div>
    </div>
  );
}
