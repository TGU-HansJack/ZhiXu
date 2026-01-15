import React, { useEffect, useMemo, useRef, useState } from "react";

type RendererWindow = Window & {
  __setTheme?: (json: string) => void;
  __setMarkdown?: (md: string) => void;
};

function getRootCssVar(name: string, fallback: string): string {
  try {
    const v = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
    return v || fallback;
  } catch {
    return fallback;
  }
}

export function MarkdownRendererFrame({
  markdown,
  className,
  onMarkdownChange,
}: {
  markdown: string;
  className?: string;
  onMarkdownChange?: (md: string) => void;
}) {
  const iframeRef = useRef<HTMLIFrameElement | null>(null);
  const [height, setHeight] = useState<number>(220);
  const onMarkdownChangeRef = useRef<((md: string) => void) | undefined>(undefined);
  onMarkdownChangeRef.current = onMarkdownChange;

  const themeJson = useMemo(() => {
    const theme = {
      bg: getRootCssVar("--bg", "#ffffff"),
      panel: getRootCssVar("--panel", "rgba(245, 245, 245, 0.75)"),
      text: getRootCssVar("--text", "rgba(0, 0, 0, 0.88)"),
      muted: getRootCssVar("--muted", "rgba(0, 0, 0, 0.55)"),
      accent: getRootCssVar("--accent", "#2f6feb"),
      border: getRootCssVar("--border", "rgba(0, 0, 0, 0.08)"),
    };
    return JSON.stringify(theme);
  }, []);

  useEffect(() => {
    const iframe = iframeRef.current;
    if (!iframe) return;

    const onLoad = () => {
      const win = iframe.contentWindow as RendererWindow | null;
      if (!win) return;
      win.__setTheme?.(themeJson);
      win.__setMarkdown?.(markdown || "");
    };

    iframe.addEventListener("load", onLoad);
    if (iframe.contentDocument?.readyState === "complete") onLoad();
    return () => iframe.removeEventListener("load", onLoad);
  }, [markdown, themeJson]);

  useEffect(() => {
    const win = iframeRef.current?.contentWindow as RendererWindow | null;
    win?.__setMarkdown?.(markdown || "");
  }, [markdown]);

  useEffect(() => {
    const onMessage = (ev: MessageEvent) => {
      const iframeWin = iframeRef.current?.contentWindow;
      if (!iframeWin || ev.source !== iframeWin) return;
      const msg = ev.data as any;
      if (!msg || typeof msg !== "object") return;
      if (msg.__zhixuMarkdownRenderer !== true) return;
      if (msg.type === "task-toggle" && typeof msg.markdown === "string") {
        onMarkdownChangeRef.current?.(msg.markdown);
        return;
      }
      const next = Number(msg.height);
      if (!Number.isFinite(next)) return;
      const clamped = Math.min(10_000, Math.max(140, Math.round(next)));
      setHeight(clamped);
    };
    window.addEventListener("message", onMessage);
    return () => window.removeEventListener("message", onMessage);
  }, []);

  return (
    <iframe
      ref={iframeRef}
      className={className}
      src="/markdown-renderer/index.html"
      title="Markdown"
      style={{ height, overflow: "hidden" }}
    />
  );
}
