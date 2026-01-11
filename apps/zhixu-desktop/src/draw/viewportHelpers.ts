import type { DrawEditorModel } from "./editorModel";
import type { DrawPoint } from "./types";

export const MIN_SCALE = 0.3;
export const MAX_SCALE = 5.0;

export function snapViewport(editor: DrawEditorModel, marginPx: number) {
  const page = editor.currentPageOrNull();
  if (!page) return;
  const { width: vw, height: vh } = editor.viewport.viewportSize;
  if (vw <= 0 || vh <= 0) return;
  const scale = Math.max(0.0001, editor.viewport.scale);
  const contentW = page.width * scale;
  const contentH = page.height * scale;

  const clampAxis = (t: number, viewport: number, content: number): number => {
    const min = viewport - content - marginPx;
    const max = marginPx;
    if (min > max) return (min + max) / 2;
    return Math.min(max, Math.max(min, t));
  };

  editor.viewport.translation = [
    clampAxis(editor.viewport.translation[0], vw, contentW),
    clampAxis(editor.viewport.translation[1], vh, contentH),
  ];
}

export function fitPageToWidth(editor: DrawEditorModel, marginPx: number) {
  const page = editor.currentPageOrNull();
  if (!page) return;
  const { width: vw } = editor.viewport.viewportSize;
  if (vw <= 0) return;
  const availableW = Math.max(1, vw - marginPx * 2);
  const scale = Math.min(MAX_SCALE, Math.max(MIN_SCALE, availableW / Math.max(1, page.width)));
  editor.viewport.scale = scale;
  editor.viewport.translation = [marginPx, marginPx];
  snapViewport(editor, marginPx);
}

export function centerPage(editor: DrawEditorModel, marginPx: number) {
  const page = editor.currentPageOrNull();
  if (!page) return;
  const { width: vw, height: vh } = editor.viewport.viewportSize;
  if (vw <= 0 || vh <= 0) return;
  const scale = Math.max(0.0001, editor.viewport.scale);
  const contentW = page.width * scale;
  const contentH = page.height * scale;
  editor.viewport.translation = [(vw - contentW) / 2, (vh - contentH) / 2];
  snapViewport(editor, marginPx);
}

export function zoomAroundViewPoint(editor: DrawEditorModel, viewPoint: DrawPoint, nextScale: number, marginPx: number) {
  const oldScale = Math.max(0.0001, editor.viewport.scale);
  const oldTranslation = editor.viewport.translation;
  const scale = Math.min(MAX_SCALE, Math.max(MIN_SCALE, nextScale));

  const pageUnder = [(viewPoint[0] - oldTranslation[0]) / oldScale, (viewPoint[1] - oldTranslation[1]) / oldScale] as DrawPoint;
  editor.viewport.scale = scale;
  editor.viewport.translation = [viewPoint[0] - pageUnder[0] * scale, viewPoint[1] - pageUnder[1] * scale];
  snapViewport(editor, marginPx);
}

