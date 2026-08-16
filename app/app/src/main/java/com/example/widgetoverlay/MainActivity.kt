package com.example.widgetoverlay

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.widgetoverlay.ui.Design
import com.example.widgetoverlay.ui.applySystemBarsInsets
import com.example.widgetoverlay.ui.dp
import com.example.widgetoverlay.ui.filledButton
import com.example.widgetoverlay.ui.materialCard
import com.example.widgetoverlay.ui.outlinedButton
import com.example.widgetoverlay.ui.roundedRect
import com.example.widgetoverlay.ui.textButton
import com.example.widgetoverlay.ui.themeColor
import com.example.widgetoverlay.ui.tonalButton
import com.example.widgetoverlay.ui.wormIndicator
import com.google.android.material.R as MaterialR
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private lateinit var widgetHost: WidgetHostController
    private lateinit var widgetNameText: TextView
    private lateinit var statusText: TextView
    private lateinit var previewContainer: FrameLayout
    private lateinit var overlayButtons: List<MaterialButton>
    private lateinit var stackCountText: TextView
    private var previewVisible = false
    private var currentPreviewPage = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Apply dynamic colors if available
        DynamicColors.applyToActivityIfAvailable(this)

        widgetHost = WidgetHostController(this)
        setContentView(createContentView())
        requestNotificationPermissionIfNeeded()

        // Restore state if available
        if (savedInstanceState != null) {
            currentPreviewPage = savedInstanceState.getInt(KEY_PREVIEW_PAGE, 0)
            previewVisible = savedInstanceState.getBoolean(KEY_PREVIEW_VISIBLE, false)
        } else {
            // Auto-show preview if valid widget exists (first launch only)
            if (widgetHost.hasValidWidget()) {
                previewVisible = true
            }
        }
        refreshUi()
    }

    override fun onStart() {
        super.onStart()
        if (previewVisible && widgetHost.hasValidWidget()) {
            renderInAppPreview()
        }
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_PREVIEW_PAGE, currentPreviewPage)
        outState.putBoolean(KEY_PREVIEW_VISIBLE, previewVisible)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        currentPreviewPage = savedInstanceState.getInt(KEY_PREVIEW_PAGE, 0)
        previewVisible = savedInstanceState.getBoolean(KEY_PREVIEW_VISIBLE, false)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (widgetHost.handleActivityResult(this, requestCode, resultCode, data)) {
            previewVisible = false
            previewContainer.removeAllViews()
            refreshUi()
            if (widgetHost.hasValidWidget()) {
                previewVisible = true
                renderInAppPreview()
            }
        }
    }

    private fun createContentView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(themeColor(MaterialR.attr.colorSurface))
        }
        applySystemBarsInsets(root)

        // Material Toolbar
        root.addView(MaterialToolbar(this).apply {
            title = getString(R.string.app_name)
            setBackgroundColor(themeColor(MaterialR.attr.colorSurfaceContainer))
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(56)
        ))

        // Scrollable content
        val scroll = ScrollView(this).apply {
            setBackgroundColor(themeColor(MaterialR.attr.colorSurface))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }

        // Widget info card
        content.addView(createWidgetCard(), matchWidth())

        // Display methods card
        content.addView(createDisplayCard(), matchWidth())

        // Status
        statusText = TextView(this).apply {
            setTextColor(themeColor(MaterialR.attr.colorOnSurfaceVariant))
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dp(16), dp(16), dp(16), 0)
        }
        content.addView(statusText, matchWidth())

        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0, 1f
        ))

        return root
    }

    private fun createWidgetCard(): MaterialCardView {
        val card = materialCard()
        val content = cardContent()

        // Card header
        content.addView(cardTitle(getString(R.string.widget_card_title)))

        // Widget name (follows the preview pager's current page)
        widgetNameText = TextView(this).apply {
            text = getString(R.string.no_widget)
            setTextColor(themeColor(MaterialR.attr.colorOnSurface))
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(0, dp(8), 0, dp(12))
        }
        content.addView(widgetNameText, matchWidth())

        // Preview container - larger for better widget display
        previewContainer = FrameLayout(this).apply {
            minimumHeight = dp(250)
            background = roundedRect(
                themeColor(MaterialR.attr.colorSurfaceContainerHighest),
                Design.RADIUS_MEDIUM,
            )
        }
        content.addView(previewContainer, matchWidth(height = dp(280)))

        // Stack info
        stackCountText = TextView(this).apply {
            text = getString(R.string.widget_stack_count, widgetHost.getStackCount())
            setTextColor(themeColor(MaterialR.attr.colorOnSurfaceVariant))
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(4), 0, dp(8))
        }
        content.addView(stackCountText, matchWidth())

        // Add widget button
        val addButton = filledButton(getString(R.string.add_widget)) {
            widgetHost.beginWidgetPickForStack(this)
        }
        content.addView(addButton, matchWidth())

        // Remove current widget button
        val removeButton = outlinedButton(getString(R.string.remove_widget)) {
            stopOverlay()
            val stackCount = widgetHost.getStackCount()
            if (stackCount > 1) {
                // Multiple widgets - remove current page
                widgetHost.removeWidgetAtStackIndex(currentPreviewPage)
                // Adjust page index if needed
                val newStackCount = widgetHost.getStackCount()
                if (currentPreviewPage >= newStackCount) {
                    currentPreviewPage = maxOf(0, newStackCount - 1)
                }
            } else {
                // Single widget - remove selected
                widgetHost.removeSelectedWidget()
                currentPreviewPage = 0
            }
            previewVisible = false
            previewContainer.removeAllViews()
            refreshUi()
            if (widgetHost.hasValidWidget()) {
                previewVisible = true
                renderInAppPreview()
            }
        }
        content.addView(removeButton, matchWidth().apply { topMargin = dp(8) })

        card.addView(content)
        return card
    }

    private fun createDisplayCard(): MaterialCardView {
        val card = materialCard()
        val content = cardContent()

        content.addView(cardTitle(getString(R.string.display_card_title)))

        val hasWidget = widgetHost.hasValidWidget()
        val overlayBtn = filledButton(getString(R.string.open_overlay)) { openOverlayPanel() }
        val launcherBtn = tonalButton(getString(R.string.show_launcher)) { showOverlayLauncher() }
        val inAppBtn = outlinedButton(getString(R.string.open_in_app)) {
            previewVisible = true
            renderInAppPreview()
            refreshUi(getString(R.string.in_app_preview_opened))
        }
        val hideBtn = textButton(getString(R.string.hide_overlay)) {
            stopOverlay()
            refreshUi(getString(R.string.overlay_hidden))
        }

        overlayButtons = listOf(overlayBtn, launcherBtn, inAppBtn, hideBtn)
        overlayButtons.forEach { it.isEnabled = hasWidget }

        content.addView(overlayBtn, matchWidth().apply { topMargin = dp(8) })
        content.addView(launcherBtn, matchWidth().apply { topMargin = dp(8) })
        content.addView(inAppBtn, matchWidth().apply { topMargin = dp(8) })
        content.addView(hideBtn, matchWidth().apply { topMargin = dp(8) })

        card.addView(content)
        return card
    }

    // --- Card helpers ---

    private fun cardContent(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
    }

    private fun cardTitle(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(themeColor(MaterialR.attr.colorOnSurfaceVariant))
        setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f)
        setTypeface(null, android.graphics.Typeface.BOLD)
    }

    // --- Overlay control ---

    private fun openOverlayPanel() {
        if (!widgetHost.hasValidWidget()) {
            refreshUi(getString(R.string.no_widget))
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            explainAndOpenOverlaySettings()
            return
        }
        startOverlay(OverlayService.ACTION_SHOW_PANEL)
        refreshUi(getString(R.string.overlay_open))
    }

    private fun showOverlayLauncher() {
        if (!widgetHost.hasValidWidget()) {
            refreshUi(getString(R.string.no_widget))
            return
        }
        if (!Settings.canDrawOverlays(this)) {
            explainAndOpenOverlaySettings()
            return
        }
        startOverlay(OverlayService.ACTION_SHOW_LAUNCHER)
        refreshUi(getString(R.string.overlay_open))
    }

    private fun startOverlay(action: String) {
        val intent = OverlayService.intent(this, action)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopOverlay() {
        startService(OverlayService.intent(this, OverlayService.ACTION_HIDE))
    }

    private fun explainAndOpenOverlaySettings() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.overlay_permission_title))
            .setMessage(getString(R.string.overlay_permission_message))
            .setPositiveButton(getString(R.string.request_overlay_access)) { _, _ ->
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun renderInAppPreview() {
        previewContainer.removeAllViews()

        val stack = widgetHost.getWidgetStack()
        // If stack is empty, use selected widget ID
        val widgetIds = if (stack.isEmpty()) {
            val selectedId = widgetHost.selectedWidgetId()
            if (selectedId != null) listOf(selectedId) else emptyList()
        } else {
            stack
        }

        if (widgetIds.isEmpty()) {
            previewContainer.addView(unavailableWidgetText())
            return
        }

        if (widgetIds.size == 1) {
            // Single widget - show its provider preview directly
            val image = createPreviewImageView(widgetIds.first())
            previewContainer.addView(image, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))
            return
        }

        // Multiple widgets - pager over provider previews with indicator.
        // The main screen intentionally renders only provider preview images: live
        // RemoteViews here would set size options on the same widget ids the overlay
        // displays, making the two surfaces fight over each widget's shape and size.
        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }

        val viewPager = ViewPager2(this).apply {
            adapter = PreviewImagePagerAdapter(widgetIds)
            // Restore page position
            setCurrentItem(currentPreviewPage.coerceIn(0, widgetIds.size - 1), false)
        }
        contentLayout.addView(viewPager, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0, 1f
        ))

        // Page indicator below ViewPager (worm indicator follows the pager on its own)
        val indicator = wormIndicator(viewPager)
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPreviewPage = position
                if (::widgetNameText.isInitialized) {
                    widgetHost.getStackWidgetLabel(position)?.let { widgetNameText.text = it }
                }
            }
        })
        contentLayout.addView(
            indicator,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(12)
                bottomMargin = dp(12)
            }
        )

        previewContainer.addView(contentLayout, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        ))
    }

    /** Provider preview image for the widget, falling back to its app icon. */
    private fun createPreviewImageView(widgetId: Int): ImageView {
        val preview = previewDrawableFor(widgetId) ?: icAppsFallback()
        return ImageView(this).apply {
            setImageDrawable(preview)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
    }

    private fun previewDrawableFor(widgetId: Int): android.graphics.drawable.Drawable? {
        val stack = widgetHost.getWidgetStack()
        val index = if (stack.isEmpty()) 0 else stack.indexOf(widgetId).takeIf { it >= 0 } ?: 0
        val info = widgetHost.widgetInfoAt(index) ?: return null
        val providerPreview = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                info.loadPreviewImage(this, resources.displayMetrics.densityDpi)
            } else null
        } catch (_: Exception) {
            null
        }
        if (providerPreview != null) return providerPreview
        return try {
            packageManager.getApplicationIcon(info.provider.packageName)
        } catch (_: Exception) {
            null
        }
    }

    private fun icAppsFallback(): android.graphics.drawable.Drawable =
        androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_apps)!!.also {
            it.setTint(themeColor(MaterialR.attr.colorOnSurfaceVariant))
        }

    private fun unavailableWidgetText(): TextView = TextView(this).apply {
        text = getString(R.string.widget_not_available)
        gravity = Gravity.CENTER
        setTextColor(themeColor(MaterialR.attr.colorOnSurfaceVariant))
    }

    // Pager adapter over static provider preview images (no live RemoteViews on this screen).
    private inner class PreviewImagePagerAdapter(
        private val widgetIds: List<Int>,
    ) : RecyclerView.Adapter<PreviewImagePagerAdapter.PreviewViewHolder>() {

        inner class PreviewViewHolder(val image: ImageView) : RecyclerView.ViewHolder(image)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PreviewViewHolder =
            PreviewViewHolder(ImageView(this@MainActivity).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setPadding(dp(12), dp(12), dp(12), dp(12))
            })

        override fun onBindViewHolder(holder: PreviewViewHolder, position: Int) {
            holder.image.setImageDrawable(previewDrawableFor(widgetIds[position]) ?: icAppsFallback())
        }

        override fun getItemCount(): Int = widgetIds.size
    }


    /** Label of the widget currently shown on the preview pager (falls back to the selection). */
    private fun currentPreviewWidgetLabel(): String? {
        val stack = widgetHost.getWidgetStack()
        if (stack.isEmpty()) return widgetHost.selectedWidgetLabel()
        val index = currentPreviewPage.coerceIn(0, stack.size - 1)
        return widgetHost.getStackWidgetLabel(index) ?: widgetHost.selectedWidgetLabel()
    }

    private fun refreshUi(statusOverride: String? = null) {
        val label = widgetHost.selectedWidgetLabel()
        val hasWidget = label != null

        widgetNameText.text = currentPreviewWidgetLabel()
            ?: label
            ?: getString(R.string.no_widget)

        if (::overlayButtons.isInitialized) {
            overlayButtons.forEach { it.isEnabled = hasWidget }
        }

        // Update stack count
        if (::stackCountText.isInitialized) {
            stackCountText.text = getString(R.string.widget_stack_count, widgetHost.getStackCount())
        }

        val route = SurfacePolicy.widgetRoute(
            hasWidget = hasWidget,
            overlayPermissionGranted = Settings.canDrawOverlays(this),
        )
        val surfaceDescription = when (route) {
            SurfaceRoute.OVERLAY_WIDGET -> getString(R.string.status_overlay_allowed)
            SurfaceRoute.IN_APP_WIDGET -> getString(R.string.status_overlay_denied)
            SurfaceRoute.NONE -> getString(R.string.status_pick_widget)
            else -> ""
        }
        statusText.text = getString(R.string.status_prefix, statusOverride ?: surfaceDescription)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATION_PERMISSION)
        }
    }

    private fun matchWidth(height: Int = ViewGroup.LayoutParams.WRAP_CONTENT): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        private const val REQUEST_NOTIFICATION_PERMISSION = 9001
        private const val KEY_PREVIEW_PAGE = "preview_page"
        private const val KEY_PREVIEW_VISIBLE = "preview_visible"
    }
}
