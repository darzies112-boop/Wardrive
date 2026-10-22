package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.DiscoveredNetwork
import com.example.data.model.GeoMath
import com.example.data.model.GpsFix
import com.example.data.model.MapDisplayMode
import com.example.data.model.SecurityType
import com.example.data.model.TrackBreadcrumb
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
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@Composable
fun TacticalMapAndRadarScreen(
  mapDisplayMode: MapDisplayMode,
  onSelectMapMode: (MapDisplayMode) -> Unit,
  gpsFix: GpsFix,
  networks: List<DiscoveredNetwork>,
  breadcrumbs: List<TrackBreadcrumb>,
  isSessionRecording: Boolean,
  sessionElapsedSeconds: Long,
  sessionDistanceMeters: Double,
  onStartOrResumeSession: () -> Unit,
  onPauseSession: () -> Unit,
  onSaveAndSyncSession: () -> Unit,
  onSelectNetwork: (DiscoveredNetwork) -> Unit,
  modifier: Modifier = Modifier
) {
  var zoomMetersPerScreen by remember { mutableFloatStateOf(260f) }
  var panOffsetMetersX by remember { mutableFloatStateOf(0f) }
  var panOffsetMetersY by remember { mutableFloatStateOf(0f) }
  var colorBySecurity by remember { mutableStateOf(true) }

  Column(
    modifier = modifier
      .fillMaxSize()
      .padding(horizontal = 12.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp)
  ) {
    // Mode Switcher & Legend Toggle Row
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MapDisplayMode.entries.forEach { mode ->
          FilterChip(
            selected = mapDisplayMode == mode,
            onClick = { onSelectMapMode(mode) },
            label = {
              Text(
                text = mode.label,
                style = MaterialTheme.typography.labelMedium
              )
            },
            modifier = Modifier.testTag("map_mode_${mode.name.lowercase()}"),
            colors = FilterChipDefaults.filterChipColors(
              selectedContainerColor = PhosphorEmerald.copy(alpha = 0.2f),
              selectedLabelColor = PhosphorEmerald
            )
          )
        }
      }

      FilterChip(
        selected = colorBySecurity,
        onClick = { colorBySecurity = !colorBySecurity },
        label = {
          Text(
            text = if (colorBySecurity) "Color: Security" else "Color: Band",
            style = MaterialTheme.typography.labelSmall
          )
        },
        modifier = Modifier.testTag("toggle_node_color_mode")
      )
    }

    // Main Tactical Visualization Area
    Box(
      modifier = Modifier
        .fillMaxWidth()
        .weight(1f)
    ) {
      when (mapDisplayMode) {
        MapDisplayMode.GPS_GRID -> {
          TacticalGpsGridCanvas(
            gpsFix = gpsFix,
            networks = networks,
            breadcrumbs = breadcrumbs,
            zoomMetersSpan = zoomMetersPerScreen,
            panMetersX = panOffsetMetersX,
            panMetersY = panOffsetMetersY,
            colorBySecurity = colorBySecurity,
            onPanMeters = { dx, dy ->
              panOffsetMetersX += dx
              panOffsetMetersY += dy
            },
            onZoomChange = { factor ->
              zoomMetersPerScreen = (zoomMetersPerScreen * factor).coerceIn(80f, 1200f)
            },
            onResetCenter = {
              panOffsetMetersX = 0f
              panOffsetMetersY = 0f
            },
            onSelectNetwork = onSelectNetwork,
            modifier = Modifier.fillMaxSize()
          )
        }

        MapDisplayMode.RF_RADAR -> {
          PolarRfRadarCanvas(
            gpsFix = gpsFix,
            networks = networks,
            colorBySecurity = colorBySecurity,
            onSelectNetwork = onSelectNetwork,
            modifier = Modifier.fillMaxSize()
          )
        }

        MapDisplayMode.SPLIT_HUD -> {
          Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            TacticalGpsGridCanvas(
              gpsFix = gpsFix,
              networks = networks,
              breadcrumbs = breadcrumbs,
              zoomMetersSpan = zoomMetersPerScreen,
              panMetersX = panOffsetMetersX,
              panMetersY = panOffsetMetersY,
              colorBySecurity = colorBySecurity,
              onPanMeters = { dx, dy ->
                panOffsetMetersX += dx
                panOffsetMetersY += dy
              },
              onZoomChange = { factor ->
                zoomMetersPerScreen = (zoomMetersPerScreen * factor).coerceIn(80f, 1200f)
              },
              onResetCenter = {
                panOffsetMetersX = 0f
                panOffsetMetersY = 0f
              },
              onSelectNetwork = onSelectNetwork,
              modifier = Modifier
                .fillMaxWidth()
                .weight(0.56f)
            )

            PolarRfRadarCanvas(
              gpsFix = gpsFix,
              networks = networks,
              colorBySecurity = colorBySecurity,
              onSelectNetwork = onSelectNetwork,
              modifier = Modifier
                .fillMaxWidth()
                .weight(0.44f)
            )
          }
        }
      }
    }

    // Bottom Wardrive Track Recording & Telemetry Strip
    SessionRecorderStrip(
      isRecording = isSessionRecording,
      elapsedSeconds = sessionElapsedSeconds,
      distanceMeters = sessionDistanceMeters,
      networkCount = networks.size,
      openCount = networks.count { it.parsedSecurity().isVulnerableOrOpen },
      onStartOrResume = onStartOrResumeSession,
      onPause = onPauseSession,
      onSaveAndSync = onSaveAndSyncSession
    )
  }
}

@Composable
private fun TacticalGpsGridCanvas(
  gpsFix: GpsFix,
  networks: List<DiscoveredNetwork>,
  breadcrumbs: List<TrackBreadcrumb>,
  zoomMetersSpan: Float,
  panMetersX: Float,
  panMetersY: Float,
  colorBySecurity: Boolean,
  onPanMeters: (Float, Float) -> Unit,
  onZoomChange: (Float) -> Unit,
  onResetCenter: () -> Unit,
  onSelectNetwork: (DiscoveredNetwork) -> Unit,
  modifier: Modifier = Modifier
) {
  var plottedNodes by remember { mutableStateOf<List<Pair<Offset, DiscoveredNetwork>>>(emptyList()) }

  Surface(
    modifier = modifier.clip(RoundedCornerShape(14.dp)),
    color = ObsidianBg,
    border = BorderStroke(1.dp, GridHairline),
    shape = RoundedCornerShape(14.dp)
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      Canvas(
        modifier = Modifier
          .fillMaxSize()
          .testTag("tactical_gps_grid_canvas")
          .pointerInput(zoomMetersSpan) {
            detectDragGestures { change, dragAmount ->
              change.consume()
              val metersPerPx = zoomMetersSpan / size.width.coerceAtLeast(1)
              onPanMeters(-dragAmount.x * metersPerPx, dragAmount.y * metersPerPx)
            }
          }
          .pointerInput(plottedNodes) {
            detectTapGestures { tapOffset ->
              val hit = plottedNodes.minByOrNull { (pos, _) ->
                hypot(pos.x - tapOffset.x, pos.y - tapOffset.y)
              }
              if (hit != null && hypot(hit.first.x - tapOffset.x, hit.first.y - tapOffset.y) <= 44f) {
                onSelectNetwork(hit.second)
              }
            }
          }
      ) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val metersPerPixel = zoomMetersSpan / w.coerceAtLeast(1f)

        fun latLonToCanvas(lat: Double, lon: Double): Offset {
          val metersNorth = (lat - gpsFix.latitude) * 111320.0 - panMetersY
          val metersEast =
            (lon - gpsFix.longitude) * 111320.0 * cos(Math.toRadians(gpsFix.latitude)) - panMetersX
          val px = cx + (metersEast / metersPerPixel).toFloat()
          val py = cy - (metersNorth / metersPerPixel).toFloat()
          return Offset(px, py)
        }

        // 1. Tactical coordinate grid lines
        val gridSpacingMeters = when {
          zoomMetersSpan <= 160f -> 25f
          zoomMetersSpan <= 400f -> 50f
          else -> 100f
        }
        val gridSpacingPx = (gridSpacingMeters / metersPerPixel).coerceAtLeast(24f)

        var xLine = (cx % gridSpacingPx)
        while (xLine < w) {
          drawLine(
            color = GridHairline,
            start = Offset(xLine, 0f),
            end = Offset(xLine, h),
            strokeWidth = 1f
          )
          xLine += gridSpacingPx
        }
        var yLine = (cy % gridSpacingPx)
        while (yLine < h) {
          drawLine(
            color = GridHairline,
            start = Offset(0f, yLine),
            end = Offset(w, yLine),
            strokeWidth = 1f
          )
          yLine += gridSpacingPx
        }

        // Crosshair axes
        val gpsScreenPos = latLonToCanvas(gpsFix.latitude, gpsFix.longitude)
        drawLine(
          color = TelemetryCyan.copy(alpha = 0.25f),
          start = Offset(gpsScreenPos.x, 0f),
          end = Offset(gpsScreenPos.x, h),
          strokeWidth = 1.5f,
          pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
        )
        drawLine(
          color = TelemetryCyan.copy(alpha = 0.25f),
          start = Offset(0f, gpsScreenPos.y),
          end = Offset(w, gpsScreenPos.y),
          strokeWidth = 1.5f,
          pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
        )

        // 2. GPS Track Breadcrumb Polyline
        if (breadcrumbs.size >= 2) {
          val trackPath = Path()
          breadcrumbs.forEachIndexed { idx, pt ->
            val p = latLonToCanvas(pt.lat, pt.lon)
            if (idx == 0) trackPath.moveTo(p.x, p.y) else trackPath.lineTo(p.x, p.y)
          }
          drawPath(
            path = trackPath,
            color = TelemetryCyan.copy(alpha = 0.7f),
            style = Stroke(width = 4f, cap = StrokeCap.Round)
          )
          breadcrumbs.forEach { pt ->
            val p = latLonToCanvas(pt.lat, pt.lon)
            drawCircle(
              color = TelemetryCyan,
              radius = 3.5f,
              center = p
            )
          }
        }

        // 3. Discovered Wi-Fi AP Beacons & RF Coverage Halos
        val currentNodes = mutableListOf<Pair<Offset, DiscoveredNetwork>>()
        for (net in networks) {
          val pos = latLonToCanvas(net.bestLat, net.bestLon)
          if (pos.x in -60f..(w + 60f) && pos.y in -60f..(h + 60f)) {
            currentNodes.add(pos to net)
            val nodeColor = resolveNodeColor(net, colorBySecurity)
            val normalizedStrength = ((net.currentRssi.coerceIn(-95L, -35L) + 95f) / 60f)
            val haloRadius = (16f + normalizedStrength * 28f)

            drawCircle(
              brush = Brush.radialGradient(
                colors = listOf(nodeColor.copy(alpha = 0.32f), Color.Transparent),
                center = pos,
                radius = haloRadius
              ),
              radius = haloRadius,
              center = pos
            )
            drawCircle(
              color = ObsidianBg,
              radius = 8f,
              center = pos
            )
            drawCircle(
              color = nodeColor,
              radius = 6f,
              center = pos
            )
          }
        }
        plottedNodes = currentNodes

        // 4. Live User GPS Receiver Position & Accuracy Ring
        val accuracyPx = (gpsFix.accuracyMeters / metersPerPixel).coerceIn(10f, 60f)
        drawCircle(
          color = TelemetryCyan.copy(alpha = 0.18f),
          radius = accuracyPx,
          center = gpsScreenPos
        )
        drawCircle(
          color = TelemetryCyan,
          radius = 7f,
          center = gpsScreenPos
        )
        drawCircle(
          color = Color.White,
          radius = 3f,
          center = gpsScreenPos
        )
      }

      // Top-Left Coordinate & Scale Overlay
      Column(
        modifier = Modifier
          .align(Alignment.TopStart)
          .padding(10.dp)
          .background(ObsidianBg.copy(alpha = 0.82f), RoundedCornerShape(8.dp))
          .padding(horizontal = 10.dp, vertical = 6.dp)
      ) {
        Text(
          text = String.format(Locale.US, "LAT %.5f  LON %.5f", gpsFix.latitude, gpsFix.longitude),
          style = MaterialTheme.typography.labelMedium,
          color = TelemetryCyan
        )
        Text(
          text = String.format(
            Locale.US,
            "ALT %.1fm  ±%.1fm  SPAN %.0fm  •  TAP NODE TO INSPECT",
            gpsFix.altitude,
            gpsFix.accuracyMeters,
            zoomMetersSpan
          ),
          style = MaterialTheme.typography.labelSmall,
          color = TextSecondaryHud
        )
      }

      // Right-Side Map Zoom & Center Controls (48dp touch targets)
      Column(
        modifier = Modifier
          .align(Alignment.CenterEnd)
          .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        Surface(
          color = TacticalElevated.copy(alpha = 0.9f),
          shape = CircleShape,
          border = BorderStroke(1.dp, GridHairline)
        ) {
          IconButton(
            onClick = { onZoomChange(0.75f) },
            modifier = Modifier
              .size(48.dp)
              .testTag("map_zoom_in_button")
          ) {
            Icon(
              imageVector = Icons.Default.Add,
              contentDescription = "Zoom In",
              tint = PhosphorEmerald
            )
          }
        }
        Surface(
          color = TacticalElevated.copy(alpha = 0.9f),
          shape = CircleShape,
          border = BorderStroke(1.dp, GridHairline)
        ) {
          IconButton(
            onClick = { onZoomChange(1.35f) },
            modifier = Modifier
              .size(48.dp)
              .testTag("map_zoom_out_button")
          ) {
            Icon(
              imageVector = Icons.Default.Remove,
              contentDescription = "Zoom Out",
              tint = PhosphorEmerald
            )
          }
        }
        Surface(
          color = TacticalElevated.copy(alpha = 0.9f),
          shape = CircleShape,
          border = BorderStroke(1.dp, GridHairline)
        ) {
          IconButton(
            onClick = onResetCenter,
            modifier = Modifier
              .size(48.dp)
              .testTag("map_center_gps_button")
          ) {
            Icon(
              imageVector = Icons.Default.MyLocation,
              contentDescription = "Center on GPS",
              tint = TelemetryCyan
            )
          }
        }
      }

      // Bottom-Start Legend Pill
      Row(
        modifier = Modifier
          .align(Alignment.BottomStart)
          .padding(10.dp)
          .background(ObsidianBg.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
          .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        if (colorBySecurity) {
          LegendDot(color = PhosphorEmerald, label = "WPA3/WPA2")
          LegendDot(color = AmberAlert, label = "WEP/WPA")
          LegendDot(color = CrimsonBeacon, label = "OPEN")
        } else {
          LegendDot(color = PhosphorEmerald, label = "2.4G")
          LegendDot(color = TelemetryCyan, label = "5G")
          LegendDot(color = VioletSpectrum6G, label = "6G")
        }
      }
    }
  }
}

@Composable
private fun PolarRfRadarCanvas(
  gpsFix: GpsFix,
  networks: List<DiscoveredNetwork>,
  colorBySecurity: Boolean,
  onSelectNetwork: (DiscoveredNetwork) -> Unit,
  modifier: Modifier = Modifier
) {
  val infiniteTransition = rememberInfiniteTransition(label = "radar_sweep")
  val sweepAngle by infiniteTransition.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(durationMillis = 3600, easing = LinearEasing),
      repeatMode = RepeatMode.Restart
    ),
    label = "radar_sweep_angle"
  )

  var radarNodes by remember { mutableStateOf<List<Pair<Offset, DiscoveredNetwork>>>(emptyList()) }

  Surface(
    modifier = modifier.clip(RoundedCornerShape(14.dp)),
    color = ObsidianBg,
    border = BorderStroke(1.dp, GridHairline),
    shape = RoundedCornerShape(14.dp)
  ) {
    Box(modifier = Modifier.fillMaxSize()) {
      Canvas(
        modifier = Modifier
          .fillMaxSize()
          .testTag("polar_rf_radar_canvas")
          .pointerInput(radarNodes) {
            detectTapGestures { tapOffset ->
              val hit = radarNodes.minByOrNull { (pos, _) ->
                hypot(pos.x - tapOffset.x, pos.y - tapOffset.y)
              }
              if (hit != null && hypot(hit.first.x - tapOffset.x, hit.first.y - tapOffset.y) <= 44f) {
                onSelectNetwork(hit.second)
              }
            }
          }
      ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val maxRadius = (minOf(size.width, size.height) * 0.43f).coerceAtLeast(40f)

        // Concentric RSSI dBm Rings (-45 dBm, -65 dBm, -80 dBm, -95 dBm)
        val ringFractions = listOf(0.25f, 0.50f, 0.75f, 1.0f)
        for (fraction in ringFractions) {
          drawCircle(
            color = PhosphorEmerald.copy(alpha = 0.22f),
            radius = maxRadius * fraction,
            center = Offset(cx, cy),
            style = Stroke(width = 1.5f)
          )
        }

        // Crosshairs
        drawLine(
          color = PhosphorEmerald.copy(alpha = 0.18f),
          start = Offset(cx - maxRadius, cy),
          end = Offset(cx + maxRadius, cy),
          strokeWidth = 1f
        )
        drawLine(
          color = PhosphorEmerald.copy(alpha = 0.18f),
          start = Offset(cx, cy - maxRadius),
          end = Offset(cx, cy + maxRadius),
          strokeWidth = 1f
        )

        // Sweeping Radar Sector Arc
        drawArc(
          brush = Brush.sweepGradient(
            colors = listOf(
              Color.Transparent,
              PhosphorEmerald.copy(alpha = 0.05f),
              PhosphorEmerald.copy(alpha = 0.28f)
            ),
            center = Offset(cx, cy)
          ),
          startAngle = sweepAngle - 45f,
          sweepAngle = 45f,
          useCenter = true,
          topLeft = Offset(cx - maxRadius, cy - maxRadius),
          size = Size(maxRadius * 2f, maxRadius * 2f)
        )

        val sweepRad = Math.toRadians(sweepAngle.toDouble())
        drawLine(
          color = PhosphorEmerald,
          start = Offset(cx, cy),
          end = Offset(
            cx + (maxRadius * cos(sweepRad)).toFloat(),
            cy + (maxRadius * sin(sweepRad)).toFloat()
          ),
          strokeWidth = 2f
        )

        // Plot AP blips by bearing & RSSI attenuation (-35 dBm near center, -95 dBm outer edge)
        val blips = mutableListOf<Pair<Offset, DiscoveredNetwork>>()
        for (net in networks) {
          val bearingDeg = if (net.bestLat != gpsFix.latitude || net.bestLon != gpsFix.longitude) {
            GeoMath.bearingDegrees(gpsFix.latitude, gpsFix.longitude, net.bestLat, net.bestLon)
          } else {
            (net.bssid.hashCode() and 0x7FFFFFFF) % 360f
          }
          val rssiClamped = net.currentRssi.coerceIn(-95L, -35L).toFloat()
          val distanceFraction = ((-35f - rssiClamped) / 60f).coerceIn(0.08f, 0.96f)
          val r = maxRadius * distanceFraction
          val rad = Math.toRadians((bearingDeg - 90f).toDouble())
          val bx = cx + (r * cos(rad)).toFloat()
          val by = cy + (r * sin(rad)).toFloat()
          val blipPos = Offset(bx, by)
          blips.add(blipPos to net)

          val blipColor = resolveNodeColor(net, colorBySecurity)
          drawCircle(
            color = blipColor.copy(alpha = 0.28f),
            radius = 12f,
            center = blipPos
          )
          drawCircle(
            color = blipColor,
            radius = 5.5f,
            center = blipPos
          )
        }
        radarNodes = blips

        // Center receiver node
        drawCircle(
          color = TelemetryCyan,
          radius = 5f,
          center = Offset(cx, cy)
        )
      }

      Column(
        modifier = Modifier
          .align(Alignment.TopStart)
          .padding(10.dp)
          .background(ObsidianBg.copy(alpha = 0.82f), RoundedCornerShape(8.dp))
          .padding(horizontal = 10.dp, vertical = 6.dp)
      ) {
        Text(
          text = "POLAR RF SIGNAL RADAR",
          style = MaterialTheme.typography.labelMedium,
          color = PhosphorEmerald
        )
        Text(
          text = "RINGS: -45 / -65 / -80 / -95 dBm  •  ${networks.size} BEACONS",
          style = MaterialTheme.typography.labelSmall,
          color = TextSecondaryHud
        )
      }
    }
  }
}

private fun resolveNodeColor(net: DiscoveredNetwork, colorBySecurity: Boolean): Color {
  return if (colorBySecurity) {
    when (net.parsedSecurity()) {
      SecurityType.OPEN -> CrimsonBeacon
      SecurityType.WEP, SecurityType.WPA -> AmberAlert
      SecurityType.WPA3 -> TelemetryCyan
      SecurityType.WPA2, SecurityType.OWE -> PhosphorEmerald
    }
  } else {
    when (net.band) {
      "6 GHz" -> VioletSpectrum6G
      "5 GHz" -> TelemetryCyan
      else -> PhosphorEmerald
    }
  }
}

@Composable
private fun LegendDot(color: Color, label: String) {
  Row(verticalAlignment = Alignment.CenterVertically) {
    Box(
      modifier = Modifier
        .size(8.dp)
        .background(color, CircleShape)
    )
    Spacer(modifier = Modifier.width(4.dp))
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurface
    )
  }
}

@Composable
private fun SessionRecorderStrip(
  isRecording: Boolean,
  elapsedSeconds: Long,
  distanceMeters: Double,
  networkCount: Int,
  openCount: Int,
  onStartOrResume: () -> Unit,
  onPause: () -> Unit,
  onSaveAndSync: () -> Unit
) {
  val minutes = elapsedSeconds / 60
  val seconds = elapsedSeconds % 60
  val timeFormatted = String.format(Locale.US, "%02d:%02d", minutes, seconds)
  val distFormatted = if (distanceMeters >= 1000.0) {
    String.format(Locale.US, "%.2f km", distanceMeters / 1000.0)
  } else {
    String.format(Locale.US, "%.0f m", distanceMeters)
  }

  Surface(
    color = TacticalSlate,
    shape = RoundedCornerShape(14.dp),
    border = BorderStroke(
      1.dp,
      if (isRecording) PhosphorEmerald.copy(alpha = 0.6f) else GridHairline
    ),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 12.dp, vertical = 10.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "DRIVE $timeFormatted",
            style = MaterialTheme.typography.labelLarge,
            color = if (isRecording) PhosphorEmerald else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = distFormatted,
            style = MaterialTheme.typography.labelLarge,
            color = TelemetryCyan
          )
        }
        Text(
          text = "$networkCount APs Mapped  •  $openCount Open/WEP",
          style = MaterialTheme.typography.labelSmall,
          color = TextSecondaryHud
        )
      }

      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!isRecording) {
          Button(
            onClick = onStartOrResume,
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
              containerColor = PhosphorEmerald,
              contentColor = ObsidianBg
            ),
            modifier = Modifier.testTag("start_wardrive_button")
          ) {
            Icon(
              imageVector = Icons.Default.PlayArrow,
              contentDescription = "Start Wardrive",
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = if (elapsedSeconds > 0L) "Resume" else "Record",
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold
            )
          }
        } else {
          OutlinedButton(
            onClick = onPause,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, AmberAlert),
            modifier = Modifier.testTag("pause_wardrive_button")
          ) {
            Icon(
              imageVector = Icons.Default.Pause,
              contentDescription = "Pause Wardrive",
              tint = AmberAlert,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "Pause",
              style = MaterialTheme.typography.labelLarge,
              color = AmberAlert
            )
          }
        }

        OutlinedButton(
          onClick = onSaveAndSync,
          shape = RoundedCornerShape(10.dp),
          border = BorderStroke(1.dp, TelemetryCyan),
          modifier = Modifier.testTag("sync_session_cloud_button")
        ) {
          Icon(
            imageVector = Icons.Default.CloudUpload,
            contentDescription = "Sync Session to Cloud",
            tint = TelemetryCyan,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(4.dp))
          Text(
            text = "Sync",
            style = MaterialTheme.typography.labelLarge,
            color = TelemetryCyan
          )
        }
      }
    }
  }
}
