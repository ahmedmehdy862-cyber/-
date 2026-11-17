const { isAdmin, parseBody, safeEqual, json } = require("../../lib/admin-auth");
const { readCredential, writeCredential, hashPassword, verifyPassword } = require("../../lib/admin-credentials");

/**
 * Requires a valid session. The bootstrap password from env works once, then the stored
 * scrypt hash becomes the only accepted credential, so the temporary password is retired.
 */
module.exports = async function password(req, res) {
  if (req.method !== "POST") return json(res, 405, { error: "method_not_allowed" });
  if (!isAdmin(req)) return json(res, 401, { error: "authentication_required" });
  const body = parseBody(req.body);
  const currentPassword = String(body.currentPassword || "");
  const newPassword = String(body.newPassword || "");
  if (newPassword.length < 10) return json(res, 400, { error: "password_too_short" });
  if (safeEqual(currentPassword, newPassword)) return json(res, 400, { error: "password_unchanged" });

  const stored = await readCredential();
  const currentOk = stored ? verifyPassword(currentPassword, stored) : safeEqual(currentPassword, process.env.ADMIN_PASSWORD || "");
  if (!currentOk) return json(res, 401, { error: "invalid_credentials" });

  try {
    const { salt, hash } = hashPassword(newPassword);
    await writeCredential({
      username: process.env.ADMIN_USERNAME,
      salt,
      hash,
      mustChange: false,
      updatedAt: new Date().toISOString(),
    });
    return json(res, 200, { ok: true });
  } catch {
    return json(res, 500, { error: "password_save_failed" });
  }
};
