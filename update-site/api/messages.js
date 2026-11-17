const { readMessages } = require("../lib/messaging-store");

/** Public: the newest team messages the app shows without shipping a new build. */
module.exports = async function messages(request, response) {
  if (request.method !== "GET") {
    response.setHeader("Allow", "GET");
    return response.status(405).json({ error: "method_not_allowed" });
  }
  try {
    const messages = await readMessages();
    response.setHeader("Cache-Control", "public, s-maxage=60, stale-while-revalidate=600");
    return response.status(200).json({ messages });
  } catch {
    return response.status(503).json({ error: "messages_unavailable" });
  }
};
