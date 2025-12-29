const { pool } = require("./db");

async function initDb() {
  await pool.query(`
CREATE TABLE IF NOT EXISTS users (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  username VARCHAR(64) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_users_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
`);

  await pool.query(`
CREATE TABLE IF NOT EXISTS devices (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  device_id VARCHAR(128) NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_devices_user_device (user_id, device_id),
  KEY idx_devices_user (user_id),
  CONSTRAINT fk_devices_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
`);

  await pool.query(`
CREATE TABLE IF NOT EXISTS notes (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  note_id VARCHAR(128) NOT NULL,
  device_id VARCHAR(128) NOT NULL,
  updated_at_ms BIGINT NOT NULL,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  encrypted TINYINT(1) NOT NULL DEFAULT 1,
  payload MEDIUMBLOB NOT NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_notes_user_note (user_id, note_id),
  KEY idx_notes_user_updated (user_id, updated_at_ms),
  CONSTRAINT fk_notes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
`);

  await pool.query(`
CREATE TABLE IF NOT EXISTS vault_files (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id BIGINT UNSIGNED NOT NULL,
  path VARCHAR(1024) NOT NULL,
  path_hash BINARY(32) NOT NULL,
  updated_at_ms BIGINT NOT NULL,
  mtime_ms BIGINT NOT NULL,
  size_bytes BIGINT NOT NULL,
  sha256 CHAR(64) NOT NULL,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  content LONGBLOB NULL,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_vault_files_user_path (user_id, path_hash),
  KEY idx_vault_files_user_updated (user_id, updated_at_ms),
  CONSTRAINT fk_vault_files_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
`);

  // Best-effort migration for older schemas.
  // - Old schema used UNIQUE(user_id, path) which can exceed InnoDB key length under utf8mb4.
  // - New schema uses SHA-256(path) in `path_hash` for the unique key.
  try {
    const [colRows] = await pool.query("SHOW COLUMNS FROM vault_files LIKE 'path_hash'");
    const hasPathHash = Array.isArray(colRows) && colRows.length > 0;
    if (!hasPathHash) {
      await pool.query("ALTER TABLE vault_files ADD COLUMN path_hash BINARY(32) NULL AFTER path");
    }

    await pool.query(
      "UPDATE vault_files SET path_hash = UNHEX(SHA2(path, 256)) WHERE path_hash IS NULL OR LENGTH(path_hash) != 32"
    );
    await pool.query("ALTER TABLE vault_files MODIFY path_hash BINARY(32) NOT NULL").catch(() => undefined);

    // Replace unique index to use (user_id, path_hash).
    // Drop old index if present; ignore failures.
    await pool.query("ALTER TABLE vault_files DROP INDEX uq_vault_files_user_path").catch(() => undefined);

    // Expand path length back to 1024 (safe because not indexed).
    await pool.query("ALTER TABLE vault_files MODIFY path VARCHAR(1024) NOT NULL").catch(() => undefined);

    await pool.query("ALTER TABLE vault_files ADD UNIQUE KEY uq_vault_files_user_path (user_id, path_hash)").catch(() => undefined);
  } catch (_) {
    // ignore
  }
}

module.exports = { initDb };
