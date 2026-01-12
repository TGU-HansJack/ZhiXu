import { defineConfig } from "vite";
import react from "@vitejs/plugin-react-swc";

export default defineConfig({
  plugins: [react()],
  clearScreen: false,
  optimizeDeps: {
    include: [
      "react",
      "react-dom",
      "react-dom/client",
      "react/jsx-runtime",
      "react/jsx-dev-runtime",
      "@tauri-apps/api",
      "@tauri-apps/api/event",
      "@tauri-apps/api/core",
      "@tauri-apps/api/webviewWindow",
      "@tauri-apps/api/window",
      "@tauri-apps/plugin-dialog",
      "codemirror",
      "@codemirror/state",
      "@codemirror/view",
      "@codemirror/commands",
      "@codemirror/lang-markdown",
    ],
    exclude: ["pdf-lib"],
    holdUntilCrawlEnd: false,
  },
  server: {
    host: "127.0.0.1",
    port: 1420,
    strictPort: true,
    preTransformRequests: false,
    warmup: {
      clientFiles: ["./src/main.tsx", "./src/App.tsx", "./src/styles.css"],
    },
  },
});
