package com.kishan.attendmate.ui.theme

import com.kishan.attendmate.ui.theme.statusColors

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
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight, onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight, onPrimaryContainer = OnPrimaryContainerLight,
    secondary = SecondaryLight, onSecondary = OnSecondaryLight,
    secondaryContainer = SecondaryContainerLight, onSecondaryContainer = OnSecondaryContainerLight,
    tertiary = TertiaryLight, onTertiary = OnTertiaryLight,
    tertiaryContainer = TertiaryContainerLight, onTertiaryContainer = OnTertiaryContainerLight,
    background = BackgroundLight, surface = SurfaceLight, surfaceVariant = SurfaceVariantLight,
    onSurface = OnSurfaceLight, onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight, outlineVariant = OutlineVariantLight,
    error = ErrorLight, onError = OnErrorLight,
    errorContainer = ErrorContainerLight, onErrorContainer = OnErrorContainerLight
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark, onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark, onPrimaryContainer = OnPrimaryContainerDark,
    secondary = SecondaryDark, onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark, onSecondaryContainer = OnSecondaryContainerDark,
    tertiary = TertiaryDark, onTertiary = OnTertiaryDark,
    tertiaryContainer = TertiaryContainerDark, onTertiaryContainer = OnTertiaryContainerDark,
    background = BackgroundDark, surface = SurfaceDark, surfaceVariant = SurfaceVariantDark,
    onSurface = OnSurfaceDark, onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark, outlineVariant = OutlineVariantDark,
    error = ErrorDark, onError = OnErrorDark,
    errorContainer = ErrorContainerDark, onErrorContainer = OnErrorContainerDark
)

data class StatusColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
    val info: Color,
    val onInfo: Color,
    val infoContainer: Color,
    val onInfoContainer: Color
)

@Composable
fun statusColors(): StatusColors = if (isSystemInDarkTheme()) {
    StatusColors(
        SuccessDark, OnSuccessDark, SuccessContainerDark, OnSuccessContainerDark,
        WarningDark, OnWarningDark, WarningContainerDark, OnWarningContainerDark,
        ErrorDark, OnErrorDark, ErrorContainerDark, OnErrorContainerDark,
        InfoDark, OnInfoDark, InfoContainerDark, OnInfoContainerDark
    )
} else {
    StatusColors(
        SuccessLight, OnSuccessLight, SuccessContainerLight, OnSuccessContainerLight,
        WarningLight, OnWarningLight, WarningContainerLight, OnWarningContainerLight,
        ErrorLight, OnErrorLight, ErrorContainerLight, OnErrorContainerLight,
        InfoLight, OnInfoLight, InfoContainerLight, OnInfoContainerLight
    )
}

@Composable
fun chartColors(): List<Color> = if (isSystemInDarkTheme()) ChartDarkColors else ChartLightColors

@Composable
fun authGradientColors(): List<Color> = if (isSystemInDarkTheme()) AuthGradientDarkColors else AuthGradientLightColors

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

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = androidx.compose.ui.platform.LocalView.current
    if (!view.isInEditMode) {
        androidx.compose.runtime.SideEffect {
            val window = (view.context as? android.app.Activity)?.window
            if (window != null) {
                androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                androidx.core.view.WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
