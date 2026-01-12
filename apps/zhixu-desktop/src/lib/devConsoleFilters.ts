declare global {
  interface Window {
    __ZHIXU_DEV_CONSOLE_FILTERS_INSTALLED__?: true;
  }
}

function shouldFilterReactDevToolsMessage(args: unknown[]): boolean {
  if (!args.length) return false;
  try {
    const text = args
      .map((a) => (typeof a === "string" ? a : ""))
      .filter(Boolean)
      .join(" ");
    if (!text) return false;
    if (text.includes("Download the React DevTools")) return true;
    if (text.includes("reactjs.org/link/react-devtools")) return true;
  } catch (_) {}
  return false;
}

export function installDevConsoleFilters(): void {
  if (!import.meta.env.DEV) return;
  if (window.__ZHIXU_DEV_CONSOLE_FILTERS_INSTALLED__) return;
  window.__ZHIXU_DEV_CONSOLE_FILTERS_INSTALLED__ = true;

  try {
    const originalInfo = console.info.bind(console) as (...args: unknown[]) => void;
    console.info = (...args: unknown[]) => {
      if (shouldFilterReactDevToolsMessage(args)) return;
      originalInfo(...args);
    };
  } catch (_) {}
}

// Install immediately so it runs before noisy framework logs.
installDevConsoleFilters();

