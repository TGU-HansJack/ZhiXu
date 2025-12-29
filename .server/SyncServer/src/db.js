const mysql = require("mysql2/promise");
const { mysql: mysqlConfig } = require("./config");

const pool = mysql.createPool({
  host: mysqlConfig.host,
  port: mysqlConfig.port,
  user: mysqlConfig.user,
  password: mysqlConfig.password,
  database: mysqlConfig.database,
  waitForConnections: true,
  connectionLimit: 10,
  queueLimit: 0
});

async function waitForDb({ retries = 60, delayMs = 1000 } = {}) {
  let lastErr = null;
  for (let i = 0; i < retries; i += 1) {
    try {
      await pool.query("SELECT 1");
      return;
    } catch (e) {
      lastErr = e;
      await new Promise((r) => setTimeout(r, delayMs));
    }
  }
  throw lastErr || new Error("DB not ready");
}

module.exports = {
  pool,
  waitForDb
};

