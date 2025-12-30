const fs = require("fs");
const path = require("path");

function resolveEntrypoint() {
  const serverEntry = path.join(__dirname, "..", "server", "index.js");
  const srcEntry = path.join(__dirname, "..", "src", "index.js");
  if (fs.existsSync(serverEntry)) return serverEntry;
  if (fs.existsSync(srcEntry)) return srcEntry;
  throw new Error("Missing entrypoint: expected server/index.js or src/index.js");
}

// eslint-disable-next-line global-require, import/no-dynamic-require
require(resolveEntrypoint());

