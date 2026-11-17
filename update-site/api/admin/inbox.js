const { isAdmin, parseBody, json } = require("../../lib/admin-auth");
const { readInbox, markSupport } = require("../../lib/messaging-store");

/** Admin: read support messages from users and mark them handled. */
module.exports = async function adminInbox(req, res) {
  if (!isAdmin(req)) return json(res, 401, { error: "authentication_required" });
  try {
    if (req.method === "GET") {
      const items = await readInbox();
      return json(res, 200, { items, open: items.filter((item) => item.status === "new").length });
    }
    if (req.method === "POST") {
      const body = parseBody(req.body);
      const id = String(body.id || "").trim();
      const status = ["new", "handled"].includes(body.status) ? body.status : "handled";
      if (!id) return json(res, 400, { error: "id_required" });
      return json(res, 200, { items: await markSupport(id, status) });
    }
    return json(res, 405, { error: "method_not_allowed" });
  } catch {
    return json(res, 500, { error: "inbox_failed" });
  }
};
