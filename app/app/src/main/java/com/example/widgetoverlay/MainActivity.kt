package com.example.widgetoverlay

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private lateinit var widgetHost: WidgetHostController
    private lateinit var notifier: SurfaceNotifier
    private lateinit var widgetNameText: TextView
    private lateinit var widgetInfoText: TextView
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
        notifier = SurfaceNotifier(this)
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
            widgetHost.startListening()
            renderInAppPreview()
        }
    }

    override fun onStop() {
        if (previewVisible) widgetHost.stopListening()
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
                widgetHost.startListening()
                renderInAppPreview()
            }
        }
    }

    private fun createContentView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.md_theme_background))
        }

        // Material Toolbar
        val toolbar = MaterialToolbar(this).apply {
            title = getString(R.string.app_name)
            setTitleTextColor(getColor(R.color.md_theme_onSurface))
            setBackgroundColor(getColor(R.color.md_theme_surface))
            elevation = dp(2).toFloat()
        }
        root.addView(toolbar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(56)
        ))

        // Scrollable content
        val scroll = ScrollView(this).apply {
            setBackgroundColor(getColor(R.color.md_theme_background))
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(24))
        }

        // Widget info card
        content.addView(createWidgetCard(), matchWidth())

        // Display methods card
        content.addView(createDisplayCard(), matchWidth())

        // System surfaces card
        content.addView(createSystemCard(), matchWidth())

        // Status
        statusText = TextView(this).apply {
            setTextColor(getColor(R.color.md_theme_onSurfaceVariant))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
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
        val card = createMaterialCard()
        val content = cardContent()

        // Card header
        content.addView(cardTitle("선택된 위젯"))

        // Widget name
        widgetNameText = TextView(this).apply {
            text = getString(R.string.no_widget)
            setTextColor(getColor(R.color.md_theme_onSurface))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(0, dp(8), 0, dp(4))
        }
        content.addView(widgetNameText, matchWidth())

        // Widget info
        widgetInfoText = TextView(this).apply {
            text = ""
            setTextColor(getColor(R.color.md_theme_onSurfaceVariant))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(0, 0, 0, dp(12))
            visibility = View.GONE
        }
        content.addView(widgetInfoText, matchWidth())

        // Preview container - larger for better widget display
        previewContainer = FrameLayout(this).apply {
            minimumHeight = dp(250)
            background = GradientDrawable().apply {
                setColor(getColor(R.color.md_theme_surfaceVariant))
                cornerRadius = dp(12).toFloat()
            }
        }
        content.addView(previewContainer, matchWidth(height = dp(280)))

        // Stack info
        stackCountText = TextView(this).apply {
            text = getString(R.string.widget_stack_count, widgetHost.getStackCount())
            setTextColor(getColor(R.color.md_theme_onSurfaceVariant))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(0, dp(4), 0, dp(8))
        }
        content.addView(stackCountText, matchWidth())

        // Add widget button
        val addButton = createFilledButton(getString(R.string.add_widget)) {
            widgetHost.beginWidgetPickForStack(this)
        }
        content.addView(addButton, matchWidth())

        // Remove current widget button
        val removeButton = createOutlinedButton(getString(R.string.remove_widget)) {
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
        val card = createMaterialCard()
        val content = cardContent()

        content.addView(cardTitle("표시 방식"))

        val hasWidget = widgetHost.hasValidWidget()
        val overlayBtn = createFilledButton(getString(R.string.open_overlay)) { openOverlayPanel() }
        val launcherBtn = createTonalButton(getString(R.string.show_launcher)) { showOverlayLauncher() }
        val inAppBtn = createOutlinedButton(getString(R.string.open_in_app)) {
            previewVisible = true
            widgetHost.startListening()
            renderInAppPreview()
            refreshUi("앱 내부 대체 표시가 열렸습니다.")
        }
        val hideBtn = createTextButton(getString(R.string.hide_overlay)) {
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

    private fun createSystemCard(): MaterialCardView {
        val card = createMaterialCard()
        val content = cardContent()

        content.addView(cardTitle("선택적 시스템 표면"))

        content.addView(createTonalButton(getString(R.string.send_bubble)) {
            if (!notifier.notificationsAllowed()) {
                requestNotificationPermissionIfNeeded()
                refreshUi("알림 권한이 필요합니다.")
            } else {
                notifier.postBubbleSummary(widgetHost.selectedWidgetLabel())
                refreshUi("버블을 지원하지 않는 기기에서는 일반 알림으로 표시됩니다.")
            }
        }, matchWidth().apply { topMargin = dp(8) })

        content.addView(createOutlinedButton(getString(R.string.start_live_update)) {
            notifier.postLiveUpdate(progress = 60)
            val route = SurfacePolicy.liveUpdateRoute(
                Build.VERSION.SDK_INT,
                userStartedJourney = true,
                notificationsAllowed = notifier.notificationsAllowed(),
            )
            val message = if (route == SurfaceRoute.LIVE_UPDATE_NOTIFICATION) {
                "Live Update 승격을 요청했습니다."
            } else {
                "일반 ongoing 알림으로 하향됩니다."
            }
            refreshUi(message)
        }, matchWidth().apply { topMargin = dp(8) })

        content.addView(createTextButton(getString(R.string.stop_live_update)) {
            notifier.completeLiveUpdate()
            refreshUi("진행 상태 알림을 완료했습니다.")
        }, matchWidth().apply { topMargin = dp(8) })

        card.addView(content)
        return card
    }

    // --- Material Card helpers ---

    private fun createMaterialCard(): MaterialCardView = MaterialCardView(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = dp(12)
        }
        setCardBackgroundColor(getColor(R.color.md_theme_surfaceVariant))
        radius = dp(16).toFloat()
        cardElevation = 0f
        strokeColor = getColor(R.color.md_theme_outlineVariant)
        strokeWidth = dp(1)
    }

    private fun cardContent(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
    }

    private fun cardTitle(text: String): TextView = TextView(this).apply {
        this.text = text
        setTextColor(getColor(R.color.md_theme_primary))
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTypeface(null, android.graphics.Typeface.BOLD)
        letterSpacing = 0.02f
    }

    // --- Material Button helpers ---

    private fun createFilledButton(text: String, action: () -> Unit): MaterialButton =
        MaterialButton(this).apply {
            this.text = text
            setTextColor(getColor(R.color.md_theme_onPrimary))
            setBackgroundColor(getColor(R.color.md_theme_primary))
            cornerRadius = dp(20)
            textSize = 14f
            isAllCaps = false
            setOnClickListener { action() }
        }

    private fun createTonalButton(text: String, action: () -> Unit): MaterialButton =
        MaterialButton(this).apply {
            this.text = text
            setTextColor(getColor(R.color.md_theme_onSecondaryContainer))
            setBackgroundColor(getColor(R.color.md_theme_secondaryContainer))
            cornerRadius = dp(20)
            textSize = 14f
            isAllCaps = false
            setOnClickListener { action() }
        }

    private fun createOutlinedButton(text: String, action: () -> Unit): MaterialButton =
        MaterialButton(this).apply {
            this.text = text
            setTextColor(getColor(R.color.md_theme_primary))
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            setStrokeColorResource(R.color.md_theme_outline)
            strokeWidth = dp(1)
            cornerRadius = dp(20)
            textSize = 14f
            isAllCaps = false
            setOnClickListener { action() }
        }

    private fun createTextButton(text: String, action: () -> Unit): MaterialButton =
        MaterialButton(this).apply {
            this.text = text
            setTextColor(getColor(R.color.md_theme_primary))
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            cornerRadius = dp(20)
            textSize = 14f
            isAllCaps = false
            setOnClickListener { action() }
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

        // Ensure host is listening before creating view
        widgetHost.startListening()

        // Calculate preview size based on container
        val containerWidth = previewContainer.width.takeIf { it > 0 } ?: dp(340)
        val containerHeight = previewContainer.height.takeIf { it > 0 } ?: dp(250)
        val widgetWidthDp = pxToDp(containerWidth - dp(16))
        val widgetHeightDp = pxToDp(containerHeight - dp(16))

        val stack = widgetHost.getWidgetStack()
        // If stack is empty, use selected widget ID
        val widgetIds = if (stack.isEmpty()) {
            val selectedId = widgetHost.selectedWidgetId()
            if (selectedId != null) listOf(selectedId) else emptyList()
        } else {
            stack
        }

        if (widgetIds.isEmpty()) {
            previewContainer.addView(TextView(this).apply {
                text = getString(R.string.widget_not_available)
                gravity = Gravity.CENTER
                setTextColor(getColor(R.color.md_theme_onSurfaceVariant))
            })
            return
        }

        if (widgetIds.size == 1) {
            // Single widget - show directly
            val widgetView = widgetHost.createSelectedWidgetView(widthDp = widgetWidthDp, heightDp = widgetHeightDp)
            if (widgetView == null) {
                previewContainer.addView(TextView(this).apply {
                    text = getString(R.string.widget_not_available)
                    gravity = Gravity.CENTER
                    setTextColor(getColor(R.color.md_theme_onSurfaceVariant))
                })
            } else {
                previewContainer.addView(widgetView, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ))
            }
        } else {
            // Multiple widgets - use ViewPager2 with indicator
            val contentLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
            }

            val viewPager = ViewPager2(this).apply {
                adapter = PreviewWidgetPagerAdapter(widgetIds, widgetWidthDp, widgetHeightDp)
                // Restore page position
                setCurrentItem(currentPreviewPage, false)
            }
            contentLayout.addView(viewPager, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0, 1f
            ))

            // Add page indicator dots below ViewPager
            val indicator = LinearLayout(this).apply {
                gravity = Gravity.CENTER
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(12), 0, dp(4))
            }
            for (i in widgetIds.indices) {
                val dot = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(6), dp(6)).apply {
                        marginStart = if (i > 0) dp(4) else 0
                    }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(if (i == currentPreviewPage) getColor(R.color.md_theme_primary) else getColor(R.color.md_theme_outlineVariant))
                    }
                }
                indicator.addView(dot)
            }
            viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    currentPreviewPage = position
                    for (j in 0 until indicator.childCount) {
                        val dot = indicator.getChildAt(j)
                        (dot.background as? GradientDrawable)?.setColor(
                            if (j == position) getColor(R.color.md_theme_primary) else getColor(R.color.md_theme_outlineVariant)
                        )
                    }
                }
            })
            contentLayout.addView(indicator)

            previewContainer.addView(contentLayout, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ))
        }
    }

    private fun pxToDp(px: Int): Int {
        return (px / resources.displayMetrics.density).toInt()
    }

    // ViewPager2 Adapter for preview
    private inner class PreviewWidgetPagerAdapter(
        private val widgetIds: List<Int>,
        private val widgetWidthDp: Int,
        private val widgetHeightDp: Int,
    ) : RecyclerView.Adapter<PreviewWidgetPagerAdapter.WidgetViewHolder>() {

        inner class WidgetViewHolder(val frame: FrameLayout) : RecyclerView.ViewHolder(frame)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WidgetViewHolder {
            val frame = FrameLayout(this@MainActivity).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }
            return WidgetViewHolder(frame)
        }

        override fun onBindViewHolder(holder: WidgetViewHolder, position: Int) {
            val frame = holder.frame
            frame.removeAllViews()

            val widgetView = if (widgetIds.size == 1) {
                widgetHost.createSelectedWidgetView(widgetWidthDp, widgetHeightDp)
            } else {
                widgetHost.createStackWidgetView(position, widgetWidthDp, widgetHeightDp)
            }

            if (widgetView == null) {
                val unavailable = TextView(this@MainActivity).apply {
                    text = getString(R.string.widget_not_available)
                    gravity = Gravity.CENTER
                    setTextColor(getColor(R.color.md_theme_onSurfaceVariant))
                }
                frame.addView(unavailable, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            } else {
                frame.addView(widgetView, FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            }
        }

        override fun getItemCount(): Int = widgetIds.size
    }

    private fun refreshUi(statusOverride: String? = null) {
        val label = widgetHost.selectedWidgetLabel()
        val hasWidget = label != null

        if (hasWidget) {
            widgetNameText.text = label
            widgetInfoText.text = getString(R.string.widget_selected, label)
            widgetInfoText.visibility = View.VISIBLE
        } else {
            widgetNameText.text = getString(R.string.no_widget)
            widgetInfoText.visibility = View.GONE
        }

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
            SurfaceRoute.OVERLAY_WIDGET -> "오버레이 권한 허용됨"
            SurfaceRoute.IN_APP_WIDGET -> "오버레이 권한 없음"
            SurfaceRoute.NONE -> "위젯을 선택하세요"
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
