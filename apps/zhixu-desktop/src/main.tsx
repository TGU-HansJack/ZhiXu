import "./lib/devConsoleFilters";
import React from "react";
import ReactDOM from "react-dom/client";
import { App } from "./App";
import { devPerfMark, initDevPerfLogging } from "./lib/perf";
import "./styles.css";

initDevPerfLogging();
devPerfMark("zhixu:boot:main.tsx");

const rootEl = document.getElementById("root")!;
devPerfMark("zhixu:react:got-root-el");

const root = ReactDOM.createRoot(rootEl);
devPerfMark("zhixu:react:createRoot");

devPerfMark("zhixu:react:render-call");
root.render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
devPerfMark("zhixu:react:render-return");
