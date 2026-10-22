package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TacticalDarkColorScheme = darkColorScheme(
  primary = PhosphorEmerald,
  onPrimary = Color(0xFF002114),
  primaryContainer = Color(0xFF003824),
  onPrimaryContainer = PhosphorEmerald,
  secondary = TelemetryCyan,
  onSecondary = Color(0xFF001F28),
  secondaryContainer = Color(0xFF003543),
  onSecondaryContainer = TelemetryCyan,
  tertiary = AmberAlert,
  onTertiary = Color(0xFF261900),
  tertiaryContainer = Color(0xFF3E2B00),
  onTertiaryContainer = AmberAlert,
  error = CrimsonBeacon,
  onError = Color.White,
  errorContainer = Color(0xFF420A16),
  onErrorContainer = Color(0xFFFFB2BC),
  background = ObsidianBg,
  onBackground = TextPrimaryHud,
  surface = TacticalSlate,
  onSurface = TextPrimaryHud,
  surfaceVariant = TacticalElevated,
  onSurfaceVariant = TextSecondaryHud,
  outline = GridHairline
)

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true,
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = TacticalDarkColorScheme,
    typography = Typography,
    content = content
  )
}
