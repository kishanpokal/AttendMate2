package com.kishan.attendmate.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun AttendMateTheme(
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val prefs = androidx.compose.runtime.remember { com.kishan.attendmate.util.PreferencesManager(context) }
    
    val themePrefState = androidx.compose.runtime.remember { 
        androidx.compose.runtime.mutableStateOf(prefs.getThemePreference()) 
    }

    androidx.compose.runtime.DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == com.kishan.attendmate.util.PreferencesManager.KEY_THEME) {
                themePrefState.value = prefs.getThemePreference()
            }
        }
        val sharedPrefs = context.getSharedPreferences(
            com.kishan.attendmate.util.PreferencesManager.PREFS_NAME, 
            android.content.Context.MODE_PRIVATE
        )
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val darkTheme = when (themePrefState.value) {
        com.kishan.attendmate.util.PreferencesManager.THEME_LIGHT -> false
        com.kishan.attendmate.util.PreferencesManager.THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }

    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
