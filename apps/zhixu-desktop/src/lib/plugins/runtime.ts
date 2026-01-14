import { createDir, deleteEntry, httpRequest, listDir, readBytesAbs, readTextFile, writeBytesAbs, writeTextFile } from "../vaultApi";
import type { InstalledPlugin, PluginManifest } from "./types";

const textDecoder = new TextDecoder();
const textEncoder = new TextEncoder();

function joinAbsPath(root: string, relPath: string): string {
  const sep = root.includes("\\") ? "\\" : "/";
  const left = root.replace(/[\\/]+$/, "");
  const right = String(relPath || "")
    .replace(/^[\\/]+/, "")
    .replace(/[\\/]+/g, sep);
  return right ? `${left}${sep}${right}` : left;
}

function normalizeEntryFileName(entry: string | undefined): string {
  const raw = String(entry || "").trim();
  if (!raw) return "main.js";
  if (/\.[a-z0-9]+$/i.test(raw)) return raw;
  return `${raw}.js`;
}

function loadCommonJsModule(code: string, api: unknown): unknown {
  const module = { exports: {} as any };
  const exports = module.exports;
  // eslint-disable-next-line no-new-func
  const fn = new Function("module", "exports", "api", code) as (module: any, exports: any, api: any) => void;
  fn(module, exports, api);
  return module.exports;
}

export type PluginActionResult =
  | string
  | {
      ok?: boolean;
      message?: string;
      title?: string;
      html?: string;
    }
  | null
  | undefined;

export async function runInstalledPluginAction(opts: {
  vaultRoot: string;
  plugin: InstalledPlugin;
  actionId: string;
  log?: (line: string) => void;
}): Promise<{ manifest: PluginManifest; result: PluginActionResult }> {
  const pluginId = opts.plugin.manifest.id;
  const entryFile = normalizeEntryFileName(opts.plugin.manifest.entry);
  const entryAbs = joinAbsPath(opts.vaultRoot, `.zhixu/plugins/${pluginId}/${entryFile}`);
  const entryCode = textDecoder.decode(await readBytesAbs(entryAbs));

  const api = {
    log: (...args: any[]) => {
      const line = args.map((v) => (typeof v === "string" ? v : JSON.stringify(v))).join(" ");
      opts.log?.(`[${pluginId}] ${line}`);
      // eslint-disable-next-line no-console
      console.log(`[plugin:${pluginId}]`, ...args);
    },
    http: async (
      method: string,
      url: string,
      body?: string | Uint8Array | null,
      contentType?: string,
      headers?: Record<string, string>,
      timeoutMs?: number,
    ): Promise<string> => {
      const hdrs: Array<{ name: string; value: string }> = [];
      if (contentType) hdrs.push({ name: "Content-Type", value: contentType });
      for (const [k, v] of Object.entries(headers || {})) hdrs.push({ name: k, value: v });
      const res = await httpRequest({
        method,
        url,
        headers: hdrs,
        body: typeof body === "string" ? textEncoder.encode(body) : body ?? undefined,
        timeoutMs,
      });
      if (!res.ok) {
        const msg = textDecoder.decode(res.bytes).slice(0, 300).trim();
        throw new Error(msg || `HTTP ${res.status}`);
      }
      return textDecoder.decode(res.bytes);
    },
    httpRaw: async (req: {
      method: string;
      url: string;
      headers?: Record<string, string>;
      body?: Uint8Array | string;
      timeoutMs?: number;
    }): Promise<{ status: number; ok: boolean; headers: Record<string, string>; bytes: Uint8Array }> => {
      const hdrs: Array<{ name: string; value: string }> = [];
      for (const [k, v] of Object.entries(req.headers || {})) hdrs.push({ name: k, value: v });
      const res = await httpRequest({
        method: req.method,
        url: req.url,
        headers: hdrs,
        body: typeof req.body === "string" ? textEncoder.encode(req.body) : req.body,
        timeoutMs: req.timeoutMs,
      });
      const outHeaders: Record<string, string> = {};
      for (const h of res.headers) outHeaders[h.name.toLowerCase()] = h.value;
      return { status: res.status, ok: res.ok, headers: outHeaders, bytes: res.bytes };
    },
    vault: {
      listDir: (relPath: string) => listDir(relPath),
      readBytes: async (relPath: string) => {
        const abs = joinAbsPath(opts.vaultRoot, relPath);
        return readBytesAbs(abs);
      },
      writeBytes: async (relPath: string, bytes: Uint8Array) => {
        const abs = joinAbsPath(opts.vaultRoot, relPath);
        return writeBytesAbs(abs, Array.from(bytes));
      },
      readText: (relPath: string) => readTextFile(relPath),
      writeText: (relPath: string, text: string) => writeTextFile(relPath, text),
      createDir: (relPath: string) => createDir(relPath),
      deleteEntry: (relPath: string) => deleteEntry(relPath),
    },
  };

  const configObj = (() => {
    try {
      return opts.plugin.configText ? JSON.parse(opts.plugin.configText) : {};
    } catch {
      return {};
    }
  })();

  const ctx = {
    plugin: opts.plugin.manifest,
    config: configObj,
    vault: { root: opts.vaultRoot },
    app: { platform: "desktop" as const },
  };

  const mod = loadCommonJsModule(entryCode, api) as any;
  const action = mod?.actions?.[opts.actionId];
  if (typeof action !== "function") {
    throw new Error(`Action not found: ${opts.actionId}`);
  }

  const res = action(ctx, api);
  const result = (await Promise.resolve(res)) as PluginActionResult;
  return { manifest: opts.plugin.manifest, result };
}
