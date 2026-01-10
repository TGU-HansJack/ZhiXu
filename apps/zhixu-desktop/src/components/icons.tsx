import React from "react";

type IconProps = {
  size?: number;
};

function MaskIcon({ size = 18, file }: IconProps & { file: string }) {
  return (
    <span
      className="uiIcon"
      aria-hidden="true"
      style={{
        width: size,
        height: size,
        WebkitMaskImage: `url(${file})`,
        maskImage: `url(${file})`,
      }}
    />
  );
}

export function IconSidebar(props: IconProps) {
  return <MaskIcon {...props} file="/icons/bars-3.svg" />;
}

export function IconSidebarClose(props: IconProps) {
  return <MaskIcon {...props} file="/icons/left-panel-close.svg" />;
}

export function IconSidebarOpen(props: IconProps) {
  return <MaskIcon {...props} file="/icons/left-panel-open.svg" />;
}

export function IconSpace(props: IconProps) {
  return <MaskIcon {...props} file="/icons/folder-outline.svg" />;
}

export function IconTasks(props: IconProps) {
  return <MaskIcon {...props} file="/icons/checkbox-outline.svg" />;
}

export function IconCalendar(props: IconProps) {
  return <MaskIcon {...props} file="/icons/calendar-clear-outline.svg" />;
}

export function IconQuadrant(props: IconProps) {
  return <MaskIcon {...props} file="/icons/grid-outline.svg" />;
}

export function IconWorkshop(props: IconProps) {
  return <MaskIcon {...props} file="/icons/storefront-outline.svg" />;
}

export function IconSearch(props: IconProps) {
  return <MaskIcon {...props} file="/icons/search-outline.svg" />;
}

export function IconPlus(props: IconProps) {
  return <MaskIcon {...props} file="/icons/document-plus.svg" />;
}

export function IconFolderPlus(props: IconProps) {
  return <MaskIcon {...props} file="/icons/folder-plus.svg" />;
}

export function IconRefresh(props: IconProps) {
  return <MaskIcon {...props} file="/icons/arrow-path.svg" />;
}

export function IconSave(props: IconProps) {
  return <MaskIcon {...props} file="/icons/save-outline.svg" />;
}

export function IconTrash(props: IconProps) {
  return <MaskIcon {...props} file="/icons/trash.svg" />;
}

export function IconRename(props: IconProps) {
  return <MaskIcon {...props} file="/icons/pencil-square.svg" />;
}

export function IconX(props: IconProps) {
  return <MaskIcon {...props} file="/icons/x-mark.svg" />;
}

export function IconMinimize(props: IconProps) {
  return <MaskIcon {...props} file="/icons/minus.svg" />;
}

export function IconMaximize(props: IconProps) {
  return <MaskIcon {...props} file="/icons/square-outline.svg" />;
}

export function IconClose(props: IconProps) {
  return <MaskIcon {...props} file="/icons/x-mark.svg" />;
}

export function IconChevron({ size = 14, open }: { size?: number; open: boolean }) {
  return (
    <span
      className={`uiIcon chevron${open ? " open" : ""}`}
      aria-hidden="true"
      style={{
        width: size,
        height: size,
        WebkitMaskImage: "url(/icons/chevron-right.svg)",
        maskImage: "url(/icons/chevron-right.svg)",
      }}
    />
  );
}

export function IconFolder(props: IconProps) {
  return <MaskIcon {...props} file="/icons/folder.svg" />;
}

export function IconDocument(props: IconProps) {
  return <MaskIcon {...props} file="/icons/document.svg" />;
}
