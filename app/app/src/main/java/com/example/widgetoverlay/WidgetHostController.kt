package com.example.widgetoverlay

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * Owns the AppWidgetHost contract. It never reads provider data directly: the provider supplies
 * RemoteViews to the AppWidgetHostView through the platform.
 */
class WidgetHostController(private val context: Context) {
    private val appContext = context.applicationContext
    private val appWidgetManager = AppWidgetManager.getInstance(appContext)
    private val repository = WidgetRepository(appContext)
    private val host = AppWidgetHost(appContext, HOST_ID)

    private var isListening = false
    private var isStackMode = false

    fun beginWidgetPick(activity: Activity) {
        isStackMode = false
        val intent = Intent(activity, WidgetPickerActivity::class.java)
        activity.startActivityForResult(intent, REQUEST_PICK_WIDGET)
    }

    fun beginWidgetPickForStack(activity: Activity) {
        isStackMode = true
        val intent = Intent(activity, WidgetPickerActivity::class.java)
        activity.startActivityForResult(intent, REQUEST_PICK_WIDGET)
    }

    /** Returns true if the result belonged to this controller. */
    fun handleActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        return when (requestCode) {
            REQUEST_PICK_WIDGET -> {
                if (resultCode != Activity.RESULT_OK || data == null) {
                    isStackMode = false
                    return true
                }

                val chosenId = data.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID,
                )
                if (chosenId == AppWidgetManager.INVALID_APPWIDGET_ID) {
                    isStackMode = false
                    return true
                }

                if (appWidgetManager.getAppWidgetInfo(chosenId) == null) {
                    try {
                        host.deleteAppWidgetId(chosenId)
                    } catch (_: Exception) {
                        // Ignore errors
                    }
                    isStackMode = false
                    return true
                }

                commitSelectedWidget(chosenId)
                isStackMode = false
                true
            }

            else -> false
        }
    }

    // --- Single widget ---

    fun selectedWidgetId(): Int? = repository.selectedWidgetId()?.takeIf {
        appWidgetManager.getAppWidgetInfo(it) != null
    }

    fun selectedWidgetLabel(): String? {
        val info = selectedWidgetId()?.let(appWidgetManager::getAppWidgetInfo) ?: return null
        return info.loadLabel(appContext.packageManager).toString()
    }

    fun hasValidWidget(): Boolean = selectedWidgetId() != null

    fun createSelectedWidgetView(widthDp: Int, heightDp: Int): AppWidgetHostView? {
        val id = selectedWidgetId() ?: return null
        return createWidgetViewById(id, widthDp, heightDp)
    }

    // --- Widget Stack ---

    fun getWidgetStack(): List<Int> = repository.getWidgetStack()

    fun addToStack(widgetId: Int) {
        repository.addToStack(widgetId)
    }

    fun removeFromStack(widgetId: Int) {
        repository.removeFromStack(widgetId)
    }

    fun clearStack() {
        repository.clearStack()
    }

    fun getStackCount(): Int = repository.getStackCount()

    fun createStackWidgetView(index: Int, widthDp: Int, heightDp: Int): AppWidgetHostView? {
        val id = repository.getWidgetAtStackIndex(index) ?: return null
        return createWidgetViewById(id, widthDp, heightDp)
    }

    fun getStackWidgetLabel(index: Int): String? {
        val id = repository.getWidgetAtStackIndex(index) ?: return null
        val info = appWidgetManager.getAppWidgetInfo(id) ?: return null
        return info.loadLabel(appContext.packageManager).toString()
    }

    fun getStackWidgetInfo(index: Int): android.appwidget.AppWidgetProviderInfo? {
        val id = repository.getWidgetAtStackIndex(index) ?: return null
        return appWidgetManager.getAppWidgetInfo(id)
    }

    /**
     * Info of the widget shown at [position]; falls back to the selected widget when the
     * stack is empty (single-widget setups). Used to estimate each widget's natural height.
     */
    fun widgetInfoAt(position: Int): android.appwidget.AppWidgetProviderInfo? {
        val id = repository.getWidgetAtStackIndex(position) ?: selectedWidgetId() ?: return null
        return appWidgetManager.getAppWidgetInfo(id)
    }

    fun removeWidgetAtStackIndex(index: Int) {
        stopListening()
        val id = repository.getWidgetAtStackIndex(index) ?: return
        try {
            host.deleteAppWidgetId(id)
        } catch (_: Exception) {
            // Ignore errors
        }
        repository.removeFromStack(id)

        // If removed widget was selected, set next available as selected
        if (repository.selectedWidgetId() == id) {
            repository.clearSelectedWidget()
            val remaining = repository.getWidgetStack()
            if (remaining.isNotEmpty()) {
                repository.saveSelectedWidget(remaining.first())
            }
        }
    }

    // --- Common ---

    fun startListening() {
        if (!isListening) {
            host.startListening()
            isListening = true
        }
    }

    fun stopListening() {
        if (isListening) {
            host.stopListening()
            isListening = false
        }
    }

    fun removeSelectedWidget() {
        stopListening()
        val selectedId = repository.selectedWidgetId()
        selectedId?.let {
            try {
                host.deleteAppWidgetId(it)
            } catch (_: Exception) {
                // Ignore errors when deleting widget
            }
            repository.removeFromStack(it)
        }
        repository.clearSelectedWidget()

        // Set next widget from stack as selected if available
        val remainingStack = repository.getWidgetStack()
        if (remainingStack.isNotEmpty()) {
            repository.saveSelectedWidget(remainingStack.first())
        }
    }

    fun validateOrClear(): Boolean {
        if (repository.isSelectedWidgetValid()) return true
        repository.clearSelectedWidget()
        return false
    }

    private fun createWidgetViewById(id: Int, widthDp: Int, heightDp: Int): AppWidgetHostView? {
        return try {
            val info = appWidgetManager.getAppWidgetInfo(id) ?: return null
            updateSizeOptions(id, widthDp, heightDp)
            host.createView(appContext, id, info)
        } catch (_: Exception) {
            null
        }
    }

    private fun commitSelectedWidget(id: Int) {
        try {
            if (isStackMode) {
                // Stack mode: only add to stack, don't replace selected widget
                repository.addToStack(id)
                // If no selected widget, set this as selected
                if (repository.selectedWidgetId() == null) {
                    repository.saveSelectedWidget(id)
                }
            } else {
                // Select mode: clear stack and start fresh with new widget
                // Delete old selected widget
                try {
                    repository.selectedWidgetId()?.takeIf { it != id }?.let(host::deleteAppWidgetId)
                } catch (_: Exception) {
                    // Ignore errors when deleting old widget
                }
                // Clear stack and set new widget as selected
                repository.clearStack()
                repository.saveSelectedWidget(id)
                repository.addToStack(id)
            }
        } catch (_: Exception) {
            // Ignore errors during commit
        }
    }

    private fun updateSizeOptions(id: Int, widthDp: Int, heightDp: Int) {
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
        }
        appWidgetManager.updateAppWidgetOptions(id, options)
    }

    companion object {
        private const val HOST_ID = 0x574F564C // WOVL
        const val REQUEST_PICK_WIDGET = 1001
    }
}
