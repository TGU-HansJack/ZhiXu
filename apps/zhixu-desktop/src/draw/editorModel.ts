import type {
  DrawDocument,
  DrawElement,
  DrawPage,
  DrawPenStyle,
  DrawPoint,
  DrawShapeMode,
  DrawStrokeTool,
  DrawToolId,
  DrawViewMode,
} from "./types";
import { cloneElements } from "./clone";

// NOTE: Keep this file focused on state+helpers; rendering and input live elsewhere.

export type PreviewShape = {
  mode: DrawShapeMode;
  start: DrawPoint;
  end: DrawPoint;
};

export type PreviewStroke = {
  tool: DrawStrokeTool;
  colorArgb: number;
  width: number;
  alpha: number;
  points: DrawPoint[];
};

export class DrawViewport {
  viewportSize: { width: number; height: number } = { width: 0, height: 0 };
  scale = 1;
  translation: DrawPoint = [0, 0];

  viewToPage(pointInView: DrawPoint): DrawPoint {
    const s = this.scale > 0.0001 ? this.scale : 0.0001;
    return [(pointInView[0] - this.translation[0]) / s, (pointInView[1] - this.translation[1]) / s];
  }
}

export class DrawEditorModel {
  doc: DrawDocument;
  currentPageIndex = 0;

  toolId: DrawToolId = "pen";
  viewMode: DrawViewMode = "writing";
  penStyle: DrawPenStyle = "fountainPen";
  shapeMode: DrawShapeMode = "line";

  fountainPenColorArgb = 0xff000000 | 0;
  fountainPenWidth = 3;
  ballpointPenColorArgb = 0xff000000 | 0;
  ballpointPenWidth = 3;

  highlighterColorArgb = 0xff000000 | 0;
  highlighterWidth = 18;
  highlighterAlpha = 0.35;

  shapeColorArgb = 0xff000000 | 0;
  shapeWidth = 3;

  eraserRadius = 14;

  selectedElementIds: Set<string> = new Set();
  lassoPathPoints: DrawPoint[] = [];
  previewShape: PreviewShape | null = null;
  eraserCursor: DrawPoint | null = null;
  previewStroke: PreviewStroke | null = null;

  viewport: DrawViewport = new DrawViewport();

  constructor(doc: DrawDocument) {
    this.doc = doc;
  }

  get currentPenColorArgb(): number {
    return this.penStyle === "fountainPen" ? this.fountainPenColorArgb : this.ballpointPenColorArgb;
  }

  set currentPenColorArgb(value: number) {
    if (this.penStyle === "fountainPen") this.fountainPenColorArgb = value;
    else this.ballpointPenColorArgb = value;
  }

  get currentPenWidth(): number {
    return this.penStyle === "fountainPen" ? this.fountainPenWidth : this.ballpointPenWidth;
  }

  set currentPenWidth(value: number) {
    if (this.penStyle === "fountainPen") this.fountainPenWidth = value;
    else this.ballpointPenWidth = value;
  }

  currentPageOrNull(): DrawPage | null {
    return this.doc.pages[this.currentPageIndex] ?? null;
  }

  ensureHasAtLeastOnePage(defaultWidth = 595, defaultHeight = 842) {
    if (this.doc.pages.length > 0) return;
    this.doc.pages.push({
      id: "page_001",
      width: defaultWidth,
      height: defaultHeight,
      backgroundColorArgb: 0xffffffff | 0,
      elements: [],
    });
    this.syncPageOrder();
    this.currentPageIndex = 0;
  }

  clearOverlaysAndSelection() {
    this.selectedElementIds = new Set();
    this.lassoPathPoints = [];
    this.previewShape = null;
    this.previewStroke = null;
    this.eraserCursor = null;
  }

  goToPreviousPage() {
    if (this.doc.pages.length === 0) return;
    this.currentPageIndex = Math.max(0, this.currentPageIndex - 1);
    this.clearOverlaysAndSelection();
  }

  goToNextPage() {
    if (this.doc.pages.length === 0) return;
    this.currentPageIndex = Math.min(this.doc.pages.length - 1, this.currentPageIndex + 1);
    this.clearOverlaysAndSelection();
  }

  addPageLikeCurrent() {
    const current = this.currentPageOrNull();
    const w = current?.width ?? 595;
    const h = current?.height ?? 842;
    const bg = current?.backgroundColorArgb ?? (0xffffffff | 0);
    const id = `page_${String(this.doc.pages.length + 1).padStart(3, "0")}`;
    this.doc.pages.push({ id, width: w, height: h, backgroundColorArgb: bg, elements: [] });
    this.syncPageOrder();
    this.currentPageIndex = this.doc.pages.length - 1;
    this.clearOverlaysAndSelection();
  }

  insertPageAfterCurrent() {
    const current = this.currentPageOrNull();
    const w = current?.width ?? 595;
    const h = current?.height ?? 842;
    const bg = current?.backgroundColorArgb ?? (0xffffffff | 0);
    const id = `page_${String(this.doc.pages.length + 1).padStart(3, "0")}`;
    const insertAt = Math.min(Math.max(0, this.currentPageIndex + 1), this.doc.pages.length);
    this.doc.pages.splice(insertAt, 0, { id, width: w, height: h, backgroundColorArgb: bg, elements: [] });
    this.syncPageOrder();
    this.currentPageIndex = insertAt;
    this.clearOverlaysAndSelection();
  }

  rotateCurrentPage90Degrees() {
    const page = this.currentPageOrNull();
    if (!page) return;
    const oldW = page.width;
    const oldH = page.height;
    if (oldW <= 1 || oldH <= 1) return;

    const rotate = (p: DrawPoint): DrawPoint => [oldH - p[1], p[0]];

    for (const el of page.elements) {
      if (el.type === "stroke") {
        el.points = el.points.map(rotate);
      } else {
        el.start = rotate(el.start);
        el.end = rotate(el.end);
      }
    }
    page.width = oldH;
    page.height = oldW;
    this.clearOverlaysAndSelection();
  }

  snapshotPageElements(pageIndex: number = this.currentPageIndex): DrawElement[] {
    const page = this.doc.pages[pageIndex];
    if (!page) return [];
    return cloneElements(page.elements);
  }

  restorePageElements(elements: DrawElement[], pageIndex: number = this.currentPageIndex) {
    const page = this.doc.pages[pageIndex];
    if (!page) return;
    page.elements = cloneElements(elements);
    this.clearOverlaysAndSelection();
  }

  private syncPageOrder() {
    this.doc.meta.pageOrder = this.doc.pages.map((_, i) => `pages/page_${String(i + 1).padStart(3, "0")}.json`);
  }
}
