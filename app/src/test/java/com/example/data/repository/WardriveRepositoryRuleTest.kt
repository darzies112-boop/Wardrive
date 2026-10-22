package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.DiscoveredNetwork
import com.example.data.model.WardriveSession
import com.google.firebase.firestore.FirebaseFirestoreException
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class WardriveRepositoryRuleTest : FirestoreEmulatorTestBase() {

  @Test
  fun upsertNetwork_andObserveNetworks_authenticatedOwner_succeeds() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val repository = WardriveRepository(firestore, auth)
    val uniqueMac = "00:1A:2B:${UUID.randomUUID().toString().take(2).uppercase()}:4D:5E"
    val network = DiscoveredNetwork(
      userId = aliceUid,
      bssid = uniqueMac,
      ssid = "AetherPulse_Test_AP",
      capabilities = "[WPA3-SAE-CCMP][ESS]",
      securityType = "WPA3",
      frequencyMhz = 5180L,
      band = "5 GHz",
      channel = 36L,
      channelWidthMhz = 80L,
      currentRssi = -52L,
      bestRssi = -49L,
      bestLat = 37.7749,
      bestLon = -122.4194,
      bestAlt = 19.0,
      accuracyMeters = 3.5,
      vendor = "Ubiquiti Inc",
      seenCount = 1L
    )

    val result = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.upsertDiscoveredNetwork(network)
    }
    assertTrue("Expected upsertDiscoveredNetwork to succeed: ${result.exceptionOrNull()}", result.isSuccess)
    val docId = result.getOrThrow()

    val observed = withTimeout(FLOW_TIMEOUT_MS) {
      repository.observeUserNetworks(aliceUid).first { list -> list.any { it.id == docId } }
    }
    assertTrue(observed.any { it.id == docId && it.ssid == "AetherPulse_Test_AP" })
  }

  @Test
  fun createSession_andObserveSessions_authenticatedOwner_succeeds() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val repository = WardriveRepository(firestore, auth)
    val session = WardriveSession(
      id = "sess_${UUID.randomUUID().toString().replace("-", "_")}",
      userId = aliceUid,
      title = "Telemetry Run Alpha",
      durationSeconds = 920L,
      distanceMeters = 2400.0,
      totalNetworks = 19L,
      openNetworks = 2L,
      band24Count = 8L,
      band5Count = 9L,
      band6Count = 2L,
      startLat = 37.7749,
      startLon = -122.4194,
      endLat = 37.7810,
      endLon = -122.4110,
      trackPointsSummary = "37.7749,-122.4194,15.0,-52;37.7810,-122.4110,16.5,-60"
    )

    val createRes = withTimeout(DEFAULT_TIMEOUT_MS) {
      repository.createSession(session)
    }
    assertTrue("Expected createSession to succeed: ${createRes.exceptionOrNull()}", createRes.isSuccess)
    val sessionId = createRes.getOrThrow()

    val observed = withTimeout(FLOW_TIMEOUT_MS) {
      repository.observeUserSessions(aliceUid).first { list -> list.any { it.id == sessionId } }
    }
    assertTrue(observed.any { it.id == sessionId && it.title == "Telemetry Run Alpha" })
  }

  @Test
  fun getNetworkById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
    val aliceUid = signInTestUser(ALICE_EMAIL)
    val aliceRepo = WardriveRepository(firestore, auth)
    val docId = withTimeout(DEFAULT_TIMEOUT_MS) {
      aliceRepo.upsertDiscoveredNetwork(
        DiscoveredNetwork(
          id = "net_${UUID.randomUUID().toString().replace("-", "_")}",
          userId = aliceUid,
          bssid = "AA:BB:CC:DD:EE:01",
          ssid = "Alice_Private_AP"
        )
      ).getOrThrow()
    }

    signInTestUser(BOB_EMAIL)
    val bobRepo = WardriveRepository(firestore, auth)
    val crossReadResult = withTimeout(DEFAULT_TIMEOUT_MS) {
      bobRepo.getNetworkById(aliceUid, docId)
    }
    assertTrue(crossReadResult.isFailure)
    val ex = crossReadResult.exceptionOrNull() as? FirebaseFirestoreException
    assertNotNull(ex)
    assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ex?.code)
  }

  @Test
  fun observeNetworks_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
    auth.signOut()
    val repository = WardriveRepository(firestore, auth)
    try {
      withTimeout(FLOW_TIMEOUT_MS) {
        repository.observeUserNetworks("unauth_user_123").first()
      }
      fail("Expected FirebaseFirestoreException PERMISSION_DENIED for unauthenticated caller")
    } catch (e: Exception) {
      val firestoreEx = (e as? FirebaseFirestoreException)
        ?: (e.cause as? FirebaseFirestoreException)
        ?: (e.cause?.cause as? FirebaseFirestoreException)
      assertNotNull("Expected FirebaseFirestoreException in cause chain, got: $e", firestoreEx)
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreEx?.code)
    }
  }

  private companion object {
    const val ALICE_EMAIL = "alice.wardrive@test.com"
    const val BOB_EMAIL = "bob.wardrive@test.com"
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 3000L
  }
}
