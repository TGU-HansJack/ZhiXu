import React, { useEffect, useMemo, useRef } from "react";
import type { CodeMirrorSelection } from "./CodeMirrorEditor";
import { isDevPerfEnabled, recordDurationMs } from "../lib/perf";
import type { EditorDisplaySettings } from "../lib/editorDisplaySettings";

export type MarkdownEditorMode = "live" | "source";

export type EditorHeaderActionInfo = {
  action: string;
  detail: Record<string, unknown>;
  ctrlKey: boolean;
  shiftKey: boolean;
  altKey: boolean;
};

export type EditorHeaderActionHandler = (info: EditorHeaderActionInfo) => void | boolean | Promise<void | boolean>;

type Props = {
  path: string;
  value: string;
  selection: CodeMirrorSelection;
  placeholder?: string;
  mode: MarkdownEditorMode;
  displaySettings: EditorDisplaySettings;
  onChange: (next: string) => void;
  onSelectionChange?: (next: CodeMirrorSelection) => void;
  onKeyDownCapture?: (ev: KeyboardEvent) => void;
  onHeaderAction?: EditorHeaderActionHandler;
};

type EditorTheme = {
  surface: string;
  onSurface: string;
  outline: string;
  primary: string;
};

type EditorWindow = Window & {
  __setTheme?: (json: string) => void;
  __setFontSize?: (px: number) => void;
  __setMode?: (mode: string) => void;
  __setPlaceholder?: (s: string) => void;
  __setShowLineNumbers?: (v: boolean) => void;
  __setShowProperties?: (v: boolean) => void;
  __setReadableLineLength?: (v: boolean) => void;
  __setHeaderPath?: (path: string) => void;
  __setDoc?: (text: string, selStart: number, selEnd: number) => void;
  __onHeaderAction?: (info: EditorHeaderActionInfo) => void;
  ZhixuEditor?: {
    docChanged?: (text: string, selStart: number, selEnd: number) => void;
    selectionChanged?: (selStart: number, selEnd: number) => void;
  };
};

function getRootCssVar(name: string, fallback: string): string {
  try {
    const v = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
    return v || fallback;
  } catch (_) {
    return fallback;
  }
}

export function ZhixuMarkdownEditor({
  path,
  value,
  selection,
  placeholder,
  mode,
  displaySettings,
  onChange,
  onSelectionChange,
  onKeyDownCapture,
  onHeaderAction,
}: Props) {
  const iframeRef = useRef<HTMLIFrameElement | null>(null);
  const winRef = useRef<EditorWindow | null>(null);
  const cleanupRef = useRef<(() => void) | null>(null);
  const callbacksRef = useRef({ onChange, onSelectionChange, onKeyDownCapture, onHeaderAction });
  callbacksRef.current = { onChange, onSelectionChange, onKeyDownCapture, onHeaderAction };

  const lastEditorValueRef = useRef<string | null>(null);
  const lastEditorSelRef = useRef<CodeMirrorSelection | null>(null);

  const latestRef = useRef({ path, value, selection, placeholder, mode, displaySettings });
  latestRef.current = { path, value, selection, placeholder, mode, displaySettings };

  const themeJson = useMemo(() => {
    const theme: EditorTheme = {
      surface: getRootCssVar("--bg", "#FFFFFF"),
      onSurface: getRootCssVar("--text", "#111111"),
      outline: getRootCssVar("--border", "rgba(0,0,0,0.18)"),
      primary: getRootCssVar("--accent", "#2F6FEB"),
    };
    return JSON.stringify(theme);
  }, []);

  const iframeSrc = useMemo(() => {
    const base = "/markdown-editor/index.html";
    return isDevPerfEnabled() ? `${base}?perf=1` : base;
  }, []);

  useEffect(() => {
    const iframe = iframeRef.current;
    if (!iframe) return;
    const loadStart = isDevPerfEnabled() ? performance.now() : 0;

    const handleKeyDown = (ev: KeyboardEvent) => {
      const mod = ev.ctrlKey || ev.metaKey;
      const key = (ev.key || "").toLowerCase();

      if (mod && key === "s") {
        try {
          const win = winRef.current;
          const el = win?.document?.activeElement as HTMLElement | null;
          if (el && (el.tagName === "INPUT" || el.tagName === "TEXTAREA") && typeof (el as any).blur === "function") el.blur();
        } catch (_) {}

        try {
          ev.preventDefault();
          ev.stopPropagation();
        } catch (_) {}

        // Allow blur-triggered commits (frontmatter widgets) to run before the app save handler.
        try {
          winRef.current?.setTimeout(() => callbacksRef.current.onKeyDownCapture?.(ev), 0);
        } catch (_) {
          callbacksRef.current.onKeyDownCapture?.(ev);
        }
        return;
      }

      callbacksRef.current.onKeyDownCapture?.(ev);
    };

    const attach = (win: EditorWindow) => {
      cleanupRef.current?.();
      cleanupRef.current = null;

      winRef.current = win;
      win.ZhixuEditor = {
        docChanged: (text, selStart, selEnd) => {
          lastEditorValueRef.current = text;
          lastEditorSelRef.current = { anchor: selStart, head: selEnd };
          callbacksRef.current.onChange(text);
          callbacksRef.current.onSelectionChange?.({ anchor: selStart, head: selEnd });
        },
        selectionChanged: (selStart, selEnd) => {
          lastEditorSelRef.current = { anchor: selStart, head: selEnd };
          callbacksRef.current.onSelectionChange?.({ anchor: selStart, head: selEnd });
        },
      };

      win.__onHeaderAction = (info) => {
        try {
          const action = String(info?.action ?? "");
          const handler = callbacksRef.current.onHeaderAction;
          if (!handler) return;
          const res = handler({
            action,
            detail: (info?.detail && typeof info.detail === "object" ? (info.detail as Record<string, unknown>) : {}) ?? {},
            ctrlKey: Boolean(info?.ctrlKey),
            shiftKey: Boolean(info?.shiftKey),
            altKey: Boolean(info?.altKey),
          });

          if (action !== "renameFile") return;
          const isThenable = res && typeof (res as Promise<unknown>).then === "function";
          if (isThenable) {
            void (res as Promise<void | boolean>)
              .then((ok) => {
                if (ok === false) win.__setHeaderPath?.(latestRef.current.path);
              })
              .catch(() => win.__setHeaderPath?.(latestRef.current.path));
          } else if (res === false) {
            win.__setHeaderPath?.(latestRef.current.path);
          }
        } catch (_) {
          try {
            win.__setHeaderPath?.(latestRef.current.path);
          } catch (_) {}
        }
      };

      win.addEventListener("keydown", handleKeyDown, true);
      cleanupRef.current = () => {
        win.removeEventListener("keydown", handleKeyDown, true);
        try {
          delete win.__onHeaderAction;
        } catch (_) {}
      };
    };

    const onLoad = () => {
      const win = iframe.contentWindow as EditorWindow | null;
      if (!win) return;
      attach(win);

      win.__setTheme?.(themeJson);
      const latest = latestRef.current;
      win.__setHeaderPath?.(latest.path);
      win.__setPlaceholder?.(latest.placeholder ?? "");
      win.__setMode?.(latest.mode);
      win.__setShowLineNumbers?.(latest.displaySettings.showLineNumbers);
      win.__setShowProperties?.(latest.displaySettings.notePropertiesDisplay !== "source");
      win.__setReadableLineLength?.(latest.displaySettings.readableLineLength);
      win.__setDoc?.(latest.value, latest.selection.anchor, latest.selection.head);
      lastEditorValueRef.current = latest.value;
      lastEditorSelRef.current = latest.selection;

      if (loadStart) recordDurationMs("md-editor:iframe-load", performance.now() - loadStart, { src: iframeSrc });
    };

    iframe.addEventListener("load", onLoad);
    if (iframe.contentWindow && iframe.contentDocument?.readyState === "complete") onLoad();

    return () => {
      iframe.removeEventListener("load", onLoad);
      cleanupRef.current?.();
      cleanupRef.current = null;
      winRef.current = null;
    };
  }, [themeJson]);

  useEffect(() => {
    const win = winRef.current;
    if (!win) return;
    win.__setMode?.(mode);
  }, [mode]);

  useEffect(() => {
    const win = winRef.current;
    if (!win) return;
    win.__setHeaderPath?.(path);
  }, [path]);

  useEffect(() => {
    const win = winRef.current;
    if (!win) return;
    win.__setPlaceholder?.(placeholder ?? "");
  }, [placeholder]);

  useEffect(() => {
    const win = winRef.current;
    if (!win) return;
    win.__setShowLineNumbers?.(displaySettings.showLineNumbers);
    win.__setShowProperties?.(displaySettings.notePropertiesDisplay !== "source");
    win.__setReadableLineLength?.(displaySettings.readableLineLength);
  }, [
    displaySettings.notePropertiesDisplay,
    displaySettings.readableLineLength,
    displaySettings.showLineNumbers,
  ]);

  useEffect(() => {
    const win = winRef.current;
    if (!win) return;

    const prevValue = lastEditorValueRef.current;
    const prevSel = lastEditorSelRef.current;
    if (prevValue === value && prevSel?.anchor === selection.anchor && prevSel?.head === selection.head) return;

    win.__setDoc?.(value, selection.anchor, selection.head);
    lastEditorValueRef.current = value;
    lastEditorSelRef.current = selection;
  }, [selection.anchor, selection.head, value]);

  return <iframe ref={iframeRef} className="mdEditorFrame" src={iframeSrc} title="Markdown Editor" />;
}
