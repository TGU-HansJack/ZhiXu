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
  rootLabel: string;
  nodes: TreeNode[];
  activePath: string | null;
  onToggleDir: (path: string) => void;
  onOpenFile: (path: string) => void;
};

export function FileTree({ rootLabel, nodes, activePath, onToggleDir, onOpenFile }: Props) {
  return (
    <div className="tree" role="tree" aria-label="文件列表">
      <div className="treeRow" style={{ paddingLeft: 6 }} role="treeitem" aria-level={1}>
        <span className="twisty" />
        <span className="icon" aria-hidden="true">
          <IconFolder size={16} />
        </span>
        <Tooltip label={rootLabel} placement="right">
          <span className="label">{rootLabel}</span>
        </Tooltip>
      </div>
      {nodes.map((node) => {
        const isActive = activePath === node.path;
        const pad = 12 + node.depth * 14;
        const displayName = node.isDir ? node.name : stripExtension(node.name);
        const typeLabel = node.isDir ? null : getFileTypeLabel(node.name);
        return (
          <div
            key={node.path}
            className={`treeRow${isActive ? " active" : ""}`}
            style={{ paddingLeft: pad }}
            role="treeitem"
            aria-level={node.depth + 2}
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
            <Tooltip label={node.path} placement="right">
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
