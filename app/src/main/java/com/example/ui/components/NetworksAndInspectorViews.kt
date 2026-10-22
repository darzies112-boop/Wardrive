package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.DiscoveredNetwork
import com.example.data.model.NetworkFilter
import com.example.data.model.SecurityType
import com.example.ui.theme.AmberAlert
import com.example.ui.theme.CrimsonBeacon
import com.example.ui.theme.GridHairline
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PhosphorEmerald
import com.example.ui.theme.TacticalElevated
import com.example.ui.theme.TacticalSlate
import com.example.ui.theme.TelemetryCyan
import com.example.ui.theme.TextSecondaryHud
import com.example.ui.theme.VioletSpectrum6G
import java.util.Locale

@Composable
fun NetworksScannerScreen(
  networks: List<DiscoveredNetwork>,
  activeFilter: NetworkFilter,
  searchQuery: String,
  onFilterChange: (NetworkFilter) -> Unit,
  onSearchQueryChange: (String) -> Unit,
  onSelectNetwork: (DiscoveredNetwork) -> Unit,
  onSyncNetworkToCloud: (DiscoveredNetwork) -> Unit,
  modifier: Modifier = Modifier
) {
  val filteredNetworks = networks.filter { net ->
    val matchesQuery = searchQuery.isBlank() ||
      net.ssid.contains(searchQuery, ignoreCase = true) ||
      net.bssid.contains(searchQuery, ignoreCase = true) ||
      net.vendor.contains(searchQuery, ignoreCase = true)

    val matchesFilter = when (activeFilter) {
      NetworkFilter.ALL -> true
      NetworkFilter.OPEN_VULNERABLE -> net.parsedSecurity().isVulnerableOrOpen
      NetworkFilter.WPA3_ONLY -> net.parsedSecurity() == SecurityType.WPA3
      NetworkFilter.BAND_24 -> net.band == "2.4 GHz"
      NetworkFilter.BAND_5 -> net.band == "5 GHz"
      NetworkFilter.BAND_6 -> net.band == "6 GHz"
      NetworkFilter.STRONG_SIGNAL -> net.currentRssi >= -65L
    }
    matchesQuery && matchesFilter
  }.sortedByDescending { it.currentRssi }

  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 12.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    // Search Bar
    OutlinedTextField(
      value = searchQuery,
      onValueChange = onSearchQueryChange,
      placeholder = {
        Text(
          text = "Search SSID, BSSID MAC, or Vendor OUI...",
          style = MaterialTheme.typography.bodyMedium
        )
      },
      leadingIcon = {
        Icon(
          imageVector = Icons.Default.Search,
          contentDescription = "Search Networks",
          tint = TelemetryCyan
        )
      },
      trailingIcon = {
        if (searchQuery.isNotEmpty()) {
          IconButton(onClick = { onSearchQueryChange("") }) {
            Icon(
              imageVector = Icons.Default.Clear,
              contentDescription = "Clear Search"
            )
          }
        }
      },
      singleLine = true,
      shape = RoundedCornerShape(12.dp),
      modifier = Modifier
        .fillMaxWidth()
        .testTag("network_search_input")
    )

    // Filter Chips Row
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .horizontalScroll(rememberScrollState()),
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      NetworkFilter.entries.forEach { filter ->
        FilterChip(
          selected = activeFilter == filter,
          onClick = { onFilterChange(filter) },
          label = {
            Text(
              text = filter.label,
              style = MaterialTheme.typography.labelMedium
            )
          },
          modifier = Modifier.testTag("network_filter_${filter.name.lowercase()}"),
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = TelemetryCyan.copy(alpha = 0.2f),
            selectedLabelColor = TelemetryCyan
          )
        )
      }
    }

    // Summary Counter Row
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = "SHOWING ${filteredNetworks.size} OF ${networks.size} ACCESS POINTS",
        style = MaterialTheme.typography.labelMedium,
        color = TextSecondaryHud
      )
      val openVulnCount = networks.count { it.parsedSecurity().isVulnerableOrOpen }
      if (openVulnCount > 0) {
        Text(
          text = "$openVulnCount UNSECURED / WEP",
          style = MaterialTheme.typography.labelMedium,
          color = CrimsonBeacon,
          fontWeight = FontWeight.Bold
        )
      }
    }

    if (filteredNetworks.isEmpty()) {
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 24.dp),
        colors = CardDefaults.cardColors(containerColor = TacticalSlate),
        border = BorderStroke(1.dp, GridHairline)
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Icon(
            imageVector = Icons.Default.Wifi,
            contentDescription = "No matching networks",
            tint = TextSecondaryHud,
            modifier = Modifier.size(40.dp)
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "No wireless beacons match the current filter",
            style = MaterialTheme.typography.titleMedium
          )
          Text(
            text = "Try selecting 'All APs' or enable Field Sim in the top HUD bar.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondaryHud
          )
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .testTag("network_scanner_list"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        items(filteredNetworks, key = { it.bssid }) { net ->
          NetworkTelemetryCard(
            network = net,
            onClick = { onSelectNetwork(net) },
            onSyncClick = { onSyncNetworkToCloud(net) }
          )
        }
      }
    }
  }
}

@Composable
private fun NetworkTelemetryCard(
  network: DiscoveredNetwork,
  onClick: () -> Unit,
  onSyncClick: () -> Unit
) {
  val sec = network.parsedSecurity()
  val rssiColor = when {
    network.currentRssi >= -60L -> PhosphorEmerald
    network.currentRssi >= -75L -> TelemetryCyan
    network.currentRssi >= -85L -> AmberAlert
    else -> CrimsonBeacon
  }
  val secColor = when (sec) {
    SecurityType.OPEN -> CrimsonBeacon
    SecurityType.WEP, SecurityType.WPA -> AmberAlert
    SecurityType.WPA3 -> TelemetryCyan
    SecurityType.WPA2, SecurityType.OWE -> PhosphorEmerald
  }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick)
      .testTag("network_card_${network.bssid}"),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = TacticalSlate),
    border = BorderStroke(
      1.dp,
      if (sec == SecurityType.OPEN) CrimsonBeacon.copy(alpha = 0.55f) else GridHairline
    )
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Left Signal Gauge Box
      Surface(
        color = rssiColor.copy(alpha = 0.14f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, rssiColor.copy(alpha = 0.45f)),
        modifier = Modifier.size(width = 64.dp, height = 56.dp)
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.Center
        ) {
          Text(
            text = "${network.currentRssi}",
            style = MaterialTheme.typography.titleLarge,
            color = rssiColor,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = "dBm",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondaryHud
          )
        }
      }

      Spacer(modifier = Modifier.width(12.dp))

      // Middle Telemetry Details
      Column(modifier = Modifier.weight(1f)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Text(
            text = network.ssid,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
          )
          Surface(
            color = secColor.copy(alpha = 0.18f),
            shape = RoundedCornerShape(6.dp)
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = if (sec.isVulnerableOrOpen) Icons.Default.LockOpen else Icons.Default.Lock,
                contentDescription = sec.label,
                tint = secColor,
                modifier = Modifier.size(12.dp)
              )
              Spacer(modifier = Modifier.width(3.dp))
              Text(
                text = sec.label,
                style = MaterialTheme.typography.labelSmall,
                color = secColor,
                fontWeight = FontWeight.Bold
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
          text = "${network.bssid}  •  ${network.vendor}",
          style = MaterialTheme.typography.labelMedium,
          color = TextSecondaryHud,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          val bandColor = when (network.band) {
            "6 GHz" -> VioletSpectrum6G
            "5 GHz" -> TelemetryCyan
            else -> PhosphorEmerald
          }
          Text(
            text = "${network.band} CH ${network.channel} (${network.channelWidthMhz}MHz)",
            style = MaterialTheme.typography.labelSmall,
            color = bandColor
          )
          Text(
            text = String.format(Locale.US, "GPS %.4f, %.4f", network.bestLat, network.bestLon),
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondaryHud
          )
        }
      }

      IconButton(
        onClick = onSyncClick,
        modifier = Modifier
          .size(48.dp)
          .testTag("sync_network_${network.bssid}")
      ) {
        Icon(
          imageVector = Icons.Default.CloudUpload,
          contentDescription = "Sync AP to Cloud",
          tint = TelemetryCyan
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApTelemetryInspectorSheet(
  network: DiscoveredNetwork,
  rssiHistory: List<Int>,
  onDismiss: () -> Unit,
  onSyncToCloud: (DiscoveredNetwork) -> Unit
) {
  val sec = network.parsedSecurity()
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    containerColor = TacticalSlate,
    contentColor = MaterialTheme.colorScheme.onSurface
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 20.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = network.ssid,
            style = MaterialTheme.typography.headlineMedium,
            color = PhosphorEmerald
          )
          Text(
            text = "BSSID ${network.bssid}  •  ${network.vendor}",
            style = MaterialTheme.typography.labelLarge,
            color = TelemetryCyan
          )
        }
        Surface(
          color = TacticalElevated,
          shape = RoundedCornerShape(10.dp),
          border = BorderStroke(1.dp, PhosphorEmerald.copy(alpha = 0.5f))
        ) {
          Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Text(
              text = "${network.currentRssi} dBm",
              style = MaterialTheme.typography.titleLarge,
              color = PhosphorEmerald,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "PEAK ${network.bestRssi} dBm",
              style = MaterialTheme.typography.labelSmall,
              color = TextSecondaryHud
            )
          }
        }
      }

      if (sec.isVulnerableOrOpen) {
        Surface(
          color = CrimsonBeacon.copy(alpha = 0.16f),
          shape = RoundedCornerShape(10.dp),
          border = BorderStroke(1.dp, CrimsonBeacon.copy(alpha = 0.6f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.Warning,
              contentDescription = "Security Warning",
              tint = CrimsonBeacon
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "SECURITY AUDIT ALERT: Network uses ${sec.label} (${network.capabilities}) — unencrypted or legacy cipher.",
              style = MaterialTheme.typography.bodyMedium,
              color = CrimsonBeacon
            )
          }
        }
      }

      // Live RSSI Sparkline History Graph
      Surface(
        color = ObsidianBg,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, GridHairline),
        modifier = Modifier
          .fillMaxWidth()
          .height(110.dp)
      ) {
        Column(modifier = Modifier.padding(10.dp)) {
          Text(
            text = "REAL-TIME RSSI WATERFALL SPARKLINE (-30 to -100 dBm)",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondaryHud
          )
          Spacer(modifier = Modifier.height(6.dp))
          Canvas(modifier = Modifier.fillMaxSize()) {
            val samples = if (rssiHistory.isEmpty()) listOf(network.currentRssi.toInt()) else rssiHistory
            val w = size.width
            val h = size.height
            val path = Path()
            samples.forEachIndexed { index, dbm ->
              val x = if (samples.size <= 1) w / 2f else (index.toFloat() / (samples.size - 1)) * w
              val norm = ((dbm.coerceIn(-100, -30) + 100f) / 70f)
              val y = h - norm * h
              if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
              drawCircle(
                color = PhosphorEmerald,
                radius = 4f,
                center = Offset(x, y)
              )
            }
            if (samples.size >= 2) {
              drawPath(
                path = path,
                color = PhosphorEmerald,
                style = Stroke(width = 3f, cap = StrokeCap.Round)
              )
            }
          }
        }
      }

      // Telemetry Metadata Grid
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        InspectorMetricTile(
          label = "FREQUENCY / CH",
          value = "${network.frequencyMhz} MHz (Ch ${network.channel})",
          sub = "${network.band} • ${network.channelWidthMhz} MHz Width",
          modifier = Modifier.weight(1f)
        )
        InspectorMetricTile(
          label = "PEAK GPS LOCK",
          value = String.format(Locale.US, "%.5f, %.5f", network.bestLat, network.bestLon),
          sub = String.format(Locale.US, "Alt %.1fm • ±%.1fm • %dx seen", network.bestAlt, network.accuracyMeters, network.seenCount),
          modifier = Modifier.weight(1f)
        )
      }

      Text(
        text = "802.11 CAPABILITIES: ${network.capabilities}",
        style = MaterialTheme.typography.labelMedium,
        color = TextSecondaryHud
      )

      Button(
        onClick = {
          onSyncToCloud(network)
          onDismiss()
        },
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp)
          .testTag("inspector_sync_cloud_button"),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = PhosphorEmerald,
          contentColor = ObsidianBg
        )
      ) {
        Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Save / Update AP in Cloud Catalog",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
      }

      Spacer(modifier = Modifier.height(16.dp))
    }
  }
}

@Composable
private fun InspectorMetricTile(
  label: String,
  value: String,
  sub: String,
  modifier: Modifier = Modifier
) {
  Surface(
    color = TacticalElevated,
    shape = RoundedCornerShape(10.dp),
    border = BorderStroke(1.dp, GridHairline),
    modifier = modifier
  ) {
    Column(modifier = Modifier.padding(10.dp)) {
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = TextSecondaryHud
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = value,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Bold
      )
      Text(
        text = sub,
        style = MaterialTheme.typography.labelSmall,
        color = TelemetryCyan
      )
    }
  }
}
