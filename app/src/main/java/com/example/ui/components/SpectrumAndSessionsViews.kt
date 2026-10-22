package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import com.example.data.model.DiscoveredNetwork
import com.example.data.model.SecurityType
import com.example.data.model.TrackBreadcrumb
import com.example.data.model.UiState
import com.example.data.model.WardriveSession
import com.example.data.model.WifiBand
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
import kotlin.math.exp
import kotlin.math.pow

@Composable
fun SpectrumAndSecurityScreen(
  networks: List<DiscoveredNetwork>,
  selectedBand: WifiBand,
  onSelectBand: (WifiBand) -> Unit,
  modifier: Modifier = Modifier
) {
  val bandNetworks = networks.filter { it.band == selectedBand.label }
  val candidateChannels = when (selectedBand) {
    WifiBand.BAND_24 -> listOf(1L, 6L, 11L)
    WifiBand.BAND_5 -> listOf(36L, 44L, 149L, 157L)
    WifiBand.BAND_6 -> listOf(1L, 37L, 69L, 101L)
  }
  val leastCongestedChannel = candidateChannels.minByOrNull { ch ->
    bandNetworks.count { kotlin.math.abs(it.channel - ch) <= 2L }
  } ?: 1L

  Column(
    modifier = modifier
      .fillMaxSize()
      .verticalScroll(rememberScrollState())
      .padding(horizontal = 12.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    // Band Selector Chips
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      WifiBand.entries.forEach { band ->
        val count = networks.count { it.band == band.label }
        FilterChip(
          selected = selectedBand == band,
          onClick = { onSelectBand(band) },
          label = {
            Text(
              text = "${band.label} ($count)",
              style = MaterialTheme.typography.labelLarge
            )
          },
          modifier = Modifier.testTag("spectrum_band_${band.name.lowercase()}"),
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = PhosphorEmerald.copy(alpha = 0.2f),
            selectedLabelColor = PhosphorEmerald
          )
        )
      }
    }

    // Channel Bell-Curve Spectrum Graph Card
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(14.dp),
      colors = CardDefaults.cardColors(containerColor = TacticalSlate),
      border = BorderStroke(1.dp, GridHairline)
    ) {
      Column(modifier = Modifier.padding(14.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Column {
            Text(
              text = "${selectedBand.label} CHANNEL CONGESTION CURVES",
              style = MaterialTheme.typography.titleMedium,
              color = PhosphorEmerald,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "Gaussian spectral occupancy by channel width & RSSI (-30 to -100 dBm)",
              style = MaterialTheme.typography.labelSmall,
              color = TextSecondaryHud
            )
          }
          Surface(
            color = TelemetryCyan.copy(alpha = 0.16f),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, TelemetryCyan.copy(alpha = 0.5f))
          ) {
            Text(
              text = "BEST CH $leastCongestedChannel",
              style = MaterialTheme.typography.labelMedium,
              color = TelemetryCyan,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Surface(
          color = ObsidianBg,
          shape = RoundedCornerShape(10.dp),
          border = BorderStroke(1.dp, GridHairline),
          modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
        ) {
          Canvas(
            modifier = Modifier
              .fillMaxSize()
              .padding(12.dp)
              .testTag("spectrum_congestion_canvas")
          ) {
            val w = size.width
            val h = size.height

            // Horizontal dBm guide lines
            for (i in 1..3) {
              val y = h * (i / 4f)
              drawLine(
                color = GridHairline,
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = 1f
              )
            }

            val (minCh, maxCh) = when (selectedBand) {
              WifiBand.BAND_24 -> 1f to 14f
              WifiBand.BAND_5 -> 32f to 165f
              WifiBand.BAND_6 -> 1f to 195f
            }
            val span = (maxCh - minCh).coerceAtLeast(1f)

            bandNetworks.forEachIndexed { idx, net ->
              val centerNorm = ((net.channel.toFloat() - minCh) / span).coerceIn(0.05f, 0.95f)
              val centerX = centerNorm * w
              val widthFraction = when (net.channelWidthMhz) {
                160L -> 0.22f
                80L -> 0.14f
                40L -> 0.09f
                else -> 0.06f
              }
              val sigmaPx = (w * widthFraction).coerceAtLeast(16f)
              val peakHeight = (((net.currentRssi.coerceIn(-95L, -30L) + 95f) / 65f) * h * 0.88f)
                .coerceAtLeast(18f)

              val curveColor = when (idx % 4) {
                0 -> PhosphorEmerald
                1 -> TelemetryCyan
                2 -> AmberAlert
                else -> VioletSpectrum6G
              }

              val path = Path()
              val steps = 60
              for (s in 0..steps) {
                val x = (s.toFloat() / steps) * w
                val z = (x - centerX) / sigmaPx
                val gauss = exp(-0.5 * z.toDouble().pow(2.0)).toFloat()
                val y = h - (peakHeight * gauss)
                if (s == 0) path.moveTo(x, y) else path.lineTo(x, y)
              }
              drawPath(
                path = path,
                color = curveColor,
                style = Stroke(width = 3f, cap = StrokeCap.Round)
              )
            }
          }
        }
      }
    }

    // Security Protocol Posture Breakdown Card
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = RoundedCornerShape(14.dp),
      colors = CardDefaults.cardColors(containerColor = TacticalSlate),
      border = BorderStroke(1.dp, GridHairline)
    ) {
      Column(
        modifier = Modifier.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Text(
          text = "SECURITY PROTOCOL POSTURE & ENCRYPTION AUDIT",
          style = MaterialTheme.typography.titleMedium,
          color = TelemetryCyan,
          fontWeight = FontWeight.Bold
        )

        val total = networks.size.coerceAtLeast(1)
        SecurityType.entries.forEach { secType ->
          val count = networks.count { it.parsedSecurity() == secType }
          val fraction = count.toFloat() / total.toFloat()
          val barColor = when (secType) {
            SecurityType.OPEN -> CrimsonBeacon
            SecurityType.WEP, SecurityType.WPA -> AmberAlert
            SecurityType.WPA3 -> TelemetryCyan
            SecurityType.WPA2, SecurityType.OWE -> PhosphorEmerald
          }

          Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(
                text = "${secType.label} (${if (secType.isVulnerableOrOpen) "VULNERABLE / OPEN" else "ENCRYPTED"})",
                style = MaterialTheme.typography.labelMedium,
                color = barColor
              )
              Text(
                text = "$count APs (${(fraction * 100).toInt()}%)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
              )
            }
            LinearProgressIndicator(
              progress = { fraction },
              color = barColor,
              trackColor = TacticalElevated,
              modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
            )
          }
        }
      }
    }
  }
}

@Composable
fun SessionsAndCloudExportScreen(
  liveNetworks: List<DiscoveredNetwork>,
  breadcrumbs: List<TrackBreadcrumb>,
  isSessionRecording: Boolean,
  sessionElapsedSeconds: Long,
  sessionDistanceMeters: Double,
  cloudSessionsState: UiState<List<WardriveSession>>,
  cloudNetworksState: UiState<List<DiscoveredNetwork>>,
  onStartOrResumeSession: () -> Unit,
  onPauseSession: () -> Unit,
  onSaveAndSyncSession: () -> Unit,
  onExportWigleCsv: (List<DiscoveredNetwork>) -> Unit,
  onExportKml: (String, List<DiscoveredNetwork>, List<TrackBreadcrumb>) -> Unit,
  onDeleteSession: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  val cloudNetworks = (cloudNetworksState as? UiState.Success)?.data ?: emptyList()
  val combinedNetworks = (liveNetworks + cloudNetworks).distinctBy { it.bssid }

  LazyColumn(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 12.dp, vertical = 8.dp)
      .testTag("sessions_export_list"),
    verticalArrangement = Arrangement.spacedBy(12.dp)
  ) {
    // Export & Cloud Sync Actions Card
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = TacticalSlate),
        border = BorderStroke(1.dp, GridHairline)
      ) {
        Column(
          modifier = Modifier.padding(14.dp),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = "WIGLE CSV & GOOGLE EARTH KML EXPORT",
                style = MaterialTheme.typography.titleMedium,
                color = PhosphorEmerald,
                fontWeight = FontWeight.Bold
              )
              Text(
                text = "${combinedNetworks.size} Unique BSSIDs  •  ${breadcrumbs.size} GPS Track Points",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondaryHud
              )
            }
            Icon(
              imageVector = Icons.Default.CloudDone,
              contentDescription = "Cloud Sync Active",
              tint = TelemetryCyan
            )
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Button(
              onClick = { onExportWigleCsv(combinedNetworks) },
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("export_wigle_csv_button"),
              shape = RoundedCornerShape(10.dp),
              colors = ButtonDefaults.buttonColors(
                containerColor = PhosphorEmerald,
                contentColor = ObsidianBg
              )
            ) {
              Icon(
                imageVector = Icons.Default.Download,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "WiGLE CSV",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
              )
            }

            OutlinedButton(
              onClick = {
                onExportKml("AetherPulse_Wardrive", combinedNetworks, breadcrumbs)
              },
              modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .testTag("export_kml_button"),
              shape = RoundedCornerShape(10.dp),
              border = BorderStroke(1.dp, TelemetryCyan)
            ) {
              Icon(
                imageVector = Icons.Default.Map,
                contentDescription = null,
                tint = TelemetryCyan,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "Export KML",
                style = MaterialTheme.typography.labelLarge,
                color = TelemetryCyan,
                fontWeight = FontWeight.Bold
              )
            }
          }

          OutlinedButton(
            onClick = onSaveAndSyncSession,
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
              .testTag("save_current_session_cloud_button"),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, PhosphorEmerald.copy(alpha = 0.6f))
          ) {
            Icon(
              imageVector = Icons.Default.CloudUpload,
              contentDescription = null,
              tint = PhosphorEmerald
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Finalize Current Drive & Sync to Cloud Firestore",
              style = MaterialTheme.typography.labelLarge,
              color = PhosphorEmerald
            )
          }
        }
      }
    }

    // Cloud Sessions Section Header
    item {
      Text(
        text = "CLOUD FIRESTORE WARDRIVE SESSIONS",
        style = MaterialTheme.typography.labelLarge,
        color = TelemetryCyan
      )
    }

    when (cloudSessionsState) {
      is UiState.Loading -> {
        item {
          Text(
            text = "Loading cloud wardriving sessions...",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondaryHud
          )
        }
      }

      is UiState.Error -> {
        item {
          Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = cloudSessionsState.message,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.padding(12.dp)
            )
          }
        }
      }

      is UiState.Success -> {
        val sessions = cloudSessionsState.data
        if (sessions.isEmpty()) {
          item {
            Card(
              modifier = Modifier.fillMaxWidth(),
              colors = CardDefaults.cardColors(containerColor = TacticalSlate),
              border = BorderStroke(1.dp, GridHairline)
            ) {
              Column(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                Text(
                  text = "No saved cloud sessions yet",
                  style = MaterialTheme.typography.titleMedium
                )
                Text(
                  text = "Tap 'Record' and then 'Sync' to save your GPS track and discovered BSSIDs to your account.",
                  style = MaterialTheme.typography.bodyMedium,
                  color = TextSecondaryHud
                )
              }
            }
          }
        } else {
          items(sessions, key = { it.id }) { session ->
            CloudSessionCard(
              session = session,
              onExportSessionKml = {
                onExportKml(
                  session.title,
                  combinedNetworks,
                  session.parseTrackBreadcrumbs().ifEmpty { breadcrumbs }
                )
              },
              onDelete = { onDeleteSession(session.id) }
            )
          }
        }
      }
    }
  }
}

@Composable
private fun CloudSessionCard(
  session: WardriveSession,
  onExportSessionKml: () -> Unit,
  onDelete: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("cloud_session_card_${session.id}"),
    shape = RoundedCornerShape(12.dp),
    colors = CardDefaults.cardColors(containerColor = TacticalSlate),
    border = BorderStroke(1.dp, GridHairline)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = session.title,
          style = MaterialTheme.typography.titleMedium,
          color = PhosphorEmerald,
          fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = String.format(
            Locale.US,
            "%d APs (%d Open)  •  %.0f m  •  %ds",
            session.totalNetworks,
            session.openNetworks,
            session.distanceMeters,
            session.durationSeconds
          ),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurface
        )
        Text(
          text = "Bands: 2.4G (${session.band24Count}) • 5G (${session.band5Count}) • 6G (${session.band6Count})",
          style = MaterialTheme.typography.labelSmall,
          color = TextSecondaryHud
        )
      }

      IconButton(
        onClick = onExportSessionKml,
        modifier = Modifier.size(48.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Share,
          contentDescription = "Share Session KML",
          tint = TelemetryCyan
        )
      }

      IconButton(
        onClick = onDelete,
        modifier = Modifier.size(48.dp)
      ) {
        Icon(
          imageVector = Icons.Default.Delete,
          contentDescription = "Delete Session",
          tint = CrimsonBeacon
        )
      }
    }
  }
}
