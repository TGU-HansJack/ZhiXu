const archiver = require("archiver");
const express = require("express");
const { pool } = require("../db");
const { authRequired } = require("../middleware/auth");
const { storageRoot } = require("../config");
const { buildObjectPath } = require("../storage");

const router = express.Router();
router.use(authRequired);

function parseLimit(raw, fallback, max) {
  const n = Number(raw);
  if (!Number.isFinite(n)) return fallback;
  return Math.min(Math.max(Math.floor(n), 1), max);
}

function parseOffset(raw) {
  const n = Number(raw);
  if (!Number.isFinite(n)) return 0;
  return Math.max(0, Math.floor(n));
}

function parseBool(raw) {
  const v = String(raw ?? "").trim().toLowerCase();
  return v === "1" || v === "true" || v === "yes" || v === "on";
}

function buildExportFilename(userId) {
  const iso = new Date().toISOString().replace(/\..+/, "").replace(/:/g, "-");
  return `zhixu-vault-${userId}-${iso}.zip`;
}

router.get("/stats", async (req, res) => {
  const userId = Number(req.user?.id);
  if (!Number.isFinite(userId) || userId <= 0) return res.status(401).json({ error: "invalid_user" });

  const [[row]] = await pool.query(
    `
SELECT
  COALESCE(SUM(CASE WHEN deleted = 0 THEN size_bytes ELSE 0 END), 0) AS used_bytes,
  COALESCE(SUM(CASE WHEN deleted = 0 THEN 1 ELSE 0 END), 0) AS file_count,
  COALESCE(SUM(CASE WHEN deleted = 1 THEN 1 ELSE 0 END), 0) AS deleted_count,
  COALESCE(MAX(updated_at_ms), 0) AS last_updated_at_ms
FROM vault_files
WHERE user_id = ?
`,
    [userId]
  );

  return res.json({
    usedBytes: Number(row?.used_bytes) || 0,
    fileCount: Number(row?.file_count) || 0,
    deletedCount: Number(row?.deleted_count) || 0,
    lastUpdatedAtMs: Number(row?.last_updated_at_ms) || 0
  });
});

router.get("/files", async (req, res) => {
  const userId = Number(req.user?.id);
  if (!Number.isFinite(userId) || userId <= 0) return res.status(401).json({ error: "invalid_user" });

  const includeDeleted = parseBool(req.query?.includeDeleted);
  const limit = parseLimit(req.query?.limit, 100, 500);
  const offset = parseOffset(req.query?.offset);

  const whereDeleted = includeDeleted ? "" : "AND deleted = 0";
  const [[countRow]] = await pool.query(`SELECT COUNT(*) AS total FROM vault_files WHERE user_id = ? ${whereDeleted}`, [userId]);
  const total = Number(countRow?.total) || 0;

  const [rows] = await pool.query(
    `
SELECT path, rev, updated_at_ms, mtime_ms, size_bytes, sha256, deleted
FROM vault_files
WHERE user_id = ? ${whereDeleted}
ORDER BY updated_at_ms DESC, path ASC
LIMIT ? OFFSET ?
`,
    [userId, limit, offset]
  );

  const files = (rows || []).map((r) => ({
    path: String(r.path || ""),
    rev: Number(r.rev) || 0,
    updatedAt: Number(r.updated_at_ms) || 0,
    mtimeMs: Number(r.mtime_ms) || 0,
    size: Number(r.size_bytes) || 0,
    sha256: String(r.sha256 || ""),
    deleted: Boolean(r.deleted)
  }));

  return res.json({ total, offset, limit, files });
});

router.get("/export", async (req, res) => {
  const userId = Number(req.user?.id);
  if (!Number.isFinite(userId) || userId <= 0) return res.status(401).json({ error: "invalid_user" });

  const [rows] = await pool.query(
    `
SELECT path, updated_at_ms, mtime_ms, size_bytes, sha256
FROM vault_files
WHERE user_id = ? AND deleted = 0
ORDER BY path ASC
`,
    [userId]
  );

  const filename = buildExportFilename(userId);
  res.setHeader("Content-Type", "application/zip");
  res.setHeader("Content-Disposition", `attachment; filename="${filename}"`);
  res.setHeader("Cache-Control", "no-store");

  const archive = archiver("zip", { zlib: { level: 9 } });
  archive.on("warning", (err) => {
    if (String(err?.code) !== "ENOENT") {
      try {
        // eslint-disable-next-line no-console
        console.warn(err);
      } catch (_) {
        // ignore
      }
    }
  });
  archive.on("error", () => {
    if (!res.headersSent) res.status(500);
    res.end();
  });

  res.on("close", () => {
    if (!res.writableEnded) {
      try {
        archive.abort();
      } catch (_) {
        // ignore
      }
    }
  });

  archive.pipe(res);

  const manifest = {
    version: 1,
    userId,
    exportedAtMs: Date.now(),
    fileCount: Array.isArray(rows) ? rows.length : 0,
    files: (rows || []).map((r) => ({
      path: String(r.path || ""),
      updatedAt: Number(r.updated_at_ms) || 0,
      mtimeMs: Number(r.mtime_ms) || 0,
      size: Number(r.size_bytes) || 0,
      sha256: String(r.sha256 || "")
    }))
  };
  archive.append(JSON.stringify(manifest, null, 2) + "\n", { name: "zhixu-export.json" });

  for (const r of rows || []) {
    const p = String(r.path || "");
    if (!p) continue;
    const obj = buildObjectPath(storageRoot, userId, p);
    if (!obj) continue;
    archive.file(obj.absPath, { name: p });
  }

  try {
    await archive.finalize();
  } catch (_) {
    if (!res.headersSent) res.status(500);
    return res.end();
  }
});

module.exports = router;

