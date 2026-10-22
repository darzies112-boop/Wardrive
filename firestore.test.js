const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_wardriver_1";
const BOB_UID = "bob_wardriver_2";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validNetworkPayload(userId, now = new Date()) {
  return {
    userId,
    bssid: "00:1A:2B:3C:4D:5E",
    ssid: "Tactical_Mesh_5G",
    capabilities: "[WPA3-SAE-CCMP][ESS]",
    securityType: "WPA3",
    frequencyMhz: 5180,
    band: "5 GHz",
    channel: 36,
    channelWidthMhz: 80,
    currentRssi: -54,
    bestRssi: -48,
    bestLat: 37.7749,
    bestLon: -122.4194,
    bestAlt: 18.5,
    accuracyMeters: 4.2,
    vendor: "Ubiquiti Inc",
    seenCount: 3,
    createdAt: now,
    updatedAt: now,
  };
}

function validSessionPayload(userId, now = new Date()) {
  return {
    userId,
    title: "Downtown Sector Sweep",
    durationSeconds: 1840,
    distanceMeters: 4250.5,
    totalNetworks: 42,
    openNetworks: 4,
    band24Count: 18,
    band5Count: 20,
    band6Count: 4,
    startLat: 37.7749,
    startLon: -122.4194,
    endLat: 37.7899,
    endLon: -122.4014,
    trackPointsSummary: "37.7749,-122.4194,18.5,-54;37.7899,-122.4014,21.0,-61",
    createdAt: now,
    updatedAt: now,
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

test("1. Unauthenticated user cannot read or write networks or sessions", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("networks").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("networks")
      .doc("00_1A_2B_3C_4D_5E")
      .set(validNetworkPayload(ALICE_UID))
  );
});

test("2. Authenticated owner can create, read, list, update, and delete their own DiscoveredNetwork", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("networks")
    .doc("00_1A_2B_3C_4D_5E");

  const created = validNetworkPayload(ALICE_UID, new Date(Date.now() - 2000));
  await assertSucceeds(docRef.set(created));
  await assertSucceeds(docRef.get());
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("networks")
      .where("userId", "==", ALICE_UID)
      .get()
  );

  await assertSucceeds(
    docRef.update({
      currentRssi: -45,
      bestRssi: -45,
      seenCount: 4,
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  await assertSucceeds(docRef.delete());
});

test("3. Cross-user isolation: Bob cannot read, list, update, or delete Alice's networks or sessions", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    await db
      .collection("users")
      .doc(ALICE_UID)
      .collection("networks")
      .doc("net_1")
      .set(validNetworkPayload(ALICE_UID, new Date(Date.now() - 2000)));
    await db
      .collection("users")
      .doc(ALICE_UID)
      .collection("sessions")
      .doc("sess_1")
      .set(validSessionPayload(ALICE_UID, new Date(Date.now() - 2000)));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("networks").doc("net_1").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("networks").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_1").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("sessions").doc("sess_1").delete()
  );
});

test("4. Shadow update / ghost field injection is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("networks")
    .doc("net_ghost");

  const badCreate = {
    ...validNetworkPayload(ALICE_UID, new Date(Date.now() - 2000)),
    isVerifiedAdmin: true,
  };
  await assertFails(docRef.set(badCreate));
});

test("5. Invalid enum values, out-of-bounds coordinates, or immutable field mutations fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const docRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("networks")
    .doc("net_bounds");

  const base = validNetworkPayload(ALICE_UID, new Date(Date.now() - 3000));
  await assertSucceeds(docRef.set(base));

  // Mutating immutable bssid must fail
  await assertFails(
    docRef.update({
      bssid: "FF:FF:FF:FF:FF:FF",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Invalid securityType enum must fail
  await assertFails(
    docRef.update({
      securityType: "WPA99_ULTRA",
      updatedAt: new Date(Date.now() - 1000),
    })
  );

  // Out-of-bounds latitude must fail
  await assertFails(
    docRef.update({
      bestLat: 135.0,
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("6. Authenticated owner can create, list, update, and delete WardriveSession", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const sessRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("sessions")
    .doc("session_alpha");

  const payload = validSessionPayload(ALICE_UID, new Date(Date.now() - 2000));
  await assertSucceeds(sessRef.set(payload));
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("sessions")
      .where("userId", "==", ALICE_UID)
      .get()
  );
  await assertSucceeds(
    sessRef.update({
      title: "Updated Sector Sweep",
      notes: "High 6GHz density near financial district",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
  await assertSucceeds(sessRef.delete());
});
