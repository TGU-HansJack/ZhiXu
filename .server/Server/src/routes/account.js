const express = require("express");
const bcrypt = require("bcryptjs");
const { pool } = require("../db");
const { authRequired } = require("../middleware/auth");
const { bcryptRounds } = require("../config");

const router = express.Router();
router.use(authRequired);

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
    "SELECT id, username, COALESCE(email, '') AS email, COALESCE(email_verified_at_ms, 0) AS email_verified_at_ms FROM users WHERE id = ? LIMIT 1",
    [userId]
  );
  if (!u) return res.status(404).json({ error: "not_found" });

  const [[sub]] = await pool.query(
    `
SELECT p.code AS plan_code, p.name AS plan_name, p.storage_bytes AS storage_bytes, p.price_cny_year AS price_cny_year
FROM user_subscriptions s
JOIN plans p ON p.id = s.plan_id
WHERE s.user_id = ?
LIMIT 1
`,
    [userId]
  );

  return res.json({
    userId: Number(u.id) || userId,
    username: String(u.username || ""),
    email: String(u.email || ""),
    emailVerifiedAtMs: Number(u.email_verified_at_ms) || 0,
    emailVerified: (Number(u.email_verified_at_ms) || 0) > 0,
    plan: sub
      ? {
          code: String(sub.plan_code || ""),
          name: String(sub.plan_name || ""),
          storageBytes: Number(sub.storage_bytes) || 0,
          priceCnyYear: Number(sub.price_cny_year) || 0
        }
      : null
  });
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
      ip: String(r.ip || ""),
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

router.post("/subscription", async (req, res) => {
  const userId = Number(req.user?.id);
  if (!Number.isFinite(userId) || userId <= 0) return res.status(401).json({ error: "invalid_user" });

  const planCode = String(req.body?.planCode || "").trim();
  if (!planCode) return res.status(400).json({ error: "invalid_plan" });

  const [[plan]] = await pool.query("SELECT id, code, name, storage_bytes, price_cny_year FROM plans WHERE code = ? LIMIT 1", [planCode]);
  if (!plan) return res.status(404).json({ error: "plan_not_found" });

  await pool.query(
    `
INSERT INTO user_subscriptions (user_id, plan_id, status)
VALUES (?, ?, 'active')
ON DUPLICATE KEY UPDATE
  plan_id = VALUES(plan_id),
  status = 'active'
`,
    [userId, Number(plan.id)]
  );

  return res.json({
    ok: true,
    plan: {
      code: String(plan.code || ""),
      name: String(plan.name || ""),
      storageBytes: Number(plan.storage_bytes) || 0,
      priceCnyYear: Number(plan.price_cny_year) || 0
    }
  });
});

module.exports = router;
