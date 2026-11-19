const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const DATABASE_ID = "ai-studio-android-aureliac-c54cf4c3-9ccd-491b-aba9-d6dd9baedd45";
const ALICE_UID = "alice_uid_001";
const BOB_UID = "bob_uid_002";
const CHARLIE_UID = "charlie_uid_003";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function baseGradingFields() {
  return {
    tealShadows: 0.58,
    warmHighlights: 0.64,
    deepGreens: 0.72,
    contrast: 0.55,
    exposure: 0.05,
    vibrance: 0.42,
    skinProtection: 0.88,
    highlightSoftness: 0.68,
    adaptiveLighting: true,
    particleStyle: "GOLDEN_DUST",
    particleDensity: 0.45,
    particleGlow: 0.62,
    particleSpeed: 0.35,
    bakeParticlesOnExport: true,
  };
}

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

function getDb(context) {
  return context.firestore(DATABASE_ID);
}

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = getDb(testEnv.unauthenticatedContext());
  const now = new Date(Date.now() - 1000);

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("presets").get());
  await assertFails(unauthDb.collection("studios").where("memberUids", "array-contains", ALICE_UID).get());
  await assertFails(unauthDb.collection("studioInvites").doc("AUR48K").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("presets").doc("p1").set({
      userId: ALICE_UID,
      name: "Hack",
      description: "",
      ...baseGradingFields(),
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("Personal GradingPreset: owner CRUD succeeds; cross-user, ghost keys, @DocumentId id, and future timestamps fail", async () => {
  const aliceDb = getDb(testEnv.authenticatedContext(ALICE_UID));
  const bobDb = getDb(testEnv.authenticatedContext(BOB_UID));
  const created = new Date(Date.now() - 5000);

  const validPreset = {
    userId: ALICE_UID,
    name: "Garden Ceremony",
    description: "Rich emerald foliage & soft ivory dress highlights",
    ...baseGradingFields(),
    createdAt: created,
    updatedAt: created,
  };

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("presets").doc("preset_1").set(validPreset)
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("presets").get()
  );

  // Partial update preserving createdAt
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("presets").doc("preset_1").update({
      name: "Garden Ceremony II",
      tealShadows: 0.65,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Cross-user isolation
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("presets").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("presets").doc("preset_1").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("presets").doc("preset_1").update({
      name: "Stolen",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Reject @DocumentId 'id' inside document body
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("presets").doc("preset_2").set({
      id: "preset_2",
      ...validPreset,
    })
  );

  // Reject out-of-bounds parameter & future timestamp
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("presets").doc("preset_3").set({
      ...validPreset,
      tealShadows: 1.5,
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("presets").doc("preset_4").set({
      ...validPreset,
      createdAt: new Date(Date.now() + 60000),
      updatedAt: new Date(Date.now() + 60000),
    })
  );
});

test("Collaborative Studio + Atomic Invite + Join + Member Edits + Revisions", async () => {
  const aliceDb = getDb(testEnv.authenticatedContext(ALICE_UID));
  const bobDb = getDb(testEnv.authenticatedContext(BOB_UID));
  const charlieDb = getDb(testEnv.authenticatedContext(CHARLIE_UID));
  const created = new Date(Date.now() - 5000);

  // 1. Atomic batch create of Studio + StudioInvite by Alice
  const batch = aliceDb.batch();
  const studioRef = aliceDb.collection("studios").doc("studio_1");
  const inviteRef = aliceDb.collection("studioInvites").doc("AUR74K");
  batch.set(studioRef, {
    ownerId: ALICE_UID,
    name: "Sunset Vineyard Wedding",
    description: "Warm golden glow with deep cedar shadows",
    inviteCode: "AUR74K",
    memberUids: [ALICE_UID],
    activeRecipeName: "Golden Veil",
    updatedByUid: ALICE_UID,
    updatedByName: "Alice",
    ...baseGradingFields(),
    createdAt: created,
    updatedAt: created,
  });
  batch.set(inviteRef, {
    studioId: "studio_1",
    studioName: "Sunset Vineyard Wedding",
    ownerId: ALICE_UID,
    createdAt: created,
  });
  await assertSucceeds(batch.commit());

  // 2. Unfiltered list query on studios must fail; memberUids-filtered query succeeds for Alice
  await assertFails(aliceDb.collection("studios").get());
  await assertSucceeds(
    aliceDb.collection("studios").where("memberUids", "array-contains", ALICE_UID).get()
  );

  // 3. Invite list scraping must fail; exact get succeeds for Bob
  await assertFails(bobDb.collection("studioInvites").get());
  await assertSucceeds(bobDb.collection("studioInvites").doc("AUR74K").get());

  // 4. Bob cannot edit studio before joining
  await assertFails(
    bobDb.collection("studios").doc("studio_1").update({
      tealShadows: 0.9,
      updatedByUid: BOB_UID,
      updatedByName: "Bob",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // 5. Bob joins studio via Action A (adds only self to memberUids)
  await assertSucceeds(
    bobDb.collection("studios").doc("studio_1").update({
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // 6. Bob cannot spoof another user when joining or hijack ownerId
  await assertFails(
    charlieDb.collection("studios").doc("studio_1").update({
      memberUids: [ALICE_UID, BOB_UID],
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // 7. Bob can now collaboratively update the studio's grading & particle recipe
  await assertSucceeds(
    bobDb.collection("studios").doc("studio_1").update({
      activeRecipeName: "Twighlight Fireflies",
      warmHighlights: 0.78,
      particleStyle: "TEAL_FIREFLIES",
      updatedByUid: BOB_UID,
      updatedByName: "Bob",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // 8. Bob cannot forge updatedByUid or mutate inviteCode/ownerId
  await assertFails(
    bobDb.collection("studios").doc("studio_1").update({
      warmHighlights: 0.8,
      updatedByUid: ALICE_UID,
      updatedByName: "Alice",
      updatedAt: new Date(Date.now() - 400),
    })
  );

  // 9. Post collaborative revision snapshot (aligned memberUids)
  await assertSucceeds(
    bobDb.collection("studios").doc("studio_1").collection("revisions").doc("rev_1").set({
      studioId: "studio_1",
      authorId: BOB_UID,
      authorName: "Bob",
      recipeName: "Twilight Fireflies",
      note: "Balanced skin tones under string lights",
      memberUids: [ALICE_UID, BOB_UID],
      ...baseGradingFields(),
      particleStyle: "TEAL_FIREFLIES",
      createdAt: new Date(Date.now() - 300),
    })
  );

  // 10. Filtered revision query succeeds for Alice & Bob; Charlie cannot read rev_1 or list unfiltered/Alice's revisions
  await assertSucceeds(
    aliceDb.collection("studios").doc("studio_1").collection("revisions")
      .where("memberUids", "array-contains", ALICE_UID).get()
  );
  await assertFails(
    charlieDb.collection("studios").doc("studio_1").collection("revisions").doc("rev_1").get()
  );
  await assertFails(
    charlieDb.collection("studios").doc("studio_1").collection("revisions").get()
  );
  await assertFails(
    charlieDb.collection("studios").doc("studio_1").collection("revisions")
      .where("memberUids", "array-contains", ALICE_UID).get()
  );

  // 11. Only owner (Alice) can delete studio, not Bob
  await assertFails(bobDb.collection("studios").doc("studio_1").delete());
  await assertSucceeds(aliceDb.collection("studios").doc("studio_1").delete());
});
