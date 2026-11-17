const crypto = require("node:crypto");

const PATH = "focus-study/admin-credential.json";

async function readCredential() {
  if (!process.env.BLOB_READ_WRITE_TOKEN) return null;
  try {
    const { get } = await import("@vercel/blob");
    const result = await get(PATH, { access: "public" });
    if (result?.statusCode === 200 && result.stream) return JSON.parse(await new Response(result.stream).text());
  } catch (error) {
    console.error("credential read failed", error?.message || "unknown");
  }
  return null;
}

async function writeCredential(record) {
  const { put } = await import("@vercel/blob");
  await put(PATH, JSON.stringify(record), {
    access: "public",
    addRandomSuffix: false,
    allowOverwrite: true,
    contentType: "application/json; charset=utf-8",
    cacheControlMaxAge: 0,
  });
}

function hashPassword(password, salt = crypto.randomBytes(16).toString("hex")) {
  return { salt, hash: crypto.scryptSync(String(password), salt, 64).toString("hex") };
}

function verifyPassword(password, record) {
  if (!record?.salt || !record?.hash) return false;
  const candidate = crypto.scryptSync(String(password), record.salt, 64);
  const expected = Buffer.from(record.hash, "hex");
  return candidate.length === expected.length && crypto.timingSafeEqual(candidate, expected);
}

/** The bootstrap password lives in env until the admin sets their own; then only the hash counts. */
async function mustChangePassword() {
  const record = await readCredential();
  if (!record) return true;
  return Boolean(record.mustChange);
}

module.exports = { readCredential, writeCredential, hashPassword, verifyPassword, mustChangePassword };
