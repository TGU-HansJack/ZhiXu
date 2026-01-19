const { pool } = require("../db");
const { adminEmail } = require("../config");

function normalizedAdminEmail() {
  return String(adminEmail || "")
    .trim()
    .toLowerCase();
}

async function adminRequired(req, res, next) {
  const needle = normalizedAdminEmail();
  if (!needle) return res.status(403).json({ error: "admin_not_configured" });

  const userId = Number(req.user?.id);
  if (!Number.isFinite(userId) || userId <= 0) return res.status(401).json({ error: "invalid_user" });

  const sessionId = String(req.sessionId || "").trim();
  if (!sessionId) return res.status(403).json({ error: "admin_login_required" });

  try {
    const [[row]] = await pool.query(
      `
SELECT
  COALESCE(u.email, '') AS email,
  COALESCE(s.auth_method, 'password') AS auth_method,
  COALESCE(s.revoked_at_ms, 0) AS revoked_at_ms
FROM users u
JOIN user_sessions s ON s.id = ? AND s.user_id = u.id
WHERE u.id = ?
LIMIT 1
`,
      [sessionId, userId]
    );
    if (!row) return res.status(403).json({ error: "admin_login_required" });
    const revokedAt = row?.revoked_at_ms == null ? 0 : Number(row.revoked_at_ms) || 0;
    if (revokedAt > 0) return res.status(403).json({ error: "admin_login_required" });

    const email = String(row.email || "")
      .trim()
      .toLowerCase();
    const authMethod = String(row.auth_method || "password");
    if (email !== needle || authMethod !== "email") return res.status(403).json({ error: "forbidden" });

    req.admin = { userId, email };
    return next();
  } catch (_) {
    return res.status(500).json({ error: "server_error" });
  }
}

module.exports = { adminRequired };

