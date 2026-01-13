import type { DrawElement } from "./types";

function pointsEqual(a: readonly number[][], b: readonly number[][]): boolean {
  if (a.length !== b.length) return false;
  for (let i = 0; i < a.length; i++) {
    const pa = a[i]!;
    const pb = b[i]!;
    if (pa.length !== pb.length) return false;
    for (let j = 0; j < pa.length; j++) {
      if (pa[j] !== pb[j]) return false;
    }
  }
  return true;
}

export function elementsEqual(a: readonly DrawElement[], b: readonly DrawElement[]): boolean {
  if (a.length !== b.length) return false;
  for (let i = 0; i < a.length; i++) {
    const ea = a[i]!;
    const eb = b[i]!;
    if (ea.type !== eb.type) return false;
    if (ea.type === "stroke" && eb.type === "stroke") {
      if (ea.id !== eb.id) return false;
      if (ea.tool !== eb.tool) return false;
      if (ea.colorArgb !== eb.colorArgb) return false;
      if (ea.width !== eb.width) return false;
      if (ea.alpha !== eb.alpha) return false;
      if (!pointsEqual(ea.points, eb.points)) return false;
      continue;
    }
    if (ea.type === "shape" && eb.type === "shape") {
      if (ea.id !== eb.id) return false;
      if (ea.shape !== eb.shape) return false;
      if (ea.colorArgb !== eb.colorArgb) return false;
      if (ea.width !== eb.width) return false;
      if (ea.alpha !== eb.alpha) return false;
      if (ea.start[0] !== eb.start[0] || ea.start[1] !== eb.start[1]) return false;
      if (ea.end[0] !== eb.end[0] || ea.end[1] !== eb.end[1]) return false;
      continue;
    }
    return false;
  }
  return true;
}
