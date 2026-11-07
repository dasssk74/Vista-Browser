const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or create bookmarks", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("bookmarks")
      .where("userId", "==", ALICE_UID)
      .get()
  );
});

test("Authenticated user can create, read, update, and delete their own bookmark", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("bookmarks")
    .doc("bm_1");

  const now = new Date();
  await assertSucceeds(
    docRef.set({
      userId: ALICE_UID,
      title: "DuckDuckGo Privacy Search",
      url: "https://duckduckgo.com",
      folder: "Privacy",
      faviconUrl: "https://www.google.com/s2/favicons?domain=duckduckgo.com&sz=128",
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(docRef.get());
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("bookmarks")
      .where("userId", "==", ALICE_UID)
      .get()
  );

  await assertSucceeds(
    docRef.update({
      title: "DuckDuckGo Search Engine",
      updatedAt: new Date(),
    })
  );

  await assertSucceeds(docRef.delete());
});

test("Cross-user access to bookmarks and shortcuts is rejected", async () => {
  const now = new Date();
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("users")
      .doc(ALICE_UID)
      .collection("bookmarks")
      .doc("bm_alice")
      .set({
        userId: ALICE_UID,
        title: "Alice Private Site",
        url: "https://example.com",
        folder: "General",
        faviconUrl: "",
        createdAt: now,
        updatedAt: now,
      });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("bookmarks")
      .doc("bm_alice")
      .get()
  );
});

test("Shadow update with ghost field on shortcuts is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("shortcuts")
    .doc("sc_1");

  const now = new Date();
  await assertSucceeds(
    docRef.set({
      userId: ALICE_UID,
      name: "GitHub",
      url: "https://github.com",
      logoUrl: "https://www.google.com/s2/favicons?domain=github.com&sz=128",
      isPinned: true,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertFails(
    docRef.update({
      name: "GitHub Hacked",
      isAdmin: true,
      updatedAt: new Date(),
    })
  );
});
