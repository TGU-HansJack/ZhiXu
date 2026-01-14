'use strict';

function normalizePosixPath(p) {
  return String(p || '')
    .replace(/\\/g, '/')
    .replace(/^\/+/, '')
    .replace(/\/+$/, '');
}

function normalizeBaseUrl(url) {
  return String(url || '').trim().replace(/\/+$/, '');
}

function normalizeRemoteRoot(root) {
  let out = String(root || '').trim();
  if (!out) out = '/';
  if (!out.startsWith('/')) out = '/' + out;
  out = out.replace(/\/+$/, '');
  return out || '/';
}

function joinRemoteRootUrl(baseUrl, remoteRoot) {
  const base = normalizeBaseUrl(baseUrl);
  const rr = normalizeRemoteRoot(remoteRoot);
  return base + rr + '/';
}

function toBase64Utf8(s) {
  const raw = String(s || '');
  if (typeof btoa === 'function') {
    return btoa(unescape(encodeURIComponent(raw)));
  }
  throw new Error('btoa not available');
}

function makeAuthHeader(username, password) {
  const u = String(username || '');
  const p = String(password || '');
  if (!u && !p) return null;
  return 'Basic ' + toBase64Utf8(u + ':' + p);
}

function shouldIgnorePath(path, ignorePrefixes) {
  const p = normalizePosixPath(path);
  if (!p) return false;
  const prefixes = Array.isArray(ignorePrefixes) ? ignorePrefixes : [];
  for (const raw of prefixes) {
    const pref = normalizePosixPath(raw);
    if (!pref) continue;
    if (pref.endsWith('/')) {
      if ((p + '/').startsWith(pref)) return true;
    } else {
      if (p === pref || (p + '/').startsWith(pref + '/')) return true;
    }
  }
  return false;
}

function buildRemoteUrl(remoteRootUrl, relPath, isDir) {
  const clean = normalizePosixPath(relPath);
  if (!clean) return remoteRootUrl;
  const parts = clean.split('/').filter(Boolean).map(encodeURIComponent);
  const base = remoteRootUrl.replace(/\/+$/, '') + '/';
  return base + parts.join('/') + (isDir ? '/' : '');
}

function decodeXmlText(bytes) {
  try {
    return new TextDecoder().decode(bytes);
  } catch {
    let out = '';
    for (let i = 0; i < bytes.length; i++) out += String.fromCharCode(bytes[i] & 0xff);
    return out;
  }
}

function parsePropfindResponse(xmlText, remoteRootUrl) {
  const parser = new DOMParser();
  const doc = parser.parseFromString(String(xmlText || ''), 'text/xml');
  const responses = Array.from(doc.getElementsByTagNameNS('*', 'response'));
  const rootPath = new URL(remoteRootUrl).pathname;

  const out = [];
  for (const r of responses) {
    const hrefNode =
      r.getElementsByTagNameNS('*', 'href')[0] ||
      r.getElementsByTagName('href')[0];
    const href = hrefNode ? String(hrefNode.textContent || '').trim() : '';
    if (!href) continue;

    let u;
    try {
      u = new URL(href, remoteRootUrl);
    } catch {
      continue;
    }
    let pathname = u.pathname || '';
    if (pathname.toLowerCase().startsWith(rootPath.toLowerCase())) {
      pathname = pathname.slice(rootPath.length);
    }
    let rel = decodeURIComponent(pathname || '').replace(/^\/+/, '');
    const isSelf = rel === '' || rel === '/';

    const isDir =
      /\/$/.test(String(href)) ||
      r.getElementsByTagNameNS('DAV:', 'collection').length > 0 ||
      r.getElementsByTagName('collection').length > 0;

    rel = rel.replace(/\/+$/, '');
    if (isSelf) continue;

    const lenNode =
      r.getElementsByTagNameNS('*', 'getcontentlength')[0] ||
      r.getElementsByTagName('getcontentlength')[0];
    const size = lenNode ? Number(String(lenNode.textContent || '').trim()) : NaN;

    const lmNode =
      r.getElementsByTagNameNS('*', 'getlastmodified')[0] ||
      r.getElementsByTagName('getlastmodified')[0];
    const lastModified = lmNode ? String(lmNode.textContent || '').trim() : '';

    out.push({
      path: rel,
      isDir: !!isDir,
      size: Number.isFinite(size) ? size : null,
      lastModified: lastModified || null,
    });
  }
  return out;
}

async function propfind(api, url, depth, authHeader) {
  const body =
    "<?xml version='1.0' encoding='utf-8' ?>" +
    "<D:propfind xmlns:D='DAV:'>" +
    "<D:prop>" +
    "<D:resourcetype/>" +
    "<D:getcontentlength/>" +
    "<D:getlastmodified/>" +
    "</D:prop>" +
    "</D:propfind>";

  const headers = { Depth: String(depth), 'Content-Type': 'text/xml; charset=utf-8' };
  if (authHeader) headers['Authorization'] = authHeader;

  const resp = await api.httpRaw({ method: 'PROPFIND', url, headers, body, timeoutMs: 30_000 });
  return { ...resp, text: decodeXmlText(resp.bytes) };
}

async function mkcol(api, url, authHeader) {
  const headers = {};
  if (authHeader) headers['Authorization'] = authHeader;
  const resp = await api.httpRaw({ method: 'MKCOL', url, headers, timeoutMs: 30_000 });
  // 201 created, 405 already exists, 409 conflict (parent missing) etc
  return resp.status;
}

async function putBytes(api, url, bytes, authHeader) {
  const headers = { 'Content-Type': 'application/octet-stream' };
  if (authHeader) headers['Authorization'] = authHeader;
  const resp = await api.httpRaw({ method: 'PUT', url, headers, body: bytes, timeoutMs: 60_000 });
  return resp.status;
}

async function getBytes(api, url, authHeader) {
  const headers = {};
  if (authHeader) headers['Authorization'] = authHeader;
  const resp = await api.httpRaw({ method: 'GET', url, headers, timeoutMs: 60_000 });
  if (!resp.ok) throw new Error('HTTP ' + resp.status);
  return resp.bytes;
}

async function listRemoteFiles(api, remoteRootUrl, authHeader, ignorePrefixes) {
  const files = new Map();
  const queue = [remoteRootUrl];

  while (queue.length) {
    const dirUrl = queue.shift();
    const r = await propfind(api, dirUrl, '1', authHeader);
    if (!(r.status >= 200 && r.status < 300) && r.status !== 207) {
      throw new Error('PROPFIND failed: HTTP ' + r.status);
    }
    const entries = parsePropfindResponse(r.text, remoteRootUrl);
    for (const e of entries) {
      if (shouldIgnorePath(e.path, ignorePrefixes)) continue;
      if (e.isDir) {
        queue.push(buildRemoteUrl(remoteRootUrl, e.path, true));
      } else {
        files.set(e.path, e);
      }
    }
  }

  return files;
}

async function listLocalFiles(api, ignorePrefixes) {
  const out = [];
  async function walk(dirRel) {
    const entries = await api.vault.listDir(dirRel);
    for (const e of entries) {
      const path = normalizePosixPath(e.path);
      if (shouldIgnorePath(path, ignorePrefixes)) continue;
      if (e.isDir) {
        await walk(path);
      } else {
        out.push({ path });
      }
    }
  }
  await walk('');
  return out;
}

async function ensureRemoteDirs(api, remoteRootUrl, authHeader, relPath, ensured) {
  const clean = normalizePosixPath(relPath);
  if (!clean) return;
  const parts = clean.split('/').filter(Boolean);
  let cur = '';
  for (let i = 0; i < parts.length - 1; i++) {
    cur = cur ? cur + '/' + parts[i] : parts[i];
    const key = cur.toLowerCase();
    if (ensured.has(key)) continue;
    ensured.add(key);
    const url = buildRemoteUrl(remoteRootUrl, cur, true);
    const code = await mkcol(api, url, authHeader);
    if (!(code >= 200 && code < 300) && code !== 405) {
      // ignore best-effort
    }
  }
}

async function testConnection(ctx, api) {
  const cfg = (ctx && ctx.config) || {};
  if (!cfg.enabled) return { ok: false, message: 'WebDAV 未启用（config.enabled=false）' };
  if (!cfg.baseUrl) return { ok: false, message: '请先配置 config.baseUrl' };

  const remoteRootUrl = joinRemoteRootUrl(cfg.baseUrl, cfg.remoteRoot);
  const auth = makeAuthHeader(cfg.username, cfg.password);
  const r = await propfind(api, remoteRootUrl, '0', auth);
  const ok = (r.status >= 200 && r.status < 300) || r.status === 207;
  return { ok, message: ok ? '连接成功（HTTP ' + r.status + '）' : '连接失败（HTTP ' + r.status + '）' };
}

async function syncNow(ctx, api) {
  const cfg = (ctx && ctx.config) || {};
  if (!cfg.enabled) return { ok: false, message: 'WebDAV 未启用（config.enabled=false）' };
  if (!cfg.baseUrl) return { ok: false, message: '请先配置 config.baseUrl' };

  const ignore = Array.isArray(cfg.ignore) ? cfg.ignore : ['.zhixu/', '.git/', '.obsidian/'];
  const auth = makeAuthHeader(cfg.username, cfg.password);
  const remoteRootUrl = joinRemoteRootUrl(cfg.baseUrl, cfg.remoteRoot);

  api.log('remoteRootUrl:', remoteRootUrl);

  // Ensure remote root exists.
  const probe = await propfind(api, remoteRootUrl, '0', auth);
  if (!((probe.status >= 200 && probe.status < 300) || probe.status === 207)) {
    api.log('remote root not ready, MKCOL...');
    await mkcol(api, remoteRootUrl, auth);
  }

  const ensuredDirs = new Set();
  const localFiles = await listLocalFiles(api, ignore);
  const remoteFiles = await listRemoteFiles(api, remoteRootUrl, auth, ignore);

  const localSet = new Set(localFiles.map((f) => f.path));
  let uploaded = 0;
  let downloaded = 0;
  let failed = 0;

  // Upload local files (best-effort overwrite when size differs).
  for (const f of localFiles) {
    try {
      await ensureRemoteDirs(api, remoteRootUrl, auth, f.path, ensuredDirs);
      const localBytes = await api.vault.readBytes(f.path);
      const remote = remoteFiles.get(f.path);
      if (remote && remote.size != null && remote.size === localBytes.length) {
        continue;
      }
      const target = buildRemoteUrl(remoteRootUrl, f.path, false);
      const code = await putBytes(api, target, localBytes, auth);
      if (code >= 200 && code < 300) uploaded++;
      else failed++;
    } catch (e) {
      failed++;
      api.log('upload failed:', f.path, String(e && e.message ? e.message : e));
    }
  }

  // Download remote-only files.
  for (const [path] of remoteFiles) {
    if (localSet.has(path)) continue;
    try {
      const url = buildRemoteUrl(remoteRootUrl, path, false);
      const bytes = await getBytes(api, url, auth);
      await api.vault.writeBytes(path, bytes);
      downloaded++;
    } catch (e) {
      failed++;
      api.log('download failed:', path, String(e && e.message ? e.message : e));
    }
  }

  return { ok: failed === 0, message: `同步完成：uploaded=${uploaded}, downloaded=${downloaded}, failed=${failed}` };
}

module.exports = {
  actions: {
    testConnection,
    syncNow,
  },
};

