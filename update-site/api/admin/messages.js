const { isAdmin, parseBody, json } = require("../../lib/admin-auth");
const { readMessages, appendMessage, removeMessage } = require("../../lib/messaging-store");

/** Admin: publish an instant message to every app, or delete one. */
module.exports = async function adminMessages(req, res) {
  if (!isAdmin(req)) return json(res, 401, { error: "authentication_required" });
  try {
    if (req.method === "GET") return json(res, 200, { messages: await readMessages() });
    if (req.method === "DELETE") {
      const id = String(parseBody(req.body).id || "").trim();
      if (!id) return json(res, 400, { error: "id_required" });
      return json(res, 200, { messages: await removeMessage(id) });
    }
    if (req.method !== "POST") return json(res, 405, { error: "method_not_allowed" });
    const body = parseBody(req.body);
    const title = String(body.title || "").trim().slice(0, 90);
    const text = String(body.body || "").trim().slice(0, 900);
    if (title.length < 3 || text.length < 3) return json(res, 400, { error: "title_and_body_required" });
    const message = {
      id: `m-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      title,
      body: text,
      at: new Date().toISOString(),
    };
    return json(res, 200, { ok: true, messages: await appendMessage(message) });
  } catch {
    return json(res, 500, { error: "messages_failed" });
  }
};
