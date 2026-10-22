package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.DiscoveredNetwork
import com.example.data.model.WardriveSession
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class WardriveRepository(
  private val db: FirebaseFirestore,
  private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
  // Convenience constructor resolving the named enterprise database ID from firebase_applet_config.xml
  constructor(context: Context) : this(
    FirebaseFirestore.getInstance(
      context.applicationContext.getString(R.string.firestore_database_id)
    )
  )

  private fun requireUserId(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
  }

  fun observeUserNetworks(userId: String): Flow<List<DiscoveredNetwork>> {
    val path = "users/$userId/networks"
    return db.collection("users")
      .document(userId)
      .collection("networks")
      .whereEqualTo("userId", userId)
      .snapshots()
      .map { snapshot ->
        snapshot.toObjects(
          DiscoveredNetwork::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        ).sortedByDescending { it.bestRssi }
      }
      .catch { error ->
        if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
        throw error
      }
  }

  fun observeUserSessions(userId: String): Flow<List<WardriveSession>> {
    val path = "users/$userId/sessions"
    return db.collection("users")
      .document(userId)
      .collection("sessions")
      .whereEqualTo("userId", userId)
      .snapshots()
      .map { snapshot ->
        snapshot.toObjects(
          WardriveSession::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        ).sortedByDescending { it.createdAt?.seconds ?: 0L }
      }
      .catch { error ->
        if (error is Exception) handleFirestoreError(error, OperationType.LIST, path)
        throw error
      }
  }

  suspend fun upsertDiscoveredNetwork(network: DiscoveredNetwork): Result<String> {
    val uid = try {
      requireUserId()
    } catch (e: Exception) {
      return Result.failure(e)
    }
    val docId = network.sanitizedDocumentId()
    val docRef = db.collection("users").document(uid).collection("networks").document(docId)
    return try {
      val createPayload = mutableMapOf<String, Any>(
        "userId" to uid,
        "bssid" to network.bssid,
        "ssid" to network.ssid,
        "capabilities" to network.capabilities,
        "securityType" to network.securityType,
        "frequencyMhz" to network.frequencyMhz,
        "band" to network.band,
        "channel" to network.channel,
        "channelWidthMhz" to network.channelWidthMhz,
        "currentRssi" to network.currentRssi,
        "bestRssi" to network.bestRssi,
        "bestLat" to network.bestLat,
        "bestLon" to network.bestLon,
        "bestAlt" to network.bestAlt,
        "accuracyMeters" to network.accuracyMeters,
        "vendor" to network.vendor,
        "seenCount" to network.seenCount.coerceAtLeast(1L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
      )
      if (!network.notes.isNullOrBlank()) {
        createPayload["notes"] = network.notes
      }
      docRef.set(createPayload).await()
      Result.success(docId)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun getNetworkById(ownerUid: String, networkId: String): Result<DiscoveredNetwork?> {
    val docRef = db.collection("users").document(ownerUid).collection("networks").document(networkId)
    return try {
      val snapshot = docRef.get(com.google.firebase.firestore.Source.SERVER).await()
      Result.success(
        snapshot.toObject(
          DiscoveredNetwork::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        )
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.GET, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun deleteNetwork(networkId: String): Result<Unit> {
    val uid = try {
      requireUserId()
    } catch (e: Exception) {
      return Result.failure(e)
    }
    val docRef = db.collection("users").document(uid).collection("networks").document(networkId)
    return try {
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun createSession(session: WardriveSession): Result<String> {
    val uid = try {
      requireUserId()
    } catch (e: Exception) {
      return Result.failure(e)
    }
    val collectionRef = db.collection("users").document(uid).collection("sessions")
    val docRef = if (session.id.isNotBlank()) collectionRef.document(session.id) else collectionRef.document()
    return try {
      val payload = mutableMapOf<String, Any>(
        "userId" to uid,
        "title" to session.title.ifBlank { "Sector Sweep" }.take(120),
        "durationSeconds" to session.durationSeconds.coerceIn(0L, 604800L),
        "distanceMeters" to session.distanceMeters.coerceIn(0.0, 10000000.0),
        "totalNetworks" to session.totalNetworks.coerceAtLeast(0L),
        "openNetworks" to session.openNetworks.coerceAtLeast(0L),
        "band24Count" to session.band24Count.coerceAtLeast(0L),
        "band5Count" to session.band5Count.coerceAtLeast(0L),
        "band6Count" to session.band6Count.coerceAtLeast(0L),
        "startLat" to session.startLat.coerceIn(-90.0, 90.0),
        "startLon" to session.startLon.coerceIn(-180.0, 180.0),
        "endLat" to session.endLat.coerceIn(-90.0, 90.0),
        "endLon" to session.endLon.coerceIn(-180.0, 180.0),
        "trackPointsSummary" to session.trackPointsSummary.take(50000),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
      )
      if (!session.notes.isNullOrBlank()) {
        payload["notes"] = session.notes.take(500)
      }
      docRef.set(payload).await()
      Result.success(docRef.id)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      Result.failure(e)
    }
  }

  suspend fun deleteSession(sessionId: String): Result<Unit> {
    val uid = try {
      requireUserId()
    } catch (e: Exception) {
      return Result.failure(e)
    }
    val docRef = db.collection("users").document(uid).collection("sessions").document(sessionId)
    return try {
      docRef.delete().await()
      Result.success(Unit)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      Result.failure(e)
    }
  }
}
