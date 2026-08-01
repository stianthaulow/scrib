package dev.thaulow.scrib.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import dev.thaulow.scrib.R

// background/surface are filled in from @color/window_background at composition time.
private val LightColors =
  lightColorScheme(
    onBackground = Color(0xFF1A1A1A),
    onSurface = Color(0xFF1A1A1A),
  )

private val DarkColors =
  darkColorScheme(
    onBackground = Color(0xFFF0F0F0),
    onSurface = Color(0xFFF0F0F0),
  )

@Composable
fun ScribTheme(content: @Composable () -> Unit) {
  // Shared with the launch window's android:windowBackground, so a cold start shows no colour
  // step when Compose takes over. @color/window_background is the single source of truth, and
  // is itself night-aware, so this resolves to the dark value under -night.
  val background = colorResource(R.color.window_background)
  val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
  MaterialTheme(
    colorScheme = colors.copy(background = background, surface = background),
    content = content,
  )
}
