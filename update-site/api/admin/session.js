const { isAdmin, json } = require("../../lib/admin-auth");
const { mustChangePassword } = require("../../lib/admin-credentials");

module.exports = async function session(req, res) {
  if (req.method !== "GET") return json(res, 405, { error: "method_not_allowed" });
  if (!process.env.ADMIN_USERNAME || !process.env.ADMIN_SESSION_SECRET) return json(res, 503, { error: "admin_auth_unconfigured" });
  const authenticated = isAdmin(req);
  let mustChange = false;
  if (authenticated) {
    try { mustChange = await mustChangePassword(); } catch { mustChange = false; }
  }
  return json(res, 200, {
    authenticated,
    username: authenticated ? process.env.ADMIN_USERNAME : null,
    mustChangePassword: mustChange,
  });
};
