const path = require("path");

const requireEnv = (key, fallback = undefined) => {
  const value = process.env[key];
  if (value == null || value === "") {
    if (fallback !== undefined) return fallback;
    throw new Error(`Missing env: ${key}`);
  }
  return value;
};

const parseIntEnv = (key, fallback) => {
  const raw = process.env[key];
  if (raw == null || raw === "") return fallback;
  const n = Number.parseInt(raw, 10);
  if (!Number.isFinite(n)) return fallback;
  return n;
};

module.exports = {
  port: parseIntEnv("PORT", 3001),
  nodeEnv: process.env.NODE_ENV || "development",
  jsonBodyLimit: process.env.JSON_BODY_LIMIT || "2mb",
  rawBodyLimitBytes: parseIntEnv("RAW_BODY_LIMIT_BYTES", 50 * 1024 * 1024),
  corsOrigin: process.env.CORS_ORIGIN || "*",
  storageRoot: path.resolve(process.env.STORAGE_ROOT || path.join(__dirname, "..", "storage")),
  mysql: {
    host: requireEnv("MYSQL_HOST", "127.0.0.1"),
    port: parseIntEnv("MYSQL_PORT", 3306),
    user: requireEnv("MYSQL_USER", "root"),
    password: requireEnv("MYSQL_PASSWORD", ""),
    database: requireEnv("MYSQL_DATABASE", "zhixu")
  },
  jwtSecret: requireEnv("JWT_SECRET", "dev_secret_change_me"),
  bcryptRounds: parseIntEnv("BCRYPT_ROUNDS", 12)
};
