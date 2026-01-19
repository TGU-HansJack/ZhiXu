const crypto = require("crypto");
const fs = require("fs");
const express = require("express");
const { pool } = require("../db");
const { authRequired } = require("../middleware/auth");
const { rawBodyLimitBytes, storageRoot, storageLimitBytes } = require("../config");
const { getSetting } = require("../settings");
const { buildObjectPath, atomicWriteFile, deleteFileBestEffort } = require("../storage");
const { getRequestIp } = require("../http");

const router = express.Router();
router.use(authRequired);

router.use(async (req, res, next) => {
  const userId = Number(req.user?.id);
  if (!Number.isFinite(userId) || userId <= 0) return res.status(401).json({ error: "invalid_user" });

  try {
    const disabledAll = String(await getSetting("sync_disabled_all", "0")) === "1";
    if (disabledAll) return res.status(403).json({ error: "sync_disabled_all" });

    const [[u]] = await pool.query("SELECT COALESCE(sync_disabled, 0) AS sync_disabled FROM users WHERE id = ? LIMIT 1", [userId]);
    if ((Number(u?.sync_disabled) || 0) > 0) return res.status(403).json({ error: "sync_disabled_user" });

    return next();
  } catch (_) {
    return res.status(500).json({ error: "server_error" });
  }
});

function normalizeVaultPath(raw) {
  const value = String(raw || "").trim();
  if (!value) return null;
  if (value.length > 1024) return null;
  const decoded = (() => {
    try {
      return decodeURIComponent(value);
    } catch {
      return value;
    }
  })();

  const replaced = decoded.replace(/\\/g, "/").replace(/^\//, "");
  if (!replaced) return null;
  if (replaced.includes("\0")) return null;

  const parts = replaced.split("/").filter(Boolean);
  if (parts.some((p) => p === "." || p === "..")) return null;

  const normalized = parts.join("/");

  // Never allow clients to sync internal dirs.
  const lower = normalized.toLowerCase();
  if (lower.startsWith(".zhixu/sync/")) return null;
  if (lower.startsWith(".zhixu/conflicts/")) return null;
  if (lower.startsWith(".zhixu/history/")) return null;
  const fileName = parts[parts.length - 1] || "";
  if (fileName.toLowerCase().startsWith("conflict ")) return null;
  return normalized;
}

function sha256Hex(bytes) {
  return crypto.createHash("sha256").update(bytes).digest("hex");
}

function sha256BytesUtf8(text) {
  return crypto.createHash("sha256").update(String(text), "utf8").digest();
}

async function logSyncEvent(req, { userId, action, path = "", sizeBytes = 0, statusCode = 200, errorCode = "" }) {
  const sessionId = String(req.sessionId || "").trim();
  const ip = getRequestIp(req).slice(0, 64);
  const client = String(req.header("User-Agent") || "").trim().slice(0, 255);
  const now = Date.now();
  try {
    await pool.query(
      "INSERT INTO sync_logs (user_id, session_id, action, path, ip, client, size_bytes, status_code, error_code, created_at_ms) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
      [
        userId,
        sessionId,
        String(action || "").slice(0, 32),
        String(path || "").slice(0, 1024),
        ip,
        client,
        Math.max(0, Number(sizeBytes) || 0),
        Math.max(0, Number(statusCode) || 0),
        String(errorCode || "").slice(0, 64),
        now
      ]
    );
  } catch (_) {
    // ignore best-effort log
  }
}

function respondError(req, res, { userId, action, path = "", status = 400, error = "error", sizeBytes = 0, extra = undefined }) {
  void logSyncEvent(req, { userId, action, path, sizeBytes, statusCode: status, errorCode: error });
  const payload = { error };
  if (extra && typeof extra === "object") Object.assign(payload, extra);
  return res.status(status).json(payload);
}

async function getCurrentFileRow(db, userId, path, pathHash, { forUpdate = false } = {}) {
  const lock = forUpdate ? " FOR UPDATE" : "";
  const [rows] = await db.query(
    `
SELECT path, rev, updated_at_ms, mtime_ms, size_bytes, sha256, deleted
FROM vault_files
WHERE user_id = ? AND path_hash = ?
LIMIT 2${lock}
`,
    [userId, pathHash]
  );
  const row = rows?.[0];
  if (!row) return null;
  if (String(row.path || "") !== path) return { error: "path_hash_collision" };
  return {
    path: String(row.path || path),
    rev: Number(row.rev) || 0,
    updatedAt: Number(row.updated_at_ms) || 0,
    mtimeMs: Number(row.mtime_ms) || 0,
    size: Number(row.size_bytes) || 0,
    sha256: String(row.sha256 || ""),
    deleted: Boolean(row.deleted)
  };
}

async function insertChangeRow({ db, userId, path, pathHash, rev, now, mtimeMs, sizeBytes, sha256, deleted }) {
  const [r] = await db.query(
    `
INSERT INTO vault_changes (user_id, path, path_hash, rev, updated_at_ms, mtime_ms, size_bytes, sha256, deleted)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
`,
    [userId, path, pathHash, rev, now, mtimeMs, sizeBytes, sha256, deleted ? 1 : 0]
  );
  return Number(r?.insertId) || 0;
}

router.get("/changes", async (req, res) => {
  const userId = Number(req.user.id);
  if (!Number.isFinite(userId)) return res.status(401).json({ error: "invalid_user" });

  const since = Number(req.query?.since || 0);
  const sinceId = Number.isFinite(since) && since > 0 ? since : 0;
  const limitRaw = Number(req.query?.limit || 2000);
  const limit = Number.isFinite(limitRaw) ? Math.min(Math.max(limitRaw, 1), 5000) : 2000;

  const [[cursorRow]] = await pool.query(
    "SELECT COALESCE(MAX(change_id), 0) AS server_cursor FROM vault_changes WHERE user_id = ?",
    [userId]
  );
  const serverCursor = Number(cursorRow?.server_cursor) || 0;

  if (sinceId === 0) {
    const [rows] = await pool.query(
      `
SELECT path, rev, updated_at_ms, mtime_ms, size_bytes, sha256, deleted
FROM vault_files
WHERE user_id = ?
ORDER BY path ASC
`,
      [userId]
    );
    const changes = (rows || []).map((r) => ({
      changeId: 0,
      path: String(r.path || ""),
      rev: Number(r.rev) || 0,
      updatedAt: Number(r.updated_at_ms) || 0,
      mtimeMs: Number(r.mtime_ms) || 0,
      size: Number(r.size_bytes) || 0,
      sha256: String(r.sha256 || ""),
      deleted: Boolean(r.deleted)
    }));

    void logSyncEvent(req, { userId, action: "changes_snapshot", sizeBytes: changes.length });
    return res.json({ serverTime: Date.now(), cursor: serverCursor, snapshot: true, changes, nextSince: 0, hasMore: false });
  }

  const [rows] = await pool.query(
    `
SELECT change_id, path, rev, updated_at_ms, mtime_ms, size_bytes, sha256, deleted
FROM vault_changes
WHERE user_id = ? AND change_id > ?
ORDER BY change_id ASC
LIMIT ?
`,
    [userId, sinceId, limit]
  );

  const changes = (rows || []).map((r) => ({
    changeId: Number(r.change_id) || 0,
    path: String(r.path || ""),
    rev: Number(r.rev) || 0,
    updatedAt: Number(r.updated_at_ms) || 0,
    mtimeMs: Number(r.mtime_ms) || 0,
    size: Number(r.size_bytes) || 0,
    sha256: String(r.sha256 || ""),
    deleted: Boolean(r.deleted)
  }));
  const nextSince = changes.length ? Number(changes[changes.length - 1].changeId) || sinceId : sinceId;
  const hasMore = changes.length >= limit;
  void logSyncEvent(req, { userId, action: "changes_delta", sizeBytes: changes.length });
  return res.json({ serverTime: Date.now(), cursor: serverCursor, snapshot: false, changes, nextSince, hasMore });
});

router.get("/file", async (req, res) => {
  const userId = Number(req.user.id);
  if (!Number.isFinite(userId)) return res.status(401).json({ error: "invalid_user" });

  const path = normalizeVaultPath(req.query?.path);
  if (!path) return respondError(req, res, { userId, action: "file_get", status: 400, error: "invalid_path" });
  const pathHash = sha256BytesUtf8(path);

  const [rows] = await pool.query(
    `
SELECT path, rev, mtime_ms, size_bytes, sha256, deleted
FROM vault_files
WHERE user_id = ? AND path_hash = ?
LIMIT 2
  `,
    [userId, pathHash]
  );
  const row = rows?.[0];
  if (!row) return respondError(req, res, { userId, action: "file_get", path, status: 404, error: "not_found" });
  if (String(row.path || "") !== path) return respondError(req, res, { userId, action: "file_get", path, status: 409, error: "path_hash_collision" });
  if (row.deleted) return respondError(req, res, { userId, action: "file_get", path, status: 410, error: "deleted" });

  const obj = buildObjectPath(storageRoot, userId, path);
  if (!obj) return respondError(req, res, { userId, action: "file_get", path, status: 400, error: "invalid_path" });
  const absPath = obj.absPath;

  res.setHeader("Content-Type", "application/octet-stream");
  res.setHeader("X-Zhixu-Rev", String(Number(row.rev) || 0));
  res.setHeader("X-Zhixu-Mtime-Ms", String(Number(row.mtime_ms) || 0));
  res.setHeader("X-Zhixu-Size", String(Number(row.size_bytes) || 0));
  res.setHeader("X-Zhixu-Sha256", String(row.sha256 || ""));
  try {
    await fs.promises.access(absPath, fs.constants.R_OK);
  } catch (_) {
    return respondError(req, res, { userId, action: "file_get", path, status: 404, error: "file_missing" });
  }

  void logSyncEvent(req, { userId, action: "file_get", path, sizeBytes: Number(row.size_bytes) || 0 });
  const stream = fs.createReadStream(absPath);
  stream.on("error", () => {
    if (!res.headersSent) res.status(500);
    res.end();
  });
  return stream.pipe(res.status(200));
});

router.put(
  "/file",
  express.raw({ type: "*/*", limit: rawBodyLimitBytes }),
  async (req, res) => {
    const userId = Number(req.user.id);
    if (!Number.isFinite(userId)) return res.status(401).json({ error: "invalid_user" });

    const path = normalizeVaultPath(req.query?.path);
    if (!path) return respondError(req, res, { userId, action: "file_put", status: 400, error: "invalid_path" });
    const pathHash = sha256BytesUtf8(path);

    const baseRevRaw = Number(req.query?.baseRev || req.header("X-Zhixu-Base-Rev") || 0);
    const baseRev = Number.isFinite(baseRevRaw) && baseRevRaw >= 0 ? baseRevRaw : NaN;
    if (!Number.isFinite(baseRev)) return respondError(req, res, { userId, action: "file_put", path, status: 400, error: "invalid_baseRev" });

    const mtimeMs = Number(req.query?.mtimeMs || req.header("X-Zhixu-Mtime-Ms") || 0);
    const safeMtimeMs = Number.isFinite(mtimeMs) && mtimeMs > 0 ? mtimeMs : Date.now();

    const bytes = Buffer.isBuffer(req.body) ? req.body : Buffer.alloc(0);
    if (!bytes.length) return respondError(req, res, { userId, action: "file_put", path, status: 400, error: "empty_body" });

    const now = Date.now();
    const hash = sha256Hex(bytes);
    const sizeBytes = bytes.length;

    const conn = await pool.getConnection();
    try {
      await conn.beginTransaction();

      const current = await getCurrentFileRow(conn, userId, path, pathHash, { forUpdate: true });
      if (current?.error) {
        await conn.rollback();
        return respondError(req, res, { userId, action: "file_put", path, status: 409, error: current.error, sizeBytes });
      }
      const currentRev = current?.rev || 0;

      if (baseRev !== currentRev) {
        await conn.rollback();
        return respondError(req, res, {
          userId,
          action: "file_put",
          path,
          status: 409,
          error: "rev_conflict",
          sizeBytes,
          extra: { path, expectedBaseRev: currentRev, current: current || { path, rev: 0, deleted: false } }
        });
      }

      const newRev = currentRev + 1;
      const quotaLimitBytes = Number.isFinite(storageLimitBytes) ? Number(storageLimitBytes) : 5 * 1024 * 1024 * 1024;
      if (Number.isFinite(quotaLimitBytes) && quotaLimitBytes > 0) {
        const prevSize = current && !current.deleted ? Number(current.size) || 0 : 0;
        const [[usageRow]] = await conn.query(
          `
SELECT COALESCE(SUM(CASE WHEN deleted = 0 THEN size_bytes ELSE 0 END), 0) AS used_bytes
FROM vault_files
WHERE user_id = ?
FOR UPDATE
`,
          [userId]
        );
        const usedBytes = Number(usageRow?.used_bytes) || 0;
        const nextUsed = Math.max(0, usedBytes - Math.max(0, prevSize)) + sizeBytes;
        if (nextUsed > quotaLimitBytes) {
          await conn.rollback();
          return respondError(req, res, {
            userId,
            action: "file_put",
            path,
            status: 413,
            error: "storage_quota_exceeded",
            sizeBytes,
            extra: { limitBytes: quotaLimitBytes, usedBytes, requiredBytes: nextUsed }
          });
        }
      }

      const obj = buildObjectPath(storageRoot, userId, path);
      if (!obj) {
        await conn.rollback();
        return respondError(req, res, { userId, action: "file_put", path, status: 400, error: "invalid_path", sizeBytes });
      }
      await atomicWriteFile(obj.absPath, bytes);

      await conn.query(
        `
INSERT INTO vault_files (user_id, path, path_hash, rev, updated_at_ms, mtime_ms, size_bytes, sha256, deleted)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
ON DUPLICATE KEY UPDATE
  path = VALUES(path),
  rev = VALUES(rev),
  updated_at_ms = VALUES(updated_at_ms),
  mtime_ms = VALUES(mtime_ms),
  size_bytes = VALUES(size_bytes),
  sha256 = VALUES(sha256),
  deleted = 0
`,
        [userId, path, pathHash, newRev, now, safeMtimeMs, sizeBytes, hash]
      );
      const changeId = await insertChangeRow({
        db: conn,
        userId,
        path,
        pathHash,
        rev: newRev,
        now,
        mtimeMs: safeMtimeMs,
        sizeBytes,
        sha256: hash,
        deleted: false
      });

      await conn.commit();
      void logSyncEvent(req, { userId, action: "file_put", path, sizeBytes });
      return res.json({
        ok: true,
        serverTime: now,
        path,
        rev: newRev,
        changeId,
        sha256: hash,
        size: sizeBytes,
        mtimeMs: safeMtimeMs
      });
    } catch (e) {
      try {
        await conn.rollback();
      } catch (_) {
        // ignore
      }
      return respondError(req, res, { userId, action: "file_put", path, status: 500, error: "internal_error", sizeBytes });
    } finally {
      conn.release();
    }
  }
);

router.delete("/file", async (req, res) => {
  const userId = Number(req.user.id);
  if (!Number.isFinite(userId)) return res.status(401).json({ error: "invalid_user" });

  const path = normalizeVaultPath(req.query?.path);
  if (!path) return respondError(req, res, { userId, action: "file_delete", status: 400, error: "invalid_path" });
  const pathHash = sha256BytesUtf8(path);

  const baseRevRaw = Number(req.query?.baseRev || req.header("X-Zhixu-Base-Rev") || 0);
  const baseRev = Number.isFinite(baseRevRaw) && baseRevRaw >= 0 ? baseRevRaw : NaN;
  if (!Number.isFinite(baseRev)) return respondError(req, res, { userId, action: "file_delete", path, status: 400, error: "invalid_baseRev" });

  const now = Date.now();

  const conn = await pool.getConnection();
  try {
    await conn.beginTransaction();

    const current = await getCurrentFileRow(conn, userId, path, pathHash, { forUpdate: true });
    if (current?.error) {
      await conn.rollback();
      return respondError(req, res, { userId, action: "file_delete", path, status: 409, error: current.error });
    }
    const currentRev = current?.rev || 0;

    if (baseRev !== currentRev) {
      await conn.rollback();
      return respondError(req, res, {
        userId,
        action: "file_delete",
        path,
        status: 409,
        error: "rev_conflict",
        extra: { path, expectedBaseRev: currentRev, current: current || { path, rev: 0, deleted: false } }
      });
    }

    if (current && current.deleted) {
      await conn.rollback();
      return res.json({ ok: true, serverTime: now, path, deleted: true, rev: currentRev, changeId: 0 });
    }

    const newRev = currentRev + 1;
    const zeroHash = "0".repeat(64);

    await conn.query(
      `
INSERT INTO vault_files (user_id, path, path_hash, rev, updated_at_ms, mtime_ms, size_bytes, sha256, deleted)
VALUES (?, ?, ?, ?, ?, 0, 0, ?, 1)
ON DUPLICATE KEY UPDATE
  path = VALUES(path),
  rev = VALUES(rev),
  updated_at_ms = VALUES(updated_at_ms),
  mtime_ms = 0,
  size_bytes = 0,
  sha256 = VALUES(sha256),
  deleted = 1
`,
      [userId, path, pathHash, newRev, now, zeroHash]
    );
    const changeId = await insertChangeRow({
      db: conn,
      userId,
      path,
      pathHash,
      rev: newRev,
      now,
      mtimeMs: 0,
      sizeBytes: 0,
      sha256: zeroHash,
      deleted: true
    });
    await conn.commit();

    const obj = buildObjectPath(storageRoot, userId, path);
    if (obj) await deleteFileBestEffort(obj.absPath);
    void logSyncEvent(req, { userId, action: "file_delete", path, sizeBytes: 0 });
    return res.json({ ok: true, serverTime: now, path, deleted: true, rev: newRev, changeId });
  } catch (e) {
    try {
      await conn.rollback();
    } catch (_) {
      // ignore
    }
    return respondError(req, res, { userId, action: "file_delete", path, status: 500, error: "internal_error" });
  } finally {
    conn.release();
  }
});

module.exports = router;
