const crypto = require("node:crypto");

const PATH = "focus-study/admin-credential.json";
// The stored record contains only a scrypt salt/hash, never the password itself. Private access
// keeps even that hash out of the public Blob URL; reads use the server-side @vercel/blob client
// with BLOB_READ_WRITE_TOKEN, the same path used by the rest of the API routes.
const ACCESS = "private";
const LEGACY_ACCESS = "public";

async function readBlob(access) {
  const { get } = await import("@vercel/blob");
  const result = await get(PATH, { access });
  if (result?.statusCode === 200 && result.stream) {
    return JSON.parse(await new Response(result.stream).text());
  }
  return null;
}

async function readCredential() {
  if (!process.env.BLOB_READ_WRITE_TOKEN) return null;
  try {
    const current = await readBlob(ACCESS);
    if (current) return current;
    // Public and private blobs live on different hosts. Older installs wrote this record to the
    // public host, so copy it once before deleting the public copy instead of losing admin access.
    const legacy = await readBlob(LEGACY_ACCESS);
    if (!legacy) return null;
    await writeCredential(legacy);
    try {
      const { del } = await import("@vercel/blob");
      await del(PATH, { access: LEGACY_ACCESS });
    } catch (error) {
      console.error("legacy credential cleanup failed", error?.message || "unknown");
    }
    console.log("admin credential migrated to private Blob");
    return legacy;
  } catch (error) {
    console.error("credential read failed", error?.message || "unknown");
  }
  return null;
}

async function writeCredential(record) {
  const { put } = await import("@vercel/blob");
  await put(PATH, JSON.stringify(record), {
    access: ACCESS,
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
