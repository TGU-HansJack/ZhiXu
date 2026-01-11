import type { DrawDocument, DrawElement, DrawPage } from "./types";

export function cloneElements(elements: DrawElement[]): DrawElement[] {
  return elements.map((el) => {
    if (el.type === "stroke") {
      return {
        type: "stroke",
        id: el.id,
        tool: el.tool,
        colorArgb: el.colorArgb,
        width: el.width,
        alpha: el.alpha,
        points: el.points.map((p) => [p[0], p[1]] as [number, number]),
      };
    }
    return {
      type: "shape",
      id: el.id,
      shape: el.shape,
      colorArgb: el.colorArgb,
      width: el.width,
      alpha: el.alpha,
      start: [el.start[0], el.start[1]] as [number, number],
      end: [el.end[0], el.end[1]] as [number, number],
    };
  });
}

export function clonePage(page: DrawPage): DrawPage {
  return {
    id: page.id,
    width: page.width,
    height: page.height,
    backgroundColorArgb: page.backgroundColorArgb,
    elements: cloneElements(page.elements),
  };
}

export function cloneDocument(doc: DrawDocument): DrawDocument {
  return {
    meta: {
      formatVersion: doc.meta.formatVersion,
      createdAtMs: doc.meta.createdAtMs,
      modifiedAtMs: doc.meta.modifiedAtMs,
      pageOrder: [...doc.meta.pageOrder],
    },
    pages: doc.pages.map(clonePage),
  };
}
