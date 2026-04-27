package dev.thaulow.scrib.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
  primary = Color(0xFF005FB8),
  onPrimary = Color(0xFFFFFFFF),
  primaryContainer = Color(0xFFD6E8FF),
  onPrimaryContainer = Color(0xFF003063),
  secondaryContainer = Color(0xFFE8E8E8),
  onSecondaryContainer = Color(0xFF3B3B3B),
  background = Color(0xFFFFFFFF),
  onBackground = Color(0xFF3B3B3B),
  surface = Color(0xFFF8F8F8),
  onSurface = Color(0xFF3B3B3B),
  surfaceVariant = Color(0xFFE8E8E8),
  onSurfaceVariant = Color(0xFF616161),
  outline = Color(0xFFCCCCCC),
  outlineVariant = Color(0xFFE8E8E8),
)

private val DarkColorScheme = darkColorScheme(
  primary = Color(0xFF0078D4),
  onPrimary = Color(0xFFFFFFFF),
  primaryContainer = Color(0xFF004B87),
  onPrimaryContainer = Color(0xFFCCE0FF),
  secondaryContainer = Color(0xFF313131),
  onSecondaryContainer = Color(0xFFCCCCCC),
  background = Color(0xFF1F1F1F),
  onBackground = Color(0xFFCCCCCC),
  surface = Color(0xFF181818),
  onSurface = Color(0xFFCCCCCC),
  surfaceVariant = Color(0xFF313131),
  onSurfaceVariant = Color(0xFF9D9D9D),
  outline = Color(0xFF616161),
  outlineVariant = Color(0xFF3B3B3B),
)

@Composable
fun ScribTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  MaterialTheme(
    colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
    typography = Typography,
    content = content,
  )
}
