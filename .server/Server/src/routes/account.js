const crypto = require("crypto");
const fs = require("fs");
const path = require("path");
const express = require("express");
const bcrypt = require("bcryptjs");
const { pool } = require("../db");
const { authRequired } = require("../middleware/auth");
const { bcryptRounds, storageRoot, storageLimitBytes } = require("../config");
const { isSmtpConfigured, sendMail } = require("../mailer");
const { atomicWriteFile, deleteFileBestEffort } = require("../storage");
const { normalizeIp } = require("../http");

const router = express.Router();
router.use(authRequired);

const EMAIL_CODE_TTL_MS = 10 * 60 * 1000;
const AVATAR_LIMIT_BYTES = 5 * 1024 * 1024;

function sha256HexUtf8(text) {
  return crypto.createHash("sha256").update(String(text), "utf8").digest("hex");
}

function normalizeEmail(raw) {
  const email = String(raw || "").trim().toLowerCase();
  if (!email) return null;
  if (email.length > 255) return null;
  if (!email.includes("@") || email.startsWith("@") || email.endsWith("@")) return null;
  return email;
}

function generateEmailCode() {
  const n = crypto.randomInt(100000, 1000000);
  return String(n);
}

async function issueEmailCode(email, purpose) {
  const now = Date.now();
  const code = generateEmailCode();
  const codeHash = sha256HexUtf8(code);
  const expiresAtMs = now + EMAIL_CODE_TTL_MS;
  await pool.query(
    "INSERT INTO email_codes (email, purpose, code_hash, created_at_ms, expires_at_ms, used_at_ms) VALUES (?, ?, ?, ?, ?, NULL)",
    [email, purpose, codeHash, now, expiresAtMs]
  );
  return { code, expiresAtMs };
}

function formatLastSeenText(lastSeenAtMs) {
  const ms = Number(lastSeenAtMs) || 0;
  if (ms <= 0) return "";
  const delta = Date.now() - ms;
  if (!Number.isFinite(delta)) return "";
  if (delta < 60_000) return "just now";
  if (delta < 60 * 60_000) return `${Math.max(1, Math.floor(delta / 60_000))}m ago`;
  if (delta < 24 * 60 * 60_000) return `${Math.max(1, Math.floor(delta / (60 * 60_000)))}h ago`;
  return new Date(ms).toISOString().replace("T", " ").replace("Z", "");
}

router.get("/me", async (req, res) => {
  const userId = Number(req.user?.id);
  if (!Number.isFinite(userId) || userId <= 0) return res.status(401).json({ error: "invalid_user" });

  const [[u]] = await pool.query(
    "SELECT id, username, COALESCE(email, '') AS email, COALESCE(email_verified_at_ms, 0) AS email_verified_at_ms, COALESCE(avatar_mime, '') AS avatar_mime, COALESCE(avatar_updated_at_ms, 0) AS avatar_updated_at_ms FROM users WHERE id = ? LIMIT 1",
    [userId]
  );
  if (!u) return res.status(404).json({ error: "not_found" });

  const [[storageRow]] = await pool.query(
    `
SELECT
  COALESCE(SUM(CASE WHEN deleted = 0 THEN size_bytes ELSE 0 END), 0) AS used_bytes
FROM vault_files
WHERE user_id = ?
`,
    [userId]
  );
  const usedBytes = Number(storageRow?.used_bytes) || 0;
  const limitBytes = Number.isFinite(storageLimitBytes) ? Number(storageLimitBytes) : 5 * 1024 * 1024 * 1024;

  return res.json({
    userId: Number(u.id) || userId,
    username: String(u.username || ""),
    email: String(u.email || ""),
    emailVerifiedAtMs: Number(u.email_verified_at_ms) || 0,
    emailVerified: (Number(u.email_verified_at_ms) || 0) > 0,
    avatar: {
      mime: String(u.avatar_mime || ""),
      updatedAtMs: Number(u.avatar_updated_at_ms) || 0,
      hasAvatar: String(u.avatar_mime || "") !== "" && (Number(u.avatar_updated_at_ms) || 0) > 0
    },
    storage: {
      usedBytes,
      limitBytes
    }
  });
});

router.post("/email", async (req, res) => {
  const userId = Number(req.user?.id);
  if (!Number.isFinite(userId) || userId <= 0) return res.status(401).json({ error: "invalid_user" });

  const emailRaw = String(req.body?.email ?? "").trim();
  if (!emailRaw) {
    await pool.query("UPDATE users SET email = NULL, email_verified_at_ms = 0 WHERE id = ? LIMIT 1", [userId]);
    return res.json({ ok: true, email: "", emailVerifiedAtMs: 0, emailVerified: false, verificationSent: false });
  }

  const email = normalizeEmail(emailRaw);
  if (!email) return res.status(400).json({ error: "invalid_email" });

  const [[existing]] = await pool.query("SELECT id FROM users WHERE email = ? AND id <> ? LIMIT 1", [email, userId]);
  if (existing) return res.status(409).json({ error: "email_taken" });

  await pool.query("UPDATE users SET email = ?, email_verified_at_ms = 0 WHERE id = ? LIMIT 1", [email, userId]);

  let verificationSent = false;
  if (isSmtpConfigured()) {
    try {
      const issued = await issueEmailCode(email, "verify");
      const mins = Math.max(1, Math.floor(EMAIL_CODE_TTL_MS / 60_000));
      const subject = "Zhixu: Verify your email";
      const text = `Your Zhixu verification code is: ${issued.code}\n\nThis code expires in ${mins} minutes.`;
      const sent = await sendMail({ to: email, subject, text });
      verificationSent = Boolean(sent?.ok);
    } catch (_) {
      // ignore best-effort email
    }
  }

  return res.json({ ok: true, email, emailVerifiedAtMs: 0, emailVerified: false, verificationSent });
});

router.get("/avatar", async (req, res) => {
  const userId = Number(req.user?.id);
  if (!Number.isFinite(userId) || userId <= 0) return res.status(401).json({ error: "invalid_user" });

  const [[u]] = await pool.query("SELECT COALESCE(avatar_mime, '') AS avatar_mime, COALESCE(avatar_updated_at_ms, 0) AS avatar_updated_at_ms FROM users WHERE id = ? LIMIT 1", [
    userId
  ]);
  const mime = String(u?.avatar_mime || "");
  const updatedAtMs = Number(u?.avatar_updated_at_ms) || 0;
  if (!mime || updatedAtMs <= 0) return res.status(404).json({ error: "not_found" });

  const absPath = path.join(storageRoot, "avatars", String(userId));
  try {
    await fs.promises.access(absPath, fs.constants.R_OK);
  } catch (_) {
    return res.status(404).json({ error: "not_found" });
  }

  res.setHeader("Content-Type", mime);
  res.setHeader("Cache-Control", "no-store");
  res.setHeader("X-Zhixu-Avatar-Updated-At-Ms", String(updatedAtMs));

  const stream = fs.createReadStream(absPath);
  stream.on("error", () => {
    if (!res.headersSent) res.status(500);
    res.end();
  });
  return stream.pipe(res.status(200));
});

router.put(
  "/avatar",
  express.raw({ type: "*/*", limit: AVATAR_LIMIT_BYTES }),
  async (req, res) => {
    const userId = Number(req.user?.id);
    if (!Number.isFinite(userId) || userId <= 0) return res.status(401).json({ error: "invalid_user" });

    const contentType = String(req.header("Content-Type") || "")
      .trim()
      .toLowerCase()
      .split(";")[0];
    const hintedMime = String(req.header("X-Zhixu-Avatar-Mime") || "")
      .trim()
      .toLowerCase()
      .split(";")[0];
    const mime =
      contentType === "application/octet-stream" || !contentType
        ? hintedMime || contentType
        : contentType;
    const allowed = new Set(["image/png", "image/jpeg", "image/webp", "image/gif"]);
    if (!allowed.has(mime)) return res.status(400).json({ error: "invalid_avatar_type" });

    const bytes = Buffer.isBuffer(req.body) ? req.body : Buffer.alloc(0);
    if (!bytes.length) return res.status(400).json({ error: "empty_body" });
    if (bytes.length > AVATAR_LIMIT_BYTES) return res.status(413).json({ error: "payload_too_large" });

    const absPath = path.join(storageRoot, "avatars", String(userId));
    try {
      await atomicWriteFile(absPath, bytes);
    } catch (_) {
      return res.status(500).json({ error: "write_failed" });
    }

    const now = Date.now();
    await pool.query("UPDATE users SET avatar_mime = ?, avatar_updated_at_ms = ? WHERE id = ? LIMIT 1", [mime, now, userId]);
    return res.json({ ok: true, mime, updatedAtMs: now });
  }
);

router.delete("/avatar", async (req, res) => {
  const userId = Number(req.user?.id);
  if (!Number.isFinite(userId) || userId <= 0) return res.status(401).json({ error: "invalid_user" });

  const absPath = path.join(storageRoot, "avatars", String(userId));
  await deleteFileBestEffort(absPath);
  await pool.query("UPDATE users SET avatar_mime = '', avatar_updated_at_ms = 0 WHERE id = ? LIMIT 1", [userId]);
  return res.json({ ok: true });
});

router.post("/password", async (req, res) => {
  const userId = Number(req.user?.id);
  if (!Number.isFinite(userId) || userId <= 0) return res.status(401).json({ error: "invalid_user" });

  const currentPassword = String(req.body?.currentPassword || "");
  const newPassword = String(req.body?.newPassword || "");
  if (!currentPassword || !newPassword) return res.status(400).json({ error: "invalid_password" });
  if (newPassword.length < 6) return res.status(400).json({ error: "invalid_password" });

  const [[u]] = await pool.query("SELECT password_hash FROM users WHERE id = ? LIMIT 1", [userId]);
  if (!u) return res.status(404).json({ error: "not_found" });

  const ok = await bcrypt.compare(currentPassword, String(u.password_hash || ""));
  if (!ok) return res.status(401).json({ error: "invalid_credentials" });

  const passwordHash = await bcrypt.hash(newPassword, bcryptRounds);
  await pool.query("UPDATE users SET password_hash = ? WHERE id = ? LIMIT 1", [passwordHash, userId]);

  const now = Date.now();
  const currentSessionId = String(req.sessionId || "");
  if (currentSessionId) {
    await pool.query("UPDATE user_sessions SET revoked_at_ms = ? WHERE user_id = ? AND id <> ? AND revoked_at_ms IS NULL", [
      now,
      userId,
      currentSessionId
    ]);
  } else {
    await pool.query("UPDATE user_sessions SET revoked_at_ms = ? WHERE user_id = ? AND revoked_at_ms IS NULL", [now, userId]);
  }

  return res.json({ ok: true });
});

router.get("/sync/logs", async (req, res) => {
  const userId = Number(req.user?.id);
  if (!Number.isFinite(userId) || userId <= 0) return res.status(401).json({ error: "invalid_user" });

  const limitRaw = Number(req.query?.limit || 100);
  const limit = Number.isFinite(limitRaw) ? Math.min(Math.max(limitRaw, 1), 500) : 100;

  const [rows] = await pool.query(
    `
SELECT l.id, l.created_at_ms, l.action, l.path, l.ip, l.client, l.session_id, l.size_bytes, COALESCE(s.name, '') AS session_name
FROM sync_logs l
LEFT JOIN user_sessions s ON s.id = l.session_id AND s.user_id = l.user_id
WHERE l.user_id = ?
ORDER BY l.created_at_ms DESC
LIMIT ?
`,
    [userId, limit]
  );

  const logs = (rows || []).map((r) => ({
    id: Number(r.id) || 0,
    createdAtMs: Number(r.created_at_ms) || 0,
    action: String(r.action || ""),
    path: String(r.path || ""),
    ip: normalizeIp(String(r.ip || "")),
    client: String(r.client || ""),
    sessionId: String(r.session_id || ""),
    deviceName: String(r.session_name || ""),
    sizeBytes: Number(r.size_bytes) || 0
  }));

  return res.json({ logs });
});

router.get("/sessions", async (req, res) => {
  const userId = Number(req.user?.id);
  if (!Number.isFinite(userId) || userId <= 0) return res.status(401).json({ error: "invalid_user" });

  const currentSessionId = String(req.sessionId || "");
  const [rows] = await pool.query(
    `
SELECT id, name, client, ip, location, created_at_ms, last_seen_at_ms
FROM user_sessions
WHERE user_id = ? AND revoked_at_ms IS NULL
ORDER BY last_seen_at_ms DESC
LIMIT 100
`,
    [userId]
  );

  const sessions = (rows || []).map((r) => {
    const lastSeenAtMs = Number(r.last_seen_at_ms) || 0;
    const id = String(r.id || "").trim();
    const name = String(r.name || "");
    const client = String(r.client || "");
    const current = currentSessionId && id === currentSessionId;
    return {
      sessionId: id,
      id,
      name,
      deviceName: name,
      client,
      platform: client,
      lastSeenAt: lastSeenAtMs > 0 ? new Date(lastSeenAtMs).toISOString() : "",
      lastSeenText: formatLastSeenText(lastSeenAtMs),
      ip: normalizeIp(String(r.ip || "")),
      location: String(r.location || ""),
      current,
      isCurrent: current,
      createdAtMs: Number(r.created_at_ms) || 0
    };
  });

  return res.json({ sessions });
});

router.post("/sessions/revoke", async (req, res) => {
  const userId = Number(req.user?.id);
  if (!Number.isFinite(userId) || userId <= 0) return res.status(401).json({ error: "invalid_user" });

  const sessionId = String(req.body?.sessionId || "").trim();
  if (!sessionId) return res.status(400).json({ error: "invalid_session" });

  await pool.query("UPDATE user_sessions SET revoked_at_ms = ? WHERE id = ? AND user_id = ? AND revoked_at_ms IS NULL", [
    Date.now(),
    sessionId,
    userId
  ]);
  return res.json({ ok: true });
});

module.exports = router;
