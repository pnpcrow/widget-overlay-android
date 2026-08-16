package com.example.widgetoverlay.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.annotation.AttrRes
import com.example.widgetoverlay.R
import com.google.android.material.color.MaterialColors

/**
 * Single source for color-role lookup. Activities apply dynamic colors in onCreate; the overlay
 * service resolves colors through [themed] so every surface shares the wallpaper-derived palette
 * on Android 12+ and the static palette elsewhere. Dark/light follows the system setting.
 */
object AppTheme {

    /**
     * Applies the dynamic color overlay to the given context's own theme (Android 12+) and
     * returns the same context. The overlay must be applied in place: wrapping a Service in a
     * plain ContextThemeWrapper would start from an EMPTY theme, losing the app theme's color
     * attributes and crashing attribute resolution.
     *
     * The day/night overlay variant is chosen explicitly: applyStyle bakes a style's
     * configuration variant at apply time, so callers re-invoke this whenever the system
     * uiMode changes (see [themed] with an explicit night flag).
     */
    fun themed(context: Context): Context =
        themed(context, isNightMode(context))

    fun themed(context: Context, night: Boolean): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val overlay = if (night) {
                R.style.ThemeOverlay_WidgetOverlay_DynamicColors_Night
            } else {
                R.style.ThemeOverlay_WidgetOverlay_DynamicColors_Day
            }
            context.theme.applyStyle(overlay, true)
        }
        return context
    }

    private fun isNightMode(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    fun color(context: Context, @AttrRes attr: Int): Int =
        MaterialColors.getColor(context, attr, "AppTheme: attr not resolved in theme")
}
