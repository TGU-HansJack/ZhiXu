const express = require("express");
const { pool } = require("../db");
const { authRequired } = require("../middleware/auth");

const router = express.Router();
router.use(authRequired);

async function ensureDevice(userId, deviceId) {
  if (!deviceId) return;
  await pool.query(
    `
INSERT INTO devices (user_id, device_id)
SELECT ?, ? FROM users WHERE id = ?
ON DUPLICATE KEY UPDATE device_id = VALUES(device_id)
`,
    [userId, deviceId, userId]
  );
}

router.get("/me", async (req, res) => {
  const userId = Number(req.user.id);
  if (!Number.isFinite(userId)) return res.status(401).json({ error: "invalid_user" });
  return res.json({ userId, username: req.user.username || "" });
});

router.get("/devices", async (req, res) => {
  const userId = Number(req.user.id);
  if (!Number.isFinite(userId)) return res.status(401).json({ error: "invalid_user" });

  const [rows] = await pool.query(
    "SELECT device_id FROM devices WHERE user_id = ? ORDER BY created_at DESC",
    [userId]
  );
  const devices = (rows || []).map((r) => r.device_id).filter(Boolean);
  return res.json({ devices });
});

router.post("/devices/bind", async (req, res) => {
  const userId = Number(req.user.id);
  const deviceId = String(req.body?.deviceId || "").trim();
  if (!Number.isFinite(userId)) return res.status(401).json({ error: "invalid_user" });
  if (!deviceId) return res.status(400).json({ error: "missing_deviceId" });

  try {
    await ensureDevice(userId, deviceId);
  } catch (e) {
    return res.status(500).json({ error: "db_error" });
  }
  return res.json({ ok: true });
});

router.post("/devices/unbind", async (req, res) => {
  const userId = Number(req.user.id);
  const deviceId = String(req.body?.deviceId || "").trim();
  if (!Number.isFinite(userId)) return res.status(401).json({ error: "invalid_user" });
  if (!deviceId) return res.status(400).json({ error: "missing_deviceId" });

  await pool.query("DELETE FROM devices WHERE user_id = ? AND device_id = ?", [userId, deviceId]);
  return res.json({ ok: true });
});

module.exports = router;
