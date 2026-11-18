const { readMessages } = require("../lib/messaging-store");
const { userCode } = require("../lib/user-code");

/**
 * Public feed. A message can be limited to one usage profile (student/professional/…) and to
 * app builds at or above a version code, so the team can speak to the right people.
 */
module.exports = async function messages(request, response) {
  if (request.method !== "GET") {
    response.setHeader("Allow", "GET");
    return response.status(405).json({ error: "method_not_allowed" });
  }
  try {
    const profile = String(request.query?.profile || "").trim();
    const version = Number(request.query?.version || 0);
    const uid = String(request.query?.uid || "").trim().toLowerCase();
    const all = await readMessages();
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
    return response.status(200).json({ messages });
  } catch {
    return response.status(503).json({ error: "messages_unavailable" });
  }
};
