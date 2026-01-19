const { pool } = require("./db");

async function initDb() {
  await pool.query(`
CREATE TABLE IF NOT EXISTS users (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL,
  email VARCHAR(255) NULL,
  email_verified_at_ms BIGINT NOT NULL DEFAULT 0,
  avatar_mime VARCHAR(64) NOT NULL DEFAULT '',
  avatar_updated_at_ms BIGINT NOT NULL DEFAULT 0,
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
    const [colRows] = await pool.query("SHOW COLUMNS FROM users LIKE 'email_verified_at_ms'");
    const hasCol = Array.isArray(colRows) && colRows.length > 0;
    if (!hasCol) {
      await pool.query("ALTER TABLE users ADD COLUMN email_verified_at_ms BIGINT NOT NULL DEFAULT 0 AFTER email");
    }
  } catch (_) {
    // ignore
  }

  try {
    const [colRows] = await pool.query("SHOW COLUMNS FROM users LIKE 'avatar_mime'");
    const hasCol = Array.isArray(colRows) && colRows.length > 0;
    if (!hasCol) {
      await pool.query("ALTER TABLE users ADD COLUMN avatar_mime VARCHAR(64) NOT NULL DEFAULT '' AFTER email_verified_at_ms");
    }
  } catch (_) {
    // ignore
  }

  try {
    const [colRows] = await pool.query("SHOW COLUMNS FROM users LIKE 'avatar_updated_at_ms'");
    const hasCol = Array.isArray(colRows) && colRows.length > 0;
    if (!hasCol) {
      await pool.query("ALTER TABLE users ADD COLUMN avatar_updated_at_ms BIGINT NOT NULL DEFAULT 0 AFTER avatar_mime");
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
CREATE TABLE IF NOT EXISTS email_codes (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  email VARCHAR(255) NOT NULL,
  purpose VARCHAR(32) NOT NULL,
  code_hash CHAR(64) NOT NULL,
  created_at_ms BIGINT NOT NULL,
  expires_at_ms BIGINT NOT NULL,
  used_at_ms BIGINT NULL,
  PRIMARY KEY (id),
  KEY idx_email_codes_email_purpose (email, purpose),
  KEY idx_email_codes_expires (expires_at_ms)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
`);

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

  await pool.query(`
CREATE TABLE IF NOT EXISTS sync_logs (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  session_id CHAR(36) NOT NULL DEFAULT '',
  action VARCHAR(32) NOT NULL,
  path VARCHAR(1024) NOT NULL DEFAULT '',
  ip VARCHAR(64) NOT NULL DEFAULT '',
  client VARCHAR(255) NOT NULL DEFAULT '',
  size_bytes BIGINT UNSIGNED NOT NULL DEFAULT 0,
  created_at_ms BIGINT NOT NULL,
  PRIMARY KEY (id),
  KEY idx_sync_logs_user_created (user_id, created_at_ms),
  KEY idx_sync_logs_user_session (user_id, session_id),
  CONSTRAINT fk_sync_logs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
`);
}

module.exports = { initDb };
