import type { DrawPoint } from "./types";

export function distanceSquared(a: DrawPoint, b: DrawPoint): number {
  const dx = a[0] - b[0];
  const dy = a[1] - b[1];
  return dx * dx + dy * dy;
}

export function lerp(a: DrawPoint, b: DrawPoint, t: number): DrawPoint {
  return [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t];
}

// Ray casting algorithm.
export function pointInPolygon(point: DrawPoint, polygon: DrawPoint[]): boolean {
  const x = point[0];
  const y = point[1];
  let inside = false;
  for (let i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
    const xi = polygon[i]![0];
    const yi = polygon[i]![1];
    const xj = polygon[j]![0];
    const yj = polygon[j]![1];
    const intersect = yi > y !== yj > y && x < ((xj - xi) * (y - yi)) / (yj - yi + 0.0000001) + xi;
    if (intersect) inside = !inside;
  }
  return inside;
}

export type DrawRect = { left: number; top: number; right: number; bottom: number };

export function rectFromPoints(a: DrawPoint, b: DrawPoint): DrawRect {
  return {
    left: Math.min(a[0], b[0]),
    right: Math.max(a[0], b[0]),
    top: Math.min(a[1], b[1]),
    bottom: Math.max(a[1], b[1]),
  };
}

