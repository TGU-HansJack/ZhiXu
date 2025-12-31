const express = require("express");
const { pool } = require("../db");

const router = express.Router();

router.get("/", async (_req, res) => {
  const [rows] = await pool.query("SELECT code, name, storage_bytes FROM plans ORDER BY storage_bytes ASC");
  const plans = (rows || []).map((r) => ({
    code: String(r.code || ""),
    name: String(r.name || ""),
    storageBytes: Number(r.storage_bytes) || 0
  }));
  return res.json({ plans });
});

module.exports = router;

