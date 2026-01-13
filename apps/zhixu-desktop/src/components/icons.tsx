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

export function IconArrowBack(props: IconProps) {
  return <MaskIcon {...props} file="/icons/arrow-back.svg" />;
}

export function IconUndo(props: IconProps) {
  return <MaskIcon {...props} file="/icons/arrow-uturn-left.svg" />;
}

export function IconRedo(props: IconProps) {
  return <MaskIcon {...props} file="/icons/arrow-uturn-right.svg" />;
}

export function IconMoreHorizontal(props: IconProps) {
  return <MaskIcon {...props} file="/icons/ellipsis-horizontal.svg" />;
}

export function IconChevronBack(props: IconProps) {
  return <MaskIcon {...props} file="/icons/chevron-back.svg" />;
}

export function IconChevronForward(props: IconProps) {
  return <MaskIcon {...props} file="/icons/chevron-forward.svg" />;
}

export function IconAddCircle(props: IconProps) {
  return <MaskIcon {...props} file="/icons/add-circle-outline.svg" />;
}

export function IconCheckmark(props: IconProps) {
  return <MaskIcon {...props} file="/icons/checkmark.svg" />;
}

export function IconLucidePenTool(props: IconProps) {
  return <MaskIcon {...props} file="/icons/lucide-pen-tool.svg" />;
}

export function IconLucidePencil(props: IconProps) {
  return <MaskIcon {...props} file="/icons/lucide-pencil.svg" />;
}

export function IconLucideBrush(props: IconProps) {
  return <MaskIcon {...props} file="/icons/lucide-brush.svg" />;
}

export function IconLucidePin(props: IconProps) {
  return <MaskIcon {...props} file="/icons/lucide-pin.svg" />;
}

export function IconLucidePictureInPicture(props: IconProps) {
  return <MaskIcon {...props} file="/icons/lucide-picture-in-picture.svg" />;
}

export function IconLucideHighlighter(props: IconProps) {
  return <MaskIcon {...props} file="/icons/lucide-highlighter.svg" />;
}

export function IconLucidePyramid(props: IconProps) {
  return <MaskIcon {...props} file="/icons/lucide-pyramid.svg" />;
}

export function IconLucideLasso(props: IconProps) {
  return <MaskIcon {...props} file="/icons/lucide-lasso.svg" />;
}

export function IconLucideEraser(props: IconProps) {
  return <MaskIcon {...props} file="/icons/lucide-eraser.svg" />;
}

export function IconLucideHand(props: IconProps) {
  return <MaskIcon {...props} file="/icons/lucide-hand.svg" />;
}

export function IconLucideCircleUserRound({ size = 18 }: IconProps) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      <path d="M18 20a6 6 0 0 0-12 0" />
      <circle cx="12" cy="10" r="4" />
      <circle cx="12" cy="12" r="10" />
    </svg>
  );
}

export function IconLucideSettings({ size = 18 }: IconProps) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      <path d="M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915" />
      <circle cx="12" cy="12" r="3" />
    </svg>
  );
}

export function IconChevronsUpDown({ size = 18 }: IconProps) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      <path d="m7 15 5 5 5-5" />
      <path d="m7 9 5-5 5 5" />
    </svg>
  );
}

export function IconFolderPlusLucide({ size = 18 }: IconProps) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
      focusable="false"
    >
      <path d="M12 10v6" />
      <path d="M9 13h6" />
      <path d="M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z" />
    </svg>
  );
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
