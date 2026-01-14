const { pool } = require("./db");

async function initDb() {
  await pool.query(`
CREATE TABLE IF NOT EXISTS users (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL,
  email VARCHAR(255) NULL,
  password_hash VARCHAR(255) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_users_username (username),
  UNIQUE KEY uq_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
`);

  // Best-effort migration for older schemas.
  try {
    const [colRows] = await pool.query("SHOW COLUMNS FROM users LIKE 'email'");
    const hasEmail = Array.isArray(colRows) && colRows.length > 0;
    if (!hasEmail) {
      await pool.query("ALTER TABLE users ADD COLUMN email VARCHAR(255) NULL AFTER username");
    }
  } catch (_) {
    // ignore
  }

  try {
    const [idxRows] = await pool.query("SHOW INDEX FROM users WHERE Key_name = 'uq_users_email'");
    const hasEmailIdx = Array.isArray(idxRows) && idxRows.length > 0;
    if (!hasEmailIdx) {
      await pool.query("ALTER TABLE users ADD UNIQUE KEY uq_users_email (email)");
    }
  } catch (_) {
    // ignore
  }

  await pool.query(`
CREATE TABLE IF NOT EXISTS user_sessions (
  id CHAR(36) NOT NULL,
  user_id BIGINT UNSIGNED NOT NULL,
  name VARCHAR(128) NOT NULL DEFAULT '',
  client VARCHAR(255) NOT NULL DEFAULT '',
  ip VARCHAR(64) NOT NULL DEFAULT '',
  location VARCHAR(128) NOT NULL DEFAULT '',
  refresh_token_hash CHAR(64) NOT NULL DEFAULT '',
  refresh_token_expires_at_ms BIGINT NOT NULL DEFAULT 0,
  created_at_ms BIGINT NOT NULL,
  last_seen_at_ms BIGINT NOT NULL,
  revoked_at_ms BIGINT NULL,
  PRIMARY KEY (id),
  KEY idx_user_sessions_user_last_seen (user_id, last_seen_at_ms),
  CONSTRAINT fk_user_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
`);

  await pool.query(`
CREATE TABLE IF NOT EXISTS plans (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  code VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  storage_bytes BIGINT UNSIGNED NOT NULL,
  price_cny_year INT UNSIGNED NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_plans_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
`);

  // Best-effort migration for older schemas (must happen before seeding).
  try {
    const [colRows] = await pool.query("SHOW COLUMNS FROM plans LIKE 'price_cny_year'");
    const hasPrice = Array.isArray(colRows) && colRows.length > 0;
    if (!hasPrice) {
      await pool.query("ALTER TABLE plans ADD COLUMN price_cny_year INT UNSIGNED NOT NULL DEFAULT 0 AFTER storage_bytes");
    }
  } catch (_) {
    // ignore
  }

  await pool.query(`
CREATE TABLE IF NOT EXISTS user_subscriptions (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  plan_id BIGINT UNSIGNED NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'active',
  started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_user_subscriptions_user (user_id),
  KEY idx_user_subscriptions_plan (plan_id),
  CONSTRAINT fk_user_subscriptions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  CONSTRAINT fk_user_subscriptions_plan FOREIGN KEY (plan_id) REFERENCES plans(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
`);

  // Seed built-in plans (purchase/payment integrates later).
  await pool.query(
    `
INSERT INTO plans (code, name, storage_bytes, price_cny_year)
VALUES
  ('storage_512m', 'Storage 512MB', 536870912, 20),
  ('storage_1g', 'Storage 1G', 1073741824, 30),
  ('storage_2g', 'Storage 2G', 2147483648, 40)
ON DUPLICATE KEY UPDATE
  name = VALUES(name),
  storage_bytes = VALUES(storage_bytes),
  price_cny_year = VALUES(price_cny_year)
`
  );

  // Vault file sync tables (v2).
  await pool.query(`
CREATE TABLE IF NOT EXISTS vault_files (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  path VARCHAR(1024) NOT NULL,
  path_hash BINARY(32) NOT NULL,
  rev BIGINT UNSIGNED NOT NULL DEFAULT 0,
  updated_at_ms BIGINT NOT NULL,
  mtime_ms BIGINT NOT NULL,
  size_bytes BIGINT NOT NULL,
  sha256 CHAR(64) NOT NULL,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_vault_files_user_path (user_id, path_hash),
  KEY idx_vault_files_user_updated (user_id, updated_at_ms),
  CONSTRAINT fk_vault_files_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
`);

  await pool.query(`
CREATE TABLE IF NOT EXISTS vault_changes (
  change_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  path VARCHAR(1024) NOT NULL,
  path_hash BINARY(32) NOT NULL,
  rev BIGINT UNSIGNED NOT NULL,
  updated_at_ms BIGINT NOT NULL,
  mtime_ms BIGINT NOT NULL,
  size_bytes BIGINT NOT NULL,
  sha256 CHAR(64) NOT NULL,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (change_id),
  KEY idx_vault_changes_user_change (user_id, change_id),
  KEY idx_vault_changes_user_path (user_id, path_hash),
  CONSTRAINT fk_vault_changes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
`);
}

module.exports = { initDb };
