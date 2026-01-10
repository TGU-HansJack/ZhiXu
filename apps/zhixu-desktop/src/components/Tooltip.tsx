import React, { useCallback, useId, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";

export type TooltipPlacement = "auto" | "top" | "bottom" | "left" | "right";
type ResolvedPlacement = Exclude<TooltipPlacement, "auto">;

type Props = {
  label: string;
  placement?: TooltipPlacement;
  asChild?: boolean;
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

export function Tooltip({ label, placement = "auto", asChild = true, children }: Props) {
  const tooltipId = useId();
  const hostRef = useRef<HTMLElement | null>(null);
  const [hovered, setHovered] = useState(false);
  const [resolvedPlacement, setResolvedPlacement] = useState<ResolvedPlacement>("right");
  const [anchor, setAnchor] = useState<{ x: number; y: number }>({ x: 0, y: 0 });

  const ensurePlacement = useCallback(() => {
    const host = hostRef.current;
    if (!host) return;
    const rect = host.getBoundingClientRect();
    const nextPlacement: ResolvedPlacement = placement === "auto" ? resolveAutoPlacement(rect) : placement;
    setResolvedPlacement(nextPlacement);
    setAnchor(anchorPoint(rect, nextPlacement));
  }, [placement]);

  if (!label) return children;

  const portalRoot = useMemo(() => (typeof document === "undefined" ? null : document.body), []);

  const child = React.Children.only(children);
  const childRef = (child as { ref?: React.Ref<HTMLElement> }).ref;

  const show = useCallback(() => {
    ensurePlacement();
    setHovered(true);
  }, [ensurePlacement]);

  const hide = useCallback(() => setHovered(false), []);

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
              style={{ left: anchor.x, top: anchor.y }}
            >
              {label}
            </span>,
            portalRoot,
          )
        : null}
    </>
  );
}
