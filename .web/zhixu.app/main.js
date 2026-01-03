(() => {
  const yearEl = document.getElementById("year");
  if (yearEl) yearEl.textContent = String(new Date().getFullYear());

  const initHighlighting = () => {
    const codeBlocks = document.querySelectorAll("pre code");
    if (codeBlocks.length === 0) return;

    if (window.__zhixuHighlightInit) return;
    window.__zhixuHighlightInit = true;

    const baseUrl = (() => {
      const scriptUrl = document.currentScript && document.currentScript.src ? document.currentScript.src : "";
      try {
        return new URL(".", scriptUrl || window.location.href);
      } catch {
        return new URL("./", window.location.href);
      }
    })();

    const ensureCss = () => {
      if (document.querySelector('link[data-zhixu-highlight="1"]')) return;
      const link = document.createElement("link");
      link.rel = "stylesheet";
      link.href = new URL("assets/highlight.css", baseUrl).toString();
      link.dataset.zhixuHighlight = "1";
      document.head.appendChild(link);
    };

    const run = () => {
      if (!window.hljs) return;
      codeBlocks.forEach((block) => window.hljs.highlightElement(block));
    };

    ensureCss();

    if (window.hljs) {
      run();
      return;
    }

    const script = document.createElement("script");
    script.src = new URL("assets/highlight.min.js", baseUrl).toString();
    script.defer = true;
    script.onload = run;
    document.head.appendChild(script);
  };

  initHighlighting();

  const editorHost = document.getElementById("editor");
  if (!editorHost || !window.CM) return;

  const { basicSetup, EditorView } = CM["codemirror"];
  const { markdown } = CM["@codemirror/lang-markdown"];
  const { oneDark } = CM["@codemirror/theme-one-dark"];

  const doc = `# Inbox

- [ ] 写一篇「原生优先」的说明 @id(01JH...)
- [ ] 会议：梳理同步协议边界 @id(01JH...)

## Notes
Zhixu（知序）把数据放在 Vault 里：
- docs/         笔记
- attachments/  附件
- .zhixu/       插件与内部数据

> 插件动作：module.exports.actions[actionId](context)
`;

  new EditorView({
    doc,
    extensions: [
      basicSetup,
      markdown(),
      oneDark,
      EditorView.editable.of(false),
      EditorView.theme({
        "&": { backgroundColor: "transparent" },
        ".cm-scroller": { padding: "10px 0" },
      }),
    ],
    parent: editorHost,
  });
})();
