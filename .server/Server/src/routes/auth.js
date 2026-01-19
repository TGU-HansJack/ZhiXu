const express = require("express");
const bcrypt = require("bcryptjs");
const crypto = require("crypto");
const jwt = require("jsonwebtoken");
const { pool } = require("../db");
const { isSmtpConfigured, sendMail } = require("../mailer");
const { authRequired } = require("../middleware/auth");
const { bcryptRounds, jwtSecret, jwtAccessTtl, refreshTokenTtlDays } = require("../config");
const { getRequestIp } = require("../http");

const router = express.Router();

const EMAIL_CODE_TTL_MS = 10 * 60 * 1000;

function sha256HexUtf8(text) {
  return crypto.createHash("sha256").update(String(text), "utf8").digest("hex");
}

function generateId() {
  if (typeof crypto.randomUUID === "function") return crypto.randomUUID();
  return crypto.randomBytes(16).toString("hex");
}

function generateRefreshToken() {
  // base64url is supported by Node 16+.
  try {
    return crypto.randomBytes(32).toString("base64url");
  } catch (_) {
    return crypto.randomBytes(32).toString("hex");
  }
}

function timingSafeEqualHex(aHex, bHex) {
  try {
    const a = Buffer.from(String(aHex || ""), "hex");
    const b = Buffer.from(String(bHex || ""), "hex");
    if (!a.length || a.length !== b.length) return false;
    return crypto.timingSafeEqual(a, b);
  } catch (_) {
    return false;
  }
}

function normalizeEmail(raw) {
  const email = String(raw || "").trim().toLowerCase();
  if (!email) return null;
  if (email.length > 255) return null;
  // Basic sanity check; full RFC validation is intentionally skipped.
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

async function consumeEmailCode(email, purpose, code) {
  const now = Date.now();
  const codeHash = sha256HexUtf8(code);

  const [[row]] = await pool.query(
    "SELECT id, code_hash, expires_at_ms FROM email_codes WHERE email = ? AND purpose = ? AND used_at_ms IS NULL ORDER BY created_at_ms DESC LIMIT 1",
    [email, purpose]
  );
  if (!row) return { ok: false, error: "code_not_found" };

  const expiresAtMs = Number(row.expires_at_ms) || 0;
  if (expiresAtMs > 0 && now > expiresAtMs) return { ok: false, error: "code_expired" };

  const storedHash = String(row.code_hash || "");
  if (!storedHash || !timingSafeEqualHex(storedHash, codeHash)) return { ok: false, error: "code_mismatch" };

  await pool.query("UPDATE email_codes SET used_at_ms = ? WHERE id = ? AND used_at_ms IS NULL", [now, Number(row.id)]);
  return { ok: true };
}

router.post("/email/code", async (req, res) => {
  const emailRaw = String(req.body?.email || "").trim();
  const email = normalizeEmail(emailRaw);
  if (!email) return res.status(400).json({ error: "invalid_email" });

  const rawPurpose = String(req.body?.purpose || "register").trim().toLowerCase();
  const purpose = rawPurpose === "verify" ? "verify" : "register";

  if (!isSmtpConfigured()) return res.status(503).json({ error: "smtp_not_configured" });

  try {
    const issued = await issueEmailCode(email, purpose);
    const mins = Math.max(1, Math.floor(EMAIL_CODE_TTL_MS / 60_000));
    const subject = purpose === "verify" ? "Zhixu: Verify your email" : "Zhixu: Email verification code";
    const text = `Your Zhixu verification code is: ${issued.code}\n\nThis code expires in ${mins} minutes.`;

    const sent = await sendMail({ to: email, subject, text });
    if (!sent.ok) return res.status(500).json({ error: sent.error || "send_failed" });
    return res.json({ ok: true });
  } catch (_) {
    return res.status(500).json({ error: "server_error" });
  }
});

router.post("/email/verify", async (req, res) => {
  const emailRaw = String(req.body?.email || "").trim();
  const email = normalizeEmail(emailRaw);
  const code = String(req.body?.code || "").trim();
  if (!email) return res.status(400).json({ error: "invalid_email" });
  if (!code) return res.status(400).json({ error: "invalid_email_code" });

  const [[u]] = await pool.query("SELECT id, COALESCE(email_verified_at_ms, 0) AS email_verified_at_ms FROM users WHERE email = ? LIMIT 1", [
    email
  ]);
  if (!u) return res.status(404).json({ error: "not_found" });
  if ((Number(u.email_verified_at_ms) || 0) > 0) return res.json({ ok: true, alreadyVerified: true });

  const consumed = await consumeEmailCode(email, "verify", code);
  if (!consumed.ok) return res.status(400).json({ error: consumed.error || "invalid_email_code" });

  await pool.query("UPDATE users SET email_verified_at_ms = ? WHERE id = ? AND email = ? AND email_verified_at_ms = 0 LIMIT 1", [
    Date.now(),
    Number(u.id),
    email
  ]);
  return res.json({ ok: true });
});

router.post("/register", async (req, res) => {
  const username = String(req.body?.username || "").trim();
  const password = String(req.body?.password || "");
  const emailRaw = String(req.body?.email || "").trim();
  const email = emailRaw ? normalizeEmail(emailRaw) : null;
  const emailCode = String(req.body?.emailCode || "").trim();
  if (!username || username.length < 3) return res.status(400).json({ error: "invalid_username" });
  if (!password || password.length < 6) return res.status(400).json({ error: "invalid_password" });
  if (emailRaw && !email) return res.status(400).json({ error: "invalid_email" });

  const passwordHash = await bcrypt.hash(password, bcryptRounds);
  const now = Date.now();
  let emailVerifiedAtMs = 0;

  if (email && emailCode) {
    const consumed = await consumeEmailCode(email, "register", emailCode);
    if (!consumed.ok) return res.status(400).json({ error: consumed.error || "invalid_email_code" });
    emailVerifiedAtMs = now;
  }

  try {
    const [result] = await pool.query("INSERT INTO users (username, email, password_hash) VALUES (?, ?, ?)", [
      username,
      email,
      passwordHash
    ]);

    if (email && emailVerifiedAtMs > 0) {
      await pool.query("UPDATE users SET email_verified_at_ms = ? WHERE id = ? LIMIT 1", [emailVerifiedAtMs, Number(result.insertId)]);
    } else if (email && isSmtpConfigured()) {
      try {
        const issued = await issueEmailCode(email, "verify");
        const mins = Math.max(1, Math.floor(EMAIL_CODE_TTL_MS / 60_000));
        const subject = "Zhixu: Verify your email";
        const text = `Your Zhixu verification code is: ${issued.code}\n\nThis code expires in ${mins} minutes.`;
        await sendMail({ to: email, subject, text });
      } catch (_) {
        // ignore best-effort verification email
      }
    }
    return res.status(201).json({ userId: Number(result.insertId) || 0 });
  } catch (e) {
    if (String(e?.code) === "ER_DUP_ENTRY") {
      const msg = String(e?.message || "");
      if (msg.includes("uq_users_email")) return res.status(409).json({ error: "email_taken" });
      return res.status(409).json({ error: "username_taken" });
    }
    return res.status(500).json({ error: "server_error" });
  }
});

router.post("/login", async (req, res) => {
  const username = String(req.body?.username || "").trim();
  const password = String(req.body?.password || "");
  if (!username || !password) return res.status(400).json({ error: "invalid_credentials" });

  const emailCandidate = username.toLowerCase();
  const [rows] = await pool.query("SELECT id, username, password_hash FROM users WHERE username = ? OR email = ? LIMIT 1", [
    username,
    emailCandidate
  ]);
  const user = rows?.[0];
  if (!user) return res.status(401).json({ error: "invalid_credentials" });

  const ok = await bcrypt.compare(password, user.password_hash);
  if (!ok) return res.status(401).json({ error: "invalid_credentials" });

  const now = Date.now();
  const sessionId = generateId();
  const refreshToken = generateRefreshToken();
  const refreshTokenHash = sha256HexUtf8(refreshToken);
  const refreshTokenExpiresAtMs = now + Math.max(1, Number(refreshTokenTtlDays) || 30) * 24 * 60 * 60 * 1000;

  const sessionName = String(req.body?.deviceName || req.body?.sessionName || req.header("X-Zhixu-Device-Name") || "").trim();
  const safeSessionName = sessionName.length > 128 ? sessionName.slice(0, 128) : sessionName;
  const client = String(req.header("User-Agent") || "").trim().slice(0, 255);
  const ip = getRequestIp(req).slice(0, 64);

  try {
    await pool.query(
      `
INSERT INTO user_sessions (
  id, user_id, name, client, ip, location,
  refresh_token_hash, refresh_token_expires_at_ms,
  created_at_ms, last_seen_at_ms, revoked_at_ms
)
VALUES (?, ?, ?, ?, ?, '', ?, ?, ?, ?, NULL)
`,
      [sessionId, Number(user.id), safeSessionName, client, ip, refreshTokenHash, refreshTokenExpiresAtMs, now, now]
    );
  } catch (_) {
    return res.status(500).json({ error: "server_error" });
  }

  const token = jwt.sign({ username: user.username, sid: sessionId }, jwtSecret, { subject: String(user.id), expiresIn: jwtAccessTtl });
  return res.json({ token, sessionId, refreshToken });
});

router.post("/refresh", async (req, res) => {
  const sessionId = String(req.body?.sessionId || req.body?.sid || "").trim();
  const refreshToken = String(req.body?.refreshToken || req.body?.refresh_token || "").trim();
  if (!sessionId || !refreshToken) return res.status(400).json({ error: "invalid_refresh" });

  const [[row]] = await pool.query(
    "SELECT user_id, refresh_token_hash, refresh_token_expires_at_ms, revoked_at_ms FROM user_sessions WHERE id = ? LIMIT 1",
    [sessionId]
  );
  if (!row) return res.status(401).json({ error: "invalid_refresh" });
  const revokedAt = row?.revoked_at_ms == null ? null : Number(row.revoked_at_ms);
  if (Number.isFinite(revokedAt) && revokedAt > 0) return res.status(401).json({ error: "invalid_refresh" });

  const expiresAtMs = Number(row?.refresh_token_expires_at_ms) || 0;
  if (expiresAtMs > 0 && Date.now() > expiresAtMs) return res.status(401).json({ error: "refresh_expired" });

  const hash = sha256HexUtf8(refreshToken);
  const storedHash = String(row?.refresh_token_hash || "");
  if (!storedHash || !timingSafeEqualHex(storedHash, hash)) return res.status(401).json({ error: "invalid_refresh" });

  const now = Date.now();
  const newRefreshToken = generateRefreshToken();
  const newRefreshTokenHash = sha256HexUtf8(newRefreshToken);
  const newExpiresAtMs = now + Math.max(1, Number(refreshTokenTtlDays) || 30) * 24 * 60 * 60 * 1000;

  const [uResult] = await pool.query(
    "UPDATE user_sessions SET refresh_token_hash = ?, refresh_token_expires_at_ms = ?, last_seen_at_ms = ? WHERE id = ? AND user_id = ? AND revoked_at_ms IS NULL",
    [newRefreshTokenHash, newExpiresAtMs, now, sessionId, Number(row.user_id)]
  );
  if (Number(uResult?.affectedRows) !== 1) return res.status(401).json({ error: "invalid_refresh" });

  const [[u]] = await pool.query("SELECT id, username FROM users WHERE id = ? LIMIT 1", [Number(row.user_id)]);
  if (!u) return res.status(401).json({ error: "invalid_refresh" });

  const token = jwt.sign({ username: u.username, sid: sessionId }, jwtSecret, { subject: String(u.id), expiresIn: jwtAccessTtl });
  return res.json({ token, sessionId, refreshToken: newRefreshToken });
});

router.post("/logout", authRequired, async (req, res) => {
  const userId = Number(req.user?.id);
  if (!Number.isFinite(userId) || userId <= 0) return res.status(401).json({ error: "invalid_user" });

  const sessionId = String(req.sessionId || "");
  if (sessionId) {
    await pool.query("UPDATE user_sessions SET revoked_at_ms = ? WHERE id = ? AND user_id = ? AND revoked_at_ms IS NULL", [
      Date.now(),
      sessionId,
      userId
    ]);
  }

  return res.json({ ok: true });
});

module.exports = router;
