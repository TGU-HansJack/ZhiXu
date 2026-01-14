export type NotePropertiesDisplay = "show" | "source";

export type EditorDisplaySettings = {
  readableLineLength: boolean;
  strictLineBreaks: boolean;
  notePropertiesDisplay: NotePropertiesDisplay;
  foldHeadings: boolean;
  foldIndent: boolean;
  showLineNumbers: boolean;
  showIndentGuides: boolean;
  textAlignRight: boolean;
};

export const DEFAULT_EDITOR_DISPLAY_SETTINGS: EditorDisplaySettings = {
  readableLineLength: false,
  strictLineBreaks: false,
  notePropertiesDisplay: "show",
  foldHeadings: true,
  foldIndent: true,
  showLineNumbers: false,
  showIndentGuides: false,
  textAlignRight: false,
};

const STORAGE_KEY = "zhixu:editorDisplaySettings";

function normalize(raw: unknown): EditorDisplaySettings {
  const out: EditorDisplaySettings = { ...DEFAULT_EDITOR_DISPLAY_SETTINGS };
  if (!raw || typeof raw !== "object") return out;
  const obj = raw as Record<string, unknown>;

  if (obj.readableLineLength === true || obj.readableLineLength === false) out.readableLineLength = obj.readableLineLength;
  if (obj.strictLineBreaks === true || obj.strictLineBreaks === false) out.strictLineBreaks = obj.strictLineBreaks;
  if (obj.foldHeadings === true || obj.foldHeadings === false) out.foldHeadings = obj.foldHeadings;
  if (obj.foldIndent === true || obj.foldIndent === false) out.foldIndent = obj.foldIndent;
  if (obj.showLineNumbers === true || obj.showLineNumbers === false) out.showLineNumbers = obj.showLineNumbers;
  if (obj.showIndentGuides === true || obj.showIndentGuides === false) out.showIndentGuides = obj.showIndentGuides;
  if (obj.textAlignRight === true || obj.textAlignRight === false) out.textAlignRight = obj.textAlignRight;

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

