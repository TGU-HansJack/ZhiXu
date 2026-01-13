import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { getCurrentWindow } from "@tauri-apps/api/window";
import { open } from "@tauri-apps/plugin-dialog";
import { PDFDocument } from "pdf-lib";
import type {
  DrawDocument,
  DrawElement,
  DrawPage,
  DrawPoint,
  DrawPressureCurve,
  DrawPressureMapping,
  DrawStrokePoint,
  DrawTiltMapping,
  DrawToolId,
  DrawViewMode,
} from "../draw/types";
import { cloneDocument } from "../draw/clone";
import { documentContentEqual } from "../draw/contentEqual";
import { elementBounds } from "../draw/bounds";
import { argbToRgba } from "../draw/color";
import { rectFromPoints } from "../draw/geometry";
import { centerPage, fitPageToWidth, MIN_SCALE, MAX_SCALE, snapViewport, zoomAroundViewPoint } from "../draw/viewportHelpers";
import { DrawEditorModel } from "../draw/editorModel";
import {
  EraserToolMachine,
  HighlighterToolMachine,
  LassoToolMachine,
  PanToolMachine,
  PenToolMachine,
  ShapeToolMachine,
  type DrawToolMachine,
  type ToolPointerEvent,
} from "../draw/tools";
import { elementsEqual } from "../draw/equal";
import { basename, dirname, join } from "../lib/path";
import { stripExtension } from "../lib/fileType";
import { createFile, saveFileDialog, writeBytesAbs, writeDrawDocument, writeDrawDocumentAbs } from "../lib/vaultApi";
import { Popover } from "./Popover";
import { Tooltip } from "./Tooltip";
import {
  IconAddCircle,
  IconArrowBack,
  IconChevronBack,
  IconChevronForward,
  IconCheckmark,
  IconLucideEraser,
  IconLucideHand,
  IconLucideHighlighter,
  IconLucideLasso,
  IconLucidePenTool,
  IconLucidePencil,
  IconLucidePyramid,
  IconMoreHorizontal,
  IconRedo,
  IconSave,
  IconTrash,
  IconUndo,
} from "./icons";

type UndoHistory = {
  undoStack: DrawElement[][];
  redoStack: DrawElement[][];
};

type Props = {
  path: string;
  doc: DrawDocument | null;
  savedDoc: DrawDocument | null;
  dirty: boolean;
  viewMode: DrawViewMode;
  onBack: () => void;
  onSave: () => void;
  onDeleteFile: () => void;
  onOpenFile: (path: string) => void;
  onUpdate: (patch: { doc?: DrawDocument; dirty?: boolean; viewMode?: DrawViewMode }) => void;
};

function clampToPage(editor: DrawEditorModel, pagePoint: DrawPoint): DrawPoint {
  const page = editor.currentPageOrNull();
  if (!page) return pagePoint;
  return [
    Math.min(page.width, Math.max(0, pagePoint[0])),
    Math.min(page.height, Math.max(0, pagePoint[1])),
  ];
}

function centroidOf(points: DrawPoint[]): DrawPoint {
  if (!points.length) return [0, 0];
  let x = 0;
  let y = 0;
  for (const p of points) {
    x += p[0];
    y += p[1];
  }
  return [x / points.length, y / points.length];
}

function spanOf(a: DrawPoint, b: DrawPoint): number {
  const dx = a[0] - b[0];
  const dy = a[1] - b[1];
  return Math.max(0, Math.hypot(dx, dy));
}

function isPenEraser(ev: PointerEvent): boolean {
  if (ev.pointerType !== "pen") return false;
  return (ev.buttons & 32) !== 0 || ev.button === 5;
}

function clamp01(v: number): number {
  if (!Number.isFinite(v)) return 0;
  return Math.min(1, Math.max(0, v));
}

function toolPointerExtras(ev: PointerEvent): Pick<ToolPointerEvent, "pointerType" | "pressure" | "tiltX" | "tiltY" | "twist"> {
  const pointerType = ev.pointerType || "mouse";
  const isPen = pointerType === "pen";
  const pressure = isPen ? clamp01(ev.pressure) : 1;
  const tiltX = isPen && Number.isFinite(ev.tiltX) ? ev.tiltX : 0;
  const tiltY = isPen && Number.isFinite(ev.tiltY) ? ev.tiltY : 0;
  const twist = isPen && "twist" in ev && Number.isFinite((ev as PointerEvent).twist) ? (ev as PointerEvent).twist : 0;
  return { pointerType, pressure, tiltX, tiltY, twist };
}

function drawStrokePoints(
  ctx: CanvasRenderingContext2D,
  points: readonly DrawStrokePoint[],
  colorArgb: number,
  fallbackWidth: number,
  fallbackAlpha: number,
) {
  if (!points.length) return;

  const baseColor = argbToRgba(colorArgb, 1);
  const baseWidth = Math.max(0.2, fallbackWidth);
  const baseAlpha = clamp01(fallbackAlpha);

  let hasRoundStyle = false;
  let hasFlatStyle = false;
  for (const p of points) {
    if (p.length >= 6) {
      hasFlatStyle = true;
      break;
    }
    if (p.length >= 4) hasRoundStyle = true;
  }

  ctx.save();
  ctx.fillStyle = baseColor;
  ctx.strokeStyle = baseColor;
  ctx.lineCap = "round";
  ctx.lineJoin = "round";

  if (!hasRoundStyle && !hasFlatStyle) {
    ctx.globalAlpha = baseAlpha;
    if (points.length === 1) {
      const p = points[0]!;
      ctx.beginPath();
      ctx.arc(p[0], p[1], baseWidth / 2, 0, Math.PI * 2);
      ctx.fill();
    } else {
      ctx.lineWidth = baseWidth;
      ctx.beginPath();
      ctx.moveTo(points[0]![0], points[0]![1]);
      for (let i = 1; i < points.length; i++) ctx.lineTo(points[i]![0], points[i]![1]);
      ctx.stroke();
    }
    ctx.restore();
    return;
  }

  if (hasFlatStyle) {
    const pointStyle = (p: DrawStrokePoint) => {
      const x = p[0];
      const y = p[1];
      if (p.length >= 6) {
        const rx = Number.isFinite(p[2]) ? Math.max(0.03, Math.abs(p[2]!)) : baseWidth / 2;
        const ry = Number.isFinite(p[3]) ? Math.max(0.03, Math.abs(p[3]!)) : baseWidth / 2;
        const rot = Number.isFinite(p[4]) ? p[4]! : 0;
        const alpha = Number.isFinite(p[5]) ? clamp01(p[5]!) : baseAlpha;
        return { x, y, rx, ry, rot, alpha };
      }
      const w = p.length >= 4 && Number.isFinite(p[2]) ? Math.max(0.2, p[2]!) : baseWidth;
      const a = p.length >= 4 && Number.isFinite(p[3]) ? clamp01(p[3]!) : baseAlpha;
      return { x, y, rx: w / 2, ry: w / 2, rot: 0, alpha: a };
    };

    const stamp = (x: number, y: number, rx: number, ry: number, rot: number, alpha: number) => {
      ctx.save();
      ctx.globalAlpha = clamp01(alpha);
      ctx.translate(x, y);
      if (rot) ctx.rotate(rot);
      ctx.beginPath();
      ctx.ellipse(0, 0, rx, ry, 0, 0, Math.PI * 2);
      ctx.fill();
      ctx.restore();
    };

    if (points.length === 1) {
      const p0 = pointStyle(points[0]!);
      stamp(p0.x, p0.y, p0.rx, p0.ry, p0.rot, p0.alpha);
      ctx.restore();
      return;
    }

    let prev = pointStyle(points[0]!);
    stamp(prev.x, prev.y, prev.rx, prev.ry, prev.rot, prev.alpha);

    for (let i = 1; i < points.length; i++) {
      const next = pointStyle(points[i]!);
      const dx = next.x - prev.x;
      const dy = next.y - prev.y;
      const dist = Math.hypot(dx, dy);
      const step = Math.max(0.5, Math.min(prev.rx, prev.ry, next.rx, next.ry) * 0.6);
      const n = Math.max(1, Math.ceil(dist / step));
      for (let j = 1; j <= n; j++) {
        const t = j / n;
        stamp(
          prev.x + dx * t,
          prev.y + dy * t,
          prev.rx + (next.rx - prev.rx) * t,
          prev.ry + (next.ry - prev.ry) * t,
          prev.rot + (next.rot - prev.rot) * t,
          prev.alpha + (next.alpha - prev.alpha) * t,
        );
      }
      prev = next;
    }

    ctx.restore();
    return;
  }

  const pointStyle = (p: DrawStrokePoint) => {
    const w = p.length >= 4 && Number.isFinite(p[2]) ? Math.max(0.2, p[2]!) : baseWidth;
    const a = p.length >= 4 && Number.isFinite(p[3]) ? clamp01(p[3]!) : baseAlpha;
    return { w, a };
  };

  if (points.length === 1) {
    const p = points[0]!;
    const s = pointStyle(p);
    ctx.globalAlpha = s.a;
    ctx.beginPath();
    ctx.arc(p[0], p[1], s.w / 2, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();
    return;
  }

  let prev = points[0]!;
  let prevStyle = pointStyle(prev);
  for (let i = 1; i < points.length; i++) {
    const next = points[i]!;
    const nextStyle = pointStyle(next);
    const w = (prevStyle.w + nextStyle.w) / 2;
    const a = (prevStyle.a + nextStyle.a) / 2;
    ctx.globalAlpha = a;
    ctx.lineWidth = w;
    ctx.beginPath();
    ctx.moveTo(prev[0], prev[1]);
    ctx.lineTo(next[0], next[1]);
    ctx.stroke();
    prev = next;
    prevStyle = nextStyle;
  }

  ctx.restore();
}

const DRAW_INK_SETTINGS_KEY = "zhixu.draw.inkSettings.v1";

type PersistedInkSettings = {
  pressureEnabled?: boolean;
  pressureMapping?: DrawPressureMapping;
  pressureCurve?: DrawPressureCurve;
  pressureCurveGamma?: number;
  tiltEnabled?: boolean;
  tiltMapping?: DrawTiltMapping;
};

function parseInkSettings(raw: unknown): PersistedInkSettings | null {
  if (!raw || typeof raw !== "object") return null;
  const o = raw as Record<string, unknown>;
  const out: PersistedInkSettings = {};
  if (typeof o.pressureEnabled === "boolean") out.pressureEnabled = o.pressureEnabled;
  if (o.pressureMapping === "width" || o.pressureMapping === "opacity" || o.pressureMapping === "both") {
    out.pressureMapping = o.pressureMapping;
  }
  if (o.pressureCurve === "linear" || o.pressureCurve === "soft" || o.pressureCurve === "hard" || o.pressureCurve === "custom") {
    out.pressureCurve = o.pressureCurve;
  }
  if (typeof o.pressureCurveGamma === "number" && Number.isFinite(o.pressureCurveGamma)) {
    out.pressureCurveGamma = o.pressureCurveGamma;
  }
  if (typeof o.tiltEnabled === "boolean") out.tiltEnabled = o.tiltEnabled;
  if (o.tiltMapping === "width" || o.tiltMapping === "angle" || o.tiltMapping === "shading") {
    out.tiltMapping = o.tiltMapping;
  }
  return out;
}

function loadInkSettings(): PersistedInkSettings | null {
  try {
    const raw = localStorage.getItem(DRAW_INK_SETTINGS_KEY);
    if (!raw) return null;
    return parseInkSettings(JSON.parse(raw));
  } catch {
    return null;
  }
}

function saveInkSettings(settings: PersistedInkSettings) {
  try {
    localStorage.setItem(DRAW_INK_SETTINGS_KEY, JSON.stringify(settings));
  } catch {}
}

function DrawToolbarButton({
  label,
  placement = "bottom",
  active,
  disabled,
  className,
  onClick,
  children,
}: React.PropsWithChildren<{
  label: string;
  placement?: React.ComponentProps<typeof Tooltip>["placement"];
  active?: boolean;
  disabled?: boolean;
  className?: string;
  onClick?: (ev: React.MouseEvent<HTMLButtonElement>) => void;
}>) {
  return (
    <Tooltip label={label} placement={placement}>
      <button
        type="button"
        aria-label={label}
        className={`drawToolBtn iconOnly${active ? " active" : ""}${className ? ` ${className}` : ""}`}
        onClick={onClick}
        disabled={disabled}
        data-no-drag="true"
      >
        {children}
      </button>
    </Tooltip>
  );
}

const DRAW_COLOR_SWATCHES: number[] = [
  0xff000000 | 0,
  0xff1e88e5 | 0,
  0xff43a047 | 0,
  0xfff4511e | 0,
  0xffe53935 | 0,
  0xff8e24aa | 0,
  0xfffdd835 | 0,
  0xffffffff | 0,
];

function DrawColorRow({
  selectedArgb,
  onPick,
}: {
  selectedArgb: number;
  onPick: (argb: number) => void;
}) {
  return (
    <div className="drawColorRow" data-no-drag="true">
      {DRAW_COLOR_SWATCHES.map((argb) => {
        const selected = (argb | 0) === (selectedArgb | 0);
        return (
          <button
            key={argb}
            type="button"
            className={`drawColorSwatch${selected ? " selected" : ""}`}
            style={{ backgroundColor: argbToRgba(argb, 1) }}
            onClick={() => onPick(argb)}
            aria-label="选择颜色"
            data-no-drag="true"
          />
        );
      })}
    </div>
  );
}

function DrawLabeledSlider({
  label,
  value,
  min,
  max,
  step,
  formatValue,
  onChange,
}: {
  label: string;
  value: number;
  min: number;
  max: number;
  step: number;
  formatValue?: (value: number) => string;
  onChange: (value: number) => void;
}) {
  const display = formatValue ? formatValue(value) : String(value);
  return (
    <div className="drawSlider" data-no-drag="true">
      <div className="drawSliderHeader">
        <span className="drawSliderLabel">{label}</span>
        <span className="drawSliderValue">{display}</span>
      </div>
      <input
        type="range"
        min={min}
        max={max}
        step={step}
        value={Number.isFinite(value) ? value : min}
        onChange={(ev) => onChange(Number.parseFloat(ev.currentTarget.value))}
        data-no-drag="true"
      />
    </div>
  );
}

export function ZhixuDrawEditor({ path, doc, savedDoc, dirty, viewMode, onBack, onSave, onDeleteFile, onOpenFile, onUpdate }: Props) {
  const canvasWrapRef = useRef<HTMLDivElement | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const canvasMetricsRef = useRef<{ cssWidth: number; cssHeight: number; scaleX: number; scaleY: number }>({
    cssWidth: 0,
    cssHeight: 0,
    scaleX: 1,
    scaleY: 1,
  });
  const dprRef = useRef<number>(typeof window !== "undefined" ? (window.devicePixelRatio || 1) : 1);
  const syncCanvasSizeRef = useRef<(() => void) | null>(null);
  const editorRef = useRef<DrawEditorModel | null>(null);
  const historiesRef = useRef<Record<string, UndoHistory>>({});
  const didInitViewportRef = useRef(false);
  const rafRef = useRef<number | null>(null);
  const pointersRef = useRef<Map<number, DrawPoint>>(new Map());
  const syncCanvasToDom = useCallback((): boolean => {
    const canvas = canvasRef.current;
    const editor = editorRef.current;
    if (!canvas || !editor) return false;

    const rect = canvas.getBoundingClientRect();
    const w = rect.width;
    const h = rect.height;
    if (!Number.isFinite(w) || !Number.isFinite(h) || w < 2 || h < 2) return false;

    const dpr = Number.isFinite(dprRef.current) && dprRef.current > 0 ? dprRef.current : 1;
    const nextW = Math.max(1, Math.round(w * dpr));
    const nextH = Math.max(1, Math.round(h * dpr));
    const resized = canvas.width !== nextW || canvas.height !== nextH;
    if (canvas.width !== nextW) canvas.width = nextW;
    if (canvas.height !== nextH) canvas.height = nextH;

    canvasMetricsRef.current = {
      cssWidth: w,
      cssHeight: h,
      scaleX: canvas.width / w,
      scaleY: canvas.height / h,
    };

    editor.viewport.viewportSize = { width: w, height: h };
    if (!didInitViewportRef.current) {
      didInitViewportRef.current = true;
      fitPageToWidth(editor, 24);
    } else if (resized) {
      snapViewport(editor, 24);
    }

    return true;
  }, []);
  const inputRef = useRef<{
    activeTool: DrawToolMachine | null;
    modifiesDocument: boolean;
    activePointerId: number | null;
    lastViewPos: DrawPoint;
    lastPagePos: DrawPoint;
    isTransform: boolean;
    prevCentroid: DrawPoint;
    prevSpan: number;
  }>({
    activeTool: null,
    modifiesDocument: false,
    activePointerId: null,
    lastViewPos: [0, 0],
    lastPagePos: [0, 0],
    isTransform: false,
    prevCentroid: [0, 0],
    prevSpan: 0,
  });

  const [canUndo, setCanUndo] = useState(false);
  const [canRedo, setCanRedo] = useState(false);
  const [, forceUiTick] = useState(0);
  const [toolPopoverOpen, setToolPopoverOpen] = useState(false);
  const [toolPopoverAnchor, setToolPopoverAnchor] = useState<HTMLElement | null>(null);
  const [morePopoverOpen, setMorePopoverOpen] = useState(false);
  const [morePopoverAnchor, setMorePopoverAnchor] = useState<HTMLElement | null>(null);
  const [exporting, setExporting] = useState(false);

  const tools = useMemo(() => {
    const pen = new PenToolMachine();
    const highlighter = new HighlighterToolMachine();
    const shape = new ShapeToolMachine();
    const lasso = new LassoToolMachine();
    const eraser = new EraserToolMachine();
    const pan = new PanToolMachine();

    const toolFor = (id: DrawToolId): DrawToolMachine => {
      switch (id) {
        case "pen":
          return pen;
        case "highlighter":
          return highlighter;
        case "shape":
          return shape;
        case "lasso":
          return lasso;
        case "eraser":
          return eraser;
        case "pan":
          return pan;
      }
    };

    return { pen, highlighter, shape, lasso, eraser, pan, toolFor };
  }, []);

  const requestCanvasRender = useCallback(() => {
    if (rafRef.current != null) return;
    rafRef.current = window.requestAnimationFrame(() => {
      rafRef.current = null;
      const canvas = canvasRef.current;
      const editor = editorRef.current;
      if (!canvas || !editor) return;

      syncCanvasToDom();

      const ctx = canvas.getContext("2d");
      if (!ctx) return;

      const { cssWidth, cssHeight, scaleX, scaleY } = canvasMetricsRef.current;
      const dpr = Number.isFinite(dprRef.current) && dprRef.current > 0 ? dprRef.current : 1;
      const cssW = cssWidth > 0 ? cssWidth : canvas.width / Math.max(1, dpr);
      const cssH = cssHeight > 0 ? cssHeight : canvas.height / Math.max(1, dpr);
      const sx = Number.isFinite(scaleX) && scaleX > 0 ? scaleX : dpr;
      const sy = Number.isFinite(scaleY) && scaleY > 0 ? scaleY : dpr;

      // Clear in device pixels to avoid edge artifacts/ghosting when cssW/cssH are fractional.
      ctx.setTransform(1, 0, 0, 1, 0, 0);
      ctx.clearRect(0, 0, canvas.width, canvas.height);

      ctx.setTransform(sx, 0, 0, sy, 0, 0);

      const canvasBackground = "rgba(0,0,0,0.04)";
      ctx.fillStyle = canvasBackground;
      ctx.fillRect(0, 0, cssW, cssH);

      const page = editor.currentPageOrNull();
      if (!page) return;

      const scale = Math.max(0.0001, editor.viewport.scale);
      const tx = editor.viewport.translation[0];
      const ty = editor.viewport.translation[1];

      ctx.save();
      ctx.translate(tx, ty);
      ctx.scale(scale, scale);

      ctx.fillStyle = argbToRgba(page.backgroundColorArgb, 1);
      ctx.fillRect(0, 0, page.width, page.height);

      ctx.strokeStyle = "#DDDDDD";
      ctx.lineWidth = 1 / scale;
      ctx.strokeRect(0, 0, page.width, page.height);

      const selectionColor = "rgba(47,111,235,0.9)";
      for (const el of page.elements) {
        if (el.type === "stroke") {
          drawStrokePoints(ctx, el.points, el.colorArgb, el.width, el.alpha);
        } else {
          const alpha = Math.min(1, Math.max(0, el.alpha));
          const color = argbToRgba(el.colorArgb, alpha);
          const w = Math.max(0.2, el.width);
          ctx.strokeStyle = color;
          ctx.lineWidth = w;
          ctx.lineCap = "round";
          ctx.lineJoin = "round";
          const rect = rectFromPoints(el.start, el.end);
          if (el.shape === "line") {
            ctx.beginPath();
            ctx.moveTo(el.start[0], el.start[1]);
            ctx.lineTo(el.end[0], el.end[1]);
            ctx.stroke();
          } else if (el.shape === "rectangle") {
            ctx.strokeRect(rect.left, rect.top, rect.right - rect.left, rect.bottom - rect.top);
          } else {
            const cx = (rect.left + rect.right) / 2;
            const cy = (rect.top + rect.bottom) / 2;
            const rx = Math.max(0.001, (rect.right - rect.left) / 2);
            const ry = Math.max(0.001, (rect.bottom - rect.top) / 2);
            ctx.beginPath();
            ctx.ellipse(cx, cy, rx, ry, 0, 0, Math.PI * 2);
            ctx.stroke();
          }
        }

        if (editor.selectedElementIds.has(el.id)) {
          const bounds = elementBounds(el);
          if (bounds) {
            ctx.save();
            ctx.strokeStyle = selectionColor;
            ctx.lineWidth = 1.5 / scale;
            ctx.setLineDash([8 / scale, 8 / scale]);
            ctx.strokeRect(bounds.left, bounds.top, bounds.right - bounds.left, bounds.bottom - bounds.top);
            ctx.restore();
          }
        }
      }

      // Overlays
      if (editor.previewShape) {
        const preview = editor.previewShape;
        const color = argbToRgba(editor.shapeColorArgb, 0.9);
        const w = Math.max(0.2, editor.shapeWidth);
        const rect = rectFromPoints(preview.start, preview.end);
        ctx.save();
        ctx.strokeStyle = color;
        ctx.lineWidth = w;
        ctx.setLineDash([10 / scale, 10 / scale]);
        if (preview.mode === "line") {
          ctx.beginPath();
          ctx.moveTo(preview.start[0], preview.start[1]);
          ctx.lineTo(preview.end[0], preview.end[1]);
          ctx.stroke();
        } else if (preview.mode === "rectangle") {
          ctx.strokeRect(rect.left, rect.top, rect.right - rect.left, rect.bottom - rect.top);
        } else {
          const cx = (rect.left + rect.right) / 2;
          const cy = (rect.top + rect.bottom) / 2;
          const rx = Math.max(0.001, (rect.right - rect.left) / 2);
          const ry = Math.max(0.001, (rect.bottom - rect.top) / 2);
          ctx.beginPath();
          ctx.ellipse(cx, cy, rx, ry, 0, 0, Math.PI * 2);
          ctx.stroke();
        }
        ctx.restore();
      }

      if (editor.lassoPathPoints.length >= 2) {
        ctx.save();
        ctx.strokeStyle = "rgba(168,85,247,0.9)";
        ctx.lineWidth = 1.6 / scale;
        ctx.setLineDash([10 / scale, 8 / scale]);
        ctx.beginPath();
        ctx.moveTo(editor.lassoPathPoints[0]![0], editor.lassoPathPoints[0]![1]);
        for (let i = 1; i < editor.lassoPathPoints.length; i++) {
          ctx.lineTo(editor.lassoPathPoints[i]![0], editor.lassoPathPoints[i]![1]);
        }
        ctx.stroke();
        ctx.restore();
      }

      if (editor.eraserCursor) {
        ctx.save();
        ctx.strokeStyle = "rgba(217,44,44,0.85)";
        ctx.lineWidth = 1.5 / scale;
        ctx.beginPath();
        ctx.arc(editor.eraserCursor[0], editor.eraserCursor[1], Math.max(1, editor.eraserRadius), 0, Math.PI * 2);
        ctx.stroke();
        ctx.restore();
      }

      if (editor.previewStroke && editor.previewStroke.points.length) {
        const preview = editor.previewStroke;
        const fallback =
          preview.tool === "highlighter"
            ? { width: editor.highlighterWidth, alpha: editor.highlighterAlpha }
            : { width: editor.currentPenWidth, alpha: 1 };
        drawStrokePoints(ctx, preview.points, preview.colorArgb, fallback.width, fallback.alpha);
      }

      ctx.restore();
    });
  }, []);

  const ensureHistoryForCurrentPage = useCallback((): UndoHistory | null => {
    const editor = editorRef.current;
    if (!editor) return null;
    const page = editor.currentPageOrNull();
    if (!page) return null;
    let history = historiesRef.current[page.id];
    if (!history) {
      history = { undoStack: [editor.snapshotPageElements()], redoStack: [] };
      historiesRef.current[page.id] = history;
    }
    if (history.undoStack.length === 0) history.undoStack.push(editor.snapshotPageElements());
    return history;
  }, []);

  const refreshUndoRedoAvailability = useCallback(() => {
    const history = ensureHistoryForCurrentPage();
    setCanUndo((history?.undoStack.length ?? 0) > 1);
    setCanRedo((history?.redoStack.length ?? 0) > 0);
  }, [ensureHistoryForCurrentPage]);

  const publishDoc = useCallback(() => {
    const editor = editorRef.current;
    if (!editor) return;
    const nextDoc = cloneDocument(editor.doc);
    const dirty = savedDoc ? !documentContentEqual(nextDoc, savedDoc) : true;
    onUpdate({ doc: nextDoc, dirty });
  }, [onUpdate, savedDoc]);

  const commitEdit = useCallback(() => {
    const editor = editorRef.current;
    if (!editor) return;
    const history = ensureHistoryForCurrentPage();
    if (!history) return;
    const snapshot = editor.snapshotPageElements();
    const last = history.undoStack.length ? history.undoStack[history.undoStack.length - 1]! : null;
    if (last && elementsEqual(last, snapshot)) {
      refreshUndoRedoAvailability();
      return;
    }
    editor.doc.meta.modifiedAtMs = Date.now();
    if (history.undoStack.length >= 100) history.undoStack.shift();
    history.undoStack.push(snapshot);
    history.redoStack = [];
    refreshUndoRedoAvailability();
    publishDoc();
  }, [ensureHistoryForCurrentPage, publishDoc, refreshUndoRedoAvailability]);

  const toolMachineFor = useCallback(
    (id: DrawToolId): DrawToolMachine => tools.toolFor(id),
    [tools],
  );

  useEffect(() => {
    if (typeof window === "undefined") return;
    if (!(window as any).__TAURI__) return;

    const appWindow = getCurrentWindow();
    let unlisten: (() => void) | null = null;
    let disposed = false;

    const setDpr = (next: number) => {
      if (!Number.isFinite(next) || next <= 0) return;
      if (dprRef.current === next) return;
      dprRef.current = next;
      syncCanvasSizeRef.current?.();
      requestCanvasRender();
    };

    appWindow
      .scaleFactor()
      .then((factor) => {
        if (disposed) return;
        setDpr(factor);
      })
      .catch(() => {});

    appWindow
      .onScaleChanged(({ payload }) => setDpr(payload.scaleFactor))
      .then((fn) => {
        unlisten = fn;
      })
      .catch(() => {});

    return () => {
      disposed = true;
      unlisten?.();
    };
  }, [requestCanvasRender]);

  const applyToolId = useCallback(
    (next: DrawToolId) => {
      const editor = editorRef.current;
      if (!editor) return;
      editor.toolId = next;
      editor.clearOverlaysAndSelection();
      requestCanvasRender();
      forceUiTick((n) => n + 1);
    },
    [requestCanvasRender],
  );

  const handleToolDockClick = useCallback(
    (id: DrawToolId, anchorEl: HTMLButtonElement) => {
      const editor = editorRef.current;
      if (!editor) return;

      setMorePopoverOpen(false);

      if (editor.viewMode !== "writing") {
        if (id === "pan") applyToolId("pan");
        return;
      }

      if (id === "lasso" || id === "pan") {
        applyToolId(id);
        setToolPopoverOpen(false);
        return;
      }

      if (editor.toolId === id) {
        if (toolPopoverOpen && toolPopoverAnchor === anchorEl) {
          setToolPopoverOpen(false);
        } else {
          setToolPopoverAnchor(anchorEl);
          setToolPopoverOpen(true);
        }
        return;
      }

      applyToolId(id);
      setToolPopoverOpen(false);
    },
    [applyToolId, toolPopoverAnchor, toolPopoverOpen],
  );

  const undo = useCallback(() => {
    const editor = editorRef.current;
    if (!editor) return;
    const history = ensureHistoryForCurrentPage();
    if (!history) return;
    if (history.undoStack.length <= 1) return;
    const current = history.undoStack.pop()!;
    history.redoStack.push(current);
    const previous = history.undoStack[history.undoStack.length - 1]!;
    editor.restorePageElements(previous);
    editor.doc.meta.modifiedAtMs = Date.now();
    refreshUndoRedoAvailability();
    publishDoc();
    requestCanvasRender();
    forceUiTick((n) => n + 1);
  }, [ensureHistoryForCurrentPage, publishDoc, refreshUndoRedoAvailability, requestCanvasRender]);

  const redo = useCallback(() => {
    const editor = editorRef.current;
    if (!editor) return;
    const history = ensureHistoryForCurrentPage();
    if (!history) return;
    if (!history.redoStack.length) return;
    const next = history.redoStack.pop()!;
    history.undoStack.push(next);
    editor.restorePageElements(next);
    editor.doc.meta.modifiedAtMs = Date.now();
    refreshUndoRedoAvailability();
    publishDoc();
    requestCanvasRender();
    forceUiTick((n) => n + 1);
  }, [ensureHistoryForCurrentPage, publishDoc, refreshUndoRedoAvailability, requestCanvasRender]);

  // Init editor state once per mount/doc load.
  useEffect(() => {
    if (!doc) return;
    if (editorRef.current) return;
    const editor = new DrawEditorModel(cloneDocument(doc));
    editor.ensureHasAtLeastOnePage();
    editor.viewMode = viewMode;

    const ink = loadInkSettings();
    if (ink) {
      if (typeof ink.pressureEnabled === "boolean") editor.pressureEnabled = ink.pressureEnabled;
      if (ink.pressureMapping) editor.pressureMapping = ink.pressureMapping;
      if (ink.pressureCurve) editor.pressureCurve = ink.pressureCurve;
      if (typeof ink.pressureCurveGamma === "number") editor.pressureCurveGamma = Math.min(3, Math.max(0.2, ink.pressureCurveGamma));
      if (typeof ink.tiltEnabled === "boolean") editor.tiltEnabled = ink.tiltEnabled;
      if (ink.tiltMapping) editor.tiltMapping = ink.tiltMapping;
    }

    editorRef.current = editor;
    historiesRef.current = {};
    didInitViewportRef.current = false;
    refreshUndoRedoAvailability();
    requestCanvasRender();
    forceUiTick((n) => n + 1);
  }, [doc, refreshUndoRedoAvailability, requestCanvasRender, viewMode]);

  // Keep viewMode in sync when changed externally.
  useEffect(() => {
    const editor = editorRef.current;
    if (!editor) return;
    if (editor.viewMode === viewMode) return;
    editor.viewMode = viewMode;
    if (viewMode === "reading") {
      editor.toolId = "pan";
      editor.clearOverlaysAndSelection();
    }
    requestCanvasRender();
    forceUiTick((n) => n + 1);
  }, [requestCanvasRender, viewMode]);

  useEffect(() => {
    const canvas = canvasRef.current;
    const editor = editorRef.current;
    if (!canvas || !editor) return;

    const syncSize = () => {
      syncCanvasToDom();
      requestCanvasRender();
    };

    const ro = new ResizeObserver(syncSize);
    ro.observe(canvas);
    syncCanvasSizeRef.current = syncSize;
    syncSize();
    const raf = window.requestAnimationFrame(syncSize);
    window.addEventListener("resize", syncSize);
    return () => {
      syncCanvasSizeRef.current = null;
      ro.disconnect();
      window.removeEventListener("resize", syncSize);
      window.cancelAnimationFrame(raf);
    };
  }, [doc, requestCanvasRender, syncCanvasToDom]);

  const handlePointerDown = useCallback(
    (ev: React.PointerEvent<HTMLCanvasElement>) => {
      const canvas = canvasRef.current;
      const editor = editorRef.current;
      if (!canvas || !editor) return;
      if (ev.button !== 0 && !isPenEraser(ev.nativeEvent)) return;

      try {
        canvas.setPointerCapture(ev.pointerId);
      } catch (_) {}
      const rect = canvas.getBoundingClientRect();
      const viewPos: DrawPoint = [ev.clientX - rect.left, ev.clientY - rect.top];
      pointersRef.current.set(ev.pointerId, viewPos);

      const input = inputRef.current;
      const pressedPoints = [...pointersRef.current.values()];
      if (pressedPoints.length >= 2) {
        if (!input.isTransform) {
          input.isTransform = true;
          input.activeTool?.onCancel(editor);
          input.activeTool = null;
          input.activePointerId = null;
          input.modifiesDocument = false;
          editor.previewShape = null;
          editor.previewStroke = null;
          editor.eraserCursor = null;
          editor.lassoPathPoints = [];
          input.prevCentroid = centroidOf(pressedPoints);
          input.prevSpan = spanOf(pressedPoints[0]!, pressedPoints[1]!);
        }
        requestCanvasRender();
        return;
      }

      const pickedTool: DrawToolMachine = (() => {
        if (editor.viewMode === "reading") return toolMachineFor("pan");
        if (isPenEraser(ev.nativeEvent)) return toolMachineFor("eraser");
        return toolMachineFor(editor.toolId);
      })();

      input.activeTool = pickedTool;
      input.modifiesDocument = ["pen", "highlighter", "shape", "eraser"].includes(pickedTool.id);
      input.activePointerId = ev.pointerId;
      input.lastViewPos = viewPos;
      input.lastPagePos = clampToPage(editor, editor.viewport.viewToPage(viewPos));
      const native = ev.nativeEvent as PointerEvent;
      const toolEvent: ToolPointerEvent = {
        viewPosition: viewPos,
        viewDelta: [0, 0],
        pagePosition: input.lastPagePos,
        pageDelta: [0, 0],
        ...toolPointerExtras(native),
      };
      pickedTool.onDown(editor, toolEvent);
      requestCanvasRender();
    },
    [requestCanvasRender, toolMachineFor],
  );

  const handlePointerMove = useCallback(
    (ev: React.PointerEvent<HTMLCanvasElement>) => {
      const canvas = canvasRef.current;
      const editor = editorRef.current;
      if (!canvas || !editor) return;
      if (!pointersRef.current.has(ev.pointerId)) return;

      const input = inputRef.current;

      const rect = canvas.getBoundingClientRect();
      const viewPos: DrawPoint = [ev.clientX - rect.left, ev.clientY - rect.top];
      pointersRef.current.set(ev.pointerId, viewPos);

      const pressedPoints = [...pointersRef.current.values()];

      if (input.isTransform && pressedPoints.length >= 2) {
        const currCentroid = centroidOf(pressedPoints);
        const currSpan = spanOf(pressedPoints[0]!, pressedPoints[1]!);
        const zoomChange = input.prevSpan > 0 ? currSpan / input.prevSpan : 1;

        const oldScale = Math.max(0.0001, editor.viewport.scale);
        const oldTranslation = editor.viewport.translation;
        const newScale = Math.min(MAX_SCALE, Math.max(MIN_SCALE, oldScale * zoomChange));

        const pageUnderPrev: DrawPoint = [
          (input.prevCentroid[0] - oldTranslation[0]) / oldScale,
          (input.prevCentroid[1] - oldTranslation[1]) / oldScale,
        ];
        editor.viewport.scale = newScale;
        editor.viewport.translation = [
          currCentroid[0] - pageUnderPrev[0] * newScale,
          currCentroid[1] - pageUnderPrev[1] * newScale,
        ];

        input.prevCentroid = currCentroid;
        input.prevSpan = currSpan;
        requestCanvasRender();
        return;
      }

      if (input.isTransform) return;
      if (input.activePointerId !== ev.pointerId) return;
      if (!input.activeTool) return;

      const native = ev.nativeEvent as PointerEvent;
      const samples: PointerEvent[] =
        typeof native.getCoalescedEvents === "function" && native.getCoalescedEvents().length
          ? native.getCoalescedEvents()
          : [native];

      for (const sample of samples) {
        const sampleViewPos: DrawPoint = [sample.clientX - rect.left, sample.clientY - rect.top];
        pointersRef.current.set(ev.pointerId, sampleViewPos);

        const viewDelta: DrawPoint = [sampleViewPos[0] - input.lastViewPos[0], sampleViewPos[1] - input.lastViewPos[1]];
        const pagePos = clampToPage(editor, editor.viewport.viewToPage(sampleViewPos));
        const pageDelta: DrawPoint = [pagePos[0] - input.lastPagePos[0], pagePos[1] - input.lastPagePos[1]];

        input.activeTool.onMove(editor, { viewPosition: sampleViewPos, viewDelta, pagePosition: pagePos, pageDelta, ...toolPointerExtras(sample) });
        input.lastViewPos = sampleViewPos;
        input.lastPagePos = pagePos;
      }
      requestCanvasRender();
    },
    [requestCanvasRender],
  );

  const endPointer = useCallback(
    (ev: React.PointerEvent<HTMLCanvasElement>, kind: "up" | "cancel") => {
      const canvas = canvasRef.current;
      const editor = editorRef.current;
      if (!canvas || !editor) return;
      pointersRef.current.delete(ev.pointerId);
      try {
        canvas.releasePointerCapture(ev.pointerId);
      } catch (_) {}

      const input = inputRef.current;

      if (input.isTransform) {
        if (pointersRef.current.size < 2) {
          input.isTransform = false;
          input.activeTool = null;
          input.activePointerId = null;
          input.modifiesDocument = false;
          snapViewport(editor, 24);
          pointersRef.current.clear();
          requestCanvasRender();
        }
        return;
      }

      if (input.activePointerId !== ev.pointerId) return;
      const tool = input.activeTool;
      if (!tool) return;

      const rect = canvas.getBoundingClientRect();
      const viewPos: DrawPoint = [ev.clientX - rect.left, ev.clientY - rect.top];
      const viewDelta: DrawPoint = [viewPos[0] - input.lastViewPos[0], viewPos[1] - input.lastViewPos[1]];
      const pagePos = clampToPage(editor, editor.viewport.viewToPage(viewPos));
      const pageDelta: DrawPoint = [pagePos[0] - input.lastPagePos[0], pagePos[1] - input.lastPagePos[1]];
      const native = ev.nativeEvent as PointerEvent;
      const toolEvent: ToolPointerEvent = { viewPosition: viewPos, viewDelta, pagePosition: pagePos, pageDelta, ...toolPointerExtras(native) };

      if (kind === "cancel") {
        tool.onCancel(editor);
      } else {
        tool.onUp(editor, toolEvent);
        if (input.modifiesDocument) commitEdit();
        if (tool.id === "pan") snapViewport(editor, 24);
      }

      input.activeTool = null;
      input.activePointerId = null;
      input.modifiesDocument = false;
      requestCanvasRender();
    },
    [commitEdit, requestCanvasRender],
  );

  const handleWheel = useCallback(
    (ev: WheelEvent) => {
      const canvas = canvasRef.current;
      const editor = editorRef.current;
      if (!canvas || !editor) return;

      ev.preventDefault();

      if (ev.ctrlKey || ev.metaKey || ev.altKey) {
        const rect = canvas.getBoundingClientRect();
        const viewPos: DrawPoint = [ev.clientX - rect.left, ev.clientY - rect.top];
        const factor = Math.exp(-ev.deltaY * 0.001);
        zoomAroundViewPoint(editor, viewPos, editor.viewport.scale * factor, 24);
        requestCanvasRender();
        return;
      }

      editor.viewport.translation = [
        editor.viewport.translation[0] - ev.deltaX,
        editor.viewport.translation[1] - ev.deltaY,
      ];
      snapViewport(editor, 24);
      requestCanvasRender();
    },
    [requestCanvasRender],
  );

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    canvas.addEventListener("wheel", handleWheel, { passive: false });
    return () => {
      canvas.removeEventListener("wheel", handleWheel);
    };
  }, [handleWheel]);

  const deleteSelection = useCallback(() => {
    const editor = editorRef.current;
    if (!editor) return;
    const page = editor.currentPageOrNull();
    if (!page) return;
    const selected = editor.selectedElementIds;
    if (!selected.size) return;
    page.elements = page.elements.filter((el) => !selected.has(el.id));
    editor.selectedElementIds = new Set();
    commitEdit();
    requestCanvasRender();
    forceUiTick((n) => n + 1);
  }, [commitEdit, requestCanvasRender]);

  const clearCurrentPage = useCallback(() => {
    const editor = editorRef.current;
    if (!editor) return;
    if (editor.viewMode !== "writing") return;
    const ok = window.confirm("确定清空当前页面？");
    if (!ok) return;
    const page = editor.currentPageOrNull();
    if (!page) return;
    page.elements = [];
    editor.clearOverlaysAndSelection();
    commitEdit();
    requestCanvasRender();
    forceUiTick((n) => n + 1);
  }, [commitEdit, requestCanvasRender]);

  const goPrevPage = useCallback(() => {
    const editor = editorRef.current;
    if (!editor) return;
    editor.goToPreviousPage();
    centerPage(editor, 24);
    refreshUndoRedoAvailability();
    requestCanvasRender();
    forceUiTick((n) => n + 1);
  }, [refreshUndoRedoAvailability, requestCanvasRender]);

  const goNextPage = useCallback(() => {
    const editor = editorRef.current;
    if (!editor) return;
    editor.goToNextPage();
    centerPage(editor, 24);
    refreshUndoRedoAvailability();
    requestCanvasRender();
    forceUiTick((n) => n + 1);
  }, [refreshUndoRedoAvailability, requestCanvasRender]);

  const addPage = useCallback(() => {
    const editor = editorRef.current;
    if (!editor) return;
    if (editor.viewMode !== "writing") return;
    editor.addPageLikeCurrent();
    centerPage(editor, 24);
    editor.doc.meta.modifiedAtMs = Date.now();
    refreshUndoRedoAvailability();
    publishDoc();
    requestCanvasRender();
    forceUiTick((n) => n + 1);
  }, [publishDoc, refreshUndoRedoAvailability, requestCanvasRender]);

  const insertPageAfterCurrent = useCallback(() => {
    const editor = editorRef.current;
    if (!editor) return;
    if (editor.viewMode !== "writing") return;
    editor.insertPageAfterCurrent();
    centerPage(editor, 24);
    editor.doc.meta.modifiedAtMs = Date.now();
    refreshUndoRedoAvailability();
    publishDoc();
    requestCanvasRender();
    forceUiTick((n) => n + 1);
  }, [publishDoc, refreshUndoRedoAvailability, requestCanvasRender]);

  const rotateCurrentPage90 = useCallback(() => {
    const editor = editorRef.current;
    if (!editor) return;
    if (editor.viewMode !== "writing") return;
    const pageId = editor.currentPageOrNull()?.id;
    editor.rotateCurrentPage90Degrees();
    centerPage(editor, 24);
    if (pageId) delete historiesRef.current[pageId];
    editor.doc.meta.modifiedAtMs = Date.now();
    refreshUndoRedoAvailability();
    publishDoc();
    requestCanvasRender();
    forceUiTick((n) => n + 1);
  }, [publishDoc, refreshUndoRedoAvailability, requestCanvasRender]);

  const updateCurrentPageBackground = useCallback(
    (argb: number) => {
      const editor = editorRef.current;
      if (!editor) return;
      if (editor.viewMode !== "writing") return;
      const page = editor.currentPageOrNull();
      if (!page) return;
      page.backgroundColorArgb = argb;
      editor.doc.meta.modifiedAtMs = Date.now();
      publishDoc();
      requestCanvasRender();
      forceUiTick((n) => n + 1);
    },
    [publishDoc, requestCanvasRender],
  );

  const setViewMode = useCallback(
    (next: DrawViewMode) => {
      setToolPopoverOpen(false);
      setMorePopoverOpen(false);
      onUpdate({ viewMode: next });
    },
    [onUpdate],
  );

  const saveAs = useCallback(async () => {
    const editor = editorRef.current;
    if (!editor) return;
    const base = stripExtension(basename(path)) || "Drawing";
    const defaultName = join(dirname(path), `${base}_copy.zhixu`);
    const next = window.prompt("另存为（库内相对路径）：", defaultName);
    if (!next) return;
    try {
      await createFile(next);
      await writeDrawDocument(next, cloneDocument(editor.doc));
      onOpenFile(next);
      setToolPopoverOpen(false);
    } catch (e) {
      console.error(e);
      window.alert(String(e));
    }
  }, [onOpenFile, path]);

  const renderPageToPngBytes = useCallback(async (page: DrawPage, desiredScale: number = 2, maxDimPx: number = 2048): Promise<Uint8Array> => {
    const w = Math.max(1, page.width);
    const h = Math.max(1, page.height);
    const scale = Math.max(0.1, Math.min(desiredScale, maxDimPx / w, maxDimPx / h));
    const outW = Math.max(1, Math.round(w * scale));
    const outH = Math.max(1, Math.round(h * scale));
    const canvas = document.createElement("canvas");
    canvas.width = outW;
    canvas.height = outH;
    const ctx = canvas.getContext("2d");
    if (!ctx) throw new Error("Canvas not supported");
    ctx.scale(scale, scale);

    ctx.fillStyle = argbToRgba(page.backgroundColorArgb, 1);
    ctx.fillRect(0, 0, w, h);

    for (const el of page.elements) {
      if (el.type === "stroke") {
        drawStrokePoints(ctx, el.points, el.colorArgb, el.width, el.alpha);
      } else {
        const alpha = Math.min(1, Math.max(0, el.alpha));
        const color = argbToRgba(el.colorArgb, alpha);
        const strokeW = Math.max(0.2, el.width);
        ctx.strokeStyle = color;
        ctx.lineWidth = strokeW;
        ctx.lineCap = "round";
        ctx.lineJoin = "round";
        const rect = rectFromPoints(el.start, el.end);
        if (el.shape === "line") {
          ctx.beginPath();
          ctx.moveTo(el.start[0], el.start[1]);
          ctx.lineTo(el.end[0], el.end[1]);
          ctx.stroke();
        } else if (el.shape === "rectangle") {
          ctx.strokeRect(rect.left, rect.top, rect.right - rect.left, rect.bottom - rect.top);
        } else {
          const cx = (rect.left + rect.right) / 2;
          const cy = (rect.top + rect.bottom) / 2;
          const rx = Math.max(0.001, (rect.right - rect.left) / 2);
          const ry = Math.max(0.001, (rect.bottom - rect.top) / 2);
          ctx.beginPath();
          ctx.ellipse(cx, cy, rx, ry, 0, 0, Math.PI * 2);
          ctx.stroke();
        }
      }
    }

    const blob: Blob = await new Promise((resolve, reject) => {
      canvas.toBlob((b) => (b ? resolve(b) : reject(new Error("Failed to create PNG"))), "image/png");
    });
    const buf = await blob.arrayBuffer();
    return new Uint8Array(buf);
  }, []);

  const exportOriginal = useCallback(async () => {
    const editor = editorRef.current;
    if (!editor || exporting) return;
    setExporting(true);
    try {
      const base = stripExtension(basename(path)) || "Drawing";
      const target = await saveFileDialog({ title: "导出原件", defaultPath: `${base}.zhixu`, filters: [{ name: "Zhixu", extensions: ["zhixu"] }] });
      if (!target) return;
      await writeDrawDocumentAbs(target, cloneDocument(editor.doc));
      setMorePopoverOpen(false);
    } catch (e) {
      console.error(e);
      window.alert(String(e));
    } finally {
      setExporting(false);
    }
  }, [exporting, path]);

  const exportImages = useCallback(async () => {
    const editor = editorRef.current;
    if (!editor || exporting) return;
    setExporting(true);
    try {
      const dir = await open({ directory: true, multiple: false, title: "选择导出目录" });
      if (typeof dir !== "string") return;
      const base = stripExtension(basename(path)) || "Drawing";
      const cleanDir = dir.replace(/[\\\\/]+$/, "");
      const sep = cleanDir.includes("\\\\") ? "\\\\" : "/";
      for (let i = 0; i < editor.doc.pages.length; i++) {
        const page = editor.doc.pages[i]!;
        const bytes = await renderPageToPngBytes(page);
        const name = `${base}_${i + 1}.png`;
        const outPath = `${cleanDir}${sep}${name}`;
        await writeBytesAbs(outPath, Array.from(bytes));
      }
      setMorePopoverOpen(false);
    } catch (e) {
      console.error(e);
      window.alert(String(e));
    } finally {
      setExporting(false);
    }
  }, [exporting, path, renderPageToPngBytes]);

  const exportPdf = useCallback(async () => {
    const editor = editorRef.current;
    if (!editor || exporting) return;
    setExporting(true);
    try {
      const base = stripExtension(basename(path)) || "Drawing";
      const target = await saveFileDialog({ title: "导出 PDF", defaultPath: `${base}.pdf`, filters: [{ name: "PDF", extensions: ["pdf"] }] });
      if (!target) return;

      const pdf = await PDFDocument.create();
      for (const page of editor.doc.pages) {
        const pngBytes = await renderPageToPngBytes(page);
        const image = await pdf.embedPng(pngBytes);
        const pdfPage = pdf.addPage([Math.max(1, page.width), Math.max(1, page.height)]);
        pdfPage.drawImage(image, { x: 0, y: 0, width: Math.max(1, page.width), height: Math.max(1, page.height) });
      }
      const pdfBytes = await pdf.save();
      await writeBytesAbs(target, Array.from(pdfBytes));
      setMorePopoverOpen(false);
    } catch (e) {
      console.error(e);
      window.alert(String(e));
    } finally {
      setExporting(false);
    }
  }, [exporting, path, renderPageToPngBytes]);

  const editor = editorRef.current;
  if (!doc) return <div className="emptyState">正在加载绘图…</div>;
  if (!editor) return <div className="emptyState">正在初始化绘图编辑器…</div>;

  const activeTool = editor.viewMode === "reading" ? "pan" : editor.toolId;
  const canEdit = editor.viewMode === "writing";

  const penIcon = editor.penStyle === "fountainPen" ? <IconLucidePenTool size={18} /> : <IconLucidePencil size={18} />;
  const penColor = argbToRgba(editor.currentPenColorArgb, 1);
  const highlighterColor = argbToRgba(editor.highlighterColorArgb, 1);
  const shapeColor = argbToRgba(editor.shapeColorArgb, 1);

  return (
    <div className="drawEditor">
      <div className="drawToolbar noDrag" data-no-drag="true">
        <div className="drawToolbarGroup">
          <DrawToolbarButton label="返回" onClick={onBack}>
            <IconArrowBack size={18} />
          </DrawToolbarButton>
          <DrawToolbarButton label="撤销" onClick={undo} disabled={!canEdit || !canUndo}>
            <IconUndo size={18} />
          </DrawToolbarButton>
          <DrawToolbarButton label="重做" onClick={redo} disabled={!canEdit || !canRedo}>
            <IconRedo size={18} />
          </DrawToolbarButton>
        </div>

        <div className="drawToolbarGroup">
          <DrawToolbarButton label="笔" active={activeTool === "pen"} disabled={!canEdit} onClick={(ev) => handleToolDockClick("pen", ev.currentTarget)}>
            <span style={{ color: penColor }}>{penIcon}</span>
          </DrawToolbarButton>
          <DrawToolbarButton
            label="荧光笔"
            active={activeTool === "highlighter"}
            disabled={!canEdit}
            onClick={(ev) => handleToolDockClick("highlighter", ev.currentTarget)}
          >
            <span style={{ color: highlighterColor }}>
              <IconLucideHighlighter size={18} />
            </span>
          </DrawToolbarButton>
          <DrawToolbarButton label="形状" active={activeTool === "shape"} disabled={!canEdit} onClick={(ev) => handleToolDockClick("shape", ev.currentTarget)}>
            <span style={{ color: shapeColor }}>
              <IconLucidePyramid size={18} />
            </span>
          </DrawToolbarButton>
          <DrawToolbarButton label="套索" active={activeTool === "lasso"} disabled={!canEdit} onClick={(ev) => handleToolDockClick("lasso", ev.currentTarget)}>
            <IconLucideLasso size={18} />
          </DrawToolbarButton>
          <DrawToolbarButton label="橡皮" active={activeTool === "eraser"} disabled={!canEdit} onClick={(ev) => handleToolDockClick("eraser", ev.currentTarget)}>
            <IconLucideEraser size={18} />
          </DrawToolbarButton>
          <DrawToolbarButton label="拖动" active={activeTool === "pan"} onClick={(ev) => handleToolDockClick("pan", ev.currentTarget)}>
            <IconLucideHand size={18} />
          </DrawToolbarButton>
        </div>

        <span className="drawToolbarSpacer" />

        <div className="drawToolbarGroup">
          <DrawToolbarButton label="清空" onClick={clearCurrentPage} disabled={!canEdit}>
            <IconTrash size={18} />
          </DrawToolbarButton>
          <DrawToolbarButton label="保存" onClick={onSave} disabled={!dirty}>
            <IconSave size={18} />
          </DrawToolbarButton>
          <DrawToolbarButton
            label="更多"
            onClick={(ev) => {
              setToolPopoverOpen(false);
              setMorePopoverAnchor(ev.currentTarget);
              setMorePopoverOpen((v) => !v);
            }}
          >
            <IconMoreHorizontal size={18} />
          </DrawToolbarButton>
        </div>
      </div>

      <Popover open={toolPopoverOpen && canEdit} anchorEl={toolPopoverAnchor} placement="bottom-start" onClose={() => setToolPopoverOpen(false)}>
        <div className="drawPopover">
          {editor.toolId === "pen" ? (
            <div className="drawPenStyleHeader">
              <button
                type="button"
                className={`drawPenStyleOption${editor.penStyle === "fountainPen" ? " selected" : ""}`}
                onClick={() => {
                  editor.penStyle = "fountainPen";
                  requestCanvasRender();
                  forceUiTick((n) => n + 1);
                }}
                data-no-drag="true"
              >
                <span className="drawPenStyleLabel">钢笔</span>
                <span style={{ color: argbToRgba(editor.fountainPenColorArgb, 1) }}>
                  <IconLucidePenTool size={22} />
                </span>
              </button>
              <button
                type="button"
                className={`drawPenStyleOption${editor.penStyle === "ballpointPen" ? " selected" : ""}`}
                onClick={() => {
                  editor.penStyle = "ballpointPen";
                  requestCanvasRender();
                  forceUiTick((n) => n + 1);
                }}
                data-no-drag="true"
              >
                <span className="drawPenStyleLabel">圆珠笔</span>
                <span style={{ color: argbToRgba(editor.ballpointPenColorArgb, 1) }}>
                  <IconLucidePencil size={22} />
                </span>
              </button>
            </div>
          ) : (
            <div className="drawPopoverTitle">
              {editor.toolId === "highlighter"
                ? "荧光笔"
                : editor.toolId === "shape"
                  ? "形状"
                  : editor.toolId === "lasso"
                    ? "套索"
                    : editor.toolId === "eraser"
                      ? "橡皮"
                      : "拖动"}
            </div>
          )}

          {editor.toolId === "shape" ? (
            <div className="drawModeChips" data-no-drag="true">
              <button
                type="button"
                className={`drawModeChip${editor.shapeMode === "line" ? " active" : ""}`}
                onClick={() => {
                  editor.shapeMode = "line";
                  forceUiTick((n) => n + 1);
                }}
                data-no-drag="true"
              >
                线
              </button>
              <button
                type="button"
                className={`drawModeChip${editor.shapeMode === "rectangle" ? " active" : ""}`}
                onClick={() => {
                  editor.shapeMode = "rectangle";
                  forceUiTick((n) => n + 1);
                }}
                data-no-drag="true"
              >
                矩形
              </button>
              <button
                type="button"
                className={`drawModeChip${editor.shapeMode === "ellipse" ? " active" : ""}`}
                onClick={() => {
                  editor.shapeMode = "ellipse";
                  forceUiTick((n) => n + 1);
                }}
                data-no-drag="true"
              >
                椭圆
              </button>
            </div>
          ) : null}

          {editor.toolId === "pen" || editor.toolId === "highlighter" || editor.toolId === "shape" ? (
            <DrawColorRow
              selectedArgb={
                editor.toolId === "pen"
                  ? editor.currentPenColorArgb
                  : editor.toolId === "highlighter"
                    ? editor.highlighterColorArgb
                    : editor.shapeColorArgb
              }
              onPick={(argb) => {
                if (editor.toolId === "pen") editor.currentPenColorArgb = argb;
                else if (editor.toolId === "highlighter") editor.highlighterColorArgb = argb;
                else if (editor.toolId === "shape") editor.shapeColorArgb = argb;
                requestCanvasRender();
                forceUiTick((n) => n + 1);
              }}
            />
          ) : null}

          {editor.toolId === "pen" ? (
            <>
              <DrawLabeledSlider
                label="线条粗细"
                value={editor.currentPenWidth}
                min={0.5}
                max={18}
                step={0.1}
                formatValue={(v) => v.toFixed(1)}
                onChange={(v) => {
                  editor.currentPenWidth = v;
                  forceUiTick((n) => n + 1);
                }}
              />

              <div className="drawPopoverDivider" />

              <div className="drawPopoverSectionTitle">压感</div>
              <button
                type="button"
                className="drawPopoverItem"
                onClick={() => {
                  editor.pressureEnabled = !editor.pressureEnabled;
                  saveInkSettings({
                    pressureEnabled: editor.pressureEnabled,
                    pressureMapping: editor.pressureMapping,
                    pressureCurve: editor.pressureCurve,
                    pressureCurveGamma: editor.pressureCurveGamma,
                    tiltEnabled: editor.tiltEnabled,
                    tiltMapping: editor.tiltMapping,
                  });
                  forceUiTick((n) => n + 1);
                }}
                data-no-drag="true"
              >
                <span>启用压感</span>
                {editor.pressureEnabled ? <IconCheckmark size={18} /> : null}
              </button>

              <div className="drawPopoverSubTitle">压感映射</div>
              <div className="drawModeChips" data-no-drag="true">
                <button
                  type="button"
                  className={`drawModeChip${editor.pressureMapping === "width" ? " active" : ""}`}
                  onClick={() => {
                    editor.pressureMapping = "width";
                    saveInkSettings({
                      pressureEnabled: editor.pressureEnabled,
                      pressureMapping: editor.pressureMapping,
                      pressureCurve: editor.pressureCurve,
                      pressureCurveGamma: editor.pressureCurveGamma,
                      tiltEnabled: editor.tiltEnabled,
                      tiltMapping: editor.tiltMapping,
                    });
                    forceUiTick((n) => n + 1);
                  }}
                >
                  压力 → 笔宽
                </button>
                <button
                  type="button"
                  className={`drawModeChip${editor.pressureMapping === "opacity" ? " active" : ""}`}
                  onClick={() => {
                    editor.pressureMapping = "opacity";
                    saveInkSettings({
                      pressureEnabled: editor.pressureEnabled,
                      pressureMapping: editor.pressureMapping,
                      pressureCurve: editor.pressureCurve,
                      pressureCurveGamma: editor.pressureCurveGamma,
                      tiltEnabled: editor.tiltEnabled,
                      tiltMapping: editor.tiltMapping,
                    });
                    forceUiTick((n) => n + 1);
                  }}
                >
                  压力 → 不透明度
                </button>
                <button
                  type="button"
                  className={`drawModeChip${editor.pressureMapping === "both" ? " active" : ""}`}
                  onClick={() => {
                    editor.pressureMapping = "both";
                    saveInkSettings({
                      pressureEnabled: editor.pressureEnabled,
                      pressureMapping: editor.pressureMapping,
                      pressureCurve: editor.pressureCurve,
                      pressureCurveGamma: editor.pressureCurveGamma,
                      tiltEnabled: editor.tiltEnabled,
                      tiltMapping: editor.tiltMapping,
                    });
                    forceUiTick((n) => n + 1);
                  }}
                >
                  压力 → 笔宽 + 不透明度
                </button>
              </div>

              <div className="drawPopoverSubTitle">压感曲线</div>
              <div className="drawModeChips" data-no-drag="true">
                <button
                  type="button"
                  className={`drawModeChip${editor.pressureCurve === "linear" ? " active" : ""}`}
                  onClick={() => {
                    editor.pressureCurve = "linear";
                    saveInkSettings({
                      pressureEnabled: editor.pressureEnabled,
                      pressureMapping: editor.pressureMapping,
                      pressureCurve: editor.pressureCurve,
                      pressureCurveGamma: editor.pressureCurveGamma,
                      tiltEnabled: editor.tiltEnabled,
                      tiltMapping: editor.tiltMapping,
                    });
                    forceUiTick((n) => n + 1);
                  }}
                >
                  线性
                </button>
                <button
                  type="button"
                  className={`drawModeChip${editor.pressureCurve === "soft" ? " active" : ""}`}
                  onClick={() => {
                    editor.pressureCurve = "soft";
                    saveInkSettings({
                      pressureEnabled: editor.pressureEnabled,
                      pressureMapping: editor.pressureMapping,
                      pressureCurve: editor.pressureCurve,
                      pressureCurveGamma: editor.pressureCurveGamma,
                      tiltEnabled: editor.tiltEnabled,
                      tiltMapping: editor.tiltMapping,
                    });
                    forceUiTick((n) => n + 1);
                  }}
                >
                  软
                </button>
                <button
                  type="button"
                  className={`drawModeChip${editor.pressureCurve === "hard" ? " active" : ""}`}
                  onClick={() => {
                    editor.pressureCurve = "hard";
                    saveInkSettings({
                      pressureEnabled: editor.pressureEnabled,
                      pressureMapping: editor.pressureMapping,
                      pressureCurve: editor.pressureCurve,
                      pressureCurveGamma: editor.pressureCurveGamma,
                      tiltEnabled: editor.tiltEnabled,
                      tiltMapping: editor.tiltMapping,
                    });
                    forceUiTick((n) => n + 1);
                  }}
                >
                  硬
                </button>
                <button
                  type="button"
                  className={`drawModeChip${editor.pressureCurve === "custom" ? " active" : ""}`}
                  onClick={() => {
                    editor.pressureCurve = "custom";
                    saveInkSettings({
                      pressureEnabled: editor.pressureEnabled,
                      pressureMapping: editor.pressureMapping,
                      pressureCurve: editor.pressureCurve,
                      pressureCurveGamma: editor.pressureCurveGamma,
                      tiltEnabled: editor.tiltEnabled,
                      tiltMapping: editor.tiltMapping,
                    });
                    forceUiTick((n) => n + 1);
                  }}
                >
                  自定义
                </button>
              </div>

              {editor.pressureCurve === "custom" ? (
                <DrawLabeledSlider
                  label="曲线指数"
                  value={editor.pressureCurveGamma}
                  min={0.2}
                  max={3}
                  step={0.05}
                  formatValue={(v) => v.toFixed(2)}
                  onChange={(v) => {
                    editor.pressureCurveGamma = v;
                    saveInkSettings({
                      pressureEnabled: editor.pressureEnabled,
                      pressureMapping: editor.pressureMapping,
                      pressureCurve: editor.pressureCurve,
                      pressureCurveGamma: editor.pressureCurveGamma,
                      tiltEnabled: editor.tiltEnabled,
                      tiltMapping: editor.tiltMapping,
                    });
                    forceUiTick((n) => n + 1);
                  }}
                />
              ) : null}

              <div className="drawPopoverDivider" />

              <div className="drawPopoverSectionTitle">倾斜（Tilt）</div>
              <button
                type="button"
                className="drawPopoverItem"
                onClick={() => {
                  editor.tiltEnabled = !editor.tiltEnabled;
                  saveInkSettings({
                    pressureEnabled: editor.pressureEnabled,
                    pressureMapping: editor.pressureMapping,
                    pressureCurve: editor.pressureCurve,
                    pressureCurveGamma: editor.pressureCurveGamma,
                    tiltEnabled: editor.tiltEnabled,
                    tiltMapping: editor.tiltMapping,
                  });
                  forceUiTick((n) => n + 1);
                }}
                data-no-drag="true"
              >
                <span>启用倾斜</span>
                {editor.tiltEnabled ? <IconCheckmark size={18} /> : null}
              </button>

              <div className="drawPopoverSubTitle">倾斜映射</div>
              <div className="drawModeChips" data-no-drag="true">
                <button
                  type="button"
                  className={`drawModeChip${editor.tiltMapping === "width" ? " active" : ""}`}
                  onClick={() => {
                    editor.tiltMapping = "width";
                    saveInkSettings({
                      pressureEnabled: editor.pressureEnabled,
                      pressureMapping: editor.pressureMapping,
                      pressureCurve: editor.pressureCurve,
                      pressureCurveGamma: editor.pressureCurveGamma,
                      tiltEnabled: editor.tiltEnabled,
                      tiltMapping: editor.tiltMapping,
                    });
                    forceUiTick((n) => n + 1);
                  }}
                >
                  倾斜 → 笔宽
                </button>
                <button
                  type="button"
                  className={`drawModeChip${editor.tiltMapping === "angle" ? " active" : ""}`}
                  onClick={() => {
                    editor.tiltMapping = "angle";
                    saveInkSettings({
                      pressureEnabled: editor.pressureEnabled,
                      pressureMapping: editor.pressureMapping,
                      pressureCurve: editor.pressureCurve,
                      pressureCurveGamma: editor.pressureCurveGamma,
                      tiltEnabled: editor.tiltEnabled,
                      tiltMapping: editor.tiltMapping,
                    });
                    forceUiTick((n) => n + 1);
                  }}
                >
                  倾斜 → 笔刷角度
                </button>
                <button
                  type="button"
                  className={`drawModeChip${editor.tiltMapping === "shading" ? " active" : ""}`}
                  onClick={() => {
                    editor.tiltMapping = "shading";
                    saveInkSettings({
                      pressureEnabled: editor.pressureEnabled,
                      pressureMapping: editor.pressureMapping,
                      pressureCurve: editor.pressureCurve,
                      pressureCurveGamma: editor.pressureCurveGamma,
                      tiltEnabled: editor.tiltEnabled,
                      tiltMapping: editor.tiltMapping,
                    });
                    forceUiTick((n) => n + 1);
                  }}
                >
                  倾斜 → 阴影 / 扁平刷
                </button>
              </div>
            </>
          ) : editor.toolId === "shape" ? (
            <DrawLabeledSlider
              label="线条粗细"
              value={editor.shapeWidth}
              min={0.5}
              max={18}
              step={0.1}
              formatValue={(v) => v.toFixed(1)}
              onChange={(v) => {
                editor.shapeWidth = v;
                forceUiTick((n) => n + 1);
              }}
            />
          ) : editor.toolId === "highlighter" ? (
            <>
              <DrawLabeledSlider
                label="线条粗细"
                value={editor.highlighterWidth}
                min={4}
                max={42}
                step={0.5}
                formatValue={(v) => v.toFixed(1)}
                onChange={(v) => {
                  editor.highlighterWidth = v;
                  forceUiTick((n) => n + 1);
                }}
              />
              <DrawLabeledSlider
                label="透明度"
                value={editor.highlighterAlpha}
                min={0.05}
                max={0.9}
                step={0.01}
                formatValue={(v) => v.toFixed(2)}
                onChange={(v) => {
                  editor.highlighterAlpha = v;
                  forceUiTick((n) => n + 1);
                }}
              />
            </>
          ) : editor.toolId === "eraser" ? (
            <DrawLabeledSlider
              label="橡皮大小"
              value={editor.eraserRadius}
              min={4}
              max={64}
              step={1}
              formatValue={(v) => v.toFixed(0)}
              onChange={(v) => {
                editor.eraserRadius = v;
                forceUiTick((n) => n + 1);
              }}
            />
          ) : null}

          <div className="drawPopoverDivider" />

          <div className="drawPopoverRow" data-no-drag="true">
            <DrawToolbarButton label="上一页" placement="top" disabled={editor.currentPageIndex <= 0} onClick={goPrevPage}>
              <IconChevronBack size={18} />
            </DrawToolbarButton>
            <span className="drawPageIndicator">
              {editor.doc.pages.length ? `${editor.currentPageIndex + 1} / ${editor.doc.pages.length}` : "0 / 0"}
            </span>
            <DrawToolbarButton
              label="下一页"
              placement="top"
              disabled={editor.currentPageIndex >= editor.doc.pages.length - 1}
              onClick={goNextPage}
            >
              <IconChevronForward size={18} />
            </DrawToolbarButton>
            <DrawToolbarButton label="添加页" placement="top" onClick={addPage}>
              <IconAddCircle size={18} />
            </DrawToolbarButton>
            <span className="drawPopoverSpacer" />
            <button type="button" className="drawToolBtn" onClick={() => void saveAs()} data-no-drag="true">
              另存为
            </button>
          </div>
        </div>
      </Popover>

      <Popover open={morePopoverOpen} anchorEl={morePopoverAnchor} placement="bottom-end" onClose={() => setMorePopoverOpen(false)}>
        <div className="drawPopover">
          <div className="drawPopoverSectionTitle">页面</div>
          <button
            type="button"
            className="drawPopoverItem"
            disabled={!canEdit}
            onClick={() => {
              insertPageAfterCurrent();
              setMorePopoverOpen(false);
            }}
            data-no-drag="true"
          >
            插入一页（后一页）
          </button>

          <div className="drawPopoverSubTitle">背景颜色</div>
          <DrawColorRow
            selectedArgb={editor.currentPageOrNull()?.backgroundColorArgb ?? (0xffffffff | 0)}
            onPick={(argb) => {
              updateCurrentPageBackground(argb);
              setMorePopoverOpen(false);
            }}
          />

          <button
            type="button"
            className="drawPopoverItem"
            disabled={!canEdit}
            onClick={() => {
              rotateCurrentPage90();
              setMorePopoverOpen(false);
            }}
            data-no-drag="true"
          >
            页面旋转（90°）
          </button>

          <button
            type="button"
            className="drawPopoverItem danger"
            disabled={!canEdit || editor.selectedElementIds.size === 0}
            onClick={() => {
              deleteSelection();
              setMorePopoverOpen(false);
            }}
            data-no-drag="true"
          >
            删除选中
          </button>

          <div className="drawPopoverDivider" />

          <div className="drawPopoverSectionTitle">视图</div>
          <button
            type="button"
            className="drawPopoverItem"
            onClick={() => setViewMode("writing")}
            data-no-drag="true"
          >
            <span>书写模式</span>
            {viewMode === "writing" ? <IconCheckmark size={18} /> : null}
          </button>
          <button
            type="button"
            className="drawPopoverItem"
            onClick={() => setViewMode("reading")}
            data-no-drag="true"
          >
            <span>阅读模式</span>
            {viewMode === "reading" ? <IconCheckmark size={18} /> : null}
          </button>

          <div className="drawPopoverDivider" />

          <div className="drawPopoverSectionTitle">文件</div>
          <button
            type="button"
            className="drawPopoverItem"
            disabled={exporting}
            onClick={() => {
              setMorePopoverOpen(false);
              void exportPdf();
            }}
            data-no-drag="true"
          >
            导出 PDF
          </button>
          <button
            type="button"
            className="drawPopoverItem"
            disabled={exporting}
            onClick={() => {
              setMorePopoverOpen(false);
              void exportImages();
            }}
            data-no-drag="true"
          >
            导出图片
          </button>
          <button
            type="button"
            className="drawPopoverItem"
            disabled={exporting}
            onClick={() => {
              setMorePopoverOpen(false);
              void exportOriginal();
            }}
            data-no-drag="true"
          >
            导出原件
          </button>
          <button
            type="button"
            className="drawPopoverItem"
            onClick={() => {
              setMorePopoverOpen(false);
              void saveAs();
            }}
            data-no-drag="true"
          >
            另存为（库内）
          </button>
          <button
            type="button"
            className="drawPopoverItem danger"
            onClick={() => {
              setMorePopoverOpen(false);
              onDeleteFile();
            }}
            data-no-drag="true"
          >
            删除文件
          </button>
        </div>
      </Popover>

      <div ref={canvasWrapRef} className="drawCanvasWrap">
        <canvas
          ref={canvasRef}
          className="drawCanvas"
          onPointerDown={handlePointerDown}
          onPointerMove={handlePointerMove}
          onPointerUp={(ev) => endPointer(ev, "up")}
          onPointerCancel={(ev) => endPointer(ev, "cancel")}
        />
      </div>
    </div>
  );
}
