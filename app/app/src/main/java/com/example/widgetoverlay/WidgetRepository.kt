package com.example.widgetoverlay

import android.appwidget.AppWidgetManager
import android.content.Context

/**
 * Stores widget IDs for the widget stack.
 * Widget data remains with its provider.
 */
class WidgetRepository(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val appWidgetManager = AppWidgetManager.getInstance(context.applicationContext)

    // --- Single widget (legacy compatibility) ---

    fun selectedWidgetId(): Int? {
        val id = preferences.getInt(KEY_SELECTED_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        return id.takeIf { it != AppWidgetManager.INVALID_APPWIDGET_ID }
    }

    fun saveSelectedWidget(id: Int) {
        require(id != AppWidgetManager.INVALID_APPWIDGET_ID) { "A valid appWidgetId is required." }
        preferences.edit().putInt(KEY_SELECTED_WIDGET_ID, id).apply()
    }

    fun clearSelectedWidget() {
        preferences.edit().remove(KEY_SELECTED_WIDGET_ID).apply()
    }

    fun isSelectedWidgetValid(): Boolean {
        val id = selectedWidgetId() ?: return false
        return appWidgetManager.getAppWidgetInfo(id) != null
    }

    // --- Widget Stack ---

    fun getWidgetStack(): List<Int> {
        val raw = preferences.getString(KEY_WIDGET_STACK, null)
        val ids = raw?.split(",")
            ?.mapNotNull { it.toIntOrNull() }
            ?: emptyList()
        // Filter out invalid IDs but keep the stack structure
        return ids.filter { it != AppWidgetManager.INVALID_APPWIDGET_ID }
    }

    fun addToStack(widgetId: Int) {
        require(widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) { "A valid appWidgetId is required." }
        val current = getRawStack().toMutableList()
        if (!current.contains(widgetId)) {
            current.add(widgetId)
            saveStack(current)
        }
    }

    fun removeFromStack(widgetId: Int) {
        val current = getRawStack().toMutableList()
        current.remove(widgetId)
        saveStack(current)
    }

    fun clearStack() {
        preferences.edit().remove(KEY_WIDGET_STACK).apply()
    }

    fun getStackCount(): Int = getWidgetStack().size

    fun getStackIndex(widgetId: Int): Int = getWidgetStack().indexOf(widgetId)

    fun getWidgetAtStackIndex(index: Int): Int? {
        val stack = getWidgetStack()
        return if (index in stack.indices) stack[index] else null
    }

    /**
     * Get raw stack without filtering invalid IDs.
     * Used internally for stack manipulation.
     */
    private fun getRawStack(): List<Int> {
        return preferences.getString(KEY_WIDGET_STACK, null)
            ?.split(",")
            ?.mapNotNull { it.toIntOrNull() }
            ?: emptyList()
    }

    private fun saveStack(ids: List<Int>) {
        preferences.edit()
            .putString(KEY_WIDGET_STACK, ids.joinToString(","))
            .commit() // Use commit() instead of apply() for immediate save
    }

    companion object {
        private const val PREFERENCES_NAME = "widget_overlay_preferences"
        private const val KEY_SELECTED_WIDGET_ID = "selected_widget_id"
        private const val KEY_WIDGET_STACK = "widget_stack"
    }
}

enum class SurfaceRoute {
    NONE,
    IN_APP_WIDGET,
    OVERLAY_WIDGET,
    BUBBLE_NOTIFICATION,
    LIVE_UPDATE_NOTIFICATION,
}

/** Pure policy functions are unit-tested and keep UI permission checks explicit. */
object SurfacePolicy {
    fun widgetRoute(hasWidget: Boolean, overlayPermissionGranted: Boolean): SurfaceRoute = when {
        !hasWidget -> SurfaceRoute.NONE
        overlayPermissionGranted -> SurfaceRoute.OVERLAY_WIDGET
        else -> SurfaceRoute.IN_APP_WIDGET
    }

    fun bubbleRoute(userRequested: Boolean, notificationsAllowed: Boolean): SurfaceRoute = when {
        userRequested && notificationsAllowed -> SurfaceRoute.BUBBLE_NOTIFICATION
        else -> SurfaceRoute.NONE
    }

    fun liveUpdateRoute(
        apiLevel: Int,
        userStartedJourney: Boolean,
        notificationsAllowed: Boolean,
    ): SurfaceRoute = when {
        apiLevel >= 36 && userStartedJourney && notificationsAllowed -> SurfaceRoute.LIVE_UPDATE_NOTIFICATION
        else -> SurfaceRoute.NONE
    }
}
