package com.baoverung.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = ForestGreenMain,
    secondary = EarthBrown,
    tertiary = MossGreen,
    background = OnSurfaceDark,
    surface = OnSurfaceDark,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
  )

private val LightColorScheme =
  lightColorScheme(
    primary = ForestGreenMain,
    secondary = EarthBrown,
    tertiary = MossGreen,
    background = SurfaceLight,
    surface = SurfaceLight,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = OnSurfaceDark,
    onSurface = OnSurfaceDark,
    primaryContainer = ForestGreenLight,
    onPrimaryContainer = ForestGreenDark,
    secondaryContainer = Color(0xFFD7CCC8),
    onSecondaryContainer = EarthBrownDark
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  // Typography cần được định nghĩa trong Type.kt shared
  MaterialTheme(colorScheme = colorScheme, content = content)
}
