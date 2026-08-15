package com.example.widgetoverlay.ui

import android.content.Context
import android.os.Build
import androidx.annotation.AttrRes
import com.example.widgetoverlay.R
import com.google.android.material.color.MaterialColors

/**
 * Single source for color-role lookup. Activities apply dynamic colors in onCreate; the overlay
 * service patches its theme through [themed] so every surface shares the wallpaper-derived
 * palette on Android 12+ and the static palette elsewhere. Dark/light follows the system setting.
 */
object AppTheme {

    /**
     * Applies the dynamic color overlay to the given context's own theme (Android 12+) and
     * returns the same context. The overlay must be applied in place: wrapping a Service in a
     * plain ContextThemeWrapper would start from an EMPTY theme, losing the app theme's color
     * attributes and crashing attribute resolution.
     */
    fun themed(context: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.theme.applyStyle(R.style.ThemeOverlay_WidgetOverlay_DynamicColors, true)
        }
        return context
    }

    fun color(context: Context, @AttrRes attr: Int): Int =
        MaterialColors.getColor(context, attr, "AppTheme: attr not resolved in theme")
}
