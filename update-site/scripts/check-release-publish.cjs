const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");
const { releasePayloadEquals, decideCiPublish } = require("../lib/release-store");

const base = {
  packageName: "com.focusstudy.poc",
  versionCode: 34,
  versionName: "0.26.0",
  storeUrl: "https://qubixstore.vercel.app/focus-study-updates",
  apkUrl: "https://github.com/OWNER/REPO/releases/download/focus-v0.26.0/MONAZZEM.apk",
  description: "نظّم مهامك وموادك وجلسات تركيزك وخطتك الدراسية في مساحة واحدة.",
  releasePageUrl: "https://update-site-sigma.vercel.app/#download",
  releaseNotes: "إصلاحات جديدة.",
};

// Generated timestamps must not defeat idempotency.
assert.equal(releasePayloadEquals(base, { ...base, publishedAt: "2026-01-01T00:00:00.000Z" }), true);
assert.equal(releasePayloadEquals(base, { ...base, versionCode: "34" }), true);

// Every meaningful field is part of the comparison.
for (const field of ["packageName", "versionName", "storeUrl", "apkUrl", "description", "releasePageUrl", "releaseNotes"]) {
  assert.equal(releasePayloadEquals(base, { ...base, [field]: `${base[field]}-changed` }), false, field);
}
assert.equal(releasePayloadEquals(base, { ...base, versionCode: 35 }), false);
assert.equal(releasePayloadEquals(null, base), false);

// CI decisions: lower is rejected; equal is unchanged only when the full payload matches.
assert.deepEqual(decideCiPublish(base, { ...base, versionCode: 33 }), {
  action: "reject", error: "version_code_must_increase",
});
assert.deepEqual(decideCiPublish(base, { ...base, publishedAt: "2026-01-01T00:00:00.000Z" }), {
  action: "unchanged",
});
assert.deepEqual(decideCiPublish(base, { ...base, releaseNotes: "ملاحظات مختلفة." }), { action: "write" });
assert.deepEqual(decideCiPublish(base, { ...base, apkUrl: "https://example.com/other.apk" }), { action: "write" });
assert.deepEqual(decideCiPublish(base, { ...base, versionName: "0.26.1", versionCode: 35 }), { action: "write" });

// The admin bundle must contain the three controls that were missing from the stale build.
const bundle = fs.readFileSync(path.join(__dirname, "..", "public", "admin", "app.bundle.js"), "utf8");
for (const literal of ["sync-release", "password-form", "password-panel"]) {
  assert.equal(bundle.includes(literal), true, `bundle is missing ${literal}`);
}

console.log("release publish checks passed");
