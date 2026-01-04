(() => {
  const navHost = document.getElementById("update-nav");
  const contentHost = document.getElementById("update-content");
  if (!navHost || !contentHost) return;

  const normalizeVersion = (v) => String(v || "").trim().replace(/^[vV]/, "");

  const fetchText = async (url) => {
    const resp = await fetch(url, { cache: "no-cache" });
    if (!resp.ok) throw new Error(`Failed to fetch: ${url}`);
    return await resp.text();
  };

  const ensureMarkdownIt = () => {
    if (!window.markdownit) throw new Error("markdown-it not loaded");
    return window.markdownit({
      html: false,
      linkify: true,
      breaks: true,
      typographer: true,
    });
  };

  const setHashIfEmpty = (latestVersion) => {
    const hash = (window.location.hash || "").replace(/^#/, "");
    if (hash) return;
    if (!latestVersion) return;
    history.replaceState(null, "", `#${latestVersion}`);
  };

  const scrollToHash = () => {
    const hash = (window.location.hash || "").replace(/^#/, "");
    if (!hash) return;
    const id = normalizeVersion(hash);
    const el = document.getElementById(id);
    if (!el) return;
    el.scrollIntoView({ behavior: "smooth", block: "start" });
  };

  const renderNav = (versions) => {
    navHost.innerHTML = "";
    const ul = document.createElement("ul");
    versions.forEach((v) => {
      const ver = normalizeVersion(v);
      const li = document.createElement("li");
      const a = document.createElement("a");
      a.href = `#${ver}`;
      a.textContent = ver;
      li.appendChild(a);
      ul.appendChild(li);
    });
    navHost.appendChild(ul);
  };

  const renderAll = async () => {
    const md = ensureMarkdownIt();

    const updateJson = JSON.parse(await fetchText("./update.json"));
    const latestVersion = normalizeVersion(updateJson.latestVersion);
    const versions =
      (Array.isArray(updateJson.versions) && updateJson.versions.length > 0
        ? updateJson.versions
        : latestVersion
          ? [latestVersion]
          : []
      ).map(normalizeVersion);

    renderNav(versions);
    setHashIfEmpty(latestVersion);

    contentHost.innerHTML = "";

    for (const v of versions) {
      const section = document.createElement("section");
      section.id = v;

      const text = await fetchText(`./${encodeURIComponent(v)}.md`);
      section.innerHTML = md.render(text);

      const divider = document.createElement("hr");
      contentHost.appendChild(section);
      contentHost.appendChild(divider);
    }

    scrollToHash();
  };

  renderAll().catch((err) => {
    contentHost.innerHTML = `<p class="muted">加载更新日志失败：${String(err && err.message ? err.message : err)}</p>`;
  });

  window.addEventListener("hashchange", () => scrollToHash());
})();

