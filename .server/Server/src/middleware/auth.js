const jwt = require("jsonwebtoken");
const { pool } = require("../db");
const { jwtSecret } = require("../config");

async function authRequired(req, res, next) {
  const header = String(req.header("Authorization") || "");
  const token = header.startsWith("Bearer ") ? header.slice("Bearer ".length).trim() : "";
  if (!token) return res.status(401).json({ error: "missing_token" });

  let decoded;
  try {
    decoded = jwt.verify(token, jwtSecret);
    const userId = Number(decoded?.sub);
    if (!Number.isFinite(userId) || userId <= 0) return res.status(401).json({ error: "invalid_token" });

    const sessionIdRaw = decoded?.sid ?? decoded?.sessionId ?? "";
    const sessionId = typeof sessionIdRaw === "string" ? sessionIdRaw : String(sessionIdRaw || "");
    if (sessionId) {
      try {
        const [[row]] = await pool.query(
          "SELECT revoked_at_ms, last_seen_at_ms FROM user_sessions WHERE id = ? AND user_id = ? LIMIT 1",
          [sessionId, userId]
        );
        const revokedAt = row?.revoked_at_ms == null ? null : Number(row.revoked_at_ms);
        if (!row || (Number.isFinite(revokedAt) && revokedAt > 0)) return res.status(401).json({ error: "invalid_token" });

        const lastSeenAtMs = Number(row?.last_seen_at_ms) || 0;
        const now = Date.now();
        if (now - lastSeenAtMs >= 60_000) {
          await pool.query(
            "UPDATE user_sessions SET last_seen_at_ms = ? WHERE id = ? AND user_id = ? AND last_seen_at_ms < ?",
            [now, sessionId, userId, now - 60_000]
          );
        }

        req.sessionId = sessionId;
      } catch (_) {
        return res.status(500).json({ error: "server_error" });
      }
    } else {
      req.sessionId = "";
    }

    req.user = { id: userId, username: String(decoded?.username || "") };
    return next();
  } catch (_) {
    return res.status(401).json({ error: "invalid_token" });
  }
}

module.exports = { authRequired };
