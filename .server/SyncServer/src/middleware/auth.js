const jwt = require("jsonwebtoken");
const { jwtSecret } = require("../config");
const { pool } = require("../db");

function authRequired(req, res, next) {
  const header = req.header("Authorization") || "";
  const m = header.match(/^Bearer\s+(.+)$/i);
  if (!m) return res.status(401).json({ error: "missing_token" });

  try {
    const payload = jwt.verify(m[1], jwtSecret);
    const userId = Number(payload.sub);
    if (!Number.isFinite(userId)) return res.status(401).json({ error: "invalid_user" });

    pool
      .query("SELECT id, username FROM users WHERE id = ? LIMIT 1", [userId])
      .then(([rows]) => {
        const row = rows?.[0];
        if (!row) return res.status(401).json({ error: "user_not_found" });
        req.user = { id: userId, username: row.username || payload.username || "" };
        return next();
      })
      .catch(() => res.status(500).json({ error: "db_error" }));
  } catch (e) {
    return res.status(401).json({ error: "invalid_token" });
  }
}

module.exports = { authRequired };
