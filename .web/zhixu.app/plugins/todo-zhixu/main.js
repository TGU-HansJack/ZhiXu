'use strict';

const TASK_LINE_REGEX = /^(\s*-\s*\[)([ xX])(\]\s+)(.*)$/;
const FIELD_REGEX = /@([a-zA-Z][a-zA-Z0-9_-]*)\(([^)]*)\)/g;
const ID_REGEX = /@id\(([^)]*)\)/;
const DONE_REGEX = /@done\(([^)]*)\)/;
const DUE_REGEX = /@due\(([^)]*)\)/;
const REMIND_REGEX = /@remind\(([^)]*)\)/i;
const REMIND_PERSIST_REGEX = /@remind_persist\(([^)]*)\)/i;
const PRIORITY_REGEX = /@priority\(([^)]*)\)/i;
const TAG_REGEX = /@tag\(([^)]*)\)/g;

const ULID_CHARS = '0123456789ABCDEFGHJKMNPQRSTVWXYZ';

function escapeHtml(text) {
  return String(text || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function normalizeRelPath(p) {
  return String(p || '')
    .replace(/\\/g, '/')
    .replace(/^\/+/, '')
    .replace(/\/+$/, '');
}

function basename(p) {
  const clean = normalizeRelPath(p);
  if (!clean) return '';
  const idx = clean.lastIndexOf('/');
  return idx >= 0 ? clean.slice(idx + 1) : clean;
}

function stripExtension(name) {
  const n = String(name || '');
  const idx = n.lastIndexOf('.');
  return idx > 0 ? n.slice(0, idx) : n;
}

function formatNowLocal() {
  const d = new Date();
  const pad = (v) => String(v).padStart(2, '0');
  return (
    d.getFullYear() +
    '-' +
    pad(d.getMonth() + 1) +
    '-' +
    pad(d.getDate()) +
    ' ' +
    pad(d.getHours()) +
    ':' +
    pad(d.getMinutes())
  );
}

function ulid() {
  const now = BigInt(Date.now());
  let time = '';
  for (let i = 9; i >= 0; i--) {
    time += ULID_CHARS[Number((now >> BigInt(i * 5)) & 31n)];
  }

  let randBytes;
  if (typeof crypto !== 'undefined' && crypto && typeof crypto.getRandomValues === 'function') {
    randBytes = new Uint8Array(10);
    crypto.getRandomValues(randBytes);
  } else {
    randBytes = new Uint8Array(10);
    for (let i = 0; i < 10; i++) randBytes[i] = Math.floor(Math.random() * 256);
  }

  let rand = 0n;
  for (let i = 0; i < randBytes.length; i++) rand = (rand << 8n) | BigInt(randBytes[i]);

  let out = time;
  for (let i = 15; i >= 0; i--) {
    out += ULID_CHARS[Number((rand >> BigInt(i * 5)) & 31n)];
  }
  return out;
}

function parseDueEpochMillis(due) {
  const raw = String(due || '').trim();
  if (!raw) return null;
  const m = raw.match(/^(\d{4})-(\d{2})-(\d{2})(?:[ T](\d{2}):(\d{2}))?$/);
  if (!m) return null;
  const year = Number(m[1]);
  const month = Number(m[2]) - 1;
  const day = Number(m[3]);
  const hour = m[4] ? Number(m[4]) : 0;
  const minute = m[5] ? Number(m[5]) : 0;
  const dt = new Date(year, month, day, hour, minute, 0, 0);
  const ms = dt.getTime();
  return Number.isFinite(ms) ? ms : null;
}

function parseTasks(markdown) {
  const lines = String(markdown || '').split('\n');
  const tasks = [];
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    const m = line.match(TASK_LINE_REGEX);
    if (!m) continue;

    const checked = String(m[2] || '').trim().toLowerCase() === 'x';
    const rest = String(m[4] || '');
    const id = (rest.match(ID_REGEX)?.[1] || '').trim() || null;
    const due = (rest.match(DUE_REGEX)?.[1] || '').trim() || null;
    const remind = (rest.match(REMIND_REGEX)?.[1] || '').trim() || null;
    const done = (rest.match(DONE_REGEX)?.[1] || '').trim() || null;

    const remindPersistRaw = (rest.match(REMIND_PERSIST_REGEX)?.[1] || '').trim();
    const remindPersistent = remindPersistRaw
      ? ['1', 'true', 'yes'].includes(remindPersistRaw.toLowerCase())
      : false;

    const tags = [];
    for (const t of rest.matchAll(TAG_REGEX)) {
      const v = String(t[1] || '').trim();
      if (v) tags.push(v);
    }

    let priority = null;
    const pRaw = (rest.match(PRIORITY_REGEX)?.[1] || '').trim();
    if (pRaw) {
      const n = parseInt(pRaw, 10);
      if (Number.isFinite(n)) priority = Math.min(4, Math.max(1, n));
    }

    const title = rest.replace(FIELD_REGEX, '').replace(/\s{2,}/g, ' ').trim();

    tasks.push({
      lineIndex: i,
      checked: checked,
      title: title,
      id: id,
      due: due,
      remind: remind,
      remindPersistent: remindPersistent,
      done: done,
      tags: tags,
      priority: priority,
    });
  }
  return tasks;
}

function toggleTaskAtLine(markdown, lineIndex) {
  const lines = String(markdown || '').split('\n');
  const idx = typeof lineIndex === 'number' ? lineIndex : -1;
  if (idx < 0 || idx >= lines.length) return markdown;
  const line = lines[idx];
  const m = line.match(TASK_LINE_REGEX);
  if (!m) return markdown;

  const prefix = m[1];
  const currentMark = m[2];
  const mid = m[3];
  let rest = String(m[4] || '');

  const currentlyChecked = String(currentMark || '').trim().toLowerCase() === 'x';
  const nextChecked = !currentlyChecked;
  const nextMark = nextChecked ? 'x' : ' ';

  if (!ID_REGEX.test(rest)) {
    rest = rest.trimEnd() + ' @id(' + ulid() + ')';
  }

  if (nextChecked) {
    if (!DONE_REGEX.test(rest)) {
      rest = rest.trimEnd() + ' @done(' + formatNowLocal() + ')';
    }
  } else {
    rest = rest.replace(DONE_REGEX, '').replace(/\s{2,}/g, ' ').trimEnd();
  }

  lines[idx] = prefix + nextMark + mid + rest;
  return lines.join('\n');
}

function getConfig(ctx) {
  const cfg = (ctx && ctx.config) || {};
  const inboxPath = normalizeRelPath(cfg.inboxPath || '待办.md');
  const ignorePrefixes = Array.isArray(cfg.ignorePrefixes) ? cfg.ignorePrefixes.map(normalizeRelPath) : ['.zhixu'];
  return { inboxPath: inboxPath || '待办.md', ignorePrefixes };
}

function shouldIgnorePath(relPath, ignorePrefixes) {
  const p = normalizeRelPath(relPath).toLowerCase();
  for (const raw of ignorePrefixes || []) {
    const pref = normalizeRelPath(raw).toLowerCase();
    if (!pref) continue;
    if (p === pref || p.startsWith(pref + '/')) return true;
  }
  return false;
}

async function loadCache(api, cachePath) {
  try {
    const bytes = await api.vault.readBytes(cachePath);
    const raw = decodeUtf8(bytes);
    const obj = JSON.parse(String(raw || ''));
    if (!obj || typeof obj !== 'object') throw new Error('bad cache');
    if (obj.version !== 1) throw new Error('bad cache version');
    if (!obj.files || typeof obj.files !== 'object') throw new Error('bad cache files');
    return obj;
  } catch {
    return { version: 1, files: {} };
  }
}

function decodeUtf8(bytes) {
  try {
    return new TextDecoder().decode(bytes);
  } catch {
    let out = '';
    const arr = bytes && bytes.length != null ? bytes : [];
    for (let i = 0; i < arr.length; i++) out += String.fromCharCode(arr[i] & 0xff);
    return out;
  }
}

function encodeUtf8(text) {
  try {
    return new TextEncoder().encode(String(text || ''));
  } catch {
    const s = String(text || '');
    const out = new Uint8Array(s.length);
    for (let i = 0; i < s.length; i++) out[i] = s.charCodeAt(i) & 0xff;
    return out;
  }
}

async function buildTaskIndex(ctx, api) {
  const cfg = getConfig(ctx);
  const cachePath = `.zhixu/plugins/${ctx.plugin.id}/tasks-cache.json`;
  const cache = await loadCache(api, cachePath);

  const list = await api.vault.walkFiles();
  const fileInfos = Array.isArray(list) ? list : [];

  const mdFiles = [];
  for (const fi of fileInfos) {
    const rel = normalizeRelPath(fi && fi.path);
    if (!rel) continue;
    if (shouldIgnorePath(rel, cfg.ignorePrefixes)) continue;
    if (!rel.toLowerCase().endsWith('.md')) continue;
    mdFiles.push({ path: rel, mtimeMs: Number(fi.mtimeMs || 0) });
  }

  const existing = new Set(mdFiles.map((f) => f.path));
  let changed = false;

  for (const info of mdFiles) {
    const prev = cache.files[info.path];
    if (prev && typeof prev === 'object' && Number(prev.mtimeMs || 0) === info.mtimeMs && Array.isArray(prev.tasks)) {
      continue;
    }
    let text = '';
    try {
      text = await api.vault.readText(info.path);
    } catch {
      continue;
    }
    const tasks = parseTasks(text);
    cache.files[info.path] = { mtimeMs: info.mtimeMs, tasks };
    changed = true;
  }

  for (const p of Object.keys(cache.files || {})) {
    if (!existing.has(p)) {
      delete cache.files[p];
      changed = true;
    }
  }

  if (changed) {
    await api.vault.writeBytes(cachePath, encodeUtf8(JSON.stringify(cache, null, 2)));
  }

  const all = [];
  for (const [path, entry] of Object.entries(cache.files || {})) {
    const tasks = Array.isArray(entry.tasks) ? entry.tasks : [];
    for (const t of tasks) {
      const dueEpochMillis = parseDueEpochMillis(t.due);
      all.push({
        path: path,
        fileName: stripExtension(basename(path)),
        lineIndex: t.lineIndex,
        checked: !!t.checked,
        title: String(t.title || ''),
        id: t.id || null,
        due: t.due || null,
        dueEpochMillis: dueEpochMillis,
        done: t.done || null,
        priority: t.priority == null ? null : Number(t.priority),
        tags: Array.isArray(t.tags) ? t.tags : [],
      });
    }
  }

  return { cfg, cachePath, tasks: all };
}

function renderSidebar(tasks) {
  const pending = tasks.filter((t) => !t.checked);
  const completed = tasks.filter((t) => t.checked);

  pending.sort((a, b) => {
    const ad = a.dueEpochMillis == null ? Number.POSITIVE_INFINITY : a.dueEpochMillis;
    const bd = b.dueEpochMillis == null ? Number.POSITIVE_INFINITY : b.dueEpochMillis;
    if (ad !== bd) return ad - bd;
    return a.title.localeCompare(b.title, undefined, { numeric: true, sensitivity: 'base' });
  });

  completed.sort((a, b) => {
    const ad = a.done || '';
    const bd = b.done || '';
    if (ad !== bd) return bd.localeCompare(ad);
    return b.title.localeCompare(a.title, undefined, { numeric: true, sensitivity: 'base' });
  });

  function metaLine(t) {
    const parts = [];
    if (t.due) parts.push(t.due);
    if (t.priority != null) parts.push('P' + t.priority);
    if (Array.isArray(t.tags) && t.tags.length) {
      const shown = t.tags.slice(0, 2).map((x) => '#' + x);
      const extra = t.tags.length - shown.length;
      parts.push(shown.join(' ') + (extra > 0 ? ' +' + extra : ''));
    }
    if (t.fileName) parts.push(t.fileName);
    return parts.join(' · ');
  }

  function taskItem(t) {
    const title = escapeHtml(t.title || '(无标题)');
    const meta = escapeHtml(metaLine(t));
    const checked = t.checked ? 'true' : 'false';
    return `
      <div class="todoItem" data-checked="${checked}">
        <button class="todoCheck" type="button" data-todo-action="toggle" data-path="${escapeHtml(t.path)}" data-line="${t.lineIndex}">
          ${t.checked ? '&#10003;' : ''}
        </button>
        <div class="todoBody">
          <div class="todoTitle" dir="auto">${title}</div>
          <div class="todoMeta" dir="auto">${meta}</div>
        </div>
      </div>
    `;
  }

  const pendingHtml = pending.slice(0, 300).map(taskItem).join('');
  const completedHtml = completed.slice(0, 120).map(taskItem).join('');

  const tagCounts = new Map();
  for (const t of tasks) {
    const tags = Array.isArray(t && t.tags) ? t.tags : [];
    for (const raw of tags) {
      const tag = String(raw || '').trim();
      if (!tag) continue;
      tagCounts.set(tag, (tagCounts.get(tag) || 0) + 1);
    }
  }
  const tagOptions = Array.from(tagCounts.entries())
    .sort((a, b) => (b[1] - a[1] ? b[1] - a[1] : a[0].localeCompare(b[0], undefined, { sensitivity: 'base' })))
    .slice(0, 16)
    .map(([tag]) => tag);

  const tagMenuHtml = tagOptions.length
    ? tagOptions
        .map((tag) => {
          const label = escapeHtml(tag);
          return `<button type="button" class="todoMenuItem" data-todo-action="tagToggle" data-tag="${label}" data-selected="false">
            <span class="todoMenuItemLabel">#${label}</span>
            <span class="todoMenuItemCheck" aria-hidden="true">&#10003;</span>
          </button>`;
        })
        .join('')
    : `<div class="todoMenuEmpty">暂无标签</div>`;

  return `
    <style>
      .todoWrap { --todoRadius: 4px; display: flex; flex-direction: column; gap: 10px; }
      .todoHeader { display:flex; align-items:center; justify-content: space-between; gap: 8px; }
      .todoTitleText { font-weight: 700; letter-spacing: .2px; }
      .todoCounts { color: var(--muted); font-size: 12px; }
      .todoBtn { border: 1px solid var(--border); background: rgba(0,0,0,.02); color: var(--text); border-radius: var(--todoRadius); padding: 4px 8px; cursor: pointer; }
      .todoBtn:hover { border-color: rgba(47,111,235,.45); }
      .todoAdd { display:flex; gap: 6px; }
      .todoInput { flex:1 1 auto; min-width:0; border:1px solid var(--border); background: rgba(0,0,0,.02); color: var(--text); border-radius: var(--todoRadius); padding: 6px 8px; outline: none; }
      .todoInput:focus { border-color: rgba(47,111,235,.6); box-shadow: 0 0 0 2px rgba(47,111,235,.15); }
      .todoSectionTitle { font-size: 12px; color: var(--muted); margin: 2px 0 0; }
      .todoList { display:flex; flex-direction: column; gap: 6px; }
      .todoItem { display:flex; gap: 8px; align-items:flex-start; border: 1px solid var(--border); border-radius: var(--todoRadius); padding: 8px; background: rgba(0,0,0,.015); }
      .todoCheck { width: 22px; height: 22px; flex: 0 0 auto; border-radius: var(--todoRadius); border: 1px solid var(--border); background: transparent; color: var(--accent); cursor: pointer; line-height: 1; }
      .todoCheck:hover { border-color: rgba(47,111,235,.6); }
      .todoBody { flex: 1 1 auto; min-width: 0; }
      .todoTitle { width: 100%; color: var(--text); text-align: left; font-size: 13px; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
      .todoMeta { margin-top: 4px; font-size: 12px; color: var(--muted); overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
      .todoItem[data-checked="true"] .todoTitle { text-decoration: line-through; color: var(--muted); }
      .todoEmpty { padding: 14px; border: 1px dashed var(--border); border-radius: var(--todoRadius); color: var(--muted); text-align: center; }
      details.todoDetails > summary { cursor: pointer; color: var(--muted); font-size: 12px; }
      details.todoDetails[open] > summary { margin-bottom: 8px; }

      .todoComposer { border: 1px solid var(--border); border-radius: var(--todoRadius); padding: 8px; background: rgba(0,0,0,.015); }
      .todoComposerTrigger { width: 100%; display:flex; align-items:center; gap: 8px; border: 1px dashed var(--border); border-radius: var(--todoRadius); padding: 8px; background: transparent; color: var(--muted); cursor: pointer; }
      .todoComposerTrigger:hover { border-color: rgba(47,111,235,.45); color: var(--text); }
      .todoComposerForm { display: flex; flex-direction: column; gap: 8px; }
      .todoComposerRow { display: flex; align-items:center; gap: 6px; }
      .todoComposerMeta { display: flex; flex-wrap: wrap; gap: 6px; }

      .todoField { position: relative; }
      .todoFieldBtn { display: inline-flex; align-items: center; gap: 8px; border: 1px solid var(--border); border-radius: var(--todoRadius); background: rgba(0,0,0,.02); color: var(--text); padding: 4px 8px; cursor: pointer; font-size: 12px; }
      .todoFieldBtn:hover { border-color: rgba(47,111,235,.45); }
      .todoFieldValue { color: var(--muted); max-width: 160px; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }

      .todoMenu[hidden] { display: none; }
      .todoMenu { position: absolute; left: 0; top: calc(100% + 6px); z-index: 999; min-width: 220px; max-width: 320px; border: 1px solid var(--border); border-radius: var(--todoRadius); background: var(--bg); box-shadow: 0 12px 30px rgba(0,0,0,.12); padding: 4px; }
      .todoMenuItem { width: 100%; display:flex; align-items:center; justify-content: space-between; gap: 10px; border: 0; border-radius: var(--todoRadius); background: transparent; color: var(--text); padding: 6px 8px; cursor: pointer; text-align: left; font-size: 12px; }
      .todoMenuItem:hover { background: var(--iconBgHover, rgba(0,0,0,.06)); }
      .todoMenuItem[data-selected="true"] { background: rgba(47,111,235,.12); color: var(--accent); }
      .todoMenuItemLabel { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
      .todoMenuItemCheck { opacity: 0; }
      .todoMenuItem[data-selected="true"] .todoMenuItemCheck { opacity: 1; }
      .todoMenuDivider { height: 1px; margin: 4px 0; background: var(--border); opacity: .8; }
      .todoMenuInput { width: 100%; box-sizing: border-box; border: 1px solid var(--border); border-radius: var(--todoRadius); background: rgba(0,0,0,.02); color: var(--text); padding: 6px 8px; outline: none; font-size: 12px; }
      .todoMenuInput:focus { border-color: rgba(47,111,235,.6); box-shadow: 0 0 0 2px rgba(47,111,235,.15); }
      .todoMenuEmpty { padding: 8px; color: var(--muted); font-size: 12px; }
    </style>

    <div class="todoWrap">
      <div class="todoHeader">
        <div>
          <div class="todoTitleText">待办</div>
          <div class="todoCounts">未完成 ${pending.length} · 已完成 ${completed.length}</div>
        </div>
        <button class="todoBtn" type="button" data-todo-action="refresh">刷新</button>
      </div>

      <div class="todoComposer" data-todo-composer="true" data-open="false">
        <button class="todoComposerTrigger" type="button" data-todo-action="composerOpen">+ 添加任务</button>

        <form class="todoComposerForm" data-todo-add="true" hidden>
          <div class="todoComposerRow">
            <input class="todoInput" name="title" type="text" autocomplete="off" spellcheck="false" placeholder="任务名称" />
            <button class="todoBtn" type="submit">添加</button>
            <button class="todoBtn todoComposerClose" type="button" data-todo-action="composerClose">取消</button>
          </div>

          <input type="hidden" name="due" value="" />
          <input type="hidden" name="priority" value="" />
          <input type="hidden" name="tags" value="" />

          <div class="todoComposerMeta">
            <div class="todoField">
              <button class="todoFieldBtn" type="button" data-todo-action="menuToggle" data-menu="due">
                日期 <span class="todoFieldValue" data-todo-value="due">无日期</span>
              </button>
              <div class="todoMenu" data-todo-menu="due" hidden>
                <button type="button" class="todoMenuItem" data-todo-action="duePreset" data-value="clear">
                  <span class="todoMenuItemLabel">无日期</span>
                </button>
                <button type="button" class="todoMenuItem" data-todo-action="duePreset" data-value="today">
                  <span class="todoMenuItemLabel">今天</span>
                </button>
                <button type="button" class="todoMenuItem" data-todo-action="duePreset" data-value="tomorrow">
                  <span class="todoMenuItemLabel">明天</span>
                </button>
                <button type="button" class="todoMenuItem" data-todo-action="duePreset" data-value="next7">
                  <span class="todoMenuItemLabel">7 天后</span>
                </button>
                <div class="todoMenuDivider"></div>
                <input class="todoMenuInput" type="date" data-todo-role="duePicker" />
              </div>
            </div>

            <div class="todoField">
              <button class="todoFieldBtn" type="button" data-todo-action="menuToggle" data-menu="priority">
                优先级 <span class="todoFieldValue" data-todo-value="priority">无优先级</span>
              </button>
              <div class="todoMenu" data-todo-menu="priority" hidden>
                <button type="button" class="todoMenuItem" data-todo-action="prioritySet" data-value="">
                  <span class="todoMenuItemLabel">无优先级</span>
                  <span class="todoMenuItemCheck" aria-hidden="true">&#10003;</span>
                </button>
                <button type="button" class="todoMenuItem" data-todo-action="prioritySet" data-value="1">
                  <span class="todoMenuItemLabel">P1</span>
                  <span class="todoMenuItemCheck" aria-hidden="true">&#10003;</span>
                </button>
                <button type="button" class="todoMenuItem" data-todo-action="prioritySet" data-value="2">
                  <span class="todoMenuItemLabel">P2</span>
                  <span class="todoMenuItemCheck" aria-hidden="true">&#10003;</span>
                </button>
                <button type="button" class="todoMenuItem" data-todo-action="prioritySet" data-value="3">
                  <span class="todoMenuItemLabel">P3</span>
                  <span class="todoMenuItemCheck" aria-hidden="true">&#10003;</span>
                </button>
                <button type="button" class="todoMenuItem" data-todo-action="prioritySet" data-value="4">
                  <span class="todoMenuItemLabel">P4</span>
                  <span class="todoMenuItemCheck" aria-hidden="true">&#10003;</span>
                </button>
              </div>
            </div>

            <div class="todoField">
              <button class="todoFieldBtn" type="button" data-todo-action="menuToggle" data-menu="tags">
                标签 <span class="todoFieldValue" data-todo-value="tags">无标签</span>
              </button>
              <div class="todoMenu" data-todo-menu="tags" hidden>
                ${tagMenuHtml}
                <div class="todoMenuDivider"></div>
                <input class="todoMenuInput" type="text" data-todo-role="tagInput" placeholder="输入标签并回车" />
              </div>
            </div>
          </div>
        </form>
      </div>

      <div class="todoSectionTitle">任务列表</div>
      <div class="todoList">
        ${pendingHtml || `<div class="todoEmpty">暂无未完成任务</div>`}
      </div>

      <details class="todoDetails">
        <summary>已完成（最近）</summary>
        <div class="todoList">
          ${completedHtml || `<div class="todoEmpty">暂无已完成任务</div>`}
        </div>
      </details>
    </div>
  `;
}

function renderEditorPlaceholder() {
  return `
    <style>
      .todoDash { height: 100%; box-sizing: border-box; padding: 16px; display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
      .todoCard { border: 1px solid var(--border); border-radius: 4px; background: rgba(0,0,0,.015); padding: 14px; }
      .todoCardTitle { font-weight: 700; margin-bottom: 6px; }
      .todoCardSub { color: var(--muted); font-size: 12px; }
    </style>
    <div class="todoDash">
      <div class="todoCard">
        <div class="todoCardTitle">日历</div>
        <div class="todoCardSub">占位：后续在此显示按日期聚合的任务。</div>
      </div>
      <div class="todoCard">
        <div class="todoCardTitle">四象限</div>
        <div class="todoCardSub">占位：后续在此显示按优先级（P1-P4）的任务象限。</div>
      </div>
    </div>
  `;
}

async function open(ctx, api) {
  const editorHtml = renderEditorPlaceholder();

  const html = `
    <div id="todoZhixuRoot" style="min-height: 120px;">
      <div style="color: var(--muted);">正在加载待办…</div>
    </div>
    <script>
      (function () {
        const root = document.getElementById('todoZhixuRoot');
        if (!root) return;

        function qsa(sel) {
          return Array.from(root.querySelectorAll(sel));
        }

        function closeAllMenus() {
          for (const m of qsa('[data-todo-menu]')) {
            m.hidden = true;
          }
        }

        function getComposer() {
          return root.querySelector('[data-todo-composer=\"true\"]');
        }

        function getComposerForm(composer) {
          const c = composer || getComposer();
          if (!c) return null;
          return c.querySelector('form[data-todo-add=\"true\"]');
        }

        function setComposerOpen(isOpen) {
          const composer = getComposer();
          if (!composer) return;
          composer.setAttribute('data-open', isOpen ? 'true' : 'false');

          const trigger = composer.querySelector('.todoComposerTrigger');
          const form = getComposerForm(composer);
          if (trigger) trigger.hidden = !!isOpen;
          if (form) form.hidden = !isOpen;
          if (!isOpen) closeAllMenus();

          if (isOpen && form) {
            const titleInput = form.querySelector('input[name=\"title\"]');
            if (titleInput && typeof titleInput.focus === 'function') titleInput.focus();
            syncComposerUi(form);
          }
        }

        function getHiddenInput(form, name) {
          if (!form) return null;
          return form.querySelector('input[name=\"' + name + '\"]');
        }

        function readTags(form) {
          const el = getHiddenInput(form, 'tags');
          const raw = el ? String(el.value || '') : '';
          return raw
            .split(',')
            .map((x) => String(x || '').trim())
            .filter(Boolean);
        }

        function writeTags(form, tags) {
          const el = getHiddenInput(form, 'tags');
          if (!el) return;
          const uniq = Array.from(new Set((tags || []).map((x) => String(x || '').trim()).filter(Boolean)));
          el.value = uniq.join(',');
        }

        function setValueLabel(form, name, text) {
          const el = form.querySelector('[data-todo-value=\"' + name + '\"]');
          if (el) el.textContent = text;
        }

        function formatYmd(d) {
          const pad = (v) => String(v).padStart(2, '0');
          return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
        }

        function syncComposerUi(form) {
          if (!form) return;
          const due = String(getHiddenInput(form, 'due')?.value || '').trim();
          const priority = String(getHiddenInput(form, 'priority')?.value || '').trim();
          const tags = readTags(form);

          setValueLabel(form, 'due', due || '无日期');
          setValueLabel(form, 'priority', priority ? 'P' + priority : '无优先级');

          if (!tags.length) setValueLabel(form, 'tags', '无标签');
          else {
            const shown = tags.slice(0, 2).map((t) => '#' + t);
            const extra = tags.length - shown.length;
            setValueLabel(form, 'tags', shown.join(' ') + (extra > 0 ? ' +' + extra : ''));
          }

          const duePicker = form.querySelector('[data-todo-role=\"duePicker\"]');
          if (duePicker) {
            duePicker.value = due && /^\d{4}-\d{2}-\d{2}$/.test(due) ? due : '';
          }

          for (const btn of qsa('[data-todo-action=\"prioritySet\"]')) {
            const v = String(btn.getAttribute('data-value') || '').trim();
            btn.setAttribute('data-selected', v === priority ? 'true' : 'false');
          }

          for (const btn of qsa('[data-todo-action=\"tagToggle\"]')) {
            const tag = String(btn.getAttribute('data-tag') || '').trim();
            btn.setAttribute('data-selected', tags.includes(tag) ? 'true' : 'false');
          }
        }

        async function render() {
          try {
            const res = await window.ZhixuPlugin.runAction('render');
            let html = '';
            if (typeof res === 'string') html = res;
            else if (res && typeof res === 'object') {
              if (typeof res.html === 'string') html = res.html;
              else if (typeof res.message === 'string') html = '<pre>' + String(res.message) + '</pre>';
            }
            root.innerHTML = html || '<div style="color: var(--muted);">暂无内容</div>';
          } catch (e) {
            root.innerHTML = '<pre style="color: var(--muted); white-space: pre-wrap;">' + String(e && e.message ? e.message : e) + '</pre>';
          }
        }

        document.addEventListener('submit', function (ev) {
          const form = ev.target;
          if (!form || !form.matches || !form.matches('form[data-todo-add=\"true\"]')) return;
          ev.preventDefault();
          const input = form.querySelector('input[name=\"title\"]');
          const title = input ? String(input.value || '').trim() : '';
          if (!title) return;
          const due = String(getHiddenInput(form, 'due')?.value || '').trim() || null;
          const priorityRaw = String(getHiddenInput(form, 'priority')?.value || '').trim();
          const priority = priorityRaw ? parseInt(priorityRaw, 10) : null;
          const tags = readTags(form);

          if (input) input.value = '';
          const dueEl = getHiddenInput(form, 'due');
          const priorityEl = getHiddenInput(form, 'priority');
          if (dueEl) dueEl.value = '';
          if (priorityEl) priorityEl.value = '';
          writeTags(form, []);
          syncComposerUi(form);

          setComposerOpen(false);
          window.ZhixuPlugin.runAction('addTask', { title: title, due: due, priority: priority, tags: tags }).then(render).catch(render);
        });

        document.addEventListener('click', function (ev) {
          const el = ev.target && ev.target.closest ? ev.target.closest('[data-todo-action]') : null;
          if (!el) return;
          const action = el.getAttribute('data-todo-action');
          const path = el.getAttribute('data-path') || '';
          const line = parseInt(el.getAttribute('data-line') || '', 10);
          const menu = el.getAttribute('data-menu') || '';
          const value = el.getAttribute('data-value') || '';

          if (action === 'refresh') {
            render();
            return;
          }
          if (action === 'toggle' && path && Number.isFinite(line)) {
            window.ZhixuPlugin.runAction('toggleTask', { path: path, lineIndex: line }).then(render).catch(render);
            return;
          }
          if (action === 'composerOpen') {
            setComposerOpen(true);
            return;
          }
          if (action === 'composerClose') {
            setComposerOpen(false);
            return;
          }
          if (action === 'menuToggle') {
            const composer = getComposer();
            const form = getComposerForm(composer);
            if (!composer || !form || !menu) return;

            const target = composer.querySelector('[data-todo-menu=\"' + menu + '\"]');
            if (!target) return;
            const next = !!target.hidden;
            closeAllMenus();
            target.hidden = !next;
            syncComposerUi(form);
            return;
          }
          if (action === 'duePreset') {
            const form = getComposerForm();
            if (!form) return;
            const dueEl = getHiddenInput(form, 'due');
            if (!dueEl) return;

            if (value === 'clear') dueEl.value = '';
            else if (value === 'today') dueEl.value = formatYmd(new Date());
            else if (value === 'tomorrow') {
              const d = new Date();
              d.setDate(d.getDate() + 1);
              dueEl.value = formatYmd(d);
            } else if (value === 'next7') {
              const d = new Date();
              d.setDate(d.getDate() + 7);
              dueEl.value = formatYmd(d);
            }

            syncComposerUi(form);
            closeAllMenus();
            return;
          }
          if (action === 'prioritySet') {
            const form = getComposerForm();
            if (!form) return;
            const pEl = getHiddenInput(form, 'priority');
            if (!pEl) return;
            pEl.value = String(value || '').trim();
            syncComposerUi(form);
            closeAllMenus();
            return;
          }
          if (action === 'tagToggle') {
            const form = getComposerForm();
            if (!form) return;
            const tag = String(el.getAttribute('data-tag') || '').trim();
            if (!tag) return;
            const tags = readTags(form);
            if (tags.includes(tag)) writeTags(form, tags.filter((t) => t !== tag));
            else writeTags(form, tags.concat([tag]));
            syncComposerUi(form);
            return;
          }
        });

        document.addEventListener('change', function (ev) {
          const input = ev.target;
          if (!input || !input.matches || !input.matches('[data-todo-role=\"duePicker\"]')) return;
          const form = input.closest('form[data-todo-add=\"true\"]');
          if (!form) return;
          const dueEl = getHiddenInput(form, 'due');
          if (!dueEl) return;
          dueEl.value = String(input.value || '').trim();
          syncComposerUi(form);
          closeAllMenus();
        });

        document.addEventListener('keydown', function (ev) {
          const input = ev.target;
          if (!input || !input.matches || !input.matches('[data-todo-role=\"tagInput\"]')) return;
          if (ev.key !== 'Enter') return;
          ev.preventDefault();
          const form = input.closest('form[data-todo-add=\"true\"]');
          if (!form) return;
          const tag = String(input.value || '').trim();
          if (!tag) return;
          input.value = '';
          const tags = readTags(form);
          if (!tags.includes(tag)) writeTags(form, tags.concat([tag]));
          syncComposerUi(form);
        });

        document.addEventListener('mousedown', function (ev) {
          const t = ev.target;
          if (t && t.closest && t.closest('[data-todo-menu], [data-todo-action=\"menuToggle\"]')) return;
          closeAllMenus();
        });

        document.addEventListener('keydown', function (ev) {
          if (ev.key !== 'Escape') return;
          closeAllMenus();
        });

        render();
      })();
    </script>
  `;

  return { title: '待办', html: html, editorTitle: '待办', editorHtml: editorHtml };
}

async function render(ctx, api) {
  const idx = await buildTaskIndex(ctx, api);
  const html = renderSidebar(idx.tasks);
  return { ok: true, html: html };
}

async function addTask(ctx, api, input) {
  const cfg = getConfig(ctx);
  const title = String((input && input.title) || '').trim();
  if (!title) return { ok: false, message: '任务内容为空' };

  let due = String(input && input.due != null ? input.due : '').trim();
  if (due) due = due.replace(/[\r\n]/g, ' ').replace(/[()]/g, '').trim();
  if (!due) due = null;

  let priority = null;
  const priorityRaw = String(input && input.priority != null ? input.priority : '').trim();
  if (priorityRaw) {
    const n = parseInt(priorityRaw, 10);
    if (Number.isFinite(n)) priority = Math.min(4, Math.max(1, n));
  }

  let tags = [];
  const tagsRaw = input && input.tags;
  if (Array.isArray(tagsRaw)) tags = tagsRaw;
  else if (typeof tagsRaw === 'string') tags = tagsRaw.split(',');
  tags = tags
    .map((t) => String(t || '').trim())
    .map((t) => t.replace(/[()]/g, '').trim())
    .filter(Boolean);
  tags = Array.from(new Set(tags));

  const inbox = cfg.inboxPath || '待办.md';
  let before = '';
  try {
    before = await api.vault.readText(inbox);
  } catch {
    before = '';
  }

  let line = title;
  if (!TASK_LINE_REGEX.test(line)) {
    line = '- [ ] ' + line;
  }
  if (!ID_REGEX.test(line)) {
    line = line.trimEnd() + ' @id(' + ulid() + ')';
  }

  if (due && !DUE_REGEX.test(line)) {
    line = line.trimEnd() + ' @due(' + due + ')';
  }
  if (priority != null && !PRIORITY_REGEX.test(line)) {
    line = line.trimEnd() + ' @priority(' + String(priority) + ')';
  }
  if (tags.length) {
    const existing = new Set();
    for (const m of line.matchAll(TAG_REGEX)) {
      const v = String(m[1] || '').trim();
      if (v) existing.add(v);
    }
    for (const t of tags) {
      if (existing.has(t)) continue;
      line = line.trimEnd() + ' @tag(' + t + ')';
    }
  }

  const out = (before ? before.replace(/\s+$/, '') + '\n' : '') + line + '\n';
  await api.vault.writeText(inbox, out);
  return { ok: true };
}

async function toggleTask(ctx, api, input) {
  const path = normalizeRelPath(input && input.path);
  const lineIndex = Number(input && input.lineIndex);
  if (!path || !Number.isFinite(lineIndex)) return { ok: false, message: '参数错误' };
  const before = await api.vault.readText(path);
  const after = toggleTaskAtLine(before, Math.floor(lineIndex));
  if (after !== before) {
    await api.vault.writeText(path, after);
  }
  return { ok: true };
}

async function clearCache(ctx, api) {
  const cachePath = `.zhixu/plugins/${ctx.plugin.id}/tasks-cache.json`;
  try {
    await api.vault.deleteEntry(cachePath);
  } catch {
    // ignore
  }
  return { ok: true };
}

module.exports = {
  actions: {
    open,
    render,
    addTask,
    toggleTask,
    clearCache,
  },
};
