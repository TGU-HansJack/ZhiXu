require("dotenv").config();

const express = require("express");
const cors = require("cors");
const { port, jsonBodyLimit, corsOrigin } = require("./config");
const { waitForDb } = require("./db");
const { initDb } = require("./initDb");
const authRoutes = require("./routes/auth");
const accountRoutes = require("./routes/account");
const syncRoutes = require("./routes/sync");
const vaultRoutes = require("./routes/vault");

async function main() {
  await waitForDb();
  await initDb();

  const app = express();
  app.disable("x-powered-by");
  app.use(cors({ origin: corsOrigin }));
  app.use(express.json({ limit: jsonBodyLimit }));

  app.get("/health", (req, res) => res.json({ ok: true }));

  app.use("/api/auth", authRoutes);
  app.use("/api/account", accountRoutes);
  app.use("/api/sync", syncRoutes);
  app.use("/api/vault", vaultRoutes);

  app.listen(port, () => {
    // eslint-disable-next-line no-console
    console.log(`SyncServer listening on :${port}`);
  });
}

main().catch((e) => {
  // eslint-disable-next-line no-console
  console.error(e);
  process.exit(1);
});
