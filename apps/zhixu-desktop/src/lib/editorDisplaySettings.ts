export type NotePropertiesDisplay = "show" | "source";

export type EditorDisplaySettings = {
  readableLineLength: boolean;
  notePropertiesDisplay: NotePropertiesDisplay;
  showLineNumbers: boolean;
};

export const DEFAULT_EDITOR_DISPLAY_SETTINGS: EditorDisplaySettings = {
  readableLineLength: false,
  notePropertiesDisplay: "show",
  showLineNumbers: false,
};

const STORAGE_KEY = "zhixu:editorDisplaySettings";

function normalize(raw: unknown): EditorDisplaySettings {
  const out: EditorDisplaySettings = { ...DEFAULT_EDITOR_DISPLAY_SETTINGS };
  if (!raw || typeof raw !== "object") return out;
  const obj = raw as Record<string, unknown>;

  if (obj.readableLineLength === true || obj.readableLineLength === false) out.readableLineLength = obj.readableLineLength;
  if (obj.showLineNumbers === true || obj.showLineNumbers === false) out.showLineNumbers = obj.showLineNumbers;

  if (obj.notePropertiesDisplay === "show" || obj.notePropertiesDisplay === "source") out.notePropertiesDisplay = obj.notePropertiesDisplay;

  return out;
}

export function loadEditorDisplaySettings(): EditorDisplaySettings {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return { ...DEFAULT_EDITOR_DISPLAY_SETTINGS };
    return normalize(JSON.parse(raw));
  } catch {
    return { ...DEFAULT_EDITOR_DISPLAY_SETTINGS };
  }
}

export function saveEditorDisplaySettings(settings: EditorDisplaySettings): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
  } catch {
    // ignore
  }
}
