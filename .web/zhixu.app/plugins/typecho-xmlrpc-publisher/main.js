'use strict';

function escapeRegExp(s) {
  return String(s).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function toInt(v, fallback) {
  const n = Number(v);
  return isFinite(n) ? (n | 0) : fallback;
}

function toBool(v, fallback) {
  if (v === null || v === undefined) return fallback;
  if (typeof v === 'boolean') return v;
  if (typeof v === 'number') return v !== 0;
  const s = String(v).trim().toLowerCase();
  if (s === 'true' || s === '1' || s === 'yes' || s === 'y') return true;
  if (s === 'false' || s === '0' || s === 'no' || s === 'n') return false;
  return fallback;
}

function yamlQuoteString(s) {
  return JSON.stringify(String(s));
}

function yamlFormatValue(value) {
  if (value === null || value === undefined) return 'null';
  if (typeof value === 'boolean') return value ? 'true' : 'false';
  if (typeof value === 'number') return String(value);
  if (Array.isArray(value)) return '[' + value.map((v) => yamlQuoteString(v)).join(', ') + ']';
  if (value instanceof Date) return yamlQuoteString(XmlRpc.iso8601(value));
  return yamlQuoteString(String(value));
}

function ensureTrailingNewline(s) {
  const out = String(s || '');
  if (!out) return '';
  if (!/\r?\n$/.test(out)) return out + '\n';
  return out.replace(/\r?\n+$/, '\n');
}

function splitFrontmatter(text) {
  const src = String(text || '');
  const open = src.match(/^---\s*\r?\n/);
  if (!open) return { has: false, yaml: '', body: src };
  const rest = src.slice(open[0].length);
  const endRe = /\r?\n---\s*(\r?\n|$)/;
  const m = endRe.exec(rest);
  if (!m) return { has: false, yaml: '', body: src };
  const yaml = rest.slice(0, m.index);
  const body = rest.slice(m.index + m[0].length);
  return { has: true, yaml, body };
}

function readFrontmatterScalar(yaml, key) {
  const re = new RegExp('^' + escapeRegExp(key) + '\\s*:\\s*(.*)$', 'm');
  const m = re.exec(yaml);
  if (!m) return undefined;
  const raw = String(m[1] || '').trim();
  if (!raw) return '';
  if (raw === 'null') return null;
  if (raw === 'true') return true;
  if (raw === 'false') return false;
  if (raw[0] === '"' || raw[0] === "'") {
    return raw.slice(1, raw.length - 1);
  }
  return raw;
}

function readFrontmatterBlockList(yaml, key) {
  const lines = String(yaml || '').split(/\r?\n/);
  const keyOnlyRe = new RegExp('^' + escapeRegExp(key) + '\\s*:\\s*$');

  let idx = -1;
  for (let i = 0; i < lines.length; i++) {
    if (keyOnlyRe.test(lines[i])) {
      idx = i;
      break;
    }
  }
  if (idx === -1) return null;

  const out = [];
  for (let j = idx + 1; j < lines.length; j++) {
    const ln = lines[j];
    if (/^\s*$/.test(ln)) break;
    if (/^[A-Za-z0-9_.-]+\s*:/.test(ln)) break;
    const m = /^\s*-\s*(.*)$/.exec(ln);
    if (!m) break;
    const item = String(m[1] || '').trim();
    if (!item) continue;
    out.push(item.replace(/^["']|["']$/g, ''));
  }
  return out;
}

function readFrontmatterList(yaml, key) {
  const block = readFrontmatterBlockList(yaml, key);
  if (block) return block;

  const val = readFrontmatterScalar(yaml, key);
  if (val === undefined || val === null) return [];
  if (Array.isArray(val)) return val;
  const s = String(val).trim();
  if (!s) return [];
  if (s.startsWith('[') && s.endsWith(']')) {
    const inside = s.slice(1, -1).trim();
    if (!inside) return [];
    return inside
      .split(',')
      .map((x) => x.trim())
      .filter(Boolean)
      .map((x) => x.replace(/^["']|["']$/g, ''));
  }
  return s
    .split(',')
    .map((x) => x.trim())
    .filter(Boolean);
}

function upsertFrontmatter(yaml, key, value) {
  const lines = String(yaml || '').split(/\r?\n/);
  const keyRe = new RegExp('^' + escapeRegExp(key) + '\\s*:\\s*.*$');
  const keyOnlyRe = new RegExp('^' + escapeRegExp(key) + '\\s*:\\s*$');

  let idx = -1;
  for (let i = 0; i < lines.length; i++) {
    if (keyRe.test(lines[i])) {
      idx = i;
      break;
    }
  }

  const rendered = key + ': ' + yamlFormatValue(value);
  if (idx === -1) {
    lines.push(rendered);
    return ensureTrailingNewline(lines.join('\n'));
  }

  const hadKeyOnly = keyOnlyRe.test(lines[idx]);
  lines[idx] = rendered;
  if (hadKeyOnly) {
    let j = idx + 1;
    while (j < lines.length) {
      const ln = lines[j];
      if (/^\s+-\s+/.test(ln) || /^-\s+/.test(ln)) {
        lines.splice(j, 1);
        continue;
      }
      if (/^\s*$/.test(ln)) break;
      if (/^[A-Za-z0-9_.-]+\s*:/.test(ln)) break;
      break;
    }
  }
  return ensureTrailingNewline(lines.join('\n'));
}

function removeFrontmatterKey(yaml, key) {
  const lines = String(yaml || '').split(/\r?\n/);
  const keyRe = new RegExp('^' + escapeRegExp(key) + '\\s*:\\s*.*$');
  const keyOnlyRe = new RegExp('^' + escapeRegExp(key) + '\\s*:\\s*$');

  for (let i = 0; i < lines.length; i++) {
    if (keyRe.test(lines[i])) {
      const hadKeyOnly = keyOnlyRe.test(lines[i]);
      lines.splice(i, 1);
      if (hadKeyOnly) {
        while (i < lines.length) {
          const ln = lines[i];
          if (/^\s*-\s+/.test(ln) || /^-\s+/.test(ln)) {
            lines.splice(i, 1);
            continue;
          }
          break;
        }
      }
      break;
    }
  }
  return ensureTrailingNewline(lines.join('\n'));
}

function buildTextWithFrontmatter(has, yaml, body) {
  const fm = ensureTrailingNewline(yaml);
  if (has) {
    return '---\n' + fm + '---\n' + String(body || '');
  }
  if (!fm.trim()) return String(body || '');
  const sep = body && String(body).startsWith('\n') ? '' : '\n\n';
  return '---\n' + fm + '---' + sep + String(body || '');
}

class XmlRpc {
  static iso8601(date) {
    const pad = (n) => (n < 10 ? '0' + n : '' + n);
    return (
      date.getFullYear().toString() +
      pad(date.getMonth() + 1) +
      pad(date.getDate()) +
      'T' +
      pad(date.getHours()) +
      ':' +
      pad(date.getMinutes()) +
      ':' +
      pad(date.getSeconds())
    );
  }

  static parseIso8601(str) {
    if (!str) return null;
    const s = String(str).trim();
    let m = s.match(/^(\d{4})(\d{2})(\d{2})T(\d{2}):(\d{2}):(\d{2})$/);
    if (m) {
      const y = Number(m[1]);
      const mo = Number(m[2]);
      const d = Number(m[3]);
      const h = Number(m[4]);
      const mi = Number(m[5]);
      const se = Number(m[6]);
      return new Date(y, mo - 1, d, h, mi, se);
    }
    m = s.match(/^(\d{4})-(\d{2})-(\d{2})[ T](\d{2}):(\d{2}):(\d{2})$/);
    if (m) {
      const y = Number(m[1]);
      const mo = Number(m[2]);
      const d = Number(m[3]);
      const h = Number(m[4]);
      const mi = Number(m[5]);
      const se = Number(m[6]);
      return new Date(y, mo - 1, d, h, mi, se);
    }
    const dt = new Date(s);
    return isNaN(dt.getTime()) ? null : dt;
  }

  static escapeXml(s) {
    return String(s)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  static unescapeXml(s) {
    return String(s)
      .replace(/&lt;/g, '<')
      .replace(/&gt;/g, '>')
      .replace(/&quot;/g, '"')
      .replace(/&#39;/g, "'")
      .replace(/&amp;/g, '&');
  }

  static encodeValue(val) {
    if (val === null || val === undefined) return '<nil/>';
    if (Array.isArray(val)) {
      return (
        '<array><data>' +
        val.map((v) => '<value>' + XmlRpc.encodeValue(v) + '</value>').join('') +
        '</data></array>'
      );
    }
    const t = typeof val;
    if (t === 'boolean') return '<boolean>' + (val ? '1' : '0') + '</boolean>';
    if (t === 'number') return Number.isInteger(val) ? '<int>' + val + '</int>' : '<double>' + val + '</double>';
    if (val instanceof Date) return '<dateTime.iso8601>' + XmlRpc.iso8601(val) + '</dateTime.iso8601>';
    if (t === 'object') {
      return (
        '<struct>' +
        Object.keys(val)
          .map((k) => {
            return (
              '<member><name>' +
              XmlRpc.escapeXml(k) +
              '</name><value>' +
              XmlRpc.encodeValue(val[k]) +
              '</value></member>'
            );
          })
          .join('') +
        '</struct>'
      );
    }
    return '<string>' + XmlRpc.escapeXml(String(val)) + '</string>';
  }

  static buildCall(method, params) {
    return (
      '<?xml version="1.0"?>' +
      '<methodCall><methodName>' +
      XmlRpc.escapeXml(method) +
      '</methodName><params>' +
      params.map((p) => '<param><value>' + XmlRpc.encodeValue(p) + '</value></param>').join('') +
      '</params></methodCall>'
    );
  }

  static tokenize(xml) {
    const out = [];
    const s = String(xml || '');
    let i = 0;
    while (i < s.length) {
      const lt = s.indexOf('<', i);
      if (lt === -1) {
        const txt = s.slice(i);
        if (txt) out.push({ type: 'text', text: txt });
        break;
      }
      if (lt > i) {
        const txt = s.slice(i, lt);
        if (txt) out.push({ type: 'text', text: txt });
      }
      const gt = s.indexOf('>', lt + 1);
      if (gt === -1) break;
      const tagRaw = s.slice(lt + 1, gt).trim();
      i = gt + 1;

      if (!tagRaw || tagRaw.startsWith('!') || tagRaw.startsWith('?')) continue;

      if (tagRaw[0] === '/') {
        out.push({ type: 'end', name: tagRaw.slice(1).split(/\s+/)[0] });
        continue;
      }
      const selfClosing = tagRaw.endsWith('/');
      const name = tagRaw.replace(/\/$/, '').split(/\s+/)[0];
      out.push({ type: 'start', name: name });
      if (selfClosing) out.push({ type: 'end', name: name });
    }
    return out;
  }

  static parseXml(xml) {
    const root = { name: '#doc', children: [], text: '' };
    const stack = [root];
    const tokens = XmlRpc.tokenize(xml);
    for (let i = 0; i < tokens.length; i++) {
      const t = tokens[i];
      const top = stack[stack.length - 1];
      if (t.type === 'text') {
        top.text += t.text;
      } else if (t.type === 'start') {
        const node = { name: t.name, children: [], text: '' };
        top.children.push(node);
        stack.push(node);
      } else if (t.type === 'end') {
        if (stack.length > 1) stack.pop();
      }
    }
    return root;
  }

  static findFirst(node, name) {
    if (!node) return null;
    if (node.name === name) return node;
    for (let i = 0; i < (node.children || []).length; i++) {
      const found = XmlRpc.findFirst(node.children[i], name);
      if (found) return found;
    }
    return null;
  }

  static childElements(node, name) {
    const out = [];
    const children = (node && node.children) || [];
    for (let i = 0; i < children.length; i++) {
      if (!name || children[i].name === name) out.push(children[i]);
    }
    return out;
  }

  static parseValue(valueNode) {
    if (!valueNode) return null;
    if (valueNode.name === 'value') {
      const el = XmlRpc.childElements(valueNode)[0];
      if (!el) return XmlRpc.unescapeXml(String(valueNode.text || '').trim());
      return XmlRpc.parseValue(el);
    }

    const name = valueNode.name;
    const text = XmlRpc.unescapeXml(String(valueNode.text || '').trim());

    if (name === 'nil') return null;
    if (name === 'string') return text;
    if (name === 'boolean') return text === '1';
    if (name === 'int' || name === 'i4') return parseInt(text || '0', 10);
    if (name === 'double') return Number(text || '0');
    if (name === 'dateTime.iso8601') return text;

    if (name === 'struct') {
      const obj = {};
      const members = XmlRpc.childElements(valueNode, 'member');
      for (let i = 0; i < members.length; i++) {
        const member = members[i];
        const nameEl = XmlRpc.childElements(member, 'name')[0];
        const valEl = XmlRpc.childElements(member, 'value')[0];
        const k = XmlRpc.unescapeXml(String((nameEl && nameEl.text) || '').trim());
        obj[k] = XmlRpc.parseValue(valEl);
      }
      return obj;
    }

    if (name === 'array') {
      const data = XmlRpc.childElements(valueNode, 'data')[0];
      if (!data) return [];
      const values = XmlRpc.childElements(data, 'value');
      return values.map((v) => XmlRpc.parseValue(v));
    }

    return text;
  }

  static parseResponse(xmlText) {
    const doc = XmlRpc.parseXml(xmlText);
    const fault = XmlRpc.findFirst(doc, 'fault');
    if (fault) {
      const v = XmlRpc.findFirst(fault, 'value');
      const obj = XmlRpc.parseValue(v) || {};
      const msg = obj.faultString || obj['faultString'] || 'XML-RPC fault';
      const code = obj.faultCode || obj['faultCode'] || -1;
      const err = new Error('XML-RPC fault ' + code + ': ' + msg);
      err.code = code;
      throw err;
    }
    const params = XmlRpc.findFirst(doc, 'params');
    const param = params ? XmlRpc.childElements(params, 'param')[0] : null;
    const value = param ? XmlRpc.childElements(param, 'value')[0] : null;
    return XmlRpc.parseValue(value);
  }
}

function getConfig(ctx) {
  const cfg = (ctx && ctx.config) || {};
  const fmKeys = cfg.frontmatterKeys || {};
  const publishOffsetRaw = cfg.publishTimeOffsetHours != null ? cfg.publishTimeOffsetHours : cfg.publishTimeOffset;
  const syncOffsetRaw = cfg.syncTimeOffsetHours != null ? cfg.syncTimeOffsetHours : cfg.syncTimeOffset;
  return {
    endpoint: String(cfg.endpoint || '').trim(),
    username: String(cfg.username || '').trim(),
    password: String(cfg.password || ''),
    blogId: String(cfg.blogId || cfg.defaultBlogId || '0').trim() || '0',
    useFrontmatter: toBool(cfg.useFrontmatter, true),
    useCurrentTime: toBool(cfg.useCurrentTime, false),
    publishTimeOffsetHours: Number(publishOffsetRaw || 0) || 0,
    syncTimeOffsetHours: Number(syncOffsetRaw || 0) || 0,
    managePostsCount: toInt(cfg.managePostsCount, 20),
    keys: {
      title: String(fmKeys.title || 'title'),
      cid: String(fmKeys.cid || 'typecho_cid'),
      slug: String(fmKeys.slug || 'slug'),
      tags: String(fmKeys.tags || 'tags'),
      categories: String(fmKeys.categories || 'categories'),
      draft: String(fmKeys.draft || 'draft'),
      dateCreated: String(fmKeys.dateCreated || 'dateCreated'),
      lastPublished: String(fmKeys.lastPublished || 'typecho_lastPublished'),
    },
  };
}

function assertConfig(conf) {
  if (!conf.endpoint) throw new Error('Missing config.endpoint (XML-RPC endpoint url)');
  if (!conf.username) throw new Error('Missing config.username');
  if (!conf.password) throw new Error('Missing config.password');
}

function normalizeCidFromPostStruct(post) {
  if (!post) return '';
  const candidates = ['postid', 'postId', 'post_id', 'cid', 'id'];
  for (let i = 0; i < candidates.length; i++) {
    const k = candidates[i];
    const v = post[k];
    if (v === undefined || v === null) continue;
    const s = String(v).trim();
    if (s) return s;
  }
  return '';
}

function publish(ctx) {
  const conf = getConfig(ctx);
  assertConfig(conf);

  const note = (ctx && ctx.note) || {};
  const text = String(note.text || '');
  const split = splitFrontmatter(text);
  const yaml = split.yaml || '';
  const body = String(split.body || '');

  const titleFromFm = String(readFrontmatterScalar(yaml, conf.keys.title) || '').trim();
  const title =
    (conf.useFrontmatter ? titleFromFm : '') ||
    String(note.title || note.fileName || '').trim() ||
    titleFromFm ||
    'Untitled';
  const slug = String(readFrontmatterScalar(yaml, conf.keys.slug) || '').trim();
  const tags = readFrontmatterList(yaml, conf.keys.tags);
  const categories = readFrontmatterList(yaml, conf.keys.categories);
  const draftRaw = readFrontmatterScalar(yaml, conf.keys.draft);
  const draft = draftRaw === undefined ? false : toBool(draftRaw, false);

  if (!categories || categories.length === 0) {
    return { ok: false, message: 'categories 不能为空，请在 Frontmatter 里设置 ' + conf.keys.categories };
  }

  const postStruct = {
    title: title,
    description: body,
    mt_keywords: tags.join(','),
    categories: categories,
    post_type: 'post',
    wp_slug: slug || '',
    mt_allow_comments: 1,
  };

  let postDate = conf.useCurrentTime ? new Date() : XmlRpc.parseIso8601(readFrontmatterScalar(yaml, conf.keys.dateCreated)) || new Date();
  if (conf.publishTimeOffsetHours) {
    postDate = new Date(postDate.getTime() + conf.publishTimeOffsetHours * 3600 * 1000);
  }
  postStruct.dateCreated = postDate;

  const cid = String(readFrontmatterScalar(yaml, conf.keys.cid) || '').trim();
  const method = cid ? 'metaWeblog.editPost' : 'metaWeblog.newPost';
  const params = cid
    ? [String(cid), conf.username, conf.password, postStruct, !draft]
    : [conf.blogId, conf.username, conf.password, postStruct, !draft];

  const xml = XmlRpc.buildCall(method, params);
  const respText = api.http('POST', conf.endpoint, xml, 'text/xml');
  const result = XmlRpc.parseResponse(respText);
  const newCid = cid || String(result);

  let nextYaml = yaml;
  if (conf.useFrontmatter) {
    nextYaml = upsertFrontmatter(nextYaml, conf.keys.title, title);
    nextYaml = upsertFrontmatter(nextYaml, conf.keys.tags, tags);
    nextYaml = upsertFrontmatter(nextYaml, conf.keys.categories, categories);
    nextYaml = upsertFrontmatter(nextYaml, conf.keys.draft, draft);
    if (slug) nextYaml = upsertFrontmatter(nextYaml, conf.keys.slug, slug);
  }
  nextYaml = upsertFrontmatter(nextYaml, conf.keys.cid, newCid);
  nextYaml = upsertFrontmatter(nextYaml, conf.keys.lastPublished, XmlRpc.iso8601(new Date()));
  nextYaml = upsertFrontmatter(nextYaml, conf.keys.dateCreated, XmlRpc.iso8601(postStruct.dateCreated));
  if (!slug) nextYaml = upsertFrontmatter(nextYaml, conf.keys.slug, newCid);

  const nextText = buildTextWithFrontmatter(split.has || true, nextYaml, body);
  return { ok: true, message: cid ? 'Updated (cid=' + newCid + ')' : 'Published (cid=' + newCid + ')', setText: nextText };
}

function syncPublishDate(ctx) {
  const conf = getConfig(ctx);
  assertConfig(conf);

  const note = (ctx && ctx.note) || {};
  const text = String(note.text || '');
  const split = splitFrontmatter(text);
  const yaml = split.yaml || '';
  const body = String(split.body || '');

  const cid = String(readFrontmatterScalar(yaml, conf.keys.cid) || '').trim();
  if (!cid) return { ok: false, message: '未找到 CID，请先发布一次（frontmatter: ' + conf.keys.cid + ')' };

  const xml = XmlRpc.buildCall('metaWeblog.getPost', [String(cid), conf.username, conf.password]);
  const respText = api.http('POST', conf.endpoint, xml, 'text/xml');
  const result = XmlRpc.parseResponse(respText);
  const serverDateRaw = result && (result.dateCreated || result['dateCreated']);
  const serverDate = XmlRpc.parseIso8601(serverDateRaw);
  if (!serverDate) return { ok: false, message: '服务器未返回可解析的 dateCreated' };

  let d = serverDate;
  if (conf.syncTimeOffsetHours) {
    d = new Date(d.getTime() + conf.syncTimeOffsetHours * 3600 * 1000);
  }

  let nextYaml = yaml;
  nextYaml = upsertFrontmatter(nextYaml, conf.keys.dateCreated, XmlRpc.iso8601(d));
  const nextText = buildTextWithFrontmatter(split.has || true, nextYaml, body);
  return { ok: true, message: 'Synced dateCreated', setText: nextText };
}

function listRecentPosts(ctx) {
  const conf = getConfig(ctx);
  assertConfig(conf);

  const xml = XmlRpc.buildCall('metaWeblog.getRecentPosts', [conf.blogId, conf.username, conf.password, conf.managePostsCount]);
  const respText = api.http('POST', conf.endpoint, xml, 'text/xml');
  const result = XmlRpc.parseResponse(respText);

  if (!Array.isArray(result)) return { ok: false, message: 'getRecentPosts 返回格式异常' };
  if (result.length === 0) return { ok: true, message: 'Recent posts: (empty)' };

  const maxLines = Math.min(result.length, 8);
  const lines = [];
  for (let i = 0; i < maxLines; i++) {
    const p = result[i] || {};
    const cid = normalizeCidFromPostStruct(p) || '(?)';
    const title = String(p.title || p['title'] || '').trim() || '(no title)';
    lines.push(cid + ' - ' + title);
  }
  const suffix = result.length > maxLines ? ' …(+ ' + (result.length - maxLines) + ')' : '';
  return { ok: true, message: 'Recent posts:\n' + lines.join('\n') + suffix };
}

function deletePost(ctx) {
  const conf = getConfig(ctx);
  assertConfig(conf);

  const note = (ctx && ctx.note) || {};
  const text = String(note.text || '');
  const split = splitFrontmatter(text);
  const yaml = split.yaml || '';
  const body = String(split.body || '');

  const cid = String(readFrontmatterScalar(yaml, conf.keys.cid) || '').trim();
  if (!cid) return { ok: false, message: '未找到 CID（frontmatter: ' + conf.keys.cid + '）' };

  const xml = XmlRpc.buildCall('blogger.deletePost', ['', String(cid), conf.username, conf.password, true]);
  const respText = api.http('POST', conf.endpoint, xml, 'text/xml');
  const result = XmlRpc.parseResponse(respText);
  const ok = toBool(result, false);
  if (!ok) return { ok: false, message: '删除失败（服务器返回: ' + String(result) + '）' };

  let nextYaml = yaml;
  nextYaml = removeFrontmatterKey(nextYaml, conf.keys.cid);
  nextYaml = removeFrontmatterKey(nextYaml, conf.keys.lastPublished);
  const nextText = buildTextWithFrontmatter(split.has || true, nextYaml, body);
  return { ok: true, message: 'Deleted (cid=' + cid + ')', setText: nextText };
}

module.exports = {
  actions: {
    publish: publish,
    syncPublishDate: syncPublishDate,
    listRecentPosts: listRecentPosts,
    deletePost: deletePost,
  },
};
