package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

enum class SecurityType(val label: String, val isVulnerableOrOpen: Boolean) {
  OPEN("OPEN", true),
  WEP("WEP", true),
  OWE("OWE", false),
  WPA("WPA", true),
  WPA2("WPA2", false),
  WPA3("WPA3", false);

  companion object {
    fun fromCapabilities(capabilities: String): SecurityType {
      val upper = capabilities.uppercase()
      return when {
        upper.contains("SAE") || upper.contains("WPA3") -> WPA3
        upper.contains("OWE") -> OWE
        upper.contains("WPA2") || upper.contains("RSN") -> WPA2
        upper.contains("WPA") -> WPA
        upper.contains("WEP") -> WEP
        else -> OPEN
      }
    }
  }
}

enum class WifiBand(val label: String) {
  BAND_24("2.4 GHz"),
  BAND_5("5 GHz"),
  BAND_6("6 GHz");

  companion object {
    fun fromFrequency(freqMhz: Int): WifiBand = when {
      freqMhz in 2400..2500 -> BAND_24
      freqMhz in 5925..7125 -> BAND_6
      else -> BAND_5
    }

    fun frequencyToChannel(freqMhz: Int): Int = when {
      freqMhz == 2484 -> 14
      freqMhz in 2412..2472 -> (freqMhz - 2407) / 5
      freqMhz in 5170..5895 -> (freqMhz - 5000) / 5
      freqMhz in 5955..7115 -> (freqMhz - 5950) / 5
      else -> 1
    }
  }
}

@IgnoreExtraProperties
data class DiscoveredNetwork(
  @DocumentId val id: String = "",
  val userId: String = "",
  val bssid: String = "",
  val ssid: String = "",
  val capabilities: String = "",
  val securityType: String = "WPA2",
  val frequencyMhz: Long = 2412L,
  val band: String = "2.4 GHz",
  val channel: Long = 1L,
  val channelWidthMhz: Long = 20L,
  val currentRssi: Long = -70L,
  val bestRssi: Long = -70L,
  val bestLat: Double = 37.7749,
  val bestLon: Double = -122.4194,
  val bestAlt: Double = 15.0,
  val accuracyMeters: Double = 5.0,
  val vendor: String = "Unknown OUI",
  val seenCount: Long = 1L,
  val notes: String? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
) {
  fun sanitizedDocumentId(): String =
    id.ifBlank { bssid.replace(":", "_").replace(Regex("[^a-zA-Z0-9_\\-]"), "_") }

  fun parsedSecurity(): SecurityType =
    SecurityType.entries.firstOrNull { it.name == securityType }
      ?: SecurityType.fromCapabilities(capabilities)
}

@IgnoreExtraProperties
data class WardriveSession(
  @DocumentId val id: String = "",
  val userId: String = "",
  val title: String = "",
  val durationSeconds: Long = 0L,
  val distanceMeters: Double = 0.0,
  val totalNetworks: Long = 0L,
  val openNetworks: Long = 0L,
  val band24Count: Long = 0L,
  val band5Count: Long = 0L,
  val band6Count: Long = 0L,
  val startLat: Double = 37.7749,
  val startLon: Double = -122.4194,
  val endLat: Double = 37.7749,
  val endLon: Double = -122.4194,
  val trackPointsSummary: String = "",
  val notes: String? = null,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
) {
  fun parseTrackBreadcrumbs(): List<TrackBreadcrumb> {
    if (trackPointsSummary.isBlank()) return emptyList()
    return trackPointsSummary.split(";").mapNotNull { token ->
      val parts = token.split(",")
      if (parts.size >= 4) {
        val lat = parts[0].toDoubleOrNull() ?: return@mapNotNull null
        val lon = parts[1].toDoubleOrNull() ?: return@mapNotNull null
        val alt = parts[2].toDoubleOrNull() ?: 0.0
        val rssi = parts[3].toIntOrNull() ?: -75
        TrackBreadcrumb(lat = lat, lon = lon, altitude = alt, peakRssi = rssi)
      } else null
    }
  }
}

data class GpsFix(
  val latitude: Double = 37.7749,
  val longitude: Double = -122.4194,
  val altitude: Double = 18.0,
  val accuracyMeters: Float = 4.5f,
  val speedMps: Float = 0.0f,
  val bearingDegrees: Float = 0.0f,
  val provider: String = "GPS",
  val hasLock: Boolean = false,
  val timestampMs: Long = System.currentTimeMillis()
)

data class TrackBreadcrumb(
  val lat: Double,
  val lon: Double,
  val altitude: Double,
  val peakRssi: Int,
  val timestampMs: Long = System.currentTimeMillis()
)

enum class MapDisplayMode(val label: String) {
  GPS_GRID("GPS Grid"),
  RF_RADAR("RF Radar"),
  SPLIT_HUD("Split HUD")
}

enum class WardriveTab(val label: String) {
  MAP_RADAR("Map / Radar"),
  NETWORKS("Networks"),
  SPECTRUM("Spectrum"),
  SESSIONS("Sessions")
}

enum class NetworkFilter(val label: String) {
  ALL("All APs"),
  OPEN_VULNERABLE("Open / WEP"),
  WPA3_ONLY("WPA3"),
  BAND_24("2.4 GHz"),
  BAND_5("5 GHz"),
  BAND_6("6 GHz"),
  STRONG_SIGNAL("Strong > -65 dBm")
}

object GeoMath {
  private const val EARTH_RADIUS_METERS = 6371000.0

  fun haversineDistanceMeters(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double
  ): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2).pow(2.0) +
      cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return EARTH_RADIUS_METERS * c
  }

  fun bearingDegrees(
    lat1: Double,
    lon1: Double,
    lat2: Double,
    lon2: Double
  ): Float {
    val phi1 = Math.toRadians(lat1)
    val phi2 = Math.toRadians(lat2)
    val deltaLon = Math.toRadians(lon2 - lon1)
    val y = sin(deltaLon) * cos(phi2)
    val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLon)
    val theta = Math.toDegrees(atan2(y, x))
    return ((theta + 360.0) % 360.0).toFloat()
  }
}
