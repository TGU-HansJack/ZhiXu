import "./lib/devConsoleFilters";
import React from "react";
import ReactDOM from "react-dom/client";
import { App } from "./App";
import { WidgetWindow } from "./WidgetWindow";
import { devPerfMark, initDevPerfLogging } from "./lib/perf";
import "./styles.css";

window.__zhixuBoot?.log("$ load: src/main.tsx");
window.__zhixuBoot?.step("Initializing...");

initDevPerfLogging();
devPerfMark("zhixu:boot:main.tsx");

const rootEl = document.getElementById("root")!;
devPerfMark("zhixu:react:got-root-el");
window.__zhixuBoot?.log("$ react: got #root");

const root = ReactDOM.createRoot(rootEl);
devPerfMark("zhixu:react:createRoot");
window.__zhixuBoot?.log("$ react: createRoot");

devPerfMark("zhixu:react:render-call");
window.__zhixuBoot?.step("Rendering...");
window.__zhixuBoot?.log("$ react: render");
const params = new URLSearchParams(window.location.search);
const view = params.get("view");

root.render(<React.StrictMode>{view === "widget" ? <WidgetWindow /> : <App />}</React.StrictMode>);
devPerfMark("zhixu:react:render-return");
window.__zhixuBoot?.log("$ react: render-return");
