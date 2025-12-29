const express = require("express");
const bcrypt = require("bcryptjs");
const jwt = require("jsonwebtoken");
const { pool } = require("../db");
const { bcryptRounds, jwtSecret } = require("../config");

const router = express.Router();

router.post("/register", async (req, res) => {
  const username = String(req.body?.username || "").trim();
  const password = String(req.body?.password || "");
  if (!username || username.length < 3) return res.status(400).json({ error: "invalid_username" });
  if (!password || password.length < 6) return res.status(400).json({ error: "invalid_password" });

  const passwordHash = await bcrypt.hash(password, bcryptRounds);

  try {
    const [result] = await pool.query(
      "INSERT INTO users (username, password_hash) VALUES (?, ?)",
      [username, passwordHash]
    );
    return res.status(201).json({ userId: result.insertId });
  } catch (e) {
    if (String(e?.code) === "ER_DUP_ENTRY") return res.status(409).json({ error: "username_taken" });
    return res.status(500).json({ error: "server_error" });
  }
});

router.post("/login", async (req, res) => {
  const username = String(req.body?.username || "").trim();
  const password = String(req.body?.password || "");
  if (!username || !password) return res.status(400).json({ error: "invalid_credentials" });

  const [rows] = await pool.query("SELECT id, username, password_hash FROM users WHERE username = ? LIMIT 1", [username]);
  const user = rows?.[0];
  if (!user) return res.status(401).json({ error: "invalid_credentials" });

  const ok = await bcrypt.compare(password, user.password_hash);
  if (!ok) return res.status(401).json({ error: "invalid_credentials" });

  const token = jwt.sign({ username: user.username }, jwtSecret, { subject: String(user.id), expiresIn: "30d" });
  return res.json({ token });
});

module.exports = router;

