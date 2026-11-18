/**
 * The same short code the app shows under «رسائل الفريق».
 *
 * It is derived from the install id, so a message addressed to a code reaches exactly that install
 * without the dashboard ever storing or asking for a device id.
 */
function userCode(rawId) {
  const clean = String(rawId || "")
    .toLowerCase()
    .replace(/[^a-z0-9]/g, "")
    .slice(0, 8)
    .toUpperCase();
  return clean.length === 8 ? `${clean.slice(0, 4)}-${clean.slice(4)}` : clean;
}

/** Accepts "abcd1234", "ABCD-1234" or "abcd 1234" and returns the canonical code or "". */
function normalizeUserCode(input) {
  const clean = String(input || "")
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, "");
  if (clean.length !== 8) return "";
  return `${clean.slice(0, 4)}-${clean.slice(4)}`;
}

module.exports = { userCode, normalizeUserCode };
