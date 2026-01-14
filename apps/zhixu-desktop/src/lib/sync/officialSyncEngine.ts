import { deleteEntry, readBytesAbs, walkVaultFiles, writeBytesAbs, type VaultFileInfo } from "../vaultApi";
import {
  deleteVaultFileV2,
  downloadVaultFileV2,
  uploadVaultFileV2,
  vaultChangesV2,
  type VaultFileDownloadV2,
} from "./officialClient";
import { loadOfficialStateV2, saveOfficialStateV2, type OfficialVaultSyncFileState, type OfficialVaultSyncStateV2 } from "./officialState";

export type OfficialVaultSyncSummary = {
  uploaded: number;
  downloaded: number;
  deletedRemote: number;
  deletedLocal: number;
  conflicts: number;
  failed: number;
};

function joinAbsPath(root: string, relPath: string): string {
  const sep = root.includes("\\") ? "\\" : "/";
  const left = root.replace(/[\\/]+$/, "");
  const right = String(relPath || "")
    .replace(/^[\\/]+/, "")
    .replace(/[\\/]+/g, sep);
  return right ? `${left}${sep}${right}` : left;
}

function normalizeRelPath(raw: string): string {
  return String(raw || "").trim().replace(/\\/g, "/").replace(/^\/+/, "");
}

function shouldSyncPath(path: string): boolean {
  const p = normalizeRelPath(path);
  if (!p) return false;
  const name = p.includes("/") ? p.slice(p.lastIndexOf("/") + 1) : p;
  if (name.toLowerCase().startsWith("conflict ")) return false;
  const lower = p.toLowerCase();
  if (lower === ".zhixu/sync/log.jsonl") return false;
  if (lower === ".zhixu/sync/conflicts.jsonl") return false;
  if (lower === ".zhixu/sync/official_state.json") return false;
  if (lower === ".zhixu/sync/official_state_v2.json") return false;
  if (lower.startsWith(".zhixu/sync/")) return false;
  if (lower.startsWith(".zhixu/conflicts/")) return false;
  if (lower.startsWith(".zhixu/history/")) return false;
  return true;
}

function isProbablySame(local: VaultFileInfo, st: OfficialVaultSyncFileState): boolean {
  if (local.sizeBytes !== st.size) return false;
  if (local.mtimeMs <= 0 || st.localMtimeMs <= 0) return false;
  return Math.abs(local.mtimeMs - st.localMtimeMs) < 2_000;
}

async function sha256Hex(bytes: Uint8Array): Promise<string> {
  const subtle = globalThis.crypto?.subtle;
  if (!subtle) throw new Error("crypto.subtle not available");
  const buf =
    bytes.buffer instanceof ArrayBuffer && bytes.byteOffset === 0 && bytes.byteLength === bytes.buffer.byteLength
      ? bytes.buffer
      : Uint8Array.from(bytes).buffer;
  const digest = await subtle.digest("SHA-256", buf);
  return Array.from(new Uint8Array(digest))
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("");
}

function withUploaded(
  state: OfficialVaultSyncStateV2,
  path: string,
  rev: number,
  sha256: string,
  size: number,
  localMtimeMs: number,
): OfficialVaultSyncStateV2 {
  return {
    ...state,
    files: {
      ...state.files,
      [path]: { baseRev: Math.max(0, rev), sha256, size: Math.max(0, size), localMtimeMs: Math.max(0, localMtimeMs), deleted: false },
    },
  };
}

function withDeleted(state: OfficialVaultSyncStateV2, path: string, rev: number): OfficialVaultSyncStateV2 {
  const prev = state.files[path];
  return {
    ...state,
    files: {
      ...state.files,
      [path]: {
        baseRev: Math.max(0, rev),
        sha256: prev?.sha256 || "",
        size: 0,
        localMtimeMs: Date.now(),
        deleted: true,
      },
    },
  };
}

async function ensureConflictArtifact(vaultRoot: string, path: string, kind: string, bytes: Uint8Array): Promise<void> {
  const safePath = normalizeRelPath(path);
  if (!safePath) return;
  const now = Date.now();
  const baseDir = `.zhixu/conflicts/${safePath}`;
  const name = `${now}-${kind}`;
  const dest = `${baseDir}/${name}`;
  const abs = joinAbsPath(vaultRoot, dest);
  await writeBytesAbs(abs, Array.from(bytes));
}

export async function syncOfficialVault(opts: {
  vaultRoot: string;
  baseUrl: string;
  token: string;
  limit?: number;
}): Promise<OfficialVaultSyncSummary> {
  const vaultRoot = String(opts.vaultRoot || "").trim();
  if (!vaultRoot) throw new Error("No vault selected");
  const token = String(opts.token || "").trim();
  if (!token) throw new Error("Not logged in");

  let state = await loadOfficialStateV2(vaultRoot);

  let uploaded = 0;
  let downloaded = 0;
  let deletedRemote = 0;
  let deletedLocal = 0;
  let conflicts = 0;
  let failed = 0;

  const listLocal = async (): Promise<VaultFileInfo[]> => {
    const all = await walkVaultFiles();
    return all.map((f) => ({ ...f, path: normalizeRelPath(f.path) })).filter((f) => shouldSyncPath(f.path));
  };

  let localFilesNow = await listLocal();
  let localByPathNow = new Map(localFilesNow.map((f) => [f.path, f]));

  const localSha256 = async (local: VaultFileInfo): Promise<string> => {
    const abs = joinAbsPath(vaultRoot, local.path);
    const bytes = await readBytesAbs(abs);
    return sha256Hex(bytes);
  };

  const applyRemote = async (pathRaw: string, remoteRev: number, remoteDeleted: boolean): Promise<boolean> => {
    const path = normalizeRelPath(pathRaw);
    if (!shouldSyncPath(path)) return true;

    const local = localByPathNow.get(path);
    const st = state.files[path];

    const localDirty = await (async () => {
      if (!local) return Boolean(st && !st.deleted);
      if (!st) return true;
      if (st.deleted) return true;
      if (isProbablySame(local, st)) return false;
      if (!st.sha256) return true;
      const sha = await localSha256(local);
      return sha !== st.sha256;
    })();

    if (!localDirty) {
      if (remoteDeleted) {
        try {
          if (local) await deleteEntry(path);
          localByPathNow.delete(path);
          state = withDeleted(state, path, remoteRev);
          return true;
        } catch {
          return false;
        }
      }

      const download = await downloadVaultFileV2(opts.baseUrl, token, path);
      if (!download.ok || !download.value) return false;
      const abs = joinAbsPath(vaultRoot, path);
      await writeBytesAbs(abs, Array.from(download.value.bytes));
      const localMtimeMs = Date.now();
      state = withUploaded(state, path, remoteRev, download.value.sha256, download.value.bytes.length, localMtimeMs);
      localByPathNow.set(path, { path, sizeBytes: download.value.bytes.length, mtimeMs: localMtimeMs });
      return true;
    }

    if (remoteDeleted) {
      const localBytes =
        local != null
          ? await readBytesAbs(joinAbsPath(vaultRoot, path)).catch(() => new Uint8Array())
          : new Uint8Array();
      if (localBytes.length) await ensureConflictArtifact(vaultRoot, path, "local", localBytes);
      state = withDeleted(state, path, remoteRev);
      return true;
    }

    const remote = await downloadVaultFileV2(opts.baseUrl, token, path);
    if (!remote.ok || !remote.value) return false;

    if (!local) {
      const abs = joinAbsPath(vaultRoot, path);
      await writeBytesAbs(abs, Array.from(remote.value.bytes));
      const localMtimeMs = Date.now();
      state = withUploaded(state, path, remoteRev, remote.value.sha256, remote.value.bytes.length, localMtimeMs);
      localByPathNow.set(path, { path, sizeBytes: remote.value.bytes.length, mtimeMs: localMtimeMs });
      return true;
    }

    const localBytes = await readBytesAbs(joinAbsPath(vaultRoot, path)).catch(() => new Uint8Array());
    await ensureConflictArtifact(vaultRoot, path, `remote-r${remote.value.rev}`, remote.value.bytes);

    state = withUploaded(state, path, remote.value.rev, "", localBytes.length, local.mtimeMs);
    if (localBytes.length) {
      const put = await uploadVaultFileV2(opts.baseUrl, token, path, local.mtimeMs || Date.now(), localBytes, remote.value.rev);
      if (put.ok && put.value) {
        state = withUploaded(state, path, put.value.rev, put.value.sha256, localBytes.length, local.mtimeMs || Date.now());
      }
    }
    return true;
  };

  let since = state.serverCursor;
  while (true) {
    const r = await vaultChangesV2(opts.baseUrl, token, since, opts.limit ?? 2000);
    const v = r.value;
    if (!r.ok || !v) throw new Error(r.errorMessage || "Failed to pull changes");

    if (v.snapshot) {
      for (const c of v.changes) {
        const ok = await applyRemote(c.path, c.rev, c.deleted);
        if (ok) {
          if (c.deleted) deletedLocal += 1;
          else downloaded += 1;
        } else {
          failed += 1;
        }
      }
      since = v.cursor;
      state = { ...state, serverCursor: since };
      break;
    }

    for (const c of v.changes) {
      const ok = await applyRemote(c.path, c.rev, c.deleted);
      if (ok) {
        if (c.deleted) deletedLocal += 1;
        else downloaded += 1;
      } else {
        failed += 1;
      }
      since = Math.max(since, Number(c.changeId) || 0);
    }

    if (!v.hasMore) {
      state = { ...state, serverCursor: since };
      break;
    }
  }

  localFilesNow = await listLocal();
  localByPathNow = new Map(localFilesNow.map((f) => [f.path, f]));

  for (const local of localFilesNow) {
    const st = state.files[local.path];
    if (st && !st.deleted && isProbablySame(local, st)) continue;

    let bytes: Uint8Array;
    try {
      bytes = await readBytesAbs(joinAbsPath(vaultRoot, local.path));
    } catch {
      failed += 1;
      continue;
    }
    const sha = await sha256Hex(bytes);
    if (st && !st.deleted && st.sha256 && st.sha256 === sha) continue;

    const baseRev = st?.baseRev || 0;
    const put = await uploadVaultFileV2(opts.baseUrl, token, local.path, local.mtimeMs || Date.now(), bytes, baseRev);
    if (put.ok && put.value) {
      uploaded += 1;
      state = withUploaded(state, local.path, put.value.rev, put.value.sha256, bytes.length, local.mtimeMs);
    } else if (put.status === 409) {
      conflicts += 1;
      const latest: VaultFileDownloadV2 | undefined = (await downloadVaultFileV2(opts.baseUrl, token, local.path)).value;
      if (latest) {
        const ok = await applyRemote(local.path, latest.rev, false);
        if (!ok) failed += 1;
      } else {
        failed += 1;
      }
    } else {
      failed += 1;
    }
  }

  for (const [path, st] of Object.entries(state.files)) {
    if (st.deleted) continue;
    if (!shouldSyncPath(path)) continue;
    if (localByPathNow.has(path)) continue;

    const del = await deleteVaultFileV2(opts.baseUrl, token, path, st.baseRev);
    if (del.ok && del.value) {
      deletedRemote += 1;
      state = withDeleted(state, path, del.value.rev);
    } else if (del.status === 409) {
      conflicts += 1;
    } else {
      failed += 1;
    }
  }

  await saveOfficialStateV2(vaultRoot, state);

  return { uploaded, downloaded, deletedRemote, deletedLocal, conflicts, failed };
}
