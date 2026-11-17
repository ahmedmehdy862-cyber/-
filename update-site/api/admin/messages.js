const { isAdmin, parseBody, json } = require("../../lib/admin-auth");
const { readMessages, appendMessage, removeMessage, readInbox, markSupport } = require("../../lib/messaging-store");

/** Admin: publish an instant message to every app, or delete one. */
module.exports = async function adminMessages(req, res) {
  if (!isAdmin(req)) return json(res, 401, { error: "authentication_required" });
  try {
    // Inbox lives on the same function: the Hobby plan allows 12 serverless functions per deployment.
    const wantsInbox = String(req.query?.view || "") === "inbox";
    if (req.method === "GET") {
      if (wantsInbox) {
        const items = await readInbox();
        return json(res, 200, { items, open: items.filter((item) => item.status === "new").length });
      }
      return json(res, 200, { messages: await readMessages() });
    }
    if (req.method === "DELETE") {
      const id = String(parseBody(req.body).id || "").trim();
      if (!id) return json(res, 400, { error: "id_required" });
      return json(res, 200, { messages: await removeMessage(id) });
    }
    if (req.method !== "POST") return json(res, 405, { error: "method_not_allowed" });
    const body = parseBody(req.body);
    if (body.markId) {
      const id = String(body.markId || "").trim();
      const status = ["new", "handled"].includes(body.status) ? body.status : "handled";
      return json(res, 200, { items: await markSupport(id, status) });
    }
    const title = String(body.title || "").trim().slice(0, 90);
    const text = String(body.body || "").trim().slice(0, 900);
    const audienceProfile = ["", "student", "graduate", "professional", "retired", "general"].includes(
      String(body.audienceProfile || "")
    ) ? String(body.audienceProfile || "") : "";
    const minVersionCode = Math.max(0, Math.min(1_000_000, Number(body.minVersionCode || 0) || 0));
    if (title.length < 3 || text.length < 3) return json(res, 400, { error: "title_and_body_required" });
    const message = {
      id: `m-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      title,
      body: text,
      audienceProfile,
      minVersionCode,
      at: new Date().toISOString(),
    };
    return json(res, 200, { ok: true, messages: await appendMessage(message) });
  } catch {
    return json(res, 500, { error: "messages_failed" });
  }
};
