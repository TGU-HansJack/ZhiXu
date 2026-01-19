import { httpRequest } from "../vaultApi";

const textDecoder = new TextDecoder();
const textEncoder = new TextEncoder();

export type SyncServerAvatarInfo = {
  mime: string;
  updatedAtMs: number;
  hasAvatar: boolean;
};

export type SyncServerStorageInfo = {
  usedBytes: number;
  limitBytes: number;
};

export type SyncServerMe = {
  userId: number;
  username: string;
  email: string;
  emailVerifiedAtMs: number;
  emailVerified: boolean;
  avatar: SyncServerAvatarInfo;
  storage: SyncServerStorageInfo;
};

export type VaultChangeEntry = {
  changeId: number;
  path: string;
  rev: number;
  updatedAt: number;
  mtimeMs: number;
  size: number;
  sha256: string;
  deleted: boolean;
};

export type VaultChangesV2 = {
  serverTime: number;
  cursor: number;
  snapshot: boolean;
  changes: VaultChangeEntry[];
  nextSince: number;
  hasMore: boolean;
};

export type VaultFileDownloadV2 = {
  bytes: Uint8Array;
  rev: number;
  mtimeMs: number;
  size: number;
  sha256: string;
};

export type VaultPutResultV2 = {
  path: string;
  rev: number;
  changeId: number;
  sha256: string;
  size: number;
  mtimeMs: number;
};

export type VaultDeleteResultV2 = {
  path: string;
  rev: number;
  changeId: number;
  deleted: boolean;
};

export type SyncServerResult<T> = {
  ok: boolean;
  status: number;
  value?: T;
  errorMessage?: string;
  raw?: string;
};

function normalizeBaseUrl(baseUrl: string): string {
  const clean = String(baseUrl || "").trim().replace(/\/+$/, "");
  return clean || "https://zhixu.app";
}

function joinUrl(baseUrl: string, path: string): string {
  const left = normalizeBaseUrl(baseUrl);
  const right = String(path || "").trim();
  if (!right) return left;
  return right.startsWith("/") ? `${left}${right}` : `${left}/${right}`;
}

function encodeQuery(value: string): string {
  return encodeURIComponent(value);
}

function decodeText(bytes: Uint8Array): string {
  return textDecoder.decode(bytes);
}

function headerValue(headers: Array<{ name: string; value: string }>, name: string): string {
  const lower = name.toLowerCase();
  for (const h of headers) {
    if (String(h.name || "").toLowerCase() === lower) return String(h.value || "");
  }
  return "";
}

async function requestJson<T>(opts: {
  method: string;
  url: string;
  token?: string;
  body?: unknown;
  timeoutMs?: number;
}): Promise<SyncServerResult<T>> {
  const headers: Array<{ name: string; value: string }> = [{ name: "Content-Type", value: "application/json; charset=utf-8" }];
  if (opts.token) headers.push({ name: "Authorization", value: `Bearer ${opts.token}` });
  headers.push({ name: "User-Agent", value: "Zhixu-Desktop" });

  const bodyBytes =
    opts.body === undefined
      ? undefined
      : textEncoder.encode(typeof opts.body === "string" ? opts.body : JSON.stringify(opts.body));

  const res = await httpRequest({
    method: opts.method,
    url: opts.url,
    headers,
    body: bodyBytes,
    timeoutMs: opts.timeoutMs,
  });

  const raw = decodeText(res.bytes);
  if (!res.ok) {
    return { ok: false, status: res.status, errorMessage: raw.trim() || `HTTP ${res.status}`, raw };
  }

  const obj = (() => {
    try {
      return raw ? (JSON.parse(raw) as T) : (null as any as T);
    } catch {
      return null;
    }
  })();

  if (obj == null) return { ok: false, status: res.status, errorMessage: "Invalid response", raw };
  return { ok: true, status: res.status, value: obj, raw };
}

export async function health(baseUrl: string): Promise<SyncServerResult<void>> {
  const url = joinUrl(baseUrl, "/health");
  const res = await httpRequest({ method: "GET", url, headers: [{ name: "User-Agent", value: "Zhixu-Desktop" }] });
  if (!res.ok) return { ok: false, status: res.status, errorMessage: decodeText(res.bytes).trim() || `HTTP ${res.status}` };
  return { ok: true, status: res.status, value: undefined };
}

export async function register(baseUrl: string, username: string, password: string, email?: string): Promise<SyncServerResult<{ userId: number }>> {
  const url = joinUrl(baseUrl, "/api/auth/register");
  const body: any = { username, password };
  if (email) body.email = email;
  return requestJson<{ userId: number }>({ method: "POST", url, body });
}

export async function login(
  baseUrl: string,
  username: string,
  password: string,
): Promise<SyncServerResult<{ token: string; sessionId?: string; refreshToken?: string }>> {
  const url = joinUrl(baseUrl, "/api/auth/login");
  return requestJson<{ token: string; sessionId?: string; refreshToken?: string }>({
    method: "POST",
    url,
    body: { username, password, deviceName: "Zhixu Desktop" },
  });
}

export async function logout(baseUrl: string, token: string): Promise<SyncServerResult<{ ok: boolean }>> {
  const url = joinUrl(baseUrl, "/api/auth/logout");
  return requestJson<{ ok: boolean }>({ method: "POST", url, token, body: {} });
}

export async function me(baseUrl: string, token: string): Promise<SyncServerResult<SyncServerMe>> {
  const url = joinUrl(baseUrl, "/api/account/me");
  const res = await requestJson<any>({ method: "GET", url, token });
  if (!res.ok || !res.value) return res as SyncServerResult<SyncServerMe>;
  const v = res.value as any;

  const avatarObj = v?.avatar;
  const avatar: SyncServerAvatarInfo = {
    mime: String(avatarObj?.mime || ""),
    updatedAtMs: Number(avatarObj?.updatedAtMs) || 0,
    hasAvatar: Boolean(avatarObj?.hasAvatar),
  };

  const storageObj = v?.storage;
  const storage: SyncServerStorageInfo = {
    usedBytes: Number(storageObj?.usedBytes) || 0,
    limitBytes: Number(storageObj?.limitBytes) || 0,
  };

  return {
    ok: true,
    status: res.status,
    value: {
      userId: Number(v?.userId) || 0,
      username: String(v?.username || ""),
      email: String(v?.email || ""),
      emailVerifiedAtMs: Number(v?.emailVerifiedAtMs) || 0,
      emailVerified: Boolean(v?.emailVerified),
      avatar,
      storage,
    },
    raw: res.raw,
  };
}

export async function vaultChangesV2(
  baseUrl: string,
  token: string,
  since: number,
  limit: number,
): Promise<SyncServerResult<VaultChangesV2>> {
  const url = joinUrl(baseUrl, `/api/v2/vault/changes?since=${encodeQuery(String(Math.max(0, since || 0)))}&limit=${encodeQuery(String(Math.max(1, limit || 2000)))}`);
  const res = await requestJson<any>({ method: "GET", url, token });
  if (!res.ok || !res.value) return res as SyncServerResult<VaultChangesV2>;
  const v = res.value as any;
  const changes: VaultChangeEntry[] = Array.isArray(v?.changes)
    ? v.changes.map((c: any) => ({
        changeId: Number(c?.changeId) || 0,
        path: String(c?.path || ""),
        rev: Number(c?.rev) || 0,
        updatedAt: Number(c?.updatedAt) || 0,
        mtimeMs: Number(c?.mtimeMs) || 0,
        size: Number(c?.size) || 0,
        sha256: String(c?.sha256 || ""),
        deleted: Boolean(c?.deleted),
      }))
    : [];

  return {
    ok: true,
    status: res.status,
    value: {
      serverTime: Number(v?.serverTime) || Date.now(),
      cursor: Number(v?.cursor) || 0,
      snapshot: Boolean(v?.snapshot),
      changes,
      nextSince: Number(v?.nextSince) || 0,
      hasMore: Boolean(v?.hasMore),
    },
    raw: res.raw,
  };
}

export async function downloadVaultFileV2(baseUrl: string, token: string, path: string): Promise<SyncServerResult<VaultFileDownloadV2>> {
  const url = joinUrl(baseUrl, `/api/v2/vault/file?path=${encodeQuery(path)}`);
  const res = await httpRequest({
    method: "GET",
    url,
    headers: [
      { name: "Authorization", value: `Bearer ${token}` },
      { name: "User-Agent", value: "Zhixu-Desktop" },
    ],
  });
  if (!res.ok) {
    const msg = decodeText(res.bytes).trim();
    return { ok: false, status: res.status, errorMessage: msg || `HTTP ${res.status}`, raw: msg };
  }

  const rev = Number(headerValue(res.headers, "x-zhixu-rev")) || 0;
  const mtimeMs = Number(headerValue(res.headers, "x-zhixu-mtime-ms")) || 0;
  const size = Number(headerValue(res.headers, "x-zhixu-size")) || res.bytes.length;
  const sha256 = headerValue(res.headers, "x-zhixu-sha256");
  return { ok: true, status: res.status, value: { bytes: res.bytes, rev, mtimeMs, size, sha256 } };
}

export async function uploadVaultFileV2(
  baseUrl: string,
  token: string,
  path: string,
  mtimeMs: number,
  bytes: Uint8Array,
  baseRev: number,
): Promise<SyncServerResult<VaultPutResultV2>> {
  const url = joinUrl(
    baseUrl,
    `/api/v2/vault/file?path=${encodeQuery(path)}&mtimeMs=${encodeQuery(String(Math.max(0, mtimeMs || 0)))}&baseRev=${encodeQuery(String(Math.max(0, baseRev || 0)))}`,
  );
  const res = await httpRequest({
    method: "PUT",
    url,
    headers: [
      { name: "Authorization", value: `Bearer ${token}` },
      { name: "User-Agent", value: "Zhixu-Desktop" },
      { name: "Content-Type", value: "application/octet-stream" },
    ],
    body: bytes,
  });

  const raw = decodeText(res.bytes);
  if (!res.ok) return { ok: false, status: res.status, errorMessage: raw.trim() || `HTTP ${res.status}`, raw };
  const obj = (() => {
    try {
      return raw ? (JSON.parse(raw) as any) : null;
    } catch {
      return null;
    }
  })();
  if (!obj) return { ok: false, status: res.status, errorMessage: "Invalid response", raw };
  return {
    ok: true,
    status: res.status,
    value: {
      path: String(obj.path || path),
      rev: Number(obj.rev) || 0,
      changeId: Number(obj.changeId) || 0,
      sha256: String(obj.sha256 || ""),
      size: Number(obj.size) || bytes.length,
      mtimeMs: Number(obj.mtimeMs) || mtimeMs,
    },
    raw,
  };
}

export async function deleteVaultFileV2(baseUrl: string, token: string, path: string, baseRev: number): Promise<SyncServerResult<VaultDeleteResultV2>> {
  const url = joinUrl(baseUrl, `/api/v2/vault/file?path=${encodeQuery(path)}&baseRev=${encodeQuery(String(Math.max(0, baseRev || 0)))}`);
  const res = await httpRequest({
    method: "DELETE",
    url,
    headers: [
      { name: "Authorization", value: `Bearer ${token}` },
      { name: "User-Agent", value: "Zhixu-Desktop" },
    ],
  });

  const raw = decodeText(res.bytes);
  if (!res.ok) return { ok: false, status: res.status, errorMessage: raw.trim() || `HTTP ${res.status}`, raw };
  const obj = (() => {
    try {
      return raw ? (JSON.parse(raw) as any) : null;
    } catch {
      return null;
    }
  })();
  if (!obj) return { ok: false, status: res.status, errorMessage: "Invalid response", raw };
  return {
    ok: true,
    status: res.status,
    value: {
      path: String(obj.path || path),
      rev: Number(obj.rev) || 0,
      changeId: Number(obj.changeId) || 0,
      deleted: Boolean(obj.deleted),
    },
    raw,
  };
}
