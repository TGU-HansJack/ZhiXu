import React from "react";
import type { VaultEntry } from "../lib/vaultApi";

export type TreeNode = VaultEntry & {
  depth: number;
  expanded?: boolean;
  loading?: boolean;
};

type Props = {
  rootLabel: string;
  nodes: TreeNode[];
  activePath: string | null;
  onToggleDir: (path: string) => void;
  onOpenFile: (path: string) => void;
};

export function FileTree({ rootLabel, nodes, activePath, onToggleDir, onOpenFile }: Props) {
  return (
    <div className="tree" role="tree" aria-label="Explorer">
      <div className="treeRow" style={{ paddingLeft: 6 }} role="treeitem" aria-level={1}>
        <span className="twisty" />
        <span className="icon">📁</span>
        <span className="label" title={rootLabel}>
          {rootLabel}
        </span>
      </div>
      {nodes.map((n) => {
        const isActive = activePath === n.path;
        const pad = 12 + n.depth * 14;
        const twisty = n.isDir ? (n.expanded ? "▼" : "▶") : "";
        const icon = n.isDir ? "📁" : "📝";
        return (
          <div
            key={n.path}
            className={`treeRow${isActive ? " active" : ""}`}
            style={{ paddingLeft: pad }}
            role="treeitem"
            aria-level={n.depth + 2}
            aria-expanded={n.isDir ? Boolean(n.expanded) : undefined}
            onClick={() => {
              if (n.isDir) onToggleDir(n.path);
              else onOpenFile(n.path);
            }}
          >
            <span className="twisty">{n.loading ? "…" : twisty}</span>
            <span className="icon">{icon}</span>
            <span className="label" title={n.path}>
              {n.name}
            </span>
          </div>
        );
      })}
    </div>
  );
}

