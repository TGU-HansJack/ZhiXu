import React, { useEffect, useMemo, useRef } from "react";
import type { CodeMirrorSelection } from "./CodeMirrorEditor";

export type MarkdownEditorMode = "live" | "source";

type Props = {
  value: string;
  selection: CodeMirrorSelection;
  placeholder?: string;
  mode: MarkdownEditorMode;
  onChange: (next: string) => void;
  onSelectionChange?: (next: CodeMirrorSelection) => void;
  onKeyDownCapture?: (ev: KeyboardEvent) => void;
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
  __setDoc?: (text: string, selStart: number, selEnd: number) => void;
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
  value,
  selection,
  placeholder,
  mode,
  onChange,
  onSelectionChange,
  onKeyDownCapture,
}: Props) {
  const iframeRef = useRef<HTMLIFrameElement | null>(null);
  const winRef = useRef<EditorWindow | null>(null);
  const cleanupRef = useRef<(() => void) | null>(null);
  const callbacksRef = useRef({ onChange, onSelectionChange, onKeyDownCapture });
  callbacksRef.current = { onChange, onSelectionChange, onKeyDownCapture };

  const lastEditorValueRef = useRef<string | null>(null);
  const lastEditorSelRef = useRef<CodeMirrorSelection | null>(null);

  const latestRef = useRef({ value, selection, placeholder, mode });
  latestRef.current = { value, selection, placeholder, mode };

  const themeJson = useMemo(() => {
    const theme: EditorTheme = {
      surface: getRootCssVar("--bg", "#FFFFFF"),
      onSurface: getRootCssVar("--text", "#111111"),
      outline: getRootCssVar("--border", "rgba(0,0,0,0.18)"),
      primary: getRootCssVar("--accent", "#2F6FEB"),
    };
    return JSON.stringify(theme);
  }, []);

  useEffect(() => {
    const iframe = iframeRef.current;
    if (!iframe) return;

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

      win.addEventListener("keydown", handleKeyDown, true);
      cleanupRef.current = () => {
        win.removeEventListener("keydown", handleKeyDown, true);
      };
    };

    const onLoad = () => {
      const win = iframe.contentWindow as EditorWindow | null;
      if (!win) return;
      attach(win);

      win.__setTheme?.(themeJson);
      const latest = latestRef.current;
      win.__setPlaceholder?.(latest.placeholder ?? "");
      win.__setMode?.(latest.mode);
      win.__setDoc?.(latest.value, latest.selection.anchor, latest.selection.head);
      lastEditorValueRef.current = latest.value;
      lastEditorSelRef.current = latest.selection;
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
    win.__setPlaceholder?.(placeholder ?? "");
  }, [placeholder]);

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

  return <iframe ref={iframeRef} className="mdEditorFrame" src="/markdown-editor/index.html" title="Markdown Editor" />;
}
