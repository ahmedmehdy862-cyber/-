const fs = require("node:fs");
const path = require("node:path");

const RELEASE_PATH = "focus-study/latest.json";
const FALLBACK_PATH = path.join(__dirname, "..", "release.json");

/**
 * Highest release wins.
 *
 * The stored record is what the team publishes from the dashboard, while every deployment also
 * ships release.json. Reading both and keeping the newer version means an older stored record, or
 * a build that landed without one, can never make the site and the app disagree.
 */
async function readLatestRelease() {
  let stored = null;
  if (process.env.BLOB_READ_WRITE_TOKEN) {
    try {
      const { get } = await import("@vercel/blob");
      const result = await get(RELEASE_PATH, { access: "public" });
      if (result?.statusCode === 200 && result.stream) {
        stored = JSON.parse(await new Response(result.stream).text());
      }
    } catch (error) {
      console.error("Unable to read the latest release record from Blob storage", error?.message || "unknown error");
    }
  }
  let shipped = null;
  try { shipped = JSON.parse(fs.readFileSync(FALLBACK_PATH, "utf8")); } catch { shipped = null; }
  if (!stored) return shipped || {};
  if (!shipped) return stored;
  return Number(shipped.versionCode || 0) >= Number(stored.versionCode || 0) ? shipped : stored;
}

/** The copy shipped with the deployment; used when Blob has nothing newer. */
function readFallbackRelease() {
  return JSON.parse(fs.readFileSync(FALLBACK_PATH, "utf8"));
}

module.exports = { RELEASE_PATH, FALLBACK_PATH, readLatestRelease, readFallbackRelease };
