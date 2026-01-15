import { invoke } from "@tauri-apps/api/core";
import { open, save } from "@tauri-apps/plugin-dialog";
import type { DrawDocument } from "../draw/types";

export type VaultEntry = {
  path: string;
  name: string;
  isDir: boolean;
};

export type VaultFileInfo = {
  path: string;
  sizeBytes: number;
  mtimeMs: number;
};

export type VaultStat = {
  path: string;
  isDir: boolean;
  sizeBytes: number;
  mtimeMs: number;
};

export type PersistedState = {
  lastVault: string | null;
  recentVaults: string[];
};

export async function selectVault(): Promise<string> {
  const selected = await open({
    directory: true,
    multiple: false,
    title: "选择库文件夹",
  });
  if (typeof selected !== "string") {
    throw new Error("已取消选择文件夹");
  }
  return setVaultRoot(selected);
}

export async function saveFileDialog(options: { title: string; defaultPath?: string; filters?: { name: string; extensions: string[] }[] }): Promise<string | null> {
  const selected = await save({
    title: options.title,
    defaultPath: options.defaultPath,
    filters: options.filters,
  });
  return typeof selected === "string" ? selected : null;
}

export async function setVaultRoot(path: string): Promise<string> {
  return invoke<string>("set_vault_root", { path });
}

export async function getPersistedState(): Promise<PersistedState> {
  return invoke<PersistedState>("get_persisted_state");
}

export async function exitApp(): Promise<void> {
  return invoke<void>("exit_app");
}

export async function listDir(relPath: string): Promise<VaultEntry[]> {
  return invoke<VaultEntry[]>("list_dir", { relPath });
}

export async function statEntry(relPath: string): Promise<VaultStat> {
  return invoke<VaultStat>("stat_entry", { relPath });
}

export async function walkVaultFiles(): Promise<VaultFileInfo[]> {
  return invoke<VaultFileInfo[]>("walk_vault_files");
}

export async function readTextFile(relPath: string): Promise<string> {
  return invoke<string>("read_text_file", { relPath });
}

export async function writeTextFile(relPath: string, content: string): Promise<void> {
  return invoke<void>("write_text_file", { relPath, content });
}

export async function readDrawDocument(relPath: string): Promise<DrawDocument> {
  return invoke<DrawDocument>("read_draw_document", { relPath });
}

export async function writeDrawDocument(relPath: string, document: DrawDocument): Promise<void> {
  return invoke<void>("write_draw_document", { relPath, document });
}

export async function writeBytesAbs(path: string, bytes: number[]): Promise<void> {
  return invoke<void>("write_bytes_abs", { path, bytes });
}

export async function readBytesAbs(path: string): Promise<Uint8Array> {
  const bytes = await invoke<number[]>("read_bytes_abs", { path });
  return new Uint8Array(bytes);
}

export type HttpHeader = {
  name: string;
  value: string;
};

export type HttpRequest = {
  method: string;
  url: string;
  headers?: HttpHeader[];
  body?: Uint8Array;
  timeoutMs?: number;
};

export type HttpResponse = {
  status: number;
  ok: boolean;
  headers: HttpHeader[];
  bytes: Uint8Array;
};

export async function httpRequest(req: HttpRequest): Promise<HttpResponse> {
  const res = await invoke<{ status: number; ok: boolean; headers: HttpHeader[]; bytes: number[] }>("http_request", {
    req: {
      method: req.method,
      url: req.url,
      headers: req.headers,
      body: req.body ? Array.from(req.body) : undefined,
      timeoutMs: req.timeoutMs,
    },
  });
  return { status: res.status, ok: res.ok, headers: res.headers, bytes: new Uint8Array(res.bytes) };
}

export async function writeDrawDocumentAbs(path: string, document: DrawDocument): Promise<void> {
  return invoke<void>("write_draw_document_abs", { path, document });
}

export async function createDir(relPath: string): Promise<void> {
  return invoke<void>("create_dir", { relPath });
}

export async function createFile(relPath: string): Promise<void> {
  return invoke<void>("create_file", { relPath });
}

export async function renameEntry(fromRelPath: string, toRelPath: string): Promise<void> {
  return invoke<void>("rename_entry", { fromRelPath, toRelPath });
}

export async function deleteEntry(relPath: string): Promise<void> {
  return invoke<void>("delete_entry", { relPath });
}
