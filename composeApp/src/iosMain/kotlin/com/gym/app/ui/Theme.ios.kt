package com.gym.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/** iOS has no wallpaper-based colours. */
@Composable
actual fun dynamicColorScheme(dark: Boolean): ColorScheme? = null

actual val dynamicColorsAvailable: Boolean = false
