const { pool } = require("./db");

// Minimal DB-backed server settings with a tiny in-memory cache.
// Values are stored as strings; callers can parse as needed.

const CACHE_TTL_MS = 2000;
const cache = new Map();

async function getSetting(key, fallback = "") {
  const k = String(key || "").trim();
  if (!k) return String(fallback ?? "");

  const cached = cache.get(k);
  if (cached && Date.now() - cached.at < CACHE_TTL_MS) return cached.v;

  const [[row]] = await pool.query("SELECT v FROM server_settings WHERE k = ? LIMIT 1", [k]);
  const v = row ? String(row.v ?? "") : String(fallback ?? "");
  cache.set(k, { v, at: Date.now() });
  return v;
}

async function setSetting(key, value) {
  const k = String(key || "").trim();
  if (!k) throw new Error("invalid_setting_key");
  const v = String(value ?? "");
  const now = Date.now();

  await pool.query(
    `
INSERT INTO server_settings (k, v, updated_at_ms)
VALUES (?, ?, ?)
ON DUPLICATE KEY UPDATE v = VALUES(v), updated_at_ms = VALUES(updated_at_ms)
`,
    [k, v, now]
  );

  cache.set(k, { v, at: now });
  return { key: k, value: v, updatedAtMs: now };
}

module.exports = { getSetting, setSetting };

