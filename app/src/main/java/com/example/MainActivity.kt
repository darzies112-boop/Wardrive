package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.model.WardriveTab
import com.example.data.repository.WardriveRepository
import com.example.telemetry.WifiLocationTelemetryEngine
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOut
import com.example.ui.components.ApTelemetryInspectorSheet
import com.example.ui.components.NetworksScannerScreen
import com.example.ui.components.SessionsAndCloudExportScreen
import com.example.ui.components.SpectrumAndSecurityScreen
import com.example.ui.components.TacticalMapAndRadarScreen
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CrimsonBeacon
import com.example.ui.theme.GridHairline
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PhosphorEmerald
import com.example.ui.theme.TacticalElevated
import com.example.ui.theme.TacticalSlate
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextSecondaryHud
import com.example.viewmodel.WardriveViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        AppNavigation()
      }
    }
  }
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
  val listener = FirebaseAuth.AuthStateListener { auth ->
    trySend(auth.currentUser)
  }
  addAuthStateListener(listener)
  awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppNavigation(auth: FirebaseAuth = FirebaseAuth.getInstance()) {
  val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
  val user = currentUser
  if (user == null) {
    AuthScreen(onAuthSuccess = {})
  } else {
    WardriveMainScreen(currentUserId = user.uid)
  }
}

@Composable
fun WardriveMainScreen(currentUserId: String) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  val viewModel: WardriveViewModel = viewModel(
    key = currentUserId,
    factory = viewModelFactory {
      initializer {
        val app = checkNotNull(this[APPLICATION_KEY]) {
          "APPLICATION_KEY missing from CreationExtras"
        }
        val databaseId = app.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        WardriveViewModel(
          repository = WardriveRepository(db),
          currentUserId = currentUserId,
          telemetryEngine = WifiLocationTelemetryEngine(app)
        )
      }
    }
  )

  val hudState by viewModel.hudState.collectAsStateWithLifecycle()
  val cloudNetworksState by viewModel.cloudNetworksState.collectAsStateWithLifecycle()
  val cloudSessionsState by viewModel.cloudSessionsState.collectAsStateWithLifecycle()

  // Check & Request Runtime Location + Wi-Fi Permissions
  val requiredPermissions = remember {
    buildList {
      add(Manifest.permission.ACCESS_FINE_LOCATION)
      add(Manifest.permission.ACCESS_COARSE_LOCATION)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.NEARBY_WIFI_DEVICES)
      }
    }.toTypedArray()
  }

  var hasHardwarePermissions by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
      ) == PackageManager.PERMISSION_GRANTED
    )
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestMultiplePermissions()
  ) { grants ->
    val fineGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
      grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    hasHardwarePermissions = fineGranted
  }

  // Trigger subtle haptic alert when a new open/vulnerable network is spotted
  LaunchedEffect(hudState.latestOpenNetworkAlert?.bssid) {
    if (hudState.latestOpenNetworkAlert != null) {
      try {
        @Suppress("DEPRECATION")
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          vibrator?.vibrate(VibrationEffect.createOneShot(80L, VibrationEffect.DEFAULT_AMPLITUDE))
        }
      } catch (_: Exception) {
      }
    }
  }

  // Handle back press on secondary tabs to return to primary Map / Radar tab
  BackHandler(enabled = hudState.activeTab != WardriveTab.MAP_RADAR) {
    viewModel.selectTab(WardriveTab.MAP_RADAR)
  }

  val networksList = remember(hudState.liveNetworks) {
    hudState.liveNetworks.values.toList()
  }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    contentWindowInsets = WindowInsets.safeDrawing,
    containerColor = ObsidianBg,
    topBar = {
      TacticalTopHudBar(
        gpsProvider = hudState.gpsFix.provider,
        networkCount = networksList.size,
        isFieldSimEnabled = hudState.isFieldSimEnabled,
        onToggleFieldSim = { viewModel.toggleFieldSimMode(!hudState.isFieldSimEnabled) },
        onSignOut = {
          signOut(
            context = context,
            credentialManager = credentialManager,
            onSignOutComplete = {},
            scope = scope
          )
        }
      )
    },
    bottomBar = {
      NavigationBar(
        containerColor = TacticalSlate,
        tonalElevation = 4.dp
      ) {
        WardriveTab.entries.forEach { tab ->
          val icon = when (tab) {
            WardriveTab.MAP_RADAR -> Icons.Default.Radar
            WardriveTab.NETWORKS -> Icons.Default.Wifi
            WardriveTab.SPECTRUM -> Icons.Default.Equalizer
            WardriveTab.SESSIONS -> Icons.Default.History
          }
          NavigationBarItem(
            selected = hudState.activeTab == tab,
            onClick = { viewModel.selectTab(tab) },
            icon = {
              Icon(imageVector = icon, contentDescription = tab.label)
            },
            label = {
              Text(text = tab.label, style = MaterialTheme.typography.labelSmall)
            },
            modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}"),
            colors = NavigationBarItemDefaults.colors(
              selectedIconColor = ObsidianBg,
              selectedTextColor = PhosphorEmerald,
              indicatorColor = PhosphorEmerald,
              unselectedIconColor = TextSecondaryHud,
              unselectedTextColor = TextSecondaryHud
            )
          )
        }
      }
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
    ) {
      // Optional Hardware Permission Prompt Bar
      if (!hasHardwarePermissions) {
        Surface(
          color = TacticalElevated,
          border = BorderStroke(1.dp, TelemetryCyan.copy(alpha = 0.5f)),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Enable GPS & Wi-Fi permissions for live hardware scanning",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurface,
              modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
              onClick = { permissionLauncher.launch(requiredPermissions) },
              shape = RoundedCornerShape(8.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = TelemetryCyan,
                contentColor = ObsidianBg
              ),
              modifier = Modifier.testTag("grant_permissions_button")
            ) {
              Text(
                text = "Grant",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
              )
            }
          }
        }
      }

      // Open / Unsecured Network Alert Banner
      AnimatedVisibility(visible = hudState.latestOpenNetworkAlert != null) {
        val alertNet = hudState.latestOpenNetworkAlert
        if (alertNet != null) {
          Surface(
            color = CrimsonBeacon.copy(alpha = 0.18f),
            border = BorderStroke(1.dp, CrimsonBeacon),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 4.dp)
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Unsecured Network Alert",
                tint = CrimsonBeacon,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = "ALERT: ${alertNet.parsedSecurity().label} AP '${alertNet.ssid}' (${alertNet.currentRssi} dBm)",
                style = MaterialTheme.typography.labelMedium,
                color = CrimsonBeacon,
                modifier = Modifier.weight(1f)
              )
              IconButton(
                onClick = { viewModel.dismissOpenNetworkAlert() },
                modifier = Modifier.size(36.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = "Dismiss Alert",
                  tint = CrimsonBeacon,
                  modifier = Modifier.size(16.dp)
                )
              }
            }
          }
        }
      }

      // Status Toast Banner
      AnimatedVisibility(visible = hudState.statusBannerMessage != null) {
        val msg = hudState.statusBannerMessage
        if (msg != null) {
          Surface(
            color = TacticalElevated,
            border = BorderStroke(1.dp, PhosphorEmerald.copy(alpha = 0.45f)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 12.dp, vertical = 4.dp)
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = msg,
                style = MaterialTheme.typography.labelMedium,
                color = PhosphorEmerald,
                modifier = Modifier.weight(1f)
              )
              IconButton(
                onClick = { viewModel.dismissStatusBanner() },
                modifier = Modifier.size(36.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Close,
                  contentDescription = "Dismiss Status",
                  tint = TextSecondaryHud,
                  modifier = Modifier.size(16.dp)
                )
              }
            }
          }
        }
      }

      // Active Tab Content
      when (hudState.activeTab) {
        WardriveTab.MAP_RADAR -> {
          TacticalMapAndRadarScreen(
            mapDisplayMode = hudState.mapDisplayMode,
            onSelectMapMode = viewModel::setMapDisplayMode,
            gpsFix = hudState.gpsFix,
            networks = networksList,
            breadcrumbs = hudState.trackBreadcrumbs,
            isSessionRecording = hudState.isSessionRecording,
            sessionElapsedSeconds = hudState.sessionElapsedSeconds,
            sessionDistanceMeters = hudState.sessionDistanceMeters,
            onStartOrResumeSession = viewModel::startOrResumeSession,
            onPauseSession = viewModel::pauseSession,
            onSaveAndSyncSession = { viewModel.finalizeAndSyncSessionToCloud() },
            onSelectNetwork = viewModel::selectNetworkForInspection,
            modifier = Modifier.weight(1f)
          )
        }

        WardriveTab.NETWORKS -> {
          NetworksScannerScreen(
            networks = networksList,
            activeFilter = hudState.activeFilter,
            searchQuery = hudState.searchQuery,
            onFilterChange = viewModel::setNetworkFilter,
            onSearchQueryChange = viewModel::setSearchQuery,
            onSelectNetwork = viewModel::selectNetworkForInspection,
            onSyncNetworkToCloud = viewModel::syncSingleNetworkToCloud,
            modifier = Modifier.weight(1f)
          )
        }

        WardriveTab.SPECTRUM -> {
          SpectrumAndSecurityScreen(
            networks = networksList,
            selectedBand = hudState.selectedBand,
            onSelectBand = viewModel::selectBand,
            modifier = Modifier.weight(1f)
          )
        }

        WardriveTab.SESSIONS -> {
          SessionsAndCloudExportScreen(
            liveNetworks = networksList,
            breadcrumbs = hudState.trackBreadcrumbs,
            isSessionRecording = hudState.isSessionRecording,
            sessionElapsedSeconds = hudState.sessionElapsedSeconds,
            sessionDistanceMeters = hudState.sessionDistanceMeters,
            cloudSessionsState = cloudSessionsState,
            cloudNetworksState = cloudNetworksState,
            onStartOrResumeSession = viewModel::startOrResumeSession,
            onPauseSession = viewModel::pauseSession,
            onSaveAndSyncSession = { viewModel.finalizeAndSyncSessionToCloud() },
            onExportWigleCsv = { nets -> viewModel.exportWigleCsv(context, nets) },
            onExportKml = { title, nets, pts -> viewModel.exportKml(context, title, nets, pts) },
            onDeleteSession = viewModel::deleteCloudSession,
            modifier = Modifier.weight(1f)
          )
        }
      }
    }
  }

  // AP Telemetry Inspector Bottom Sheet
  val inspectedNetwork = hudState.selectedNetwork
  if (inspectedNetwork != null) {
    ApTelemetryInspectorSheet(
      network = inspectedNetwork,
      rssiHistory = hudState.rssiHistoryByBssid[inspectedNetwork.bssid] ?: emptyList(),
      onDismiss = { viewModel.selectNetworkForInspection(null) },
      onSyncToCloud = viewModel::syncSingleNetworkToCloud
    )
  }
}

@Composable
private fun TacticalTopHudBar(
  gpsProvider: String,
  networkCount: Int,
  isFieldSimEnabled: Boolean,
  onToggleFieldSim: () -> Unit,
  onSignOut: () -> Unit
) {
  Surface(
    color = TacticalSlate,
    border = BorderStroke(1.dp, GridHairline),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(
          imageVector = Icons.Default.GpsFixed,
          contentDescription = "GPS Telemetry Status",
          tint = PhosphorEmerald,
          modifier = Modifier.size(20.dp)
        )
        Column {
          Text(
            text = "AETHERPULSE",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = "$gpsProvider LOCK  •  $networkCount BSSIDs",
            style = MaterialTheme.typography.labelSmall,
            color = TelemetryCyan
          )
        }
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        FilterChip(
          selected = isFieldSimEnabled,
          onClick = onToggleFieldSim,
          leadingIcon = {
            Icon(
              imageVector = Icons.Default.Sensors,
              contentDescription = null,
              modifier = Modifier.size(16.dp)
            )
          },
          label = {
            Text(
              text = if (isFieldSimEnabled) "SIM ON" else "HW LIVE",
              style = MaterialTheme.typography.labelMedium
            )
          },
          modifier = Modifier.testTag("toggle_field_sim_button"),
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = AmberAlert.copy(alpha = 0.2f),
            selectedLabelColor = AmberAlert,
            selectedLeadingIconColor = AmberAlert
          )
        )

        IconButton(
          onClick = onSignOut,
          modifier = Modifier
            .size(48.dp)
            .testTag("sign_out_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.Logout,
            contentDescription = "Sign Out",
            tint = TextSecondaryHud
          )
        }
      }
    }
  }
}
