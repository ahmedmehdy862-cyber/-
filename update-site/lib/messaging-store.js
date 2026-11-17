const fs = require("node:fs");
const path = require("node:path");

const MESSAGES_PATH = "focus-study/team-messages.json";
const INBOX_PATH = "focus-study/support-inbox.json";
const LOCAL_DIR = path.join(__dirname, "..", ".data");

async function readJson(blobPath, fallback) {
  if (process.env.BLOB_READ_WRITE_TOKEN) {
    try {
      const { get } = await import("@vercel/blob");
      const result = await get(blobPath, { access: "public" });
      if (result?.statusCode === 200 && result.stream) {
        return JSON.parse(await new Response(result.stream).text());
      }
    } catch (error) {
      console.error("blob read failed", blobPath, error?.message || "unknown");
    }
  }
  try {
    return JSON.parse(fs.readFileSync(path.join(LOCAL_DIR, path.basename(blobPath)), "utf8"));
  } catch {
    return fallback;
  }
}

async function writeJson(blobPath, value) {
  if (process.env.BLOB_READ_WRITE_TOKEN) {
    const { put } = await import("@vercel/blob");
    await put(blobPath, JSON.stringify(value), {
      access: "public",
      addRandomSuffix: false,
      allowOverwrite: true,
      contentType: "application/json; charset=utf-8",
      cacheControlMaxAge: 30,
    });
    return;
  }
  fs.mkdirSync(LOCAL_DIR, { recursive: true });
  fs.writeFileSync(path.join(LOCAL_DIR, path.basename(blobPath)), JSON.stringify(value), "utf8");
}

async function readMessages() {
  const record = await readJson(MESSAGES_PATH, { messages: [] });
  return Array.isArray(record.messages) ? record.messages : [];
}

async function appendMessage(message) {
  const messages = await readMessages();
  const next = [message, ...messages].slice(0, 40);
  await writeJson(MESSAGES_PATH, { messages: next });
  return next;
}

async function removeMessage(id) {
  const messages = await readMessages();
  const next = messages.filter((item) => item.id !== id);
  await writeJson(MESSAGES_PATH, { messages: next });
  return next;
}

async function readInbox() {
  const record = await readJson(INBOX_PATH, { items: [] });
  return Array.isArray(record.items) ? record.items : [];
}

async function appendSupport(item) {
  const items = await readInbox();
  const next = [item, ...items].slice(0, 500);
  await writeJson(INBOX_PATH, { items: next });
  return next;
}

async function markSupport(id, status) {
  const items = await readInbox();
  const next = items.map((item) => (item.id === id ? { ...item, status } : item));
  await writeJson(INBOX_PATH, { items: next });
  return next;
}

module.exports = {
  readMessages, appendMessage, removeMessage, readInbox, appendSupport, markSupport,
};
