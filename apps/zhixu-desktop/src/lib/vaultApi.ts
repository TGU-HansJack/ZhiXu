import { invoke } from "@tauri-apps/api/core";
import { open } from "@tauri-apps/plugin-dialog";

export type VaultEntry = {
  path: string;
  name: string;
  isDir: boolean;
};

export type PersistedState = {
  lastVault: string | null;
  recentVaults: string[];
};

export async function selectVault(): Promise<string> {
  const selected = await open({
    directory: true,
    multiple: false,
    title: "Open Folder",
  });
  if (typeof selected !== "string") {
    throw new Error("Dialog canceled");
  }
  return setVaultRoot(selected);
}

export async function setVaultRoot(path: string): Promise<string> {
  return invoke<string>("set_vault_root", { path });
}

export async function getPersistedState(): Promise<PersistedState> {
  return invoke<PersistedState>("get_persisted_state");
}

export async function listDir(relPath: string): Promise<VaultEntry[]> {
  return invoke<VaultEntry[]>("list_dir", { relPath });
}

export async function readTextFile(relPath: string): Promise<string> {
  return invoke<string>("read_text_file", { relPath });
}

export async function writeTextFile(relPath: string, content: string): Promise<void> {
  return invoke<void>("write_text_file", { relPath, content });
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
