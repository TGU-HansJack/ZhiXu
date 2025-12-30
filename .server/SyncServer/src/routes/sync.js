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

router.post("/push", async (req, res) => {
  const userId = Number(req.user.id);
  const deviceId = String(req.body?.deviceId || "").trim();
  const notes = Array.isArray(req.body?.notes) ? req.body.notes : [];

  if (!Number.isFinite(userId)) return res.status(401).json({ error: "invalid_user" });
  if (!deviceId) return res.status(400).json({ error: "missing_deviceId" });

  try {
    await ensureDevice(userId, deviceId);
  } catch (e) {
    return res.status(500).json({ error: "db_error" });
  }

  let accepted = 0;
  let skipped = 0;

  for (const n of notes) {
    const noteId = String(n?.noteId || "").trim();
    const updatedAt = Number(n?.updatedAt || 0);
    const deleted = Boolean(n?.deleted || false);
    const encrypted = Boolean(n?.encrypted ?? true);
    const payloadBase64 = String(n?.payloadBase64 || "");
    if (!noteId || !Number.isFinite(updatedAt) || updatedAt <= 0) {
      skipped += 1;
      continue;
    }

    const payload = Buffer.from(payloadBase64, "base64");
    if (!payload.length && !deleted) {
      skipped += 1;
      continue;
    }

    const [existingRows] = await pool.query(
      "SELECT updated_at_ms FROM notes WHERE user_id = ? AND note_id = ? LIMIT 1",
      [userId, noteId]
    );
    const existing = existingRows?.[0];
    if (existing && Number(existing.updated_at_ms) >= updatedAt) {
      skipped += 1;
      continue;
    }

    await pool.query(
      `
INSERT INTO notes (user_id, note_id, device_id, updated_at_ms, deleted, encrypted, payload)
VALUES (?, ?, ?, ?, ?, ?, ?)
ON DUPLICATE KEY UPDATE
  device_id = VALUES(device_id),
  updated_at_ms = VALUES(updated_at_ms),
  deleted = VALUES(deleted),
  encrypted = VALUES(encrypted),
  payload = VALUES(payload)
`,
      [userId, noteId, deviceId, updatedAt, deleted ? 1 : 0, encrypted ? 1 : 0, payload]
    );
    accepted += 1;
  }

  return res.json({ accepted, skipped, serverTime: Date.now() });
});

router.get("/pull", async (req, res) => {
  const userId = Number(req.user.id);
  const deviceId = String(req.query?.deviceId || "").trim();
  const since = Number(req.query?.since || 0);
  if (!Number.isFinite(userId)) return res.status(401).json({ error: "invalid_user" });
  if (!deviceId) return res.status(400).json({ error: "missing_deviceId" });
  try {
    await ensureDevice(userId, deviceId);
  } catch (e) {
    return res.status(500).json({ error: "db_error" });
  }

  const sinceMs = Number.isFinite(since) && since > 0 ? since : 0;
  const [rows] = await pool.query(
    `
SELECT note_id, device_id, updated_at_ms, deleted, encrypted, payload
FROM notes
WHERE user_id = ? AND updated_at_ms > ?
ORDER BY updated_at_ms ASC
LIMIT 2000
`,
    [userId, sinceMs]
  );

  const notes =
    (rows || []).map((r) => ({
      noteId: r.note_id,
      deviceId: r.device_id,
      updatedAt: Number(r.updated_at_ms),
      deleted: Boolean(r.deleted),
      encrypted: Boolean(r.encrypted),
      payloadBase64: Buffer.from(r.payload || Buffer.alloc(0)).toString("base64")
    }));

  return res.json({ serverTime: Date.now(), notes });
});

module.exports = router;
