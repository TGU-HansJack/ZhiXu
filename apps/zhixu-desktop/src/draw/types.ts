export type DrawToolId = "pen" | "highlighter" | "shape" | "lasso" | "eraser" | "pan";

export type DrawPenStyle = "fountainPen" | "ballpointPen";

export type DrawShapeMode = "line" | "rectangle" | "ellipse";

export type DrawViewMode = "writing" | "reading";

export type DrawPoint = [number, number];

export type DrawPointLike = readonly [number, number, ...number[]];

export type DrawStrokePoint = [number, number, ...number[]];

export type DrawStrokeTool = "pen" | "highlighter" | "shape";

export type DrawPressureMapping = "width" | "opacity" | "both";

export type DrawPressureCurve = "linear" | "soft" | "hard" | "custom";

export type DrawTiltMapping = "width" | "angle" | "shading";

export type DrawMeta = {
  formatVersion: number;
  createdAtMs: number;
  modifiedAtMs: number;
  pageOrder: string[];
};

export type DrawStrokeElement = {
  type: "stroke";
  id: string;
  tool: DrawStrokeTool;
  colorArgb: number;
  width: number;
  alpha: number;
  points: DrawStrokePoint[];
};

export type DrawShapeElement = {
  type: "shape";
  id: string;
  shape: DrawShapeMode;
  colorArgb: number;
  width: number;
  alpha: number;
  start: DrawPoint;
  end: DrawPoint;
};

export type DrawElement = DrawStrokeElement | DrawShapeElement;

export type DrawPage = {
  id: string;
  width: number;
  height: number;
  backgroundColorArgb: number;
  elements: DrawElement[];
};

export type DrawDocument = {
  meta: DrawMeta;
  pages: DrawPage[];
};
