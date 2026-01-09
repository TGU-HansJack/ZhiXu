import React, { useEffect, useMemo, useRef } from "react";
import { EditorState } from "@codemirror/state";
import { EditorView, keymap, placeholder as cmPlaceholder } from "@codemirror/view";
import { basicSetup } from "codemirror";
import { indentWithTab } from "@codemirror/commands";
import { markdown } from "@codemirror/lang-markdown";

export type CodeMirrorSelection = { anchor: number; head: number };

type Props = {
  value: string;
  selection?: CodeMirrorSelection;
  placeholder?: string;
  onChange: (next: string) => void;
  onSelectionChange?: (next: CodeMirrorSelection) => void;
};

export function CodeMirrorEditor({ value, selection, placeholder, onChange, onSelectionChange }: Props) {
  const hostRef = useRef<HTMLDivElement | null>(null);
  const viewRef = useRef<EditorView | null>(null);
  const callbacksRef = useRef({ onChange, onSelectionChange });
  callbacksRef.current = { onChange, onSelectionChange };

  const extensions = useMemo(() => {
    const updateListener = EditorView.updateListener.of((update) => {
      if (update.docChanged) callbacksRef.current.onChange(update.state.doc.toString());
      if (update.selectionSet && callbacksRef.current.onSelectionChange) {
        const sel = update.state.selection.main;
        callbacksRef.current.onSelectionChange({ anchor: sel.anchor, head: sel.head });
      }
    });

    const theme = EditorView.theme(
      {
        "&": {
          height: "100%",
          backgroundColor: "transparent",
        },
        ".cm-scroller": {
          fontFamily:
            'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace',
          fontSize: "14px",
          lineHeight: "1.6",
        },
        ".cm-content": {
          padding: "14px 16px 22px",
        },
        "&.cm-focused": {
          outline: "none",
        },
        "&.cm-focused .cm-cursor": {
          borderLeftColor: "var(--accent)",
        },
        "&.cm-focused .cm-selectionBackground, ::selection": {
          backgroundColor: "rgba(47, 111, 235, 0.22)",
        },
        ".cm-gutters": {
          backgroundColor: "transparent",
          color: "rgba(0,0,0,0.32)",
          border: "none",
        },
      },
      { dark: false },
    );

    return [
      basicSetup,
      keymap.of([indentWithTab]),
      markdown(),
      EditorView.lineWrapping,
      EditorState.tabSize.of(2),
      EditorView.contentAttributes.of({ "aria-label": "Editor" }),
      placeholder ? cmPlaceholder(placeholder) : [],
      theme,
      updateListener,
    ];
  }, [placeholder]);

  useEffect(() => {
    const host = hostRef.current;
    if (!host) return;
    if (viewRef.current) return;

    const state = EditorState.create({
      doc: value,
      selection: selection ? { anchor: selection.anchor, head: selection.head } : undefined,
      extensions,
    });

    const view = new EditorView({ state, parent: host });
    viewRef.current = view;

    return () => {
      viewRef.current?.destroy();
      viewRef.current = null;
    };
  }, [extensions]);

  useEffect(() => {
    const view = viewRef.current;
    if (!view) return;

    const current = view.state.doc.toString();
    if (current !== value) {
      const transaction = view.state.update({
        changes: { from: 0, to: view.state.doc.length, insert: value },
      });
      view.dispatch(transaction);
    }
  }, [value]);

  useEffect(() => {
    const view = viewRef.current;
    if (!view || !selection) return;
    const sel = view.state.selection.main;
    if (sel.anchor === selection.anchor && sel.head === selection.head) return;
    view.dispatch({ selection: { anchor: selection.anchor, head: selection.head }, scrollIntoView: true });
  }, [selection?.anchor, selection?.head]);

  return <div className="cmHost" ref={hostRef} />;
}
