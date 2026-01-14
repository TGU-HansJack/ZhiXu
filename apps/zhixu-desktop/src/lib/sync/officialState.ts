import { readBytesAbs, writeBytesAbs } from "../vaultApi";

export type OfficialVaultSyncFileState = {
  baseRev: number;
  sha256: string;
  size: number;
  localMtimeMs: number;
  deleted: boolean;
};

export type OfficialVaultSyncStateV2 = {
  serverCursor: number;
  files: Record<string, OfficialVaultSyncFileState>;
};

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

const STATE_REL_PATH = ".zhixu/sync/official_state_v2.json";

export async function loadOfficialStateV2(vaultRoot: string): Promise<OfficialVaultSyncStateV2> {
  const abs = joinAbsPath(vaultRoot, STATE_REL_PATH);
  try {
    const bytes = await readBytesAbs(abs);
    const raw = textDecoder.decode(bytes);
    const obj = raw ? (JSON.parse(raw) as any) : null;
    if (!obj || typeof obj !== "object") return { serverCursor: 0, files: {} };

    const cursor = Number(obj.serverCursor) || 0;
    const filesObj = obj.files && typeof obj.files === "object" ? obj.files : {};
    const files: Record<string, OfficialVaultSyncFileState> = {};
    for (const [path, v] of Object.entries(filesObj)) {
      const p = String(path || "").trim().replace(/^\/+/, "");
      if (!p) continue;
      const f = (v || {}) as any;
      files[p] = {
        baseRev: Number(f.baseRev) || 0,
        sha256: String(f.sha256 || ""),
        size: Number(f.size) || 0,
        localMtimeMs: Number(f.localMtimeMs) || 0,
        deleted: Boolean(f.deleted),
      };
    }
    return { serverCursor: Math.max(0, cursor), files };
  } catch {
    return { serverCursor: 0, files: {} };
  }
}

export async function saveOfficialStateV2(vaultRoot: string, state: OfficialVaultSyncStateV2): Promise<void> {
  const abs = joinAbsPath(vaultRoot, STATE_REL_PATH);
  const obj = {
    version: 2,
    savedAt: Date.now(),
    serverCursor: Math.max(0, Number(state.serverCursor) || 0),
    files: state.files,
  };
  const bytes = textEncoder.encode(JSON.stringify(obj));
  await writeBytesAbs(abs, Array.from(bytes));
}

