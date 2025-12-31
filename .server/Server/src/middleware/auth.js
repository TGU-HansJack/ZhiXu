const jwt = require("jsonwebtoken");
const { jwtSecret } = require("../config");

function authRequired(req, res, next) {
  const header = String(req.header("Authorization") || "");
  const token = header.startsWith("Bearer ") ? header.slice("Bearer ".length).trim() : "";
  if (!token) return res.status(401).json({ error: "missing_token" });

  try {
    const decoded = jwt.verify(token, jwtSecret);
    const userId = Number(decoded?.sub);
    if (!Number.isFinite(userId) || userId <= 0) return res.status(401).json({ error: "invalid_token" });
    req.user = { id: userId, username: String(decoded?.username || "") };
    return next();
  } catch (_) {
    return res.status(401).json({ error: "invalid_token" });
  }
}

module.exports = { authRequired };

