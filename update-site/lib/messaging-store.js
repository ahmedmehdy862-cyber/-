const fs = require("node:fs");
const path = require("node:path");
const { userCode } = require("./user-code");

const MESSAGES_PATH = "focus-study/team-messages.json";
const INBOX_PATH = "focus-study/support-inbox.json";
const USERS_PATH = "focus-study/users.json";
const SOUNDS_PATH = "focus-study/notification-sounds.json";
const LOCAL_DIR = path.join(__dirname, "..", ".data");
// Broadcast messages are public; the support inbox, user registry and sound choices are
// read/written only through the server-side @vercel/blob client with BLOB_READ_WRITE_TOKEN.
const MESSAGES_ACCESS = "public";
const INBOX_ACCESS = "private";
const USERS_ACCESS = "private";
const SOUNDS_ACCESS = "private";
const MAX_USERS = 1000;

const SOUND_CATEGORIES = ["prayer", "task", "exam", "team", "update", "daily", "dhikr"];
const SOUND_CHOICES = ["default", "chime", "adhan", "system", "silent"];
const DEFAULT_SOUNDS = Object.fromEntries(SOUND_CATEGORIES.map((key) => [key, "default"]));

/** Keeps only known notification types and known sound ids, so a bad payload cannot change behaviour. */
function normalizeSounds(input) {
  const result = { ...DEFAULT_SOUNDS };
  if (!input || typeof input !== "object" || Array.isArray(input)) return result;
  for (const key of SOUND_CATEGORIES) {
    const value = String(input[key] || "").trim().toLowerCase();
    if (SOUND_CHOICES.includes(value)) result[key] = value;
  }
  return result;
}

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

async function readJson(blobPath, fallback, access = MESSAGES_ACCESS, strict = false) {
  if (process.env.BLOB_READ_WRITE_TOKEN) {
    try {
      const record = await readBlobJson(blobPath, access);
      if (record) return record;
    } catch (error) {
      console.error("blob read failed", blobPath, error?.message || "unknown");
      // Private stores must never fall back to an empty local file after a failed read: the next
      // write would overwrite the real registry or the saved sound choices with that empty copy.
      if (strict) throw new Error("private_store_unavailable");
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
      throw new Error("private_inbox_unavailable");
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
  const record = await readJson(USERS_PATH, { users: [] }, USERS_ACCESS, true);
  return Array.isArray(record.users) ? record.users : [];
}

async function readNotificationSounds() {
  const record = await readJson(SOUNDS_PATH, { sounds: DEFAULT_SOUNDS, updatedAt: "" }, SOUNDS_ACCESS, true);
  return { sounds: normalizeSounds(record.sounds), updatedAt: String(record.updatedAt || "") };
}

async function writeNotificationSounds(input) {
  const sounds = normalizeSounds(input);
  const updatedAt = new Date().toISOString();
  await writeJson(SOUNDS_PATH, { sounds, updatedAt }, SOUNDS_ACCESS);
  return { sounds, updatedAt };
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
  readUsers, upsertUser, readNotificationSounds, writeNotificationSounds,
  normalizeSounds, SOUND_CATEGORIES, SOUND_CHOICES, DEFAULT_SOUNDS,
};
