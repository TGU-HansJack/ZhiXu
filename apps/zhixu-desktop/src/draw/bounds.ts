import type { DrawElement } from "./types";
import type { DrawRect } from "./geometry";
import { rectFromPoints } from "./geometry";

export function elementBounds(el: DrawElement): DrawRect | null {
  if (el.type === "stroke") {
    if (!el.points.length) return null;
    let minX = Number.POSITIVE_INFINITY;
    let minY = Number.POSITIVE_INFINITY;
    let maxX = Number.NEGATIVE_INFINITY;
    let maxY = Number.NEGATIVE_INFINITY;
    for (const p of el.points) {
      minX = Math.min(minX, p[0]);
      minY = Math.min(minY, p[1]);
      maxX = Math.max(maxX, p[0]);
      maxY = Math.max(maxY, p[1]);
    }
    const pad = Math.max(1, el.width) * 0.6;
    return { left: minX - pad, top: minY - pad, right: maxX + pad, bottom: maxY + pad };
  }

  const rect = rectFromPoints(el.start, el.end);
  const pad = Math.max(1, el.width) * 0.6;
  return { left: rect.left - pad, top: rect.top - pad, right: rect.right + pad, bottom: rect.bottom + pad };
}

