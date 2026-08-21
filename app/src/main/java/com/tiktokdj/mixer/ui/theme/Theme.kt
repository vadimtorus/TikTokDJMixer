package com.tiktokdj.mixer.ui.theme

import android.app.Activity
import android.os.Build
import android.util.Log
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Тёмная цветовая схема приложения (в стиле TikTok DJ).
 * Dark color scheme of the application (TikTok DJ style).
 */
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFBB86FC),
    secondary = Color(0xFF03DAC6),
    tertiary = Color(0xFF03DAC6),
    background = Color(0xFF1A1A2E),
    surface = Color(0xFF16213E),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2D2D44),
    onSurfaceVariant = Color(0xFFCAC4D0)
)

/**
 * Светлая цветовая схема приложения (Material по умолчанию).
 * Light color scheme of the application (default Material palette).
 */
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF6650a4),
    secondary = Color(0xFF625b71),
    tertiary = Color(0xFF7D5260)
)

/**
 * Главная тема приложения.
 * Main theme of the application.
 *
 * @param darkTheme использовать тёмную тему / use dark theme
 * @param dynamicColor динамические цвета Android 12+ / dynamic colors on Android 12+
 * @param content содержимое Compose / Compose content
 */
@Composable
fun TikTokDJTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // Выбор цветовой схемы: динамическая (Android 12+), тёмная или светлая.
    // Color scheme selection: dynamic (Android 12+), dark or light.
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // ИСПРАВЛЕНО: небезопасный каст `view.context as Activity` заменён на безопасный
            // `as? Activity` с проверкой на null. Если контекст обёрнут (например,
            // ContextThemeWrapper), прямой каст приводил к ClassCastException и крашу.
            //
            // FIXED: the unsafe cast `view.context as Activity` has been replaced with a safe
            // `as? Activity` cast plus a null guard. If the context is wrapped (e.g. inside a
            // ContextThemeWrapper), the direct cast caused a ClassCastException crash.
            val activity = view.context as? Activity
            if (activity != null) {
                val window = activity.window
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars =
                    !darkTheme
            } else {
                // Контекст не является Activity — пропускаем настройку статус-бара.
                // Context is not an Activity — skip status bar appearance setup.
                Log.w(
                    "TikTokDJTheme",
                    "Контекст не Activity, вид статус-бара не изменён / " +
                        "Context is not an Activity, status bar appearance not updated"
                )
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
