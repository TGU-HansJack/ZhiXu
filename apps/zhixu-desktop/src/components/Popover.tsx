import React, { useCallback, useEffect, useMemo, useState } from "react";
import { createPortal } from "react-dom";

export type PopoverPlacement =
  | "bottom-start"
  | "bottom"
  | "bottom-end"
  | "top-start"
  | "top"
  | "top-end"
  | "right-start"
  | "right"
  | "right-end"
  | "left-start"
  | "left"
  | "left-end";

type Props = {
  open: boolean;
  anchorEl: HTMLElement | null;
  placement?: PopoverPlacement;
  onClose: () => void;
  className?: string;
  children: React.ReactNode;
};

function anchorPoint(rect: DOMRect, placement: PopoverPlacement) {
  const xCenter = rect.left + rect.width / 2;
  const yCenter = rect.top + rect.height / 2;
  switch (placement) {
    case "bottom-start":
      return { x: rect.left, y: rect.bottom, placement };
    case "bottom":
      return { x: xCenter, y: rect.bottom, placement };
    case "bottom-end":
      return { x: rect.right, y: rect.bottom, placement };
    case "top-start":
      return { x: rect.left, y: rect.top, placement };
    case "top":
      return { x: xCenter, y: rect.top, placement };
    case "top-end":
      return { x: rect.right, y: rect.top, placement };
    case "right-start":
      return { x: rect.right, y: rect.top, placement };
    case "right":
      return { x: rect.right, y: yCenter, placement };
    case "right-end":
      return { x: rect.right, y: rect.bottom, placement };
    case "left-start":
      return { x: rect.left, y: rect.top, placement };
    case "left":
      return { x: rect.left, y: yCenter, placement };
    case "left-end":
      return { x: rect.left, y: rect.bottom, placement };
  }
}

export function Popover({ open, anchorEl, placement = "bottom", onClose, className, children }: Props) {
  const portalRoot = useMemo(() => (typeof document === "undefined" ? null : document.body), []);
  const [anchor, setAnchor] = useState<{ x: number; y: number; placement: PopoverPlacement }>({ x: 0, y: 0, placement });

  const compute = useCallback(() => {
    if (!anchorEl) return;
    const rect = anchorEl.getBoundingClientRect();
    setAnchor(anchorPoint(rect, placement));
  }, [anchorEl, placement]);

  useEffect(() => {
    if (!open) return;
    compute();
  }, [compute, open]);

  useEffect(() => {
    if (!open) return;
    const onMouseDown = (ev: MouseEvent) => {
      const pop = document.querySelector(".popoverPanel");
      if (pop && ev.target instanceof Node && pop.contains(ev.target)) return;
      if (anchorEl && ev.target instanceof Node && anchorEl.contains(ev.target)) return;
      onClose();
    };
    const onKeyDown = (ev: KeyboardEvent) => {
      if (ev.key === "Escape") onClose();
    };
    window.addEventListener("mousedown", onMouseDown, true);
    window.addEventListener("keydown", onKeyDown, true);
    window.addEventListener("resize", compute);
    return () => {
      window.removeEventListener("mousedown", onMouseDown, true);
      window.removeEventListener("keydown", onKeyDown, true);
      window.removeEventListener("resize", compute);
    };
  }, [anchorEl, compute, onClose, open]);

  if (!open || !portalRoot || !anchorEl) return null;

  return createPortal(
    <div
      className={`popoverPanel${className ? ` ${className}` : ""}`}
      data-placement={anchor.placement}
      style={{ left: anchor.x, top: anchor.y }}
      role="dialog"
      aria-modal="false"
      data-no-drag="true"
    >
      {children}
    </div>,
    portalRoot,
  );
}
