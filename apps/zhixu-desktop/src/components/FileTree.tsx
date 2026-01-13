import React from "react";
import type { VaultEntry } from "../lib/vaultApi";
import { getFileTypeLabel, stripExtension } from "../lib/fileType";
import { IconChevron, IconDocument, IconFolder } from "./icons";
import { Tooltip } from "./Tooltip";

export type TreeNode = VaultEntry & {
  depth: number;
  expanded?: boolean;
  loading?: boolean;
};

type Props = {
  nodes: TreeNode[];
  activePath: string | null;
  onToggleDir: (path: string) => void;
  onOpenFile: (path: string) => void;
};

export function FileTree({ nodes, activePath, onToggleDir, onOpenFile }: Props) {
  return (
    <div className="tree" role="tree" aria-label="文件列表">
      {nodes.map((node) => {
        const isActive = activePath === node.path;
        const pad = 6 + node.depth * 14;
        const displayName = node.isDir ? node.name : stripExtension(node.name);
        const typeLabel = node.isDir ? null : getFileTypeLabel(node.name);
        return (
          <div
            key={node.path}
            className={`treeRow${isActive ? " active" : ""}`}
            style={{ paddingLeft: pad }}
            role="treeitem"
            aria-level={node.depth + 1}
            aria-expanded={node.isDir ? Boolean(node.expanded) : undefined}
            onClick={() => {
              if (node.isDir) onToggleDir(node.path);
              else onOpenFile(node.path);
            }}
          >
            <span className="twisty" aria-hidden="true">
              {node.isDir ? (
                node.loading ? (
                  <span className="spinner" aria-label="加载中" />
                ) : (
                  <IconChevron open={Boolean(node.expanded)} />
                )
              ) : null}
            </span>
            <span className="icon" aria-hidden="true">
              {node.isDir ? <IconFolder size={16} /> : <IconDocument size={16} />}
            </span>
            <Tooltip label={node.path} placement="right" boundarySelector=".sidebar.mainSidebar" gap={8}>
              <span className="label">{displayName}</span>
            </Tooltip>
            {typeLabel ? (
              <span className="fileType" aria-hidden="true">
                {typeLabel}
              </span>
            ) : null}
          </div>
        );
      })}
    </div>
  );
}
