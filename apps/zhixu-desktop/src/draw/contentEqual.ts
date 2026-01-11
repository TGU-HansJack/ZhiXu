import type { DrawDocument } from "./types";
import { elementsEqual } from "./equal";

export function documentContentEqual(a: DrawDocument, b: DrawDocument): boolean {
  if (a.pages.length !== b.pages.length) return false;
  for (let i = 0; i < a.pages.length; i++) {
    const pa = a.pages[i]!;
    const pb = b.pages[i]!;
    if (pa.id !== pb.id) return false;
    if (pa.width !== pb.width) return false;
    if (pa.height !== pb.height) return false;
    if (pa.backgroundColorArgb !== pb.backgroundColorArgb) return false;
    if (!elementsEqual(pa.elements, pb.elements)) return false;
  }
  return true;
}

