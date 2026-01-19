const fs = require("fs");
const path = require("path");
const express = require("express");
const { pool } = require("../db");
const { authRequired } = require("../middleware/auth");
const { adminRequired } = require("../middleware/admin");
const { isSmtpConfigured, sendMail } = require("../mailer");
const { adminEmail, storageRoot } = require("../config");
const { getSetting, setSetting } = require("../settings");

const router = express.Router();
router.use(authRequired);
router.use(adminRequired);

function parseBool(raw, fallback = false) {
  if (raw == null) return fallback;
  const v = String(raw).trim().toLowerCase();
  if (v === "1" || v === "true" || v === "yes" || v === "on") return true;
  if (v === "0" || v === "false" || v === "no" || v === "off") return false;
  return fallback;
}

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

async function getSyncDisabledAll() {
  const v = await getSetting("sync_disabled_all", "0");
  return String(v || "0") === "1";
}

router.get("/status", async (_req, res) => {
  try {
    return res.json({
      ok: true,
      adminEmail: String(adminEmail || "")
        .trim()
        .toLowerCase(),
      smtpConfigured: isSmtpConfigured(),
      syncDisabledAll: await getSyncDisabledAll(),
      serverTime: Date.now()
    });
  } catch (_) {
    return res.status(500).json({ error: "server_error" });
  }
});

router.get("/users", async (req, res) => {
  const q = String(req.query?.q || "").trim();
  const limit = parseLimit(req.query?.limit, 50, 200);
  const offset = parseOffset(req.query?.offset);

  const whereParts = [];
  const params = [];
  if (q) {
    if (/^\d+$/.test(q)) {
      whereParts.push("u.id = ?");
      params.push(Number(q));
    } else {
      whereParts.push("(u.username LIKE ? OR COALESCE(u.email, '') LIKE ?)");
      params.push(`%${q}%`, `%${q}%`);
    }
  }
  const where = whereParts.length ? `WHERE ${whereParts.join(" AND ")}` : "";

  try {
    const [[countRow]] = await pool.query(`SELECT COUNT(*) AS total FROM users u ${where}`, params);
    const total = Number(countRow?.total) || 0;

    const [rows] = await pool.query(
      `
SELECT
  u.id,
  u.username,
  COALESCE(u.email, '') AS email,
  COALESCE(u.email_verified_at_ms, 0) AS email_verified_at_ms,
  COALESCE(u.sync_disabled, 0) AS sync_disabled,
  UNIX_TIMESTAMP(u.created_at) * 1000 AS created_at_ms,
  COALESCE(v.used_bytes, 0) AS used_bytes,
  COALESCE(v.file_count, 0) AS file_count,
  COALESCE(v.last_updated_at_ms, 0) AS last_updated_at_ms,
  COALESCE(s.last_seen_at_ms, 0) AS last_seen_at_ms,
  COALESCE(l.last_sync_at_ms, 0) AS last_sync_at_ms,
  COALESCE(l.total_count, 0) AS sync_total_count,
  COALESCE(l.error_count, 0) AS sync_error_count
FROM users u
LEFT JOIN (
  SELECT
    user_id,
    COALESCE(SUM(CASE WHEN deleted = 0 THEN size_bytes ELSE 0 END), 0) AS used_bytes,
    COALESCE(SUM(CASE WHEN deleted = 0 THEN 1 ELSE 0 END), 0) AS file_count,
    COALESCE(MAX(updated_at_ms), 0) AS last_updated_at_ms
  FROM vault_files
  GROUP BY user_id
) v ON v.user_id = u.id
LEFT JOIN (
  SELECT user_id, COALESCE(MAX(last_seen_at_ms), 0) AS last_seen_at_ms
  FROM user_sessions
  WHERE revoked_at_ms IS NULL
  GROUP BY user_id
) s ON s.user_id = u.id
LEFT JOIN (
  SELECT
    user_id,
    COALESCE(MAX(created_at_ms), 0) AS last_sync_at_ms,
    COUNT(*) AS total_count,
    COALESCE(SUM(CASE WHEN status_code >= 400 THEN 1 ELSE 0 END), 0) AS error_count
  FROM sync_logs
  GROUP BY user_id
) l ON l.user_id = u.id
${where}
ORDER BY u.id DESC
LIMIT ? OFFSET ?
`,
      [...params, limit, offset]
    );

    const users = (rows || []).map((r) => {
      const syncTotal = Number(r.sync_total_count) || 0;
      const syncErr = Number(r.sync_error_count) || 0;
      return {
        userId: Number(r.id) || 0,
        username: String(r.username || ""),
        email: String(r.email || ""),
        emailVerifiedAtMs: Number(r.email_verified_at_ms) || 0,
        emailVerified: (Number(r.email_verified_at_ms) || 0) > 0,
        createdAtMs: Number(r.created_at_ms) || 0,
        syncDisabled: Boolean(Number(r.sync_disabled) || 0),
        storage: {
          usedBytes: Number(r.used_bytes) || 0,
          fileCount: Number(r.file_count) || 0,
          lastUpdatedAtMs: Number(r.last_updated_at_ms) || 0
        },
        sessions: {
          lastSeenAtMs: Number(r.last_seen_at_ms) || 0
        },
        sync: {
          lastSyncAtMs: Number(r.last_sync_at_ms) || 0,
          totalCount: syncTotal,
          errorCount: syncErr,
          errorRate: syncTotal > 0 ? syncErr / syncTotal : 0
        }
      };
    });

    return res.json({ total, offset, limit, users });
  } catch (_) {
    return res.status(500).json({ error: "server_error" });
  }
});

router.get("/users/:id/sync/summary", async (req, res) => {
  const userId = Number(req.params?.id);
  if (!Number.isFinite(userId) || userId <= 0) return res.status(400).json({ error: "invalid_user" });

  const days = parseLimit(req.query?.days, 30, 365);
  const sinceMs = Date.now() - days * 24 * 60 * 60 * 1000;

  try {
    const [series] = await pool.query(
      `
SELECT
  DATE(FROM_UNIXTIME(created_at_ms / 1000)) AS d,
  COUNT(*) AS total,
  COALESCE(SUM(CASE WHEN status_code >= 400 THEN 1 ELSE 0 END), 0) AS errors
FROM sync_logs
WHERE user_id = ? AND created_at_ms >= ?
GROUP BY d
ORDER BY d ASC
`,
      [userId, sinceMs]
    );

    const [codes] = await pool.query(
      `
SELECT error_code, COUNT(*) AS count
FROM sync_logs
WHERE user_id = ? AND created_at_ms >= ? AND status_code >= 400 AND error_code <> ''
GROUP BY error_code
ORDER BY count DESC
LIMIT 50
`,
      [userId, sinceMs]
    );

    return res.json({
      ok: true,
      userId,
      days,
      sinceMs,
      series: (series || []).map((r) => ({
        day: String(r.d || ""),
        total: Number(r.total) || 0,
        errors: Number(r.errors) || 0
      })),
      topErrorCodes: (codes || []).map((r) => ({ code: String(r.error_code || ""), count: Number(r.count) || 0 }))
    });
  } catch (_) {
    return res.status(500).json({ error: "server_error" });
  }
});

router.get("/sync/summary", async (req, res) => {
  const days = parseLimit(req.query?.days, 30, 365);
  const sinceMs = Date.now() - days * 24 * 60 * 60 * 1000;

  try {
    const [series] = await pool.query(
      `
SELECT
  DATE(FROM_UNIXTIME(created_at_ms / 1000)) AS d,
  COUNT(*) AS total,
  COALESCE(SUM(CASE WHEN status_code >= 400 THEN 1 ELSE 0 END), 0) AS errors
FROM sync_logs
WHERE created_at_ms >= ?
GROUP BY d
ORDER BY d ASC
`,
      [sinceMs]
    );

    const [codes] = await pool.query(
      `
SELECT error_code, COUNT(*) AS count
FROM sync_logs
WHERE created_at_ms >= ? AND status_code >= 400 AND error_code <> ''
GROUP BY error_code
ORDER BY count DESC
LIMIT 50
`,
      [sinceMs]
    );

    return res.json({
      ok: true,
      days,
      sinceMs,
      series: (series || []).map((r) => ({
        day: String(r.d || ""),
        total: Number(r.total) || 0,
        errors: Number(r.errors) || 0
      })),
      topErrorCodes: (codes || []).map((r) => ({ code: String(r.error_code || ""), count: Number(r.count) || 0 }))
    });
  } catch (_) {
    return res.status(500).json({ error: "server_error" });
  }
});

router.post("/sync/disableAll", async (req, res) => {
  const disabled = parseBool(req.body?.disabled, false);
  try {
    await setSetting("sync_disabled_all", disabled ? "1" : "0");
    return res.json({ ok: true, syncDisabledAll: await getSyncDisabledAll() });
  } catch (_) {
    return res.status(500).json({ error: "server_error" });
  }
});

router.post("/users/:id/sync", async (req, res) => {
  const userId = Number(req.params?.id);
  if (!Number.isFinite(userId) || userId <= 0) return res.status(400).json({ error: "invalid_user" });
  const disabled = parseBool(req.body?.disabled, false);

  try {
    const [r] = await pool.query("UPDATE users SET sync_disabled = ? WHERE id = ? LIMIT 1", [disabled ? 1 : 0, userId]);
    if (Number(r?.affectedRows) !== 1) return res.status(404).json({ error: "not_found" });
    return res.json({ ok: true, userId, syncDisabled: disabled });
  } catch (_) {
    return res.status(500).json({ error: "server_error" });
  }
});

router.delete("/users/:id", async (req, res) => {
  const userId = Number(req.params?.id);
  if (!Number.isFinite(userId) || userId <= 0) return res.status(400).json({ error: "invalid_user" });

  try {
    const [[u]] = await pool.query("SELECT id FROM users WHERE id = ? LIMIT 1", [userId]);
    if (!u) return res.status(404).json({ error: "not_found" });

    await pool.query("DELETE FROM users WHERE id = ? LIMIT 1", [userId]);

    const vaultDir = path.join(storageRoot, "vaults", String(userId));
    const avatarFile = path.join(storageRoot, "avatars", String(userId));
    try {
      await fs.promises.rm(vaultDir, { recursive: true, force: true });
    } catch (_) {
      // ignore best-effort wipe
    }
    try {
      await fs.promises.rm(avatarFile, { recursive: true, force: true });
    } catch (_) {
      // ignore best-effort wipe
    }

    return res.json({ ok: true, userId });
  } catch (_) {
    return res.status(500).json({ error: "server_error" });
  }
});

router.post("/email/broadcast", async (req, res) => {
  if (!isSmtpConfigured()) return res.status(503).json({ error: "smtp_not_configured" });

  const subject = String(req.body?.subject || "").trim();
  const text = req.body?.text != null ? String(req.body.text) : "";
  const html = req.body?.html != null ? String(req.body.html) : "";
  if (!subject) return res.status(400).json({ error: "invalid_subject" });
  if (!text && !html) return res.status(400).json({ error: "invalid_body" });

  const idsRaw = Array.isArray(req.body?.userIds) ? req.body.userIds : [];
  const ids = Array.from(
    new Set(
      idsRaw
        .map((v) => Number(v))
        .filter((n) => Number.isFinite(n) && n > 0)
        .slice(0, 500)
    )
  );
  if (!ids.length) return res.status(400).json({ error: "invalid_targets" });

  try {
    const placeholders = ids.map(() => "?").join(", ");
    const [rows] = await pool.query(`SELECT id, COALESCE(email, '') AS email FROM users WHERE id IN (${placeholders})`, ids);
    const targets = (rows || [])
      .map((r) => ({ userId: Number(r.id) || 0, email: String(r.email || "").trim() }))
      .filter((t) => t.userId > 0 && t.email);

    const results = [];
    for (const t of targets) {
      // Send individually to avoid leaking recipient list.
      // eslint-disable-next-line no-await-in-loop
      const sent = await sendMail({ to: t.email, subject, text: text || undefined, html: html || undefined });
      results.push({ userId: t.userId, email: t.email, ok: Boolean(sent.ok), error: sent.ok ? "" : String(sent.error || "send_failed") });
    }

    const sentCount = results.filter((r) => r.ok).length;
    const failedCount = results.length - sentCount;
    return res.json({ ok: true, totalTargets: targets.length, sentCount, failedCount, results });
  } catch (_) {
    return res.status(500).json({ error: "server_error" });
  }
});

module.exports = router;

