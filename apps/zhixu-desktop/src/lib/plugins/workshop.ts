import { createDir, deleteEntry, httpRequest, listDir, readBytesAbs, readTextFile, writeBytesAbs } from "../vaultApi";
import type { InstalledPlugin, PluginIndex, PluginIndexItem, PluginManifest } from "./types";

const textDecoder = new TextDecoder();

function normalizeBaseUrl(url: string): string {
  return String(url || "").trim().replace(/\/+$/, "");
}

function joinUrl(baseUrl: string, path: string): string {
  const base = normalizeBaseUrl(baseUrl);
  const cleaned = String(path || "").trim().replace(/^\/+/, "");
  return `${base}/${cleaned}`;
}

function joinAbsPath(root: string, relPath: string): string {
  const sep = root.includes("\\") ? "\\" : "/";
  const left = root.replace(/[\\/]+$/, "");
  const right = String(relPath || "")
    .replace(/^[\\/]+/, "")
    .replace(/[\\/]+/g, sep);
  return right ? `${left}${sep}${right}` : left;
}

async function httpGetBytes(url: string, timeoutMs = 30_000): Promise<Uint8Array> {
  const res = await httpRequest({ method: "GET", url, timeoutMs });
  if (!res.ok) {
    const msg = textDecoder.decode(res.bytes).slice(0, 300).trim();
    throw new Error(msg || `HTTP ${res.status}`);
  }
  return res.bytes;
}

async function httpGetText(url: string, timeoutMs = 30_000): Promise<string> {
  const bytes = await httpGetBytes(url, timeoutMs);
  return textDecoder.decode(bytes);
}

function normalizeEntryFileName(entry: string | undefined): string {
  const raw = String(entry || "").trim();
  if (!raw) return "main.js";
  if (/\.[a-z0-9]+$/i.test(raw)) return raw;
  return `${raw}.js`;
}

function parseManifest(text: string): PluginManifest {
  const obj = JSON.parse(text) as Partial<PluginManifest>;
  if (!obj || typeof obj !== "object") throw new Error("Invalid manifest");
  if (!obj.id || !obj.name || !obj.version) throw new Error("Invalid manifest: missing fields");
  return obj as PluginManifest;
}

export async function fetchOfficialIndex(baseUrl: string): Promise<PluginIndexItem[]> {
  const url = joinUrl(baseUrl, "index.json");
  const text = await httpGetText(url);
  const idx = JSON.parse(text) as PluginIndex;
  if (!idx || typeof idx !== "object" || !Array.isArray(idx.plugins)) {
    throw new Error("Invalid plugin index");
  }
  return idx.plugins;
}

export async function fetchOfficialManifest(baseUrl: string, pluginId: string): Promise<PluginManifest> {
  const url = joinUrl(baseUrl, `${pluginId}/manifest.json`);
  const text = await httpGetText(url);
  return parseManifest(text);
}

export async function fetchOfficialReadme(baseUrl: string, pluginId: string): Promise<string | null> {
  const url = joinUrl(baseUrl, `${pluginId}/README.md`);
  try {
    return await httpGetText(url);
  } catch {
    return null;
  }
}

export async function listInstalledPlugins(vaultRoot: string): Promise<InstalledPlugin[]> {
  const pluginRootRel = ".zhixu/plugins";
  let dirs: Array<{ path: string; name: string; isDir: boolean }> = [];
  try {
    dirs = await listDir(pluginRootRel);
  } catch {
    return [];
  }

  const installed: InstalledPlugin[] = [];
  for (const e of dirs) {
    if (!e.isDir) continue;
    const id = e.name;
    const manifestAbs = joinAbsPath(vaultRoot, `${pluginRootRel}/${id}/manifest.json`);
    let manifestText = "";
    try {
      manifestText = textDecoder.decode(await readBytesAbs(manifestAbs));
    } catch {
      continue;
    }

    let manifest: PluginManifest;
    try {
      manifest = parseManifest(manifestText);
    } catch {
      continue;
    }

    const disabledAbs = joinAbsPath(vaultRoot, `${pluginRootRel}/${id}/.disabled`);
    let enabled = true;
    try {
      await readBytesAbs(disabledAbs);
      enabled = false;
    } catch {
      enabled = true;
    }

    let configText: string | null = null;
    try {
      const cfgAbs = joinAbsPath(vaultRoot, `${pluginRootRel}/${id}/config.json`);
      configText = textDecoder.decode(await readBytesAbs(cfgAbs));
    } catch {
      configText = null;
    }

    let readmeText: string | null = null;
    try {
      readmeText = await readTextFile(`${pluginRootRel}/${id}/README.md`);
    } catch {
      readmeText = null;
    }

    installed.push({ manifest, enabled, configText, readmeText });
  }

  installed.sort((a, b) => a.manifest.name.localeCompare(b.manifest.name, undefined, { numeric: true, sensitivity: "base" }));
  return installed;
}

export async function installPluginFromOfficial(opts: {
  vaultRoot: string;
  baseUrl: string;
  pluginId: string;
  preserveConfig?: boolean;
}): Promise<PluginManifest> {
  const baseUrl = normalizeBaseUrl(opts.baseUrl);
  const pluginId = opts.pluginId;
  const preserveConfig = opts.preserveConfig ?? true;
  const pluginRelDir = `.zhixu/plugins/${pluginId}`;
  const pluginAbsDir = joinAbsPath(opts.vaultRoot, pluginRelDir);

  const manifestUrl = joinUrl(baseUrl, `${pluginId}/manifest.json`);
  const manifestText = await httpGetText(manifestUrl);
  const manifest = parseManifest(manifestText);

  await createDir(pluginRelDir);

  const filesToFetch = new Set<string>();
  filesToFetch.add("manifest.json");
  filesToFetch.add(normalizeEntryFileName(manifest.entry));
  for (const f of manifest.files || []) filesToFetch.add(String(f));
  filesToFetch.add("README.md");

  for (const fileName of filesToFetch) {
    if (!fileName) continue;
    if (preserveConfig && fileName === "config.json") {
      try {
        await readBytesAbs(joinAbsPath(opts.vaultRoot, `${pluginRelDir}/config.json`));
        continue;
      } catch {
        // fallthrough
      }
    }

    const url = joinUrl(baseUrl, `${pluginId}/${fileName}`);
    let bytes: Uint8Array;
    try {
      bytes = await httpGetBytes(url);
    } catch (e) {
      if (fileName === "README.md") continue;
      throw e;
    }
    await writeBytesAbs(joinAbsPath(pluginAbsDir, fileName), Array.from(bytes));
  }

  // Ensure enabled.
  try {
    await deleteEntry(`${pluginRelDir}/.disabled`);
  } catch {
    // ignore
  }

  return manifest;
}

export async function uninstallPlugin(vaultRoot: string, pluginId: string): Promise<void> {
  const pluginRelDir = `.zhixu/plugins/${pluginId}`;
  await deleteEntry(pluginRelDir);
}

export async function setPluginEnabled(vaultRoot: string, pluginId: string, enabled: boolean): Promise<void> {
  const pluginRelDir = `.zhixu/plugins/${pluginId}`;
  const absDisabled = joinAbsPath(vaultRoot, `${pluginRelDir}/.disabled`);
  if (enabled) {
    try {
      await deleteEntry(`${pluginRelDir}/.disabled`);
    } catch {
      // ignore
    }
    return;
  }
  await writeBytesAbs(absDisabled, []);
}

export async function savePluginConfig(vaultRoot: string, pluginId: string, configText: string): Promise<void> {
  const pluginRelDir = `.zhixu/plugins/${pluginId}`;
  const abs = joinAbsPath(vaultRoot, `${pluginRelDir}/config.json`);
  await writeBytesAbs(abs, Array.from(new TextEncoder().encode(configText)));
}
