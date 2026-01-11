import type { DrawElement, DrawPoint, DrawStrokeElement } from "./types";
import type { DrawEditorModel, PreviewStroke } from "./editorModel";
import { distanceSquared, lerp, pointInPolygon } from "./geometry";
import { newElementId } from "./id";

export type ToolPointerEvent = {
  viewPosition: DrawPoint;
  viewDelta: DrawPoint;
  pagePosition: DrawPoint;
  pageDelta: DrawPoint;
};

export interface DrawToolMachine {
  id: string;
  onDown(editor: DrawEditorModel, e: ToolPointerEvent): void;
  onMove(editor: DrawEditorModel, e: ToolPointerEvent): void;
  onUp(editor: DrawEditorModel, e: ToolPointerEvent): void;
  onCancel(editor: DrawEditorModel): void;
}

export class PenToolMachine implements DrawToolMachine {
  id = "pen";
  private activeStrokeId: string | null = null;
  private pendingStrokeIds: string[] = [];

  onDown(editor: DrawEditorModel, e: ToolPointerEvent) {
    this.pendingStrokeIds = [];
    this.activeStrokeId = null;
    this.handlePointer(editor, e);
  }

  onMove(editor: DrawEditorModel, e: ToolPointerEvent) {
    this.handlePointer(editor, e);
  }

  onUp(editor: DrawEditorModel, _e: ToolPointerEvent) {
    editor.previewStroke = null;
    this.markPendingStrokesComplete(editor);
    this.pendingStrokeIds = [];
    this.activeStrokeId = null;
  }

  onCancel(editor: DrawEditorModel) {
    editor.previewStroke = null;
    this.removePendingStrokes(editor);
    this.pendingStrokeIds = [];
    this.activeStrokeId = null;
  }

  private handlePointer(editor: DrawEditorModel, e: ToolPointerEvent) {
    const page = editor.currentPageOrNull();
    if (!page) return;
    const rawPagePos = editor.viewport.viewToPage(e.viewPosition);

    if (isInPage(page.width, page.height, rawPagePos)) {
      editor.previewStroke = null;
      const strokeId = this.activeStrokeId;
      if (!strokeId) {
        const newStrokeId = newElementId();
        page.elements.push({
          type: "stroke",
          id: newStrokeId,
          tool: "pen",
          colorArgb: editor.currentPenColorArgb,
          width: editor.currentPenWidth,
          alpha: 1,
          points: [clampToPage(page.width, page.height, rawPagePos)],
        });
        this.pendingStrokeIds.push(newStrokeId);
        this.activeStrokeId = newStrokeId;
        return;
      }

      const stroke = findLastStrokeById(page.elements, strokeId);
      if (!stroke) return;
      stroke.points.push(clampToPage(page.width, page.height, rawPagePos));
      return;
    }

    this.activeStrokeId = null;
    pushPreviewPoint(editor, {
      tool: "pen",
      colorArgb: editor.currentPenColorArgb,
      width: editor.currentPenWidth,
      alpha: 1,
      points: [],
    }, rawPagePos);
  }

  private markPendingStrokesComplete(_editor: DrawEditorModel) {
    // No-op on desktop: we don't track in-progress vs complete in the persisted format.
  }

  private removePendingStrokes(editor: DrawEditorModel) {
    const page = editor.currentPageOrNull();
    if (!page) return;
    if (!this.pendingStrokeIds.length) return;
    const pending = new Set(this.pendingStrokeIds);
    page.elements = page.elements.filter((el) => !(el.type === "stroke" && pending.has(el.id)));
  }
}

export class HighlighterToolMachine implements DrawToolMachine {
  id = "highlighter";
  private activeStrokeId: string | null = null;
  private pendingStrokeIds: string[] = [];

  onDown(editor: DrawEditorModel, e: ToolPointerEvent) {
    this.pendingStrokeIds = [];
    this.activeStrokeId = null;
    this.handlePointer(editor, e);
  }

  onMove(editor: DrawEditorModel, e: ToolPointerEvent) {
    this.handlePointer(editor, e);
  }

  onUp(editor: DrawEditorModel, _e: ToolPointerEvent) {
    editor.previewStroke = null;
    this.pendingStrokeIds = [];
    this.activeStrokeId = null;
  }

  onCancel(editor: DrawEditorModel) {
    editor.previewStroke = null;
    this.removePendingStrokes(editor);
    this.pendingStrokeIds = [];
    this.activeStrokeId = null;
  }

  private handlePointer(editor: DrawEditorModel, e: ToolPointerEvent) {
    const page = editor.currentPageOrNull();
    if (!page) return;
    const rawPagePos = editor.viewport.viewToPage(e.viewPosition);

    if (isInPage(page.width, page.height, rawPagePos)) {
      editor.previewStroke = null;
      const strokeId = this.activeStrokeId;
      if (!strokeId) {
        const newStrokeId = newElementId();
        page.elements.push({
          type: "stroke",
          id: newStrokeId,
          tool: "highlighter",
          colorArgb: editor.highlighterColorArgb,
          width: editor.highlighterWidth,
          alpha: editor.highlighterAlpha,
          points: [clampToPage(page.width, page.height, rawPagePos)],
        });
        this.pendingStrokeIds.push(newStrokeId);
        this.activeStrokeId = newStrokeId;
        return;
      }

      const stroke = findLastStrokeById(page.elements, strokeId);
      if (!stroke) return;
      stroke.points.push(clampToPage(page.width, page.height, rawPagePos));
      return;
    }

    this.activeStrokeId = null;
    pushPreviewPoint(editor, {
      tool: "highlighter",
      colorArgb: editor.highlighterColorArgb,
      width: editor.highlighterWidth,
      alpha: editor.highlighterAlpha,
      points: [],
    }, rawPagePos);
  }

  private removePendingStrokes(editor: DrawEditorModel) {
    const page = editor.currentPageOrNull();
    if (!page) return;
    if (!this.pendingStrokeIds.length) return;
    const pending = new Set(this.pendingStrokeIds);
    page.elements = page.elements.filter((el) => !(el.type === "stroke" && pending.has(el.id)));
  }
}

export class ShapeToolMachine implements DrawToolMachine {
  id = "shape";
  private start: DrawPoint | null = null;

  onDown(editor: DrawEditorModel, e: ToolPointerEvent) {
    this.start = e.pagePosition;
    editor.previewShape = { mode: editor.shapeMode, start: e.pagePosition, end: e.pagePosition };
  }

  onMove(editor: DrawEditorModel, e: ToolPointerEvent) {
    if (!this.start) return;
    editor.previewShape = { mode: editor.shapeMode, start: this.start, end: e.pagePosition };
  }

  onUp(editor: DrawEditorModel, e: ToolPointerEvent) {
    const page = editor.currentPageOrNull();
    if (!page || !this.start) return;
    page.elements.push({
      type: "shape",
      id: newElementId(),
      shape: editor.shapeMode,
      colorArgb: editor.shapeColorArgb,
      width: editor.shapeWidth,
      alpha: 1,
      start: this.start,
      end: e.pagePosition,
    });
    editor.previewShape = null;
    this.start = null;
  }

  onCancel(editor: DrawEditorModel) {
    editor.previewShape = null;
    this.start = null;
  }
}

export class LassoToolMachine implements DrawToolMachine {
  id = "lasso";
  constructor(private readonly minPercentInside: number = 0.7) {}

  onDown(editor: DrawEditorModel, e: ToolPointerEvent) {
    editor.lassoPathPoints = [e.pagePosition];
    editor.selectedElementIds = new Set();
  }

  onMove(editor: DrawEditorModel, e: ToolPointerEvent) {
    editor.lassoPathPoints.push(e.pagePosition);
  }

  onUp(editor: DrawEditorModel, e: ToolPointerEvent) {
    editor.lassoPathPoints.push(e.pagePosition);
    const polygon = editor.lassoPathPoints.slice();
    const page = editor.currentPageOrNull();
    if (!page) {
      editor.lassoPathPoints = [];
      return;
    }
    if (polygon.length < 3) {
      editor.lassoPathPoints = [];
      return;
    }

    const selected = new Set<string>();
    for (const el of page.elements) {
      if (el.type === "stroke") {
        if (!el.points.length) continue;
        let inside = 0;
        for (const p of el.points) if (pointInPolygon(p, polygon)) inside++;
        const ratio = inside / Math.max(1, el.points.length);
        if (ratio >= this.minPercentInside) selected.add(el.id);
      } else {
        const start = el.start;
        const end = el.end;
        const center: DrawPoint = [(start[0] + end[0]) / 2, (start[1] + end[1]) / 2];
        const candidates = [start, end, center];
        let inside = 0;
        for (const p of candidates) if (pointInPolygon(p, polygon)) inside++;
        if (inside >= 2) selected.add(el.id);
      }
    }

    editor.selectedElementIds = selected;
    editor.lassoPathPoints = [];
  }

  onCancel(editor: DrawEditorModel) {
    editor.lassoPathPoints = [];
  }
}

export class EraserToolMachine implements DrawToolMachine {
  id = "eraser";
  private lastPoint: DrawPoint | null = null;

  onDown(editor: DrawEditorModel, e: ToolPointerEvent) {
    this.lastPoint = e.pagePosition;
    editor.eraserCursor = e.pagePosition;
    this.eraseAt(editor, e.pagePosition);
  }

  onMove(editor: DrawEditorModel, e: ToolPointerEvent) {
    const prev = this.lastPoint ?? e.pagePosition;
    const next = e.pagePosition;
    editor.eraserCursor = next;

    const r = Math.max(1, editor.eraserRadius);
    const step = Math.max(2, r * 0.6);
    const dist = Math.sqrt(distanceSquared(prev, next));
    const n = Math.max(1, Math.ceil(dist / step));
    for (let i = 1; i <= n; i++) {
      const t = i / n;
      this.eraseAt(editor, lerp(prev, next, t));
    }

    this.lastPoint = next;
  }

  onUp(editor: DrawEditorModel, _e: ToolPointerEvent) {
    editor.eraserCursor = null;
    this.lastPoint = null;
  }

  onCancel(editor: DrawEditorModel) {
    editor.eraserCursor = null;
    this.lastPoint = null;
  }

  private eraseAt(editor: DrawEditorModel, at: DrawPoint) {
    const page = editor.currentPageOrNull();
    if (!page) return;
    const r = Math.max(1, editor.eraserRadius);
    const r2 = r * r;

    let index = 0;
    while (index < page.elements.length) {
      const el = page.elements[index]!;
      if (el.type === "stroke") {
        const pts = el.points;
        if (!pts.length) {
          index += 1;
          continue;
        }
        const segments = clipStrokePoints(pts, at, r2);
        if (segments.length === 1 && segments[0]!.length === pts.length) {
          index += 1;
          continue;
        }
        page.elements.splice(index, 1);
        for (let segIndex = 0; segIndex < segments.length; segIndex++) {
          const seg = segments[segIndex]!;
          if (!seg.length) continue;
          page.elements.splice(index + segIndex, 0, {
            type: "stroke",
            id: newElementId(),
            tool: el.tool,
            colorArgb: el.colorArgb,
            width: el.width,
            alpha: el.alpha,
            points: seg,
          });
        }
        index += Math.max(0, segments.length);
        continue;
      }

      const minX = Math.min(el.start[0], el.end[0]);
      const maxX = Math.max(el.start[0], el.end[0]);
      const minY = Math.min(el.start[1], el.end[1]);
      const maxY = Math.max(el.start[1], el.end[1]);
      const cx = Math.min(maxX, Math.max(minX, at[0]));
      const cy = Math.min(maxY, Math.max(minY, at[1]));
      if (distanceSquared([cx, cy], at) <= r2) {
        page.elements.splice(index, 1);
      } else {
        index += 1;
      }
    }
  }
}

export class PanToolMachine implements DrawToolMachine {
  id = "pan";
  onDown(_editor: DrawEditorModel, _e: ToolPointerEvent) {}
  onMove(editor: DrawEditorModel, e: ToolPointerEvent) {
    editor.viewport.translation = [editor.viewport.translation[0] + e.viewDelta[0], editor.viewport.translation[1] + e.viewDelta[1]];
  }
  onUp(_editor: DrawEditorModel, _e: ToolPointerEvent) {}
  onCancel(_editor: DrawEditorModel) {}
}

function isInPage(pageWidth: number, pageHeight: number, point: DrawPoint): boolean {
  const w = Math.max(0, pageWidth);
  const h = Math.max(0, pageHeight);
  return point[0] >= 0 && point[0] <= w && point[1] >= 0 && point[1] <= h;
}

function clampToPage(pageWidth: number, pageHeight: number, point: DrawPoint): DrawPoint {
  const w = Math.max(0, pageWidth);
  const h = Math.max(0, pageHeight);
  return [Math.min(w, Math.max(0, point[0])), Math.min(h, Math.max(0, point[1]))];
}

function pushPreviewPoint(editor: DrawEditorModel, hint: PreviewStroke, point: DrawPoint) {
  const preview = editor.previewStroke;
  const canReuse =
    preview &&
    preview.tool === hint.tool &&
    preview.colorArgb === hint.colorArgb &&
    preview.width === hint.width &&
    preview.alpha === hint.alpha;

  if (!canReuse) {
    editor.previewStroke = { ...hint, points: [point] };
    return;
  }
  preview.points.push(point);
}

function clipStrokePoints(points: DrawPoint[], eraser: DrawPoint, radiusSquared: number): DrawPoint[][] {
  if (!points.length) return [];
  const out: DrawPoint[][] = [];
  let current: DrawPoint[] | null = null;

  const flush = () => {
    if (current && current.length) out.push(current);
    current = null;
  };

  for (const p of points) {
    const keep = distanceSquared(p, eraser) > radiusSquared;
    if (keep) {
      if (!current) current = [];
      current.push(p);
    } else {
      flush();
    }
  }
  flush();

  return out;
}

function findLastStrokeById(elements: DrawElement[], id: string): DrawStrokeElement | null {
  for (let i = elements.length - 1; i >= 0; i--) {
    const el = elements[i];
    if (el?.type === "stroke" && el.id === id) return el;
  }
  return null;
}
