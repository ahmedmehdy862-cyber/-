const fs = require("node:fs");
const path = require("node:path");
const { userCode } = require("./user-code");

const MESSAGES_PATH = "focus-study/team-messages.json";
const INBOX_PATH = "focus-study/support-inbox.json";
const USERS_PATH = "focus-study/users.json";
const LOCAL_DIR = path.join(__dirname, "..", ".data");
// Broadcast messages are public; the support inbox and user registry hold user-submitted details
// and are read/written only through the server-side @vercel/blob client with BLOB_READ_WRITE_TOKEN.
const MESSAGES_ACCESS = "public";
const INBOX_ACCESS = "private";
const USERS_ACCESS = "private";
const MAX_USERS = 1000;

async function readBlobJson(blobPath, access) {
  const { get } = await import("@vercel/blob");
  const result = await get(blobPath, { access });
  if (result?.statusCode === 200 && result.stream) {
    return JSON.parse(await new Response(result.stream).text());
  }
  return null;
}

function readLocalJson(blobPath, fallback) {
  try {
    return JSON.parse(fs.readFileSync(path.join(LOCAL_DIR, path.basename(blobPath)), "utf8"));
  } catch {
    return fallback;
  }
}

async function readJson(blobPath, fallback, access = MESSAGES_ACCESS) {
  if (process.env.BLOB_READ_WRITE_TOKEN) {
    try {
      const record = await readBlobJson(blobPath, access);
      if (record) return record;
    } catch (error) {
      console.error("blob read failed", blobPath, error?.message || "unknown");
    }
  }
  return readLocalJson(blobPath, fallback);
}

/** Reads the inbox from its new private host, migrating an older public record once. */
async function readInboxJson() {
  if (process.env.BLOB_READ_WRITE_TOKEN) {
    try {
      const current = await readBlobJson(INBOX_PATH, INBOX_ACCESS);
      if (current) return current;
      const legacy = await readBlobJson(INBOX_PATH, MESSAGES_ACCESS);
      if (legacy) {
        await writeJson(INBOX_PATH, legacy, INBOX_ACCESS);
        try {
          const { del } = await import("@vercel/blob");
          await del(INBOX_PATH, { access: MESSAGES_ACCESS });
        } catch (error) {
          console.error("legacy inbox cleanup failed", error?.message || "unknown");
        }
        console.log("support inbox migrated to private Blob");
        return legacy;
      }
    } catch (error) {
      console.error("private inbox read failed", error?.message || "unknown");
    }
  }
  return readLocalJson(INBOX_PATH, { items: [] });
}

async function writeJson(blobPath, value, access = MESSAGES_ACCESS) {
  if (process.env.BLOB_READ_WRITE_TOKEN) {
    const { put } = await import("@vercel/blob");
    await put(blobPath, JSON.stringify(value), {
      access,
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
  const record = await readInboxJson();
  return Array.isArray(record.items) ? record.items : [];
}

async function appendSupport(item) {
  const items = await readInbox();
  const next = [item, ...items].slice(0, 500);
  await writeJson(INBOX_PATH, { items: next }, INBOX_ACCESS);
  return next;
}

async function markSupport(id, status) {
  const items = await readInbox();
  const next = items.map((item) => (item.id === id ? { ...item, status } : item));
  await writeJson(INBOX_PATH, { items: next }, INBOX_ACCESS);
  return next;
}

async function readUsers() {
  const record = await readJson(USERS_PATH, { users: [] }, USERS_ACCESS);
  return Array.isArray(record.users) ? record.users : [];
}

/** Upsert by anonymous install id, keeping the original first-seen timestamp. */
async function upsertUser(entry) {
  const users = await readUsers();
  const now = new Date().toISOString();
  const existing = users.find((item) => item.id === entry.id);
  const next = existing
    ? users.map((item) => item.id === entry.id
      ? { ...item, ...entry, code: userCode(entry.id), lastSeen: now, updatedAt: now }
      : item)
    : [{ ...entry, code: userCode(entry.id), firstSeen: now, lastSeen: now, updatedAt: now }, ...users]
      .slice(0, MAX_USERS);
  await writeJson(USERS_PATH, { users: next }, USERS_ACCESS);
  return next;
}

module.exports = {
  readMessages, appendMessage, removeMessage, readInbox, appendSupport, markSupport,
  readUsers, upsertUser,
};
