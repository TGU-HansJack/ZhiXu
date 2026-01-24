export type DrawSettings = {
  longPressEraserEnabled: boolean;
};

export const DEFAULT_DRAW_SETTINGS: DrawSettings = {
  longPressEraserEnabled: false,
};

const STORAGE_KEY = "zhixu.draw.settings.v1";
const CHANGE_EVENT = "zhixu:drawSettingsChanged";

function normalize(raw: unknown): DrawSettings {
  const out: DrawSettings = { ...DEFAULT_DRAW_SETTINGS };
  if (!raw || typeof raw !== "object") return out;
  const obj = raw as Record<string, unknown>;

  if (obj.longPressEraserEnabled === true || obj.longPressEraserEnabled === false) {
    out.longPressEraserEnabled = obj.longPressEraserEnabled;
  }

  return out;
}

export function loadDrawSettings(): DrawSettings {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return { ...DEFAULT_DRAW_SETTINGS };
    return normalize(JSON.parse(raw));
  } catch {
    return { ...DEFAULT_DRAW_SETTINGS };
  }
}

export function saveDrawSettings(settings: DrawSettings): void {
  const normalized = normalize(settings);
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(normalized));
  } catch {
    // ignore
  }
  try {
    window.dispatchEvent(new CustomEvent<DrawSettings>(CHANGE_EVENT, { detail: normalized }));
  } catch {
    // ignore
  }
}

export function subscribeDrawSettings(onChange: (settings: DrawSettings) => void): () => void {
  const handler = (ev: Event) => {
    const detail = (ev as CustomEvent<DrawSettings>).detail;
    onChange(normalize(detail));
  };
  window.addEventListener(CHANGE_EVENT, handler as EventListener);
  return () => window.removeEventListener(CHANGE_EVENT, handler as EventListener);
}

