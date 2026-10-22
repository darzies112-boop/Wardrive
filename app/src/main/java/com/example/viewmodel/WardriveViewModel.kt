package com.example.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.DiscoveredNetwork
import com.example.data.model.GeoMath
import com.example.data.model.GpsFix
import com.example.data.model.MapDisplayMode
import com.example.data.model.NetworkFilter
import com.example.data.model.SecurityType
import com.example.data.model.TrackBreadcrumb
import com.example.data.model.UiState
import com.example.data.model.WardriveSession
import com.example.data.model.WardriveTab
import com.example.data.model.WifiBand
import com.example.data.repository.WardriveRepository
import com.example.export.WardriveExporter
import com.example.telemetry.WifiLocationTelemetryEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class WardriveHudState(
  val activeTab: WardriveTab = WardriveTab.MAP_RADAR,
  val mapDisplayMode: MapDisplayMode = MapDisplayMode.SPLIT_HUD,
  val selectedBand: WifiBand = WifiBand.BAND_24,
  val activeFilter: NetworkFilter = NetworkFilter.ALL,
  val searchQuery: String = "",
  val isScanning: Boolean = true,
  val isFieldSimEnabled: Boolean = true,
  val isSessionRecording: Boolean = false,
  val sessionElapsedSeconds: Long = 0L,
  val sessionDistanceMeters: Double = 0.0,
  val gpsFix: GpsFix = GpsFix(),
  val liveNetworks: Map<String, DiscoveredNetwork> = emptyMap(),
  val rssiHistoryByBssid: Map<String, List<Int>> = emptyMap(),
  val trackBreadcrumbs: List<TrackBreadcrumb> = emptyList(),
  val selectedNetwork: DiscoveredNetwork? = null,
  val latestOpenNetworkAlert: DiscoveredNetwork? = null,
  val statusBannerMessage: String? = null,
  val isCloudSyncing: Boolean = false
)

class WardriveViewModel(
  private val repository: WardriveRepository,
  private val currentUserId: String,
  private val telemetryEngine: WifiLocationTelemetryEngine? = null
) : ViewModel() {

  private val _hudState = MutableStateFlow(WardriveHudState())
  val hudState: StateFlow<WardriveHudState> = _hudState.asStateFlow()

  val cloudNetworksState: StateFlow<UiState<List<DiscoveredNetwork>>> =
    repository.observeUserNetworks(currentUserId)
      .map<List<DiscoveredNetwork>, UiState<List<DiscoveredNetwork>>> { UiState.Success(it) }
      .catch { error ->
        Log.w(TAG, "Error observing cloud networks", error)
        emit(UiState.Error(error.message ?: "Failed to load cloud networks"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = UiState.Loading
      )

  val cloudSessionsState: StateFlow<UiState<List<WardriveSession>>> =
    repository.observeUserSessions(currentUserId)
      .map<List<WardriveSession>, UiState<List<WardriveSession>>> { UiState.Success(it) }
      .catch { error ->
        Log.w(TAG, "Error observing cloud sessions", error)
        emit(UiState.Error(error.message ?: "Failed to load cloud sessions"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = UiState.Loading
      )

  private var scanLoopJob: Job? = null
  private var sessionTimerJob: Job? = null
  private var simStepCounter = 0
  private val anchorLat = 37.7749
  private val anchorLon = -122.4194
  private val alertedOpenBssids = mutableSetOf<String>()

  init {
    startTelemetryLoop()
  }

  private fun startTelemetryLoop() {
    telemetryEngine?.startHardwareTelemetry(
      onGpsUpdate = { fix ->
        if (!_hudState.value.isFieldSimEnabled) {
          onNewGpsFix(fix)
        }
      },
      onWifiScan = { readings ->
        if (readings.isNotEmpty() && !_hudState.value.isFieldSimEnabled) {
          ingestRawReadings(readings, _hudState.value.gpsFix)
        }
      }
    )

    scanLoopJob?.cancel()
    scanLoopJob = viewModelScope.launch {
      while (isActive) {
        val state = _hudState.value
        if (state.isScanning) {
          if (state.isFieldSimEnabled && telemetryEngine != null) {
            val (simFix, simReadings) = telemetryEngine.generateFieldSimStep(
              anchorLat = anchorLat,
              anchorLon = anchorLon,
              stepIndex = simStepCounter++
            )
            onNewGpsFix(simFix)
            ingestRawReadings(simReadings, simFix)
          } else {
            telemetryEngine?.triggerHardwareScan()
          }
        }
        delay(2000L)
      }
    }
  }

  private fun onNewGpsFix(newFix: GpsFix) {
    _hudState.update { current ->
      val prevBreadcrumbs = current.trackBreadcrumbs
      val addedDistance = if (current.isSessionRecording && prevBreadcrumbs.isNotEmpty()) {
        val last = prevBreadcrumbs.last()
        val delta = GeoMath.haversineDistanceMeters(last.lat, last.lon, newFix.latitude, newFix.longitude)
        if (delta in 1.5..500.0) delta else 0.0
      } else 0.0

      val strongestRssi = current.liveNetworks.values.maxOfOrNull { it.currentRssi.toInt() } ?: -75
      val updatedBreadcrumbs = if (current.isSessionRecording || prevBreadcrumbs.size < 40) {
        (prevBreadcrumbs + TrackBreadcrumb(
          lat = newFix.latitude,
          lon = newFix.longitude,
          altitude = newFix.altitude,
          peakRssi = strongestRssi
        )).takeLast(180)
      } else {
        prevBreadcrumbs
      }

      current.copy(
        gpsFix = newFix,
        sessionDistanceMeters = current.sessionDistanceMeters + addedDistance,
        trackBreadcrumbs = updatedBreadcrumbs
      )
    }
  }

  private fun ingestRawReadings(
    readings: List<WifiLocationTelemetryEngine.RawApReading>,
    gpsFix: GpsFix
  ) {
    _hudState.update { current ->
      val mutableNetworks = current.liveNetworks.toMutableMap()
      val mutableHistory = current.rssiHistoryByBssid.toMutableMap()
      var newOpenAlert: DiscoveredNetwork? = current.latestOpenNetworkAlert

      for (reading in readings) {
        val prev = mutableNetworks[reading.bssid]
        val merged = WifiLocationTelemetryEngine.mergeReadingIntoNetwork(
          existing = prev,
          reading = reading,
          gpsFix = gpsFix,
          userId = currentUserId
        )
        mutableNetworks[reading.bssid] = merged

        val history = (mutableHistory[reading.bssid] ?: emptyList()) + reading.rssiDbm
        mutableHistory[reading.bssid] = history.takeLast(24)

        val sec = merged.parsedSecurity()
        if (sec.isVulnerableOrOpen && alertedOpenBssids.add(merged.bssid)) {
          newOpenAlert = merged
        }
      }

      val updatedSelected = current.selectedNetwork?.let { sel ->
        mutableNetworks[sel.bssid] ?: sel
      }

      current.copy(
        liveNetworks = mutableNetworks,
        rssiHistoryByBssid = mutableHistory,
        selectedNetwork = updatedSelected,
        latestOpenNetworkAlert = newOpenAlert
      )
    }
  }

  fun selectTab(tab: WardriveTab) {
    _hudState.update { it.copy(activeTab = tab) }
  }

  fun setMapDisplayMode(mode: MapDisplayMode) {
    _hudState.update { it.copy(mapDisplayMode = mode) }
  }

  fun selectBand(band: WifiBand) {
    _hudState.update { it.copy(selectedBand = band) }
  }

  fun setNetworkFilter(filter: NetworkFilter) {
    _hudState.update { it.copy(activeFilter = filter) }
  }

  fun setSearchQuery(query: String) {
    _hudState.update { it.copy(searchQuery = query) }
  }

  fun toggleScanning() {
    _hudState.update { it.copy(isScanning = !it.isScanning) }
  }

  fun toggleFieldSimMode(enabled: Boolean) {
    _hudState.update {
      it.copy(
        isFieldSimEnabled = enabled,
        statusBannerMessage = if (enabled) {
          "Field Simulation active: generating multi-band RF beacons & GPS trajectory"
        } else {
          "Live Hardware Sensor Mode active: reading device WifiManager & GPS"
        }
      )
    }
    if (!enabled) {
      telemetryEngine?.triggerHardwareScan()
    }
  }

  fun selectNetworkForInspection(network: DiscoveredNetwork?) {
    _hudState.update { it.copy(selectedNetwork = network) }
  }

  fun dismissOpenNetworkAlert() {
    _hudState.update { it.copy(latestOpenNetworkAlert = null) }
  }

  fun dismissStatusBanner() {
    _hudState.update { it.copy(statusBannerMessage = null) }
  }

  fun startOrResumeSession() {
    if (_hudState.value.isSessionRecording) return
    _hudState.update {
      it.copy(
        isSessionRecording = true,
        statusBannerMessage = "Wardrive recording started — logging GPS breadcrumbs & BSSIDs"
      )
    }
    sessionTimerJob?.cancel()
    sessionTimerJob = viewModelScope.launch {
      while (isActive && _hudState.value.isSessionRecording) {
        delay(1000L)
        _hudState.update { it.copy(sessionElapsedSeconds = it.sessionElapsedSeconds + 1L) }
      }
    }
  }

  fun pauseSession() {
    _hudState.update {
      it.copy(
        isSessionRecording = false,
        statusBannerMessage = "Wardrive session paused"
      )
    }
    sessionTimerJob?.cancel()
  }

  fun finalizeAndSyncSessionToCloud(customTitle: String? = null) {
    sessionTimerJob?.cancel()
    val snapshot = _hudState.value
    val networksList = snapshot.liveNetworks.values.toList()
    val nowStr = SimpleDateFormat("MMM dd HH:mm", Locale.US).format(Date())
    val title = customTitle?.takeIf { it.isNotBlank() } ?: "Sector Sweep — $nowStr"

    val openCount = networksList.count { it.parsedSecurity().isVulnerableOrOpen }.toLong()
    val b24 = networksList.count { it.band == WifiBand.BAND_24.label }.toLong()
    val b5 = networksList.count { it.band == WifiBand.BAND_5.label }.toLong()
    val b6 = networksList.count { it.band == WifiBand.BAND_6.label }.toLong()

    val breadcrumbs = snapshot.trackBreadcrumbs
    val startLat = breadcrumbs.firstOrNull()?.lat ?: snapshot.gpsFix.latitude
    val startLon = breadcrumbs.firstOrNull()?.lon ?: snapshot.gpsFix.longitude
    val endLat = breadcrumbs.lastOrNull()?.lat ?: snapshot.gpsFix.latitude
    val endLon = breadcrumbs.lastOrNull()?.lon ?: snapshot.gpsFix.longitude

    val summaryStr = breadcrumbs.takeLast(120).joinToString(";") { pt ->
      String.format(Locale.US, "%.5f,%.5f,%.1f,%d", pt.lat, pt.lon, pt.altitude, pt.peakRssi)
    }

    val session = WardriveSession(
      userId = currentUserId,
      title = title,
      durationSeconds = snapshot.sessionElapsedSeconds.coerceAtLeast(10L),
      distanceMeters = snapshot.sessionDistanceMeters,
      totalNetworks = networksList.size.toLong(),
      openNetworks = openCount,
      band24Count = b24,
      band5Count = b5,
      band6Count = b6,
      startLat = startLat,
      startLon = startLon,
      endLat = endLat,
      endLon = endLon,
      trackPointsSummary = summaryStr
    )

    viewModelScope.launch {
      _hudState.update {
        it.copy(
          isSessionRecording = false,
          isCloudSyncing = true,
          statusBannerMessage = "Syncing session and ${networksList.size} BSSIDs to Cloud Firestore..."
        )
      }
      val sessionResult = repository.createSession(session)
      var syncedNetworks = 0
      for (net in networksList) {
        val res = repository.upsertDiscoveredNetwork(net.copy(userId = currentUserId))
        if (res.isSuccess) syncedNetworks++
      }

      _hudState.update {
        if (sessionResult.isSuccess) {
          it.copy(
            isCloudSyncing = false,
            sessionElapsedSeconds = 0L,
            sessionDistanceMeters = 0.0,
            statusBannerMessage = "Synced '$title' & $syncedNetworks networks to Cloud Firestore"
          )
        } else {
          it.copy(
            isCloudSyncing = false,
            statusBannerMessage = "Cloud sync error: ${sessionResult.exceptionOrNull()?.localizedMessage ?: "Failed"}"
          )
        }
      }
    }
  }

  fun syncSingleNetworkToCloud(network: DiscoveredNetwork) {
    viewModelScope.launch {
      _hudState.update { it.copy(isCloudSyncing = true) }
      val result = repository.upsertDiscoveredNetwork(network.copy(userId = currentUserId))
      _hudState.update {
        it.copy(
          isCloudSyncing = false,
          statusBannerMessage = if (result.isSuccess) {
            "Synced ${network.ssid} (${network.bssid}) to Cloud Catalog"
          } else {
            "Sync failed: ${result.exceptionOrNull()?.localizedMessage}"
          }
        )
      }
    }
  }

  fun deleteCloudSession(sessionId: String) {
    viewModelScope.launch {
      repository.deleteSession(sessionId)
    }
  }

  fun deleteCloudNetwork(networkId: String) {
    viewModelScope.launch {
      repository.deleteNetwork(networkId)
    }
  }

  fun exportWigleCsv(context: Context, networks: List<DiscoveredNetwork>) {
    val content = WardriveExporter.buildWigleCsv(networks)
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val res = WardriveExporter.shareExportFile(
      context = context,
      fileName = "aetherpulse_wigle_$stamp.csv",
      mimeType = "text/csv",
      content = content,
      chooserTitle = "Export WiGLE v1.4 CSV"
    )
    if (res.isFailure) {
      _hudState.update {
        it.copy(statusBannerMessage = "Export error: ${res.exceptionOrNull()?.localizedMessage}")
      }
    }
  }

  fun exportKml(
    context: Context,
    title: String,
    networks: List<DiscoveredNetwork>,
    breadcrumbs: List<TrackBreadcrumb>
  ) {
    val content = WardriveExporter.buildKmlDocument(title, networks, breadcrumbs)
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val res = WardriveExporter.shareExportFile(
      context = context,
      fileName = "aetherpulse_track_$stamp.kml",
      mimeType = "application/vnd.google-earth.kml+xml",
      content = content,
      chooserTitle = "Export Google Earth KML"
    )
    if (res.isFailure) {
      _hudState.update {
        it.copy(statusBannerMessage = "Export error: ${res.exceptionOrNull()?.localizedMessage}")
      }
    }
  }

  override fun onCleared() {
    super.onCleared()
    scanLoopJob?.cancel()
    sessionTimerJob?.cancel()
    telemetryEngine?.stopHardwareTelemetry()
  }

  private companion object {
    const val TAG = "WardriveVM"
  }
}
