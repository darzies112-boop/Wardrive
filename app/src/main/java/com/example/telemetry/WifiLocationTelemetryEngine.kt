package com.example.telemetry

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import com.example.data.model.DiscoveredNetwork
import com.example.data.model.GeoMath
import com.example.data.model.GpsFix
import com.example.data.model.SecurityType
import com.example.data.model.WifiBand
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.random.Random

class WifiLocationTelemetryEngine(private val context: Context) {

  private val appContext = context.applicationContext
  private val wifiManager = appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
  private val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

  private var wifiReceiverRegistered = false
  private var locationUpdatesRegistered = false

  private var onGpsUpdateCallback: ((GpsFix) -> Unit)? = null
  private var onWifiScanCallback: ((List<RawApReading>) -> Unit)? = null

  data class RawApReading(
    val bssid: String,
    val ssid: String,
    val capabilities: String,
    val frequencyMhz: Int,
    val channelWidthMhz: Int,
    val rssiDbm: Int,
    val vendor: String,
    val approxLat: Double? = null,
    val approxLon: Double? = null
  )

  private val wifiScanReceiver = object : BroadcastReceiver() {
    override fun onReceive(ctx: Context?, intent: Intent?) {
      if (intent?.action == WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) {
        readHardwareScanResults()
      }
    }
  }

  private val locationListener = object : LocationListener {
    override fun onLocationChanged(location: Location) {
      onGpsUpdateCallback?.invoke(
        GpsFix(
          latitude = location.latitude,
          longitude = location.longitude,
          altitude = if (location.hasAltitude()) location.altitude else 18.0,
          accuracyMeters = if (location.hasAccuracy()) location.accuracy else 6.0f,
          speedMps = if (location.hasSpeed()) location.speed else 0.0f,
          bearingDegrees = if (location.hasBearing()) location.bearing else 0.0f,
          provider = location.provider ?: "GPS",
          hasLock = true,
          timestampMs = System.currentTimeMillis()
        )
      )
    }
  }

  @SuppressLint("MissingPermission")
  fun startHardwareTelemetry(
    onGpsUpdate: (GpsFix) -> Unit,
    onWifiScan: (List<RawApReading>) -> Unit
  ) {
    this.onGpsUpdateCallback = onGpsUpdate
    this.onWifiScanCallback = onWifiScan

    // Register Wi-Fi broadcast receiver
    if (!wifiReceiverRegistered) {
      try {
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          appContext.registerReceiver(wifiScanReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
          appContext.registerReceiver(wifiScanReceiver, filter)
        }
        wifiReceiverRegistered = true
      } catch (_: Exception) {
      }
    }

    // Register Location listeners
    if (!locationUpdatesRegistered && locationManager != null) {
      try {
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        for (provider in providers) {
          if (locationManager.isProviderEnabled(provider)) {
            locationManager.requestLocationUpdates(provider, 1500L, 1.0f, locationListener)
            val lastKnown = locationManager.getLastKnownLocation(provider)
            if (lastKnown != null) {
              locationListener.onLocationChanged(lastKnown)
            }
          }
        }
        locationUpdatesRegistered = true
      } catch (_: SecurityException) {
      } catch (_: Exception) {
      }
    }

    triggerHardwareScan()
  }

  @SuppressLint("MissingPermission")
  fun triggerHardwareScan() {
    try {
      @Suppress("DEPRECATION")
      wifiManager?.startScan()
      readHardwareScanResults()
    } catch (_: SecurityException) {
    } catch (_: Exception) {
    }
  }

  @SuppressLint("MissingPermission")
  private fun readHardwareScanResults() {
    try {
      val results: List<ScanResult> = wifiManager?.scanResults ?: emptyList()
      if (results.isNotEmpty()) {
        val mapped = results.mapNotNull { sr ->
          val bssid = sr.BSSID ?: return@mapNotNull null
          @Suppress("DEPRECATION")
          val rawSsid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            sr.wifiSsid?.toString()?.trim('"') ?: sr.SSID
          } else {
            sr.SSID
          }
          val cleanSsid = if (rawSsid.isNullOrBlank()) "<Hidden SSID>" else rawSsid
          val widthMhz = when (sr.channelWidth) {
            ScanResult.CHANNEL_WIDTH_40MHZ -> 40
            ScanResult.CHANNEL_WIDTH_80MHZ, ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> 80
            ScanResult.CHANNEL_WIDTH_160MHZ -> 160
            else -> 20
          }
          RawApReading(
            bssid = bssid.uppercase(),
            ssid = cleanSsid,
            capabilities = sr.capabilities ?: "[ESS]",
            frequencyMhz = sr.frequency,
            channelWidthMhz = widthMhz,
            rssiDbm = sr.level.coerceIn(-100, -25),
            vendor = resolveVendorFromMac(bssid)
          )
        }
        onWifiScanCallback?.invoke(mapped)
      }
    } catch (_: SecurityException) {
    } catch (_: Exception) {
    }
  }

  fun stopHardwareTelemetry() {
    if (wifiReceiverRegistered) {
      try {
        appContext.unregisterReceiver(wifiScanReceiver)
      } catch (_: Exception) {
      }
      wifiReceiverRegistered = false
    }
    if (locationUpdatesRegistered) {
      try {
        locationManager?.removeUpdates(locationListener)
      } catch (_: Exception) {
      }
      locationUpdatesRegistered = false
    }
  }

  // Field Simulation emitter for testing wardriving trajectories in emulators or indoor benches
  private data class SimEmitter(
    val bssid: String,
    val ssid: String,
    val capabilities: String,
    val frequencyMhz: Int,
    val channelWidthMhz: Int,
    val txPowerDbm: Int,
    val latOffsetMeters: Double,
    val lonOffsetMeters: Double,
    val vendor: String
  )

  private val fieldSimEmitters = listOf(
    SimEmitter("00:1A:2B:44:81:01", "AetherCorp-WPA3-Core", "[WPA3-SAE-CCMP][ESS]", 5180, 80, -34, 28.0, 15.0, "Cisco Systems"),
    SimEmitter("F0:9F:C2:91:3A:12", "UniFi-Mesh-North-5G", "[WPA2-PSK-CCMP][RSN-PSK-CCMP][ESS]", 5220, 80, -38, -42.0, 35.0, "Ubiquiti Inc"),
    SimEmitter("24:5E:BE:10:C4:88", "Free_City_Plaza_WiFi", "[ESS]", 2412, 20, -40, 55.0, -28.0, "Ruckus Wireless"),
    SimEmitter("70:3A:CB:55:E1:09", "TriBand-6E-Backhaul", "[WPA3-SAE-CCMP][ESS]", 5975, 160, -36, -18.0, -48.0, "Aruba / HPE"),
    SimEmitter("B4:F1:DA:08:77:4C", "Legacy_Industrial_PLC", "[WEP][ESS]", 2437, 20, -44, 85.0, 62.0, "Siemens AG"),
    SimEmitter("DC:A6:32:9E:11:04", "Telemetry_Sensor_Node", "[WPA2-PSK-CCMP][ESS]", 2462, 20, -42, -75.0, -30.0, "Raspberry Pi"),
    SimEmitter("44:D9:E7:33:A0:5B", "Metro_Transit_Hotspot", "[OWE][ESS]", 5745, 40, -39, 15.0, 95.0, "Ubiquiti Inc"),
    SimEmitter("9C:5C:8E:72:19:F0", "ASUS-ROG-Rapture-6G", "[WPA3-SAE-CCMP][ESS]", 6135, 160, -37, -95.0, 75.0, "ASUSTek Computer"),
    SimEmitter("18:E8:29:B4:02:6D", "Harbor_Logistics_AP2", "[WPA2-PSK-CCMP][ESS]", 5500, 40, -41, 110.0, -85.0, "MikroTik"),
    SimEmitter("C8:6C:87:41:90:2E", "Coffee_Guest_Open", "[ESS]", 2412, 20, -43, -32.0, 115.0, "TP-Link Corp"),
    SimEmitter("E4:5F:01:8A:3C:71", "VectorLab_5G_Ch149", "[WPA3-SAE-CCMP][ESS]", 5745, 80, -36, 68.0, 118.0, "Netgear"),
    SimEmitter("60:32:B1:14:5D:99", "IoT_SmartGrid_Gateway", "[WPA2-PSK-CCMP][ESS]", 2437, 20, -45, -118.0, -92.0, "Espressif Inc")
  )

  fun generateFieldSimStep(
    anchorLat: Double,
    anchorLon: Double,
    stepIndex: Int
  ): Pair<GpsFix, List<RawApReading>> {
    // Move along a smooth figure-8 wardriving patrol loop (~130m radius)
    val angle = stepIndex * 0.14
    val metersNorth = 95.0 * sin(angle)
    val metersEast = 125.0 * sin(angle * 0.5) * cos(angle * 0.5)

    val dLat = metersNorth / 111320.0
    val dLon = metersEast / (111320.0 * cos(Math.toRadians(anchorLat)))
    val currentLat = anchorLat + dLat
    val currentLon = anchorLon + dLon

    val nextAngle = (stepIndex + 1) * 0.14
    val nextLat = anchorLat + (95.0 * sin(nextAngle)) / 111320.0
    val nextLon = anchorLon + (125.0 * sin(nextAngle * 0.5) * cos(nextAngle * 0.5)) /
      (111320.0 * cos(Math.toRadians(anchorLat)))

    val bearing = GeoMath.bearingDegrees(currentLat, currentLon, nextLat, nextLon)
    val gpsFix = GpsFix(
      latitude = currentLat,
      longitude = currentLon,
      altitude = 18.5 + 3.0 * sin(angle * 0.3),
      accuracyMeters = 3.2f,
      speedMps = 4.8f, // ~17.3 km/h wardriving speed
      bearingDegrees = bearing,
      provider = "FIELD-SIM",
      hasLock = true,
      timestampMs = System.currentTimeMillis()
    )

    val readings = fieldSimEmitters.map { emitter ->
      val apLat = anchorLat + (emitter.latOffsetMeters / 111320.0)
      val apLon = anchorLon + (emitter.lonOffsetMeters / (111320.0 * cos(Math.toRadians(anchorLat))))
      val distMeters = GeoMath.haversineDistanceMeters(currentLat, currentLon, apLat, apLon).coerceAtLeast(2.5)
      // Log-distance path loss with slight multipath jitter
      val pathLoss = 22.0 * log10(distMeters)
      val jitter = Random.nextInt(-2, 3)
      val rssi = (emitter.txPowerDbm - pathLoss + jitter).toInt().coerceIn(-96, -32)

      RawApReading(
        bssid = emitter.bssid,
        ssid = emitter.ssid,
        capabilities = emitter.capabilities,
        frequencyMhz = emitter.frequencyMhz,
        channelWidthMhz = emitter.channelWidthMhz,
        rssiDbm = rssi,
        vendor = emitter.vendor,
        approxLat = apLat,
        approxLon = apLon
      )
    }

    return gpsFix to readings
  }

  companion object {
    private val ouiVendorMap = mapOf(
      "00:1A:2B" to "Cisco Systems",
      "F0:9F:C2" to "Ubiquiti Inc",
      "44:D9:E7" to "Ubiquiti Inc",
      "18:E8:29" to "MikroTik",
      "24:5E:BE" to "Ruckus Wireless",
      "70:3A:CB" to "Aruba / HPE",
      "B4:F1:DA" to "Siemens AG",
      "DC:A6:32" to "Raspberry Pi",
      "E4:5F:01" to "Netgear",
      "9C:5C:8E" to "ASUSTek Computer",
      "C8:6C:87" to "TP-Link Corp",
      "60:32:B1" to "Espressif Inc",
      "00:14:22" to "Dell Inc",
      "3C:5A:B4" to "Google Inc",
      "A4:83:E7" to "Apple Inc"
    )

    fun resolveVendorFromMac(bssid: String): String {
      val prefix = bssid.uppercase().take(8)
      return ouiVendorMap[prefix] ?: "IEEE 802.11 Vendor"
    }

    fun mergeReadingIntoNetwork(
      existing: DiscoveredNetwork?,
      reading: RawApReading,
      gpsFix: GpsFix,
      userId: String
    ): DiscoveredNetwork {
      val band = WifiBand.fromFrequency(reading.frequencyMhz)
      val channel = WifiBand.frequencyToChannel(reading.frequencyMhz)
      val security = SecurityType.fromCapabilities(reading.capabilities)
      val targetLat = reading.approxLat ?: gpsFix.latitude
      val targetLon = reading.approxLon ?: gpsFix.longitude

      if (existing == null) {
        return DiscoveredNetwork(
          id = reading.bssid.replace(":", "_"),
          userId = userId,
          bssid = reading.bssid,
          ssid = reading.ssid,
          capabilities = reading.capabilities,
          securityType = security.name,
          frequencyMhz = reading.frequencyMhz.toLong(),
          band = band.label,
          channel = channel.toLong(),
          channelWidthMhz = reading.channelWidthMhz.toLong(),
          currentRssi = reading.rssiDbm.toLong(),
          bestRssi = reading.rssiDbm.toLong(),
          bestLat = targetLat,
          bestLon = targetLon,
          bestAlt = (gpsFix.altitude * 10.0).roundToLong() / 10.0,
          accuracyMeters = (gpsFix.accuracyMeters * 10.0).roundToLong() / 10.0,
          vendor = reading.vendor,
          seenCount = 1L
        )
      } else {
        val isNewPeak = reading.rssiDbm >= existing.bestRssi
        return existing.copy(
          ssid = reading.ssid.ifBlank { existing.ssid },
          capabilities = reading.capabilities,
          securityType = security.name,
          frequencyMhz = reading.frequencyMhz.toLong(),
          band = band.label,
          channel = channel.toLong(),
          channelWidthMhz = reading.channelWidthMhz.toLong(),
          currentRssi = reading.rssiDbm.toLong(),
          bestRssi = maxOf(existing.bestRssi, reading.rssiDbm.toLong()),
          bestLat = if (isNewPeak) targetLat else existing.bestLat,
          bestLon = if (isNewPeak) targetLon else existing.bestLon,
          bestAlt = if (isNewPeak) ((gpsFix.altitude * 10.0).roundToLong() / 10.0) else existing.bestAlt,
          accuracyMeters = if (isNewPeak) ((gpsFix.accuracyMeters * 10.0).roundToLong() / 10.0) else existing.accuracyMeters,
          vendor = reading.vendor,
          seenCount = existing.seenCount + 1L
        )
      }
    }
  }
}
