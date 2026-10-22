package com.example.export

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import com.example.data.model.DiscoveredNetwork
import com.example.data.model.TrackBreadcrumb
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object WardriveExporter {

  private fun formatTimestamp(epochMs: Long): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Date(epochMs))
  }

  /**
   * Builds standard WiGLE v1.4 CSV content from discovered networks.
   */
  fun buildWigleCsv(networks: List<DiscoveredNetwork>): String {
    val sb = StringBuilder()
    val model = Build.MODEL?.replace(",", " ") ?: "AndroidDevice"
    val release = Build.VERSION.RELEASE ?: "16"
    sb.appendLine(
      "WigleWifi-1.4,appRelease=1.0,model=$model,release=$release,device=AetherPulse,display=TacticalHUD,board=Android,brand=AetherPulse"
    )
    sb.appendLine(
      "MAC,SSID,AuthMode,FirstSeen,Channel,RSSI,CurrentLatitude,CurrentLongitude,AltitudeMeters,AccuracyMeters,Type"
    )
    for (net in networks) {
      val safeSsid = net.ssid.replace(",", " ").replace("\"", "")
      val tsMs = (net.updatedAt?.seconds ?: net.createdAt?.seconds)?.times(1000L)
        ?: System.currentTimeMillis()
      val seenStr = formatTimestamp(tsMs)
      sb.appendLine(
        "${net.bssid},$safeSsid,${net.capabilities},$seenStr,${net.channel},${net.bestRssi}," +
          String.format(Locale.US, "%.6f,%.6f,%.1f,%.1f,WIFI", net.bestLat, net.bestLon, net.bestAlt, net.accuracyMeters)
      )
    }
    return sb.toString()
  }

  /**
   * Builds Google Earth / GIS compatible KML content with GPS track LineString and AP Placemarks.
   */
  fun buildKmlDocument(
    sessionTitle: String,
    networks: List<DiscoveredNetwork>,
    breadcrumbs: List<TrackBreadcrumb>
  ): String {
    val sb = StringBuilder()
    sb.appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
    sb.appendLine("""<kml xmlns="http://www.opengis.net/kml/2.2">""")
    sb.appendLine("""  <Document>""")
    sb.appendLine("""    <name>${escapeXml(sessionTitle)}</name>""")
    sb.appendLine("""    <description>AetherPulse Wardriving RF Telemetry Export (${networks.size} APs)</description>""")
    sb.appendLine("""    <Style id="trackStyle"><LineStyle><color>ffa0f500</color><width>4</width></LineStyle></Style>""")
    sb.appendLine("""    <Style id="openApStyle"><IconStyle><color>ff5c3bff</color><scale>1.1</scale></IconStyle></Style>""")
    sb.appendLine("""    <Style id="secureApStyle"><IconStyle><color>ffa0f500</color><scale>1.0</scale></IconStyle></Style>""")

    if (breadcrumbs.isNotEmpty()) {
      sb.appendLine("""    <Placemark>""")
      sb.appendLine("""      <name>GPS Wardrive Track (${breadcrumbs.size} pts)</name>""")
      sb.appendLine("""      <styleUrl>#trackStyle</styleUrl>""")
      sb.appendLine("""      <LineString>""")
      sb.appendLine("""        <tessellate>1</tessellate>""")
      sb.appendLine("""        <coordinates>""")
      for (pt in breadcrumbs) {
        sb.appendLine(String.format(Locale.US, "          %.6f,%.6f,%.1f", pt.lon, pt.lat, pt.altitude))
      }
      sb.appendLine("""        </coordinates>""")
      sb.appendLine("""      </LineString>""")
      sb.appendLine("""    </Placemark>""")
    }

    for (net in networks) {
      val isOpen = net.parsedSecurity().isVulnerableOrOpen
      val styleUrl = if (isOpen) "#openApStyle" else "#secureApStyle"
      sb.appendLine("""    <Placemark>""")
      sb.appendLine("""      <name>${escapeXml(net.ssid)} (${net.bestRssi} dBm)</name>""")
      sb.appendLine("""      <styleUrl>$styleUrl</styleUrl>""")
      sb.appendLine(
        """      <description><![CDATA[<b>BSSID:</b> ${net.bssid}<br/><b>Security:</b> ${net.securityType} (${escapeXml(net.capabilities)})<br/><b>Band / Ch:</b> ${net.band} Ch ${net.channel} (${net.frequencyMhz} MHz, ${net.channelWidthMhz} MHz)<br/><b>Peak RSSI:</b> ${net.bestRssi} dBm<br/><b>Vendor:</b> ${escapeXml(net.vendor)}]]></description>"""
      )
      sb.appendLine("""      <Point>""")
      sb.appendLine(
        String.format(Locale.US, "        <coordinates>%.6f,%.6f,%.1f</coordinates>", net.bestLon, net.bestLat, net.bestAlt)
      )
      sb.appendLine("""      </Point>""")
      sb.appendLine("""    </Placemark>""")
    }

    sb.appendLine("""  </Document>""")
    sb.appendLine("""</kml>""")
    return sb.toString()
  }

  fun shareExportFile(
    context: Context,
    fileName: String,
    mimeType: String,
    content: String,
    chooserTitle: String
  ): Result<File> {
    return try {
      val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
      val file = File(exportDir, fileName)
      file.writeText(content)

      val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
      )

      val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, fileName)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      val chooser = Intent.createChooser(shareIntent, chooserTitle).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(chooser)
      Result.success(file)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  /**
   * Saves the exported CSV or KML directly into the public Android Downloads folder
   * (using MediaStore.Downloads on API 29+ or cache/Downloads fallback) so the user can download
   * files directly even when no Share Sheet target app is installed.
   */
  fun saveToDownloadsFolder(
    context: Context,
    fileName: String,
    mimeType: String,
    content: String
  ): Result<String> {
    return try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val values = android.content.ContentValues().apply {
          put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName)
          put(android.provider.MediaStore.Downloads.MIME_TYPE, mimeType)
          put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
          put(android.provider.MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        if (uri != null) {
          resolver.openOutputStream(uri)?.use { out ->
            out.write(content.toByteArray(Charsets.UTF_8))
          }
          values.clear()
          values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0)
          resolver.update(uri, values, null, null)
          return Result.success("Downloads/$fileName")
        }
      }
      val fallbackDir = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "exports").apply {
        mkdirs()
      }
      val fallbackFile = File(fallbackDir, fileName)
      fallbackFile.writeText(content)
      Result.success(fallbackFile.absolutePath)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  private fun escapeXml(input: String): String =
    input
      .replace("&", "&amp;")
      .replace("<", "&lt;")
      .replace(">", "&gt;")
      .replace("\"", "&quot;")
      .replace("'", "&apos;")
}
