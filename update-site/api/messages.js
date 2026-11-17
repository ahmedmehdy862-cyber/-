const { readMessages } = require("../lib/messaging-store");

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
    const all = await readMessages();
    const messages = all.filter((item) => {
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
