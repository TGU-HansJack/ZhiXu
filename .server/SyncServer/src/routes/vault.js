const crypto = require("crypto");
const express = require("express");
const { pool } = require("../db");
const { authRequired } = require("../middleware/auth");
const { rawBodyLimitBytes } = require("../config");

const router = express.Router();
router.use(authRequired);

function normalizeVaultPath(raw) {
  const value = String(raw || "").trim();
  if (!value) return null;
  if (value.length > 512) return null;
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

  return parts.join("/");
}

function sha256Hex(bytes) {
  return crypto.createHash("sha256").update(bytes).digest("hex");
}

router.get("/manifest", async (req, res) => {
  const userId = Number(req.user.id);
  if (!Number.isFinite(userId)) return res.status(401).json({ error: "invalid_user" });

  const [rows] = await pool.query(
    `
SELECT path, updated_at_ms, mtime_ms, size_bytes, sha256, deleted
FROM vault_files
WHERE user_id = ?
ORDER BY path ASC
`,
    [userId]
  );

  const files = (rows || []).map((r) => ({
    path: r.path,
    updatedAt: Number(r.updated_at_ms),
    mtimeMs: Number(r.mtime_ms),
    size: Number(r.size_bytes),
    sha256: String(r.sha256 || ""),
    deleted: Boolean(r.deleted)
  }));

  return res.json({ serverTime: Date.now(), files });
});

router.get("/file", async (req, res) => {
  const userId = Number(req.user.id);
  if (!Number.isFinite(userId)) return res.status(401).json({ error: "invalid_user" });

  const path = normalizeVaultPath(req.query?.path);
  if (!path) return res.status(400).json({ error: "invalid_path" });

  const [rows] = await pool.query(
    `
SELECT mtime_ms, size_bytes, sha256, deleted, content
FROM vault_files
WHERE user_id = ? AND path = ?
LIMIT 1
`,
    [userId, path]
  );
  const row = rows?.[0];
  if (!row) return res.status(404).json({ error: "not_found" });
  if (row.deleted) return res.status(410).json({ error: "deleted" });

  const content = row.content || Buffer.alloc(0);
  res.setHeader("Content-Type", "application/octet-stream");
  res.setHeader("X-Zhixu-Mtime-Ms", String(Number(row.mtime_ms) || 0));
  res.setHeader("X-Zhixu-Size", String(Number(row.size_bytes) || content.length));
  res.setHeader("X-Zhixu-Sha256", String(row.sha256 || ""));
  return res.status(200).send(Buffer.from(content));
});

router.put(
  "/file",
  express.raw({ type: "*/*", limit: rawBodyLimitBytes }),
  async (req, res) => {
    const userId = Number(req.user.id);
    if (!Number.isFinite(userId)) return res.status(401).json({ error: "invalid_user" });

    const path = normalizeVaultPath(req.query?.path);
    if (!path) return res.status(400).json({ error: "invalid_path" });

    const mtimeMs = Number(req.query?.mtimeMs || 0);
    const mtime = Number.isFinite(mtimeMs) && mtimeMs > 0 ? mtimeMs : Date.now();

    const bytes = Buffer.isBuffer(req.body) ? req.body : Buffer.alloc(0);
    if (!bytes.length) return res.status(400).json({ error: "empty_body" });
    const now = Date.now();
    const hash = sha256Hex(bytes);

    await pool.query(
      `
INSERT INTO vault_files (user_id, path, updated_at_ms, mtime_ms, size_bytes, sha256, deleted, content)
VALUES (?, ?, ?, ?, ?, ?, 0, ?)
ON DUPLICATE KEY UPDATE
  updated_at_ms = VALUES(updated_at_ms),
  mtime_ms = VALUES(mtime_ms),
  size_bytes = VALUES(size_bytes),
  sha256 = VALUES(sha256),
  deleted = 0,
  content = VALUES(content)
`,
      [userId, path, now, mtime, bytes.length, hash, bytes]
    );

    return res.json({ ok: true, serverTime: now, sha256: hash, size: bytes.length, mtimeMs: mtime });
  }
);

router.delete("/file", async (req, res) => {
  const userId = Number(req.user.id);
  if (!Number.isFinite(userId)) return res.status(401).json({ error: "invalid_user" });

  const path = normalizeVaultPath(req.query?.path);
  if (!path) return res.status(400).json({ error: "invalid_path" });

  const now = Date.now();
  const [rows] = await pool.query(
    `
SELECT id, deleted
FROM vault_files
WHERE user_id = ? AND path = ?
LIMIT 1
`,
    [userId, path]
  );
  const existing = rows?.[0];
  if (!existing) {
    await pool.query(
      `
INSERT INTO vault_files (user_id, path, updated_at_ms, mtime_ms, size_bytes, sha256, deleted, content)
VALUES (?, ?, ?, 0, 0, ?, 1, NULL)
`,
      [userId, path, now, "0".repeat(64)]
    );
    return res.json({ ok: true, deleted: true, serverTime: now });
  }

  if (existing.deleted) return res.json({ ok: true, deleted: true, serverTime: now });

  await pool.query(
    `
UPDATE vault_files
SET updated_at_ms = ?, deleted = 1, content = NULL
WHERE user_id = ? AND path = ?
`,
    [now, userId, path]
  );
  return res.json({ ok: true, deleted: true, serverTime: now });
});

module.exports = router;
