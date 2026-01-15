import { WebviewWindow } from "@tauri-apps/api/webviewWindow";

export type DesktopWidgetState = {
  pluginId: string;
  actionId: string;
  enabled: boolean;
  title?: string;
  locked?: boolean;
  vaultRoot?: string;
  x?: number;
  y?: number;
  width?: number;
  height?: number;
};

const STORE_KEY = "zhixu:desktopWidgets";

function loadStore(): Record<string, DesktopWidgetState> {
  try {
    const raw = localStorage.getItem(STORE_KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw) as { widgets?: Record<string, DesktopWidgetState> } | Record<string, DesktopWidgetState>;
    if (parsed && typeof parsed === "object" && "widgets" in parsed) {
      const w = (parsed as any).widgets;
      return w && typeof w === "object" ? (w as Record<string, DesktopWidgetState>) : {};
    }
    return parsed && typeof parsed === "object" ? (parsed as Record<string, DesktopWidgetState>) : {};
  } catch {
    return {};
  }
}

function saveStore(widgets: Record<string, DesktopWidgetState>) {
  try {
    localStorage.setItem(STORE_KEY, JSON.stringify({ version: 1, widgets }));
  } catch {
    // ignore
  }
}

export function getDesktopWidgetState(pluginId: string): DesktopWidgetState | null {
  const id = String(pluginId || "").trim();
  if (!id) return null;
  const store = loadStore();
  return store[id] || null;
}

export function setDesktopWidgetState(pluginId: string, state: DesktopWidgetState | null): void {
  const id = String(pluginId || "").trim();
  if (!id) return;
  const store = loadStore();
  if (!state) delete store[id];
  else store[id] = state;
  saveStore(store);
}

export function isDesktopWidgetEnabled(pluginId: string): boolean {
  return getDesktopWidgetState(pluginId)?.enabled === true;
}

export function listDesktopWidgetStates(): DesktopWidgetState[] {
  return Object.values(loadStore());
}

function widgetLabel(pluginId: string) {
  return `widget:${pluginId}`;
}

export async function openDesktopWidget(opts: { pluginId: string; actionId: string; vaultRoot: string; title?: string }) {
  const pluginId = String(opts.pluginId || "").trim();
  const actionId = String(opts.actionId || "").trim();
  const vaultRoot = String(opts.vaultRoot || "").trim();
  if (!pluginId || !actionId) throw new Error("Missing pluginId/actionId");
  if (!vaultRoot) throw new Error("No vault selected");

  const prev = getDesktopWidgetState(pluginId);
  setDesktopWidgetState(pluginId, {
    ...(prev || { pluginId, actionId, enabled: true }),
    pluginId,
    actionId,
    enabled: true,
    title: opts.title || prev?.title,
    vaultRoot,
  });

  const label = widgetLabel(pluginId);
  const existing = WebviewWindow.getByLabel(label);
  if (existing) {
    try {
      await existing.show();
      await existing.setFocus();
    } catch {
      // ignore
    }
    return;
  }

  const url = new URL(window.location.href);
  url.searchParams.set("view", "widget");
  url.searchParams.set("pluginId", pluginId);
  url.searchParams.set("actionId", actionId);
  url.searchParams.set("vaultRoot", vaultRoot);

  const width = Math.max(200, Math.min(1200, prev?.width ?? 340));
  const height = Math.max(140, Math.min(1200, prev?.height ?? 420));

  const win = new WebviewWindow(label, {
    url: url.toString(),
    title: opts.title || "小组件",
    width,
    height,
    x: typeof prev?.x === "number" ? prev.x : undefined,
    y: typeof prev?.y === "number" ? prev.y : undefined,
    decorations: false,
    resizable: true,
    skipTaskbar: true,
    alwaysOnTop: true,
  });

  win.once("tauri://error", () => {
    const s = getDesktopWidgetState(pluginId);
    if (s) setDesktopWidgetState(pluginId, { ...s, enabled: false });
  });
}

export async function closeDesktopWidget(pluginId: string) {
  const id = String(pluginId || "").trim();
  if (!id) return;
  const label = widgetLabel(id);
  const existing = WebviewWindow.getByLabel(label);
  if (existing) {
    try {
      await existing.close();
    } catch {
      // ignore
    }
  }
  const prev = getDesktopWidgetState(id);
  if (prev) setDesktopWidgetState(id, { ...prev, enabled: false });
}
