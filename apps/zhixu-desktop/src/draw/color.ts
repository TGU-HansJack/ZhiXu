export function argbToRgba(argb: number, alpha: number = 1): string {
  const u = argb >>> 0;
  const r = (u >>> 16) & 0xff;
  const g = (u >>> 8) & 0xff;
  const b = u & 0xff;
  return `rgba(${r},${g},${b},${alpha})`;
}

