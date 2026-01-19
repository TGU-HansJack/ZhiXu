require("dotenv").config();

const express = require("express");
const cors = require("cors");
const { port, jsonBodyLimit, corsOrigin } = require("./config");
const { waitForDb } = require("./db");
const { initDb } = require("./initDb");
const authRoutes = require("./routes/auth");
const accountRoutes = require("./routes/account");
const vaultV2Routes = require("./routes/vault_v2");
const storageRoutes = require("./routes/storage");

async function main() {
  await waitForDb();
  await initDb();

  const app = express();
  app.disable("x-powered-by");
  app.use(cors({ origin: corsOrigin }));
  app.use(express.json({ limit: jsonBodyLimit }));

  app.get("/health", (_req, res) => res.json({ ok: true }));

  app.use("/api/auth", authRoutes);
  app.use("/api/account", accountRoutes);
  app.use("/api/v2/vault", vaultV2Routes);
  app.use("/api/storage", storageRoutes);

  app.listen(port, () => {
    // eslint-disable-next-line no-console
    console.log(`Server listening on :${port}`);
  });
}

main().catch((e) => {
  // eslint-disable-next-line no-console
  console.error(e);
  process.exit(1);
});
