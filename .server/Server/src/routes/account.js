const express = require("express");
const { pool } = require("../db");
const { authRequired } = require("../middleware/auth");

const router = express.Router();
router.use(authRequired);

router.get("/me", async (req, res) => {
  const userId = Number(req.user?.id);
  if (!Number.isFinite(userId) || userId <= 0) return res.status(401).json({ error: "invalid_user" });

  const [[u]] = await pool.query("SELECT id, username FROM users WHERE id = ? LIMIT 1", [userId]);
  if (!u) return res.status(404).json({ error: "not_found" });

  const [[sub]] = await pool.query(
    `
SELECT p.code AS plan_code, p.name AS plan_name, p.storage_bytes AS storage_bytes
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
    plan: sub
      ? {
          code: String(sub.plan_code || ""),
          name: String(sub.plan_name || ""),
          storageBytes: Number(sub.storage_bytes) || 0
        }
      : null
  });
});

router.post("/subscription", async (req, res) => {
  const userId = Number(req.user?.id);
  if (!Number.isFinite(userId) || userId <= 0) return res.status(401).json({ error: "invalid_user" });

  const planCode = String(req.body?.planCode || "").trim();
  if (!planCode) return res.status(400).json({ error: "invalid_plan" });

  const [[plan]] = await pool.query("SELECT id, code, name, storage_bytes FROM plans WHERE code = ? LIMIT 1", [planCode]);
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
      storageBytes: Number(plan.storage_bytes) || 0
    }
  });
});

module.exports = router;

