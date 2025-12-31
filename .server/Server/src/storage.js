const crypto = require("crypto");
const fs = require("fs");
const path = require("path");

function sha256HexUtf8(text) {
  return crypto.createHash("sha256").update(String(text), "utf8").digest("hex");
}

function ensureSafeRelativePath(p) {
  const rel = String(p || "").replace(/\\/g, "/").replace(/^\//, "");
  if (!rel) return null;
  if (rel.includes("\0")) return null;
  const parts = rel.split("/").filter(Boolean);
  if (parts.some((seg) => seg === "." || seg === "..")) return null;
  return parts.join("/");
}

function buildObjectPath(storageRoot, userId, vaultPath) {
  const safe = ensureSafeRelativePath(vaultPath);
  if (!safe) return null;
  const key = sha256HexUtf8(safe);
  const prefix = key.slice(0, 2);
  return {
    key,
    absPath: path.join(storageRoot, "vaults", String(userId), "objects", prefix, key)
  };
}

async function atomicWriteFile(absPath, bytes) {
  const dir = path.dirname(absPath);
  await fs.promises.mkdir(dir, { recursive: true });

  const tmpDir = path.join(dir, ".tmp");
  await fs.promises.mkdir(tmpDir, { recursive: true });

  const tmpName = `${path.basename(absPath)}.${process.pid}.${Date.now()}.${crypto.randomBytes(6).toString("hex")}.tmp`;
  const tmpPath = path.join(tmpDir, tmpName);

  try {
    await fs.promises.writeFile(tmpPath, bytes);
    await fs.promises.rename(tmpPath, absPath);
  } catch (e) {
    try {
      await fs.promises.unlink(tmpPath);
    } catch (_) {
      // ignore
    }
    throw e;
  }
}

async function deleteFileBestEffort(absPath) {
  try {
    await fs.promises.unlink(absPath);
  } catch (_) {
    // ignore
  }
}

module.exports = {
  buildObjectPath,
  atomicWriteFile,
  deleteFileBestEffort
};

