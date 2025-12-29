const jwt = require("jsonwebtoken");
const { jwtSecret } = require("../config");

function authRequired(req, res, next) {
  const header = req.header("Authorization") || "";
  const m = header.match(/^Bearer\s+(.+)$/i);
  if (!m) return res.status(401).json({ error: "missing_token" });

  try {
    const payload = jwt.verify(m[1], jwtSecret);
    req.user = { id: payload.sub, username: payload.username };
    return next();
  } catch (e) {
    return res.status(401).json({ error: "invalid_token" });
  }
}

module.exports = { authRequired };

