const { readMessages, upsertUser, readNotificationSounds } = require("../lib/messaging-store");
const { userCode } = require("../lib/user-code");
const { parseBody } = require("../lib/admin-auth");

const cut = (value, max) => String(value || "").trim().slice(0, max);

/**
 * Public feed. A message can be limited to one usage profile (student/professional/…) and to
 * app builds at or above a version code, so the team can speak to the right people.
 *
 * POST /api/messages is the quiet "new user" handshake: the app sends the name it already has and
 * its anonymous install id, and the dashboard lists the person under the same short code shown in
 * the app. It shares this function so the deployment stays within the Hobby function limit.
 */
module.exports = async function messages(request, response) {
  if (request.method === "POST") return register(request, response);
  if (request.method !== "GET") {
    response.setHeader("Allow", "GET, POST");
    return response.status(405).json({ error: "method_not_allowed" });
  }
  try {
    const profile = String(request.query?.profile || "").trim();
    const version = Number(request.query?.version || 0);
    const uid = String(request.query?.uid || "").trim().toLowerCase();
    const [all, sounds] = await Promise.all([readMessages(), readNotificationSounds()]);
    // A message addressed to one install ignores the audience and version rules: it is personal.
    // It can carry the full install id or the four-four code the app shows, which is derived here
    // from the id this request already sent — nothing extra is stored or asked for.
    const myCode = uid ? userCode(uid) : "";
    const addressedToMe = (item) => {
      const wantedId = String(item.targetUserId || "").trim().toLowerCase();
      const wantedCode = String(item.targetCode || "").trim().toUpperCase();
      if (!wantedId && !wantedCode) return null;
      if (!uid) return false;
      if (wantedId && (uid === wantedId || (wantedId.length >= 8 && uid.startsWith(wantedId)))) return true;
      return Boolean(wantedCode) && wantedCode === myCode;
    };
    const messages = all.filter((item) => {
      const personal = addressedToMe(item);
      if (personal !== null) return personal;
      const audience = String(item.audienceProfile || "").trim();
      const minVersion = Number(item.minVersionCode || 0);
      if (audience && profile && audience !== profile) return false;
      if (audience && !profile) return false;
      if (minVersion > 0 && version > 0 && version < minVersion) return false;
      return true;
    });
    response.setHeader("Cache-Control", "public, s-maxage=60, stale-while-revalidate=600");
    return response.status(200).json({ messages, sounds: sounds.sounds });
  } catch {
    return response.status(503).json({ error: "messages_unavailable" });
  }
};

async function register(request, response) {
  const body = parseBody(request.body);
  if (String(body.action || "") !== "register") {
    return response.status(400).json({ error: "invalid_action" });
  }
  const id = cut(body.uid, 64).toLowerCase();
  if (!/^[a-z0-9-]{6,64}$/.test(id)) {
    return response.status(400).json({ error: "invalid_user" });
  }
  const allowedProfiles = new Set(["", "student", "graduate", "professional", "retired", "general"]);
  const entry = {
    id,
    name: cut(body.name, 60) || "مستخدم مُنظِّم",
    profile: allowedProfiles.has(cut(body.profile, 24)) ? cut(body.profile, 24) : "general",
    versionName: cut(body.versionName, 32),
    versionCode: Math.max(0, Math.min(1_000_000, Number(body.versionCode) || 0)),
  };
  try {
    await upsertUser(entry);
    response.setHeader("Cache-Control", "no-store");
    return response.status(200).json({ ok: true, code: userCode(id) });
  } catch {
    return response.status(503).json({ error: "registration_unavailable" });
  }
}
