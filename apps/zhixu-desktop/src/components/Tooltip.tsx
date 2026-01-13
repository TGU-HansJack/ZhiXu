import React, { useCallback, useId, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";

export type TooltipPlacement = "auto" | "top" | "bottom" | "left" | "right";
type ResolvedPlacement = Exclude<TooltipPlacement, "auto">;

type Props = {
  label: string;
  placement?: TooltipPlacement;
  asChild?: boolean;
  boundarySelector?: string;
  gap?: number;
  children: React.ReactElement;
};

function setRef<T>(ref: React.Ref<T> | undefined, value: T) {
  if (!ref) return;
  if (typeof ref === "function") ref(value);
  else (ref as React.MutableRefObject<T>).current = value;
}

function composeHandlers<E>(
  theirs: ((event: E) => void) | undefined,
  ours: (event: E) => void,
): (event: E) => void {
  return (event) => {
    theirs?.(event);
    ours(event);
  };
}

function resolveAutoPlacement(rect: DOMRect): ResolvedPlacement {
  const vw = window.innerWidth || document.documentElement.clientWidth;
  const vh = window.innerHeight || document.documentElement.clientHeight;

  const leftSpace = rect.left;
  const rightSpace = vw - rect.right;
  const topSpace = rect.top;
  const bottomSpace = vh - rect.bottom;

  const horizBest: ResolvedPlacement = rightSpace >= leftSpace ? "right" : "left";
  const vertBest: ResolvedPlacement = bottomSpace >= topSpace ? "bottom" : "top";

  const horizSpace = Math.max(leftSpace, rightSpace);
  const vertSpace = Math.max(topSpace, bottomSpace);

  if (horizSpace >= 140 || horizSpace >= vertSpace) return horizBest;
  return vertBest;
}

function anchorPoint(rect: DOMRect, placement: ResolvedPlacement) {
  const xCenter = rect.left + rect.width / 2;
  const yCenter = rect.top + rect.height / 2;
  switch (placement) {
    case "right":
      return { x: rect.right, y: yCenter };
    case "left":
      return { x: rect.left, y: yCenter };
    case "top":
      return { x: xCenter, y: rect.top };
    case "bottom":
      return { x: xCenter, y: rect.bottom };
  }
}

function anchorPointWithBoundary(hostRect: DOMRect, placement: ResolvedPlacement, boundaryRect: DOMRect | null) {
  if (!boundaryRect) return anchorPoint(hostRect, placement);
  if (placement === "right") return { x: boundaryRect.right, y: hostRect.top + hostRect.height / 2 };
  if (placement === "left") return { x: boundaryRect.left, y: hostRect.top + hostRect.height / 2 };
  return anchorPoint(hostRect, placement);
}

export function Tooltip({ label, placement = "auto", asChild = true, boundarySelector, gap, children }: Props) {
  const enabled = Boolean(label);
  const tooltipId = useId();
  const hostRef = useRef<HTMLElement | null>(null);
  const [hovered, setHovered] = useState(false);
  const [resolvedPlacement, setResolvedPlacement] = useState<ResolvedPlacement>("right");
  const [anchor, setAnchor] = useState<{ x: number; y: number }>({ x: 0, y: 0 });
  const portalRoot = useMemo(() => (typeof document === "undefined" ? null : document.body), []);

  const ensurePlacement = useCallback(() => {
    const host = hostRef.current;
    if (!host) return;
    const hostRect = host.getBoundingClientRect();
    const nextPlacement: ResolvedPlacement = placement === "auto" ? resolveAutoPlacement(hostRect) : placement;
    setResolvedPlacement(nextPlacement);

    const boundaryRect =
      boundarySelector && nextPlacement !== "top" && nextPlacement !== "bottom"
        ? (host.closest(boundarySelector)?.getBoundingClientRect() ?? null)
        : null;
    setAnchor(anchorPointWithBoundary(hostRect, nextPlacement, boundaryRect));
  }, [placement, boundarySelector]);

  const child = React.Children.only(children);
  const childRef = (child as { ref?: React.Ref<HTMLElement> }).ref;

  const show = useCallback(() => {
    if (!enabled) return;
    ensurePlacement();
    setHovered(true);
  }, [enabled, ensurePlacement]);

  const hide = useCallback(() => {
    if (!enabled) return;
    setHovered(false);
  }, [enabled]);

  if (!enabled) return children;

  return (
    <>
      {asChild
        ? React.cloneElement(child, {
            "aria-describedby": tooltipId,
            onMouseEnter: composeHandlers(child.props.onMouseEnter, show),
            onMouseLeave: composeHandlers(child.props.onMouseLeave, hide),
            onFocus: composeHandlers(child.props.onFocus, show),
            onBlur: composeHandlers(child.props.onBlur, hide),
            ref: (node: HTMLElement | null) => {
              hostRef.current = node;
              setRef(childRef, node);
            },
          } as any)
        : (
            <span
              ref={(node) => {
                hostRef.current = node as unknown as HTMLElement | null;
              }}
              className="tooltipHost"
              data-no-drag="true"
              onMouseEnter={show}
              onMouseLeave={hide}
              onFocus={show}
              onBlur={hide}
            >
              {React.cloneElement(child, {
                "aria-describedby": tooltipId,
              })}
            </span>
          )}
      {hovered && portalRoot
        ? createPortal(
            <span
              id={tooltipId}
              role="tooltip"
              className="tooltipBubble"
              data-placement={resolvedPlacement}
              style={
                ({
                  left: anchor.x,
                  top: anchor.y,
                  ...(gap != null ? { ["--tooltip-gap" as any]: `${gap}px` } : null),
                }) as React.CSSProperties
              }
            >
              {label}
            </span>,
            portalRoot,
          )
        : null}
    </>
  );
}
