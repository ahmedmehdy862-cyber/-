/**
 * Local regression test for install-exclusive targeted messages.
 *
 * It mocks the Blob-backed store and admin auth, so no token, network or database is involved:
 *   npm run test:targeting
 */
const assert = require("node:assert/strict");
const path = require("node:path");

const ROOT = path.join(__dirname, "..");

function loadMocked(relativeModule, mocks) {
  const abs = path.resolve(ROOT, relativeModule);
  const dir = path.dirname(abs);
  const saved = new Map();
  for (const [specifier, exports] of Object.entries(mocks)) {
    const dep = require.resolve(path.resolve(dir, specifier));
    saved.set(dep, require.cache[dep]);
    require.cache[dep] = { id: dep, filename: dep, loaded: true, exports, children: [], paths: [] };
  }
  delete require.cache[abs];
  try {
    return require(abs);
  } finally {
    for (const [dep, previous] of saved) {
      if (previous) require.cache[dep] = previous;
      else delete require.cache[dep];
    }
    delete require.cache[abs];
  }
}

function makeRes() {
  return {
    headers: {},
    statusCode: 200,
    body: null,
    setHeader(key, value) { this.headers[key] = value; return this; },
    status(code) { this.statusCode = code; return this; },
    json(value) { this.body = value; return this; },
  };
}

function makeStore(users, messages) {
  let currentUsers = users;
  let currentMessages = messages;
  return {
    readUsers: async () => currentUsers,
    readMessages: async () => currentMessages,
    appendMessage: async (item) => {
      currentMessages = [item, ...currentMessages];
      return currentMessages;
    },
    readNotificationSounds: async () => ({ sounds: {}, updatedAt: "" }),
    upsertUser: async () => currentUsers,
    _messages: () => currentMessages,
  };
}

const FULL_ID = "a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6";
const CODE = "A1B2-C3D4";

async function main() {
  const store = makeStore(
    [{ id: FULL_ID, name: "أحمد", code: CODE, profile: "student" }],
    []
  );

  const adminHandler = loadMocked("api/admin/messages.js", {
    "../../lib/messaging-store": store,
    "../../lib/admin-auth": {
      isAdmin: () => true,
      parseBody: (body) => body || {},
      json: (res, code, value) => res.status(code).json(value),
    },
  });

  let res = makeRes();
  await adminHandler({
    method: "POST",
    query: {},
    body: { title: "رسالة خاصة", body: "مرحبا أحمد", targetRef: CODE },
  }, res);
  assert.equal(res.statusCode, 200, JSON.stringify(res.body));
  const published = store._messages()[0];
  assert.equal(published.targetUserId, FULL_ID, "code must resolve to the full install id");
  assert.equal(published.targetCode, CODE, "short code stays only as a dashboard label");
  console.log("✓ admin resolves the short code to the full install id");

  res = makeRes();
  await adminHandler({
    method: "POST",
    query: {},
    body: { title: "رسالة", body: "نص", targetRef: "ZZZZ-ZZZZ" },
  }, res);
  assert.equal(res.statusCode, 404, JSON.stringify(res.body));
  assert.equal(res.body.error, "target_user_not_found");
  console.log("✓ an unregistered short code is rejected");

  const messagesHandler = loadMocked("api/messages.js", {
    "../lib/messaging-store": store,
    "../lib/admin-auth": { parseBody: (body) => body || {} },
  });

  const feed = async (uid) => {
    const response = makeRes();
    await messagesHandler({ method: "GET", query: { uid, profile: "student", version: "37" } }, response);
    return response;
  };

  const mine = await feed(FULL_ID);
  assert.equal(mine.statusCode, 200);
  assert.equal(mine.body.messages.length, 1, "the target install must see the message");
  assert.equal(mine.headers["Cache-Control"], "private, no-store");
  console.log("✓ only the exact install id sees the message; response is private and uncached");

  for (const [label, uid] of [
    ["code as uid", CODE.toLowerCase()],
    ["code without dash", "A1B2C3D4".toLowerCase()],
    ["prefix of the id", FULL_ID.slice(0, 8)],
    ["different install", "99999999999999999999999999999999"],
    ["no uid", ""],
  ]) {
    const response = await feed(uid);
    assert.equal(response.body.messages.length, 0, `${label} must not see the targeted message`);
  }
  console.log("✓ code-as-uid, prefixes, other installs and anonymous callers are excluded");

  store._messages().push({
    id: "m-broadcast", title: "إعلان", body: "للجميع", audienceProfile: "", minVersionCode: 0,
  });
  assert.equal((await feed(FULL_ID)).body.messages.length, 2, "target install also sees broadcasts");
  const anonymous = await feed("");
  assert.equal(anonymous.body.messages.length, 1, "anonymous callers still see broadcasts");
  assert.equal(anonymous.headers["Cache-Control"], "public, s-maxage=60, stale-while-revalidate=600");
  console.log("✓ broadcasts stay visible and publicly cacheable");

  console.log("ALL_TARGETING_TESTS_PASSED");
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
