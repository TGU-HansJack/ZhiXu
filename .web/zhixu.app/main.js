(() => {
  const yearEl = document.getElementById("year");
  if (yearEl) yearEl.textContent = String(new Date().getFullYear());

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

