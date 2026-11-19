const { isAdmin, parseBody, json } = require("../../lib/admin-auth");
const {
  readMessages, appendMessage, removeMessage, readInbox, markSupport, readUsers,
  readNotificationSounds, writeNotificationSounds
} = require("../../lib/messaging-store");
const { normalizeUserCode } = require("../../lib/user-code");

/** Admin: publish an instant message to every app, or delete one. */
module.exports = async function adminMessages(req, res) {
  if (!isAdmin(req)) return json(res, 401, { error: "authentication_required" });
  try {
    // Inbox lives on the same function: the Hobby plan allows 12 serverless functions per deployment.
    const view = String(req.query?.view || "");
    if (req.method === "GET") {
      if (view === "inbox") {
        const items = await readInbox();
        return json(res, 200, { items, open: items.filter((item) => item.status === "new").length });
      }
      if (view === "users") {
        const users = (await readUsers()).sort((a, b) =>
          String(b.firstSeen || "").localeCompare(String(a.firstSeen || ""))
        );
        return json(res, 200, { users, total: users.length });
      }
      if (view === "sounds") return json(res, 200, await readNotificationSounds());
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
    if (view === "sounds") return json(res, 200, await writeNotificationSounds(body.sounds));
    const title = String(body.title || "").trim().slice(0, 90);
    const text = String(body.body || "").trim().slice(0, 900);
    const audienceProfile = ["", "student", "graduate", "professional", "retired", "general"].includes(
      String(body.audienceProfile || "")
    ) ? String(body.audienceProfile || "") : "";
    const minVersionCode = Math.max(0, Math.min(1_000_000, Number(body.minVersionCode || 0) || 0));
    // The dashboard can paste either the short code people read out (AB12-CD34) or a full id.
    const rawRef = String(body.targetRef || body.targetUserId || "").trim();
    let targetUserId = "";
    let targetCode = "";
    if (rawRef) {
      targetCode = normalizeUserCode(rawRef);
      if (!targetCode) {
        if (!/^[a-z0-9-]{6,64}$/i.test(rawRef)) return json(res, 400, { error: "invalid_target_user" });
        targetUserId = rawRef.toLowerCase();
      }
    }
    if (title.length < 3 || text.length < 3) return json(res, 400, { error: "title_and_body_required" });
    const message = {
      id: `m-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
      title,
      body: text,
      audienceProfile,
      minVersionCode,
      targetUserId,
      targetCode,
      at: new Date().toISOString(),
    };
    return json(res, 200, { ok: true, messages: await appendMessage(message) });
  } catch {
    return json(res, 500, { error: "messages_failed" });
  }
};
