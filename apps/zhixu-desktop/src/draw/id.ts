export function newElementId(): string {
  try {
    return globalThis.crypto?.randomUUID?.() ?? fallbackId();
  } catch (_) {
    return fallbackId();
  }
}

function fallbackId(): string {
  const rand = Math.random().toString(16).slice(2);
  return `el_${Date.now().toString(16)}_${rand}`;
}

