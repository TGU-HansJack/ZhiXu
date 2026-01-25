import type { DrawPenStyle, DrawPoint, DrawShapeMode, DrawToolId } from "../draw/types";
import { MAX_SCALE, MIN_SCALE } from "../draw/viewportHelpers";

export type DrawHistory = {
  toolId?: DrawToolId;
  penStyle?: DrawPenStyle;
  fountainPenColorArgb?: number;
  fountainPenWidth?: number;
  ballpointPenColorArgb?: number;
  ballpointPenWidth?: number;
  highlighterColorArgb?: number;
  highlighterWidth?: number;
  highlighterAlpha?: number;
  shapeMode?: DrawShapeMode;
  shapeColorArgb?: number;
  shapeWidth?: number;
  eraserRadius?: number;
  pageIndex?: number;
  viewport?: {
    scale?: number;
    translation?: DrawPoint;
  };
};

const STORAGE_PREFIX = "zhixu.draw.history.v1:";

function storageKey(path: string): string {
  return `${STORAGE_PREFIX}${encodeURIComponent(path)}`;
}

function clampNumber(v: number, min: number, max: number): number {
  if (!Number.isFinite(v)) return min;
  return Math.min(max, Math.max(min, v));
}

function clampInt(v: number, min: number, max: number): number {
  if (!Number.isFinite(v)) return min;
  return Math.min(max, Math.max(min, Math.floor(v)));
}

function parsePoint(value: unknown): DrawPoint | null {
  if (!Array.isArray(value) || value.length < 2) return null;
  const x = value[0];
  const y = value[1];
  if (typeof x !== "number" || typeof y !== "number") return null;
  if (!Number.isFinite(x) || !Number.isFinite(y)) return null;
  return [x, y];
}

function normalize(raw: unknown): DrawHistory {
  const out: DrawHistory = {};
  if (!raw || typeof raw !== "object") return out;
  const obj = raw as Record<string, unknown>;

  if (
    obj.toolId === "pen" ||
    obj.toolId === "highlighter" ||
    obj.toolId === "shape" ||
    obj.toolId === "lasso" ||
    obj.toolId === "eraser" ||
    obj.toolId === "pan"
  ) {
    out.toolId = obj.toolId;
  }

  if (obj.penStyle === "fountainPen" || obj.penStyle === "ballpointPen") out.penStyle = obj.penStyle;
  if (typeof obj.fountainPenColorArgb === "number" && Number.isFinite(obj.fountainPenColorArgb)) {
    out.fountainPenColorArgb = obj.fountainPenColorArgb | 0;
  }
  if (typeof obj.fountainPenWidth === "number") out.fountainPenWidth = clampNumber(obj.fountainPenWidth, 0.2, 80);
  if (typeof obj.ballpointPenColorArgb === "number" && Number.isFinite(obj.ballpointPenColorArgb)) {
    out.ballpointPenColorArgb = obj.ballpointPenColorArgb | 0;
  }
  if (typeof obj.ballpointPenWidth === "number") out.ballpointPenWidth = clampNumber(obj.ballpointPenWidth, 0.2, 80);

  if (typeof obj.highlighterColorArgb === "number" && Number.isFinite(obj.highlighterColorArgb)) {
    out.highlighterColorArgb = obj.highlighterColorArgb | 0;
  }
  if (typeof obj.highlighterWidth === "number") out.highlighterWidth = clampNumber(obj.highlighterWidth, 1, 200);
  if (typeof obj.highlighterAlpha === "number") out.highlighterAlpha = clampNumber(obj.highlighterAlpha, 0, 1);

  if (obj.shapeMode === "line" || obj.shapeMode === "rectangle" || obj.shapeMode === "ellipse") out.shapeMode = obj.shapeMode;
  if (typeof obj.shapeColorArgb === "number" && Number.isFinite(obj.shapeColorArgb)) out.shapeColorArgb = obj.shapeColorArgb | 0;
  if (typeof obj.shapeWidth === "number") out.shapeWidth = clampNumber(obj.shapeWidth, 0.2, 80);

  if (typeof obj.eraserRadius === "number") out.eraserRadius = clampNumber(obj.eraserRadius, 1, 120);
  if (typeof obj.pageIndex === "number") out.pageIndex = clampInt(obj.pageIndex, 0, 10_000);

  const viewport = obj.viewport;
  if (viewport && typeof viewport === "object") {
    const v = viewport as Record<string, unknown>;
    const next: NonNullable<DrawHistory["viewport"]> = {};
    if (typeof v.scale === "number" && Number.isFinite(v.scale)) next.scale = clampNumber(v.scale, MIN_SCALE, MAX_SCALE);
    const point = parsePoint(v.translation);
    if (point) next.translation = point;
    if (next.scale != null || next.translation != null) out.viewport = next;
  }

  return out;
}

export function loadDrawHistory(path: string): DrawHistory | null {
  if (!path) return null;
  try {
    const raw = localStorage.getItem(storageKey(path));
    if (!raw) return null;
    return normalize(JSON.parse(raw));
  } catch {
    return null;
  }
}

export function saveDrawHistory(path: string, history: DrawHistory): void {
  if (!path) return;
  try {
    localStorage.setItem(storageKey(path), JSON.stringify(normalize(history)));
  } catch {
    // ignore
  }
}

export function moveDrawHistory(oldPath: string, nextPath: string): void {
  if (!oldPath || !nextPath || oldPath === nextPath) return;
  const oldKey = storageKey(oldPath);
  const nextKey = storageKey(nextPath);
  try {
    const raw = localStorage.getItem(oldKey);
    if (!raw) return;
    localStorage.setItem(nextKey, raw);
    localStorage.removeItem(oldKey);
  } catch {
    // ignore
  }
}

