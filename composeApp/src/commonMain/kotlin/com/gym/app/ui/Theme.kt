package com.gym.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.gym.app.AppPlatform

// "You" is shown in primary (orange), your partner in tertiary (teal), everywhere in the app.
private val Dark = darkColorScheme(
    primary = Color(0xFFFF7A45),
    onPrimary = Color(0xFF2A0E00),
    primaryContainer = Color(0xFF5A2410),
    onPrimaryContainer = Color(0xFFFFDBCC),
    tertiary = Color(0xFF3CC8B4),
    onTertiary = Color(0xFF00201B),
    tertiaryContainer = Color(0xFF0E4A42),
    onTertiaryContainer = Color(0xFFB4F1E6),
    background = Color(0xFF111317),
    surface = Color(0xFF111317),
    surfaceContainerLowest = Color(0xFF0C0E11),
    surfaceContainerLow = Color(0xFF181A1F),
    surfaceContainer = Color(0xFF1C1F24),
    surfaceContainerHigh = Color(0xFF23262C),
    surfaceContainerHighest = Color(0xFF2C2F36),
)

private val Light = lightColorScheme(
    primary = Color(0xFFC2410C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCC),
    onPrimaryContainer = Color(0xFF3A1200),
    tertiary = Color(0xFF0F766E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFB4F1E6),
    onTertiaryContainer = Color(0xFF00201B),
)

enum class ThemeMode(val label: String) { System("Phone setting"), Light("Light"), Dark("Dark") }

/** Light/dark and wallpaper colours: a per-phone choice, saved on the phone. */
class Appearance(private val platform: AppPlatform) {
    var mode by mutableStateOf(
        ThemeMode.entries.firstOrNull { it.name == platform.loadSetting("theme") } ?: ThemeMode.System
    )
        private set
    var dynamicColors by mutableStateOf(platform.loadSetting("dynamicColors") == "true")
        private set

    fun changeMode(m: ThemeMode) {
        mode = m
        platform.saveSetting("theme", m.name)
    }

    fun changeDynamicColors(on: Boolean) {
        dynamicColors = on
        platform.saveSetting("dynamicColors", on.toString())
    }
}

val LocalAppearance = staticCompositionLocalOf<Appearance?> { null }

/** The phone's wallpaper-based colours (Android 12+), or null where there are none. */
@Composable
expect fun dynamicColorScheme(dark: Boolean): ColorScheme?

/** Whether the phone can do wallpaper colours at all (Android 12+). */
expect val dynamicColorsAvailable: Boolean

@Composable
fun GymTheme(appearance: Appearance?, content: @Composable () -> Unit) {
    val dark = when (appearance?.mode ?: ThemeMode.System) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val scheme = (if (appearance?.dynamicColors == true) dynamicColorScheme(dark) else null) ?: if (dark) Dark else Light
    MaterialTheme(colorScheme = scheme, content = content)
}

/** True when the current theme is dark (for system bar icons). */
@Composable
fun isAppDark(appearance: Appearance?): Boolean = when (appearance?.mode ?: ThemeMode.System) {
    ThemeMode.System -> isSystemInDarkTheme()
    ThemeMode.Light -> false
    ThemeMode.Dark -> true
}
