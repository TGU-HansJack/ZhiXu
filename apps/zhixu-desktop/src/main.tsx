import React from "react";
import ReactDOM from "react-dom/client";
import { App } from "./App";
import { devPerfMark, initDevPerfLogging } from "./lib/perf";
import "./styles.css";

initDevPerfLogging();
devPerfMark("zhixu:boot:main.tsx");

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
);
