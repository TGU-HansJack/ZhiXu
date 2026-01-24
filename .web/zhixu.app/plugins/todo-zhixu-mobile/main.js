'use strict';

const TASK_LINE_REGEX = /^(\s*-\s*\[)([ xX])(\]\s+)(.*)$/;
const FIELD_REGEX = /@([a-zA-Z][a-zA-Z0-9_-]*)\(([^)]*)\)/g;
const ID_REGEX = /@id\(([^)]*)\)/;
const DONE_REGEX = /@done\(([^)]*)\)/;
const DUE_REGEX = /@due\(([^)]*)\)/;

const ULID_CHARS = '0123456789ABCDEFGHJKMNPQRSTVWXYZ';

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

function escapeHtml(text) {
  return String(text || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\"/g, '&quot;')
    .replace(/'/g, '&#39;');
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

function shouldIgnorePath(relPath, ignorePrefixes) {
  const p = normalizeRelPath(relPath).toLowerCase();
  for (const raw of ignorePrefixes || []) {
    const pref = normalizeRelPath(raw).toLowerCase();
    if (!pref) continue;
    if (p === pref || p.startsWith(pref + '/')) return true;
  }
  return false;
}

function getConfig(ctx) {
  const cfg = (ctx && ctx.config) || {};
  const ignorePrefixes = Array.isArray(cfg.ignorePrefixes) ? cfg.ignorePrefixes.map(normalizeRelPath) : ['.zhixu'];
  const enableCache = typeof cfg.enableCache === 'boolean' ? cfg.enableCache : true;
  const maxFiles = Number.isFinite(cfg.maxFiles) ? Math.max(1, Math.floor(cfg.maxFiles)) : 8000;
  const maxTasks = Number.isFinite(cfg.maxTasks) ? Math.max(1, Math.floor(cfg.maxTasks)) : 300;
  return { ignorePrefixes, enableCache, maxFiles, maxTasks };
}

function cachePath(ctx) {
  const id = (ctx && ctx.plugin && ctx.plugin.id) || 'todo-zhixu-mobile';
  return '.zhixu/plugins/' + String(id) + '/tasks-cache.json';
}

function loadCache(ctx) {
  const path = cachePath(ctx);
  try {
    const raw = api.vault.readText(path);
    const obj = JSON.parse(String(raw || ''));
    if (!obj || typeof obj !== 'object') throw new Error('bad cache');
    if (obj.version !== 1) throw new Error('bad cache version');
    if (!obj.files || typeof obj.files !== 'object') throw new Error('bad cache files');
    return obj;
  } catch {
    return { version: 1, files: {} };
  }
}

function saveCache(ctx, cache) {
  const path = cachePath(ctx);
  try {
    api.vault.writeText(path, JSON.stringify(cache, null, 2));
  } catch (e) {
    api.log('saveCache failed:', String(e && e.message ? e.message : e));
  }
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
    const title = rest.replace(FIELD_REGEX, '').replace(/\s{2,}/g, ' ').trim();

    tasks.push({
      lineIndex: i,
      checked,
      title: title || '(无标题)',
      id,
      due,
      rawLine: line,
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

function buildTodoPage(ctx) {
  const conf = getConfig(ctx);

  const cache = conf.enableCache ? loadCache(ctx) : { version: 1, files: {} };
  const cacheFiles = cache.files || {};

  const files = api.vault.walkFiles({
    includeNonMarkdownFiles: false,
    includeHidden: false,
    includeInternal: false,
    maxEntries: conf.maxFiles,
  });

  const seen = Object.create(null);
  const items = [];

  for (const f of files || []) {
    const relPath = normalizeRelPath(f && f.relativePath ? f.relativePath : '');
    if (!relPath || relPath.endsWith('/')) continue;
    if (shouldIgnorePath(relPath, conf.ignorePrefixes)) continue;

    const uri = String((f && f.uri) || '').trim();
    if (!uri) continue;

    const mtime = Number((f && f.lastModified) || 0) || 0;
    const size = Number((f && f.size) || 0) || 0;
    const name = String((f && f.name) || basename(relPath) || relPath).trim();

    seen[relPath] = true;

    let entry = cacheFiles[relPath];
    let tasks;
    if (conf.enableCache && entry && entry.lastModified === mtime && entry.size === size && Array.isArray(entry.tasks)) {
      tasks = entry.tasks;
    } else {
      const text = api.vault.readTextUri(uri);
      tasks = parseTasks(text);
      if (conf.enableCache) {
        cacheFiles[relPath] = { lastModified: mtime, size: size, name: name, uri: uri, tasks: tasks };
      }
    }

    for (const t of tasks || []) {
      items.push({
        title: String(t && t.title ? t.title : '(无标题)'),
        subtitle: name,
        checked: !!(t && t.checked),
        docUri: uri,
        lineIndex: typeof t.lineIndex === 'number' ? t.lineIndex : null,
        toggleActionId: 'toggleTask',
      });
      if (items.length >= conf.maxTasks) break;
    }
    if (items.length >= conf.maxTasks) break;
  }

  if (conf.enableCache) {
    for (const k of Object.keys(cacheFiles)) {
      if (!seen[k]) delete cacheFiles[k];
    }
    cache.files = cacheFiles;
    saveCache(ctx, cache);
  }

  items.sort((a, b) => {
    const ac = a.checked ? 1 : 0;
    const bc = b.checked ? 1 : 0;
    if (ac !== bc) return ac - bc;
    return String(a.subtitle || '').localeCompare(String(b.subtitle || '')) || String(a.title || '').localeCompare(String(b.title || ''));
  });

  return {
    title: '待办列表',
    items: items,
  };
}

function page(ctx) {
  try {
    const out = buildTodoPage(ctx);
    return { ok: true, page: out };
  } catch (e) {
    const msg = e && e.message ? String(e.message) : String(e);
    return { ok: false, message: msg };
  }
}

function toggleTask(ctx) {
  const input = (ctx && ctx.input) || {};
  const docUri = String(input.docUri || '').trim();
  const lineIndexRaw = input.lineIndex;
  const lineIndex = typeof lineIndexRaw === 'number' ? (lineIndexRaw | 0) : parseInt(String(lineIndexRaw || ''), 10);
  if (!docUri) return { ok: false, message: '缺少 docUri' };
  if (!Number.isFinite(lineIndex)) return { ok: false, message: '缺少 lineIndex' };

  const before = api.vault.readTextUri(docUri);
  const after = toggleTaskAtLine(before, lineIndex);
  if (after === before) return { ok: false, message: '不是有效的任务行' };
  const ok = api.vault.writeTextUri(docUri, after);
  return { ok: !!ok, message: ok ? '已更新' : '写入失败', changedDocUri: docUri };
}

module.exports = {
  actions: {
    page: page,
    toggleTask: toggleTask,
  },
};

