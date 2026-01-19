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

const parseBoolEnv = (key, fallback) => {
  const raw = process.env[key];
  if (raw == null || raw === "") return fallback;
  if (raw === "1" || raw.toLowerCase() === "true" || raw.toLowerCase() === "yes") return true;
  if (raw === "0" || raw.toLowerCase() === "false" || raw.toLowerCase() === "no") return false;
  return fallback;
};

module.exports = {
  port: parseIntEnv("PORT", 3001),
  nodeEnv: process.env.NODE_ENV || "development",
  jsonBodyLimit: process.env.JSON_BODY_LIMIT || "2mb",
  rawBodyLimitBytes: parseIntEnv("RAW_BODY_LIMIT_BYTES", 50 * 1024 * 1024),
  storageLimitBytes: parseIntEnv("STORAGE_LIMIT_BYTES", 5 * 1024 * 1024 * 1024),
  corsOrigin: process.env.CORS_ORIGIN || "*",
  storageRoot: path.resolve(process.env.STORAGE_ROOT || path.join(__dirname, "..", "storage")),
  mysql: {
    host: requireEnv("MYSQL_HOST", "127.0.0.1"),
    port: parseIntEnv("MYSQL_PORT", 3306),
    user: requireEnv("MYSQL_USER", "root"),
    password: requireEnv("MYSQL_PASSWORD", ""),
    database: requireEnv("MYSQL_DATABASE", "zhixu")
  },
  smtp: {
    host: process.env.SMTP_HOST || "",
    port: parseIntEnv("SMTP_PORT", 587),
    secure: parseBoolEnv("SMTP_SECURE", false),
    user: process.env.SMTP_USER || "",
    pass: process.env.SMTP_PASS || "",
    from: process.env.SMTP_FROM || "",
    tlsRejectUnauthorized: parseBoolEnv("SMTP_TLS_REJECT_UNAUTHORIZED", true)
  },
  jwtSecret: requireEnv("JWT_SECRET", "dev_secret_change_me"),
  jwtAccessTtl: process.env.JWT_ACCESS_TTL || "30d",
  refreshTokenTtlDays: parseIntEnv("REFRESH_TOKEN_TTL_DAYS", 30),
  bcryptRounds: parseIntEnv("BCRYPT_ROUNDS", 12)
};
