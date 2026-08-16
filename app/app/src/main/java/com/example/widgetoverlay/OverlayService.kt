package com.example.widgetoverlay

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.widgetoverlay.ui.AppTheme
import com.example.widgetoverlay.ui.Design
import com.example.widgetoverlay.ui.colorWithAlpha
import com.example.widgetoverlay.ui.dp
import com.example.widgetoverlay.ui.roundedRect
import com.example.widgetoverlay.ui.themeColor
import com.example.widgetoverlay.ui.tonalIconButton
import com.example.widgetoverlay.ui.wormIndicator
import com.google.android.material.R as MaterialR
import kotlin.math.roundToInt

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var widgetHost: WidgetHostController

    private var launcherView: View? = null
    private var panelView: View? = null
    private var scrimView: View? = null
    private var launcherParams: WindowManager.LayoutParams? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var isAnimating = false
    private var isRefreshing = false
    private var currentAnimator: AnimatorSet? = null
    private var viewPager: ViewPager2? = null
    private var pageIndicator: com.tbuonomo.viewpagerdotsindicator.WormDotsIndicator? = null
    private var panelX = 0
    private var panelY = 0
    private var lastNightMode = Int.MIN_VALUE

    private val uiPrefs by lazy { getSharedPreferences("overlay_ui", Context.MODE_PRIVATE) }

    /** Themed context: carries dynamic color + day/night roles into overlay views. */
    private val uiContext: Context
        get() = this

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        widgetHost = WidgetHostController(this)
        // Apply dynamic colors to this service's theme once; uiContext resolves roles from it.
        AppTheme.themed(this)
        lastNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        createNotificationChannels()
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        val nightMode = newConfig.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        if (nightMode == lastNightMode) return
        lastNightMode = nightMode
        rebuildForThemeChange()
    }

    /**
     * Overlay views resolve theme colors into drawables at build time, so a system
     * light/dark switch leaves them stale. Recreates the currently visible surface
     * (add-first-then-remove, same as transitions) with the new palette.
     */
    private fun rebuildForThemeChange() {
        currentAnimator?.cancel()
        val panel = panelView
        if (panel != null) {
            panelView = null
            viewPager = null
            pageIndicator = null
            addPanel(animate = false)
            panelView?.post { removeViewSafely(panel) }
            return
        }
        val launcher = launcherView
        if (launcher != null) {
            launcherView = null
            addLauncher(animate = false)
            launcherView?.post { removeViewSafely(launcher) }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this) || !widgetHost.validateOrClear()) {
            stopOverlay()
            return START_NOT_STICKY
        }
        startAsForeground()
        widgetHost.startListening()
        when (intent?.action ?: ACTION_SHOW_LAUNCHER) {
            ACTION_SHOW_PANEL -> showPanel()
            ACTION_SHOW_LAUNCHER -> showLauncher()
            ACTION_HIDE -> animateStopOverlay()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        currentAnimator?.cancel()
        removeViewSafely(panelView)
        removeViewSafely(launcherView)
        removeViewSafely(scrimView)
        panelView = null
        launcherView = null
        scrimView = null
        widgetHost.stopListening()
        super.onDestroy()
    }

    // --- Launcher ---

    private fun showLauncher() {
        if (isAnimating) return

        val currentPanel = panelView
        if (currentPanel != null) {
            animatePanelToLauncher(currentPanel)
            return
        }
        if (launcherView != null) return

        addLauncher(animate = true)
    }

    private fun animatePanelToLauncher(panel: View) {
        isAnimating = true
        anchorPivotToLauncher(panel)
        animateExit(panel, toScale = 0.4f) {
            // Detach the panel state first, then show the launcher. The faded-out panel window
            // is removed only after the launcher's first frame so no empty frame is presented.
            panelView = null
            viewPager = null
            pageIndicator = null
            addLauncher(animate = true)
            launcherView?.post { removeViewSafely(panel) }
            removeScrim(animated = true)
        }
    }

    private fun addLauncher(animate: Boolean) {
        if (launcherView != null) return

        val context = uiContext
        val button = FrameLayout(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(themeColor(MaterialR.attr.colorPrimaryContainer))
            }
            elevation = dp(6).toFloat()
            contentDescription = getString(R.string.launcher_bubble_desc)
            setOnClickListener { showPanel() }
            setOnTouchListener(DragTouchListener())
            addView(android.widget.ImageView(context).apply {
                setImageResource(R.drawable.ic_apps)
                imageTintList = ColorStateList.valueOf(
                    AppTheme.color(context, MaterialR.attr.colorOnPrimaryContainer)
                )
            }, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
        }

        // Reuse the existing params (kept up to date by drag) so the bubble reappears where
        // the user left it instead of the hardcoded default position.
        val params = launcherParams ?: overlayParams(
            width = dp(BUBBLE_SIZE_DP),
            height = dp(BUBBLE_SIZE_DP),
            gravity = Gravity.TOP or Gravity.START,
            x = dp(16),
            y = dp(180),
        ).also { launcherParams = it }

        if (animate) {
            button.alpha = 0f
            button.scaleX = 0.4f
            button.scaleY = 0.4f
            isAnimating = true
        }
        launcherView = button
        windowManager.addView(button, params)

        if (animate) animateEnter(button, fromScale = 0.4f) else isAnimating = false
    }

    // --- Panel ---

    private fun showPanel() {
        if (isAnimating) return

        val currentLauncher = launcherView
        if (currentLauncher != null) {
            animateLauncherToPanel(currentLauncher)
            return
        }
        if (panelView != null) return

        addPanel(animate = true)
    }

    private fun animateLauncherToPanel(launcher: View) {
        isAnimating = true
        animateExit(launcher, toScale = 0.6f) {
            launcherView = null
            addPanel(animate = true)
            panelView?.post { removeViewSafely(launcher) }
        }
    }

    private fun addPanel(animate: Boolean) {
        if (panelView != null) return

        val context = uiContext

        val stack = widgetHost.getWidgetStack()
        // If stack is empty, use selected widget ID
        val widgetIds = if (stack.isEmpty()) {
            val selectedId = widgetHost.selectedWidgetId()
            if (selectedId != null) listOf(selectedId) else emptyList()
        } else {
            stack
        }
        val hasMultipleWidgets = widgetIds.size > 1

        val screenWidth = screenWidthPx()
        val screenHeight = screenHeightPx()
        val panelWidth = minOf(dp(380), screenWidth - dp(24)).coerceAtLeast(dp(260))
        val panelHeight = minOf(dp(520), screenHeight - dp(80)).coerceAtLeast(dp(280))

        // Restore the user's last panel position, clamped to the current screen. The panel is
        // always an absolute TOP|START window (centered coordinates on first launch) so drag
        // math never needs a gravity conversion.
        val savedXDp = uiPrefs.getInt(KEY_PANEL_X_DP, -1)
        val savedYDp = uiPrefs.getInt(KEY_PANEL_Y_DP, -1)
        val positioned = savedXDp >= 0 && savedYDp >= 0
        panelX = if (positioned) dp(savedXDp).coerceIn(0, (screenWidth - panelWidth).coerceAtLeast(0))
                 else (screenWidth - panelWidth) / 2
        panelY = if (positioned) dp(savedYDp).coerceIn(0, (screenHeight - panelHeight).coerceAtLeast(0))
                 else (screenHeight - panelHeight) / 2

        // Outer container is a FrameLayout so a full-cover loading overlay can layer on top
        // of the content and absorb events while the panel refreshes.
        val container = FrameLayout(context).apply {
            background = panelBackground()
            elevation = dp(16).toFloat()
        }
        // Single compact chrome row: centered grab handle + actions at the end. Merging the
        // rows and dropping the separate widget-frame card keeps the panel to one surface.
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(10))
        }
        container.addView(content, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        val header = FrameLayout(context)
        // Drag handle: drag to move the panel, tap to minimize
        val handle = FrameLayout(context).apply {
            isClickable = true
            isFocusable = true
            contentDescription = getString(R.string.minimize)
            setOnClickListener { showLauncher() }
            setOnTouchListener(PanelDragTouchListener())
            addView(View(context).apply {
                background = GradientDrawable().apply {
                    cornerRadius = dp(2).toFloat()
                    setColor(
                        colorWithAlpha(
                            AppTheme.color(context, MaterialR.attr.colorOnSurfaceVariant),
                            0.38f
                        )
                    )
                }
            }, FrameLayout.LayoutParams(dp(32), dp(4), Gravity.CENTER))
        }
        header.addView(handle, FrameLayout.LayoutParams(dp(120), dp(48), Gravity.CENTER))
        val actions = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
        }
        actions.addView(context.tonalIconButton(R.drawable.ic_refresh, getString(R.string.refresh)) {
            refreshPanelContent()
        }, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(4) })
        actions.addView(
            context.tonalIconButton(R.drawable.ic_remove, getString(R.string.minimize)) {
                showLauncher()
            },
            LinearLayout.LayoutParams(dp(40), dp(40)),
        )
        header.addView(actions, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER_VERTICAL or Gravity.END,
        ))
        content.addView(header, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(48)
        ))

        // Widget pager sits directly on the panel surface (no framing card)
        val widgetWidthDp = pxToDp(panelWidth - dp(24))
        val widgetHeightDp = pxToDp(panelHeight - dp(100))

        val pager = ViewPager2(context).apply {
            adapter = WidgetPagerAdapter(widgetIds, widgetWidthDp, widgetHeightDp)
        }
        viewPager = pager
        content.addView(pager, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0, 1f
        ).apply { topMargin = dp(6) })

        // Page indicator dots (only if multiple widgets)
        if (hasMultipleWidgets) {
            pageIndicator = wormIndicator(pager)
            content.addView(
                pageIndicator,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    topMargin = dp(6)
                }
            )
        }

        panelView = container
        panelParams = overlayParams(
            width = panelWidth,
            height = panelHeight,
            gravity = Gravity.TOP or Gravity.START,
            x = panelX,
            y = panelY,
        )

        anchorPivotToLauncher(container)
        if (animate) {
            container.alpha = 0f
            container.scaleX = 0.5f
            container.scaleY = 0.5f
            isAnimating = true
        }
        addScrim(animated = animate)
        windowManager.addView(container, panelParams)

        if (animate) animateEnter(container, fromScale = 0.5f) else isAnimating = false
    }

    /**
     * Refreshes the widget content in place: a loading overlay covers the panel (blocking
     * input), the pager content is rebuilt without recreating the window, then the overlay
     * clears and input resumes.
     */
    private fun refreshPanelContent() {
        val panel = panelView as? FrameLayout ?: return
        if (isRefreshing || isAnimating) return
        isRefreshing = true

        val context = uiContext
        val loading = FrameLayout(context).apply {
            isClickable = true
            isFocusable = true
            background = roundedRect(
                colorWithAlpha(Color.BLACK, 0.32f),
                Design.RADIUS_XLARGE,
            )
            addView(ProgressBar(context).apply {
                indeterminateTintList = ColorStateList.valueOf(
                    themeColor(MaterialR.attr.colorPrimary)
                )
            }, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER))
        }
        panel.addView(loading, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Rebuild after a short delay so the loading state is perceptible, then give the
        // fresh RemoteViews a beat to render before releasing input.
        loading.postDelayed({
            rebuildWidgetContent()
            loading.postDelayed({
                if (loading.isAttachedToWindow) panel.removeView(loading)
                isRefreshing = false
            }, REFRESH_SETTLE_MS)
        }, REFRESH_LOADING_MS)
    }

    /** Rebuilds the pager adapter in place; the panel window itself is never recreated. */
    private fun rebuildWidgetContent() {
        val pager = viewPager ?: return
        val widthDp = pxToDp(pager.width - dp(12))
        val heightDp = pxToDp(pager.height - dp(12))

        val stack = widgetHost.getWidgetStack()
        val widgetIds = if (stack.isEmpty()) {
            listOfNotNull(widgetHost.selectedWidgetId())
        } else {
            stack
        }
        // A new adapter resets the pager to page 0; restore the page the user was viewing.
        val page = pager.currentItem.coerceIn(0, (widgetIds.size - 1).coerceAtLeast(0))
        pager.adapter = WidgetPagerAdapter(widgetIds, widthDp, heightDp)
        pager.setCurrentItem(page, false)
        // The worm indicator follows the pager's page-change callbacks on its own.
    }

    // --- Scrim ---

    /** Full-screen translucent layer that dims whatever is behind the panel and absorbs its touches. */
    private fun addScrim(animated: Boolean) {
        if (scrimView != null) return
        val scrim = View(uiContext).apply {
            setBackgroundColor(colorWithAlpha(Color.BLACK, SCRIM_ALPHA))
            // Tapping the scrim minimizes the panel back to the launcher bubble.
            setOnClickListener { showLauncher() }
        }
        scrimView = scrim
        val params = WindowManager.LayoutParams(
            screenWidthPx(),
            screenHeightPx() + statusBarInsetPx(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // No FLAG_NOT_TOUCH_MODAL: the scrim must consume touches instead of passing
            // them through to the app behind the overlay. Explicit full-display size with a
            // negative y offset (overlay coords start below the status bar) covers the whole
            // screen including the status and navigation bar areas.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            title = "WidgetOverlayScrim"
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = -statusBarInsetPx()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setFitInsetsTypes(0)
            }
        }
        if (animated) {
            scrim.alpha = 0f
            windowManager.addView(scrim, params)
            scrim.animate().alpha(SCRIM_ALPHA).setDuration(SCRIM_DURATION).start()
        } else {
            windowManager.addView(scrim, params)
        }
    }

    /**
     * Height of the status bar. Overlay window coordinates are relative to the content frame
     * (below the status bar), so the scrim offsets by this to reach the very top of the display.
     */
    private fun statusBarInsetPx(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val top = windowManager.currentWindowMetrics.windowInsets
                .getInsets(android.view.WindowInsets.Type.statusBars()).top
            if (top > 0) return top
        }
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    private fun removeScrim(animated: Boolean) {
        val scrim = scrimView ?: return
        scrimView = null
        if (animated) {
            scrim.animate()
                .alpha(0f)
                .setDuration(SCRIM_DURATION)
                .withEndAction { removeViewSafely(scrim) }
                .start()
        } else {
            removeViewSafely(scrim)
        }
    }

    /**
     * Frosted-panel look: near-opaque tonal surface with a hairline border. Real backdrop blur
     * is not applied to application-overlay windows on most devices, so this is the reliable
     * approximation.
     */
    private fun panelBackground(): GradientDrawable {
        val context = uiContext
        val surface = themeColor(MaterialR.attr.colorSurfaceContainerHigh)
        val hairline = colorWithAlpha(
            themeColor(MaterialR.attr.colorOnSurfaceVariant), 0.2f
        )
        return roundedRect(
            colorWithAlpha(surface, 0.94f),
            Design.RADIUS_XLARGE,
            strokeColor = hairline,
        )
    }

    /** Anchors the view's scale pivot to the launcher bubble so transitions read as one motion. */
    private fun anchorPivotToLauncher(view: View) {
        val params = launcherParams
        val bubbleCenterX = (params?.x?.plus(dp(BUBBLE_SIZE_DP) / 2))?.toFloat()
            ?: screenWidthPx() / 2f
        val bubbleCenterY = (params?.y?.plus(dp(BUBBLE_SIZE_DP) / 2))?.toFloat()
            ?: screenHeightPx() * 0.25f
        val width = panelParams?.width ?: view.width
        val height = panelParams?.height ?: view.height
        view.pivotX = (bubbleCenterX - panelX).coerceIn(0f, width.toFloat())
        view.pivotY = (bubbleCenterY - panelY).coerceIn(0f, height.toFloat())
    }

    // --- Animations ---

    /**
     * Exit animation: shrinks/fades from the view's CURRENT property values (never hardcoded
     * starts, which snap when an interrupted press-scale is still applied) and reports whether
     * the transition continuation should still run.
     */
    private fun animateExit(view: View, toScale: Float, onEnded: () -> Unit) {
        view.animate().cancel()
        var cancelled = false
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, View.SCALE_X, view.scaleX, toScale),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, view.scaleY, toScale),
                ObjectAnimator.ofFloat(view, View.ALPHA, view.alpha, 0f),
            )
            duration = Design.DURATION_SHORT
            interpolator = Design.EASE_ACCELERATE
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled) onEnded()
                }
            })
            currentAnimator = this
            start()
        }
    }

    /** Enter animation; clears the global guard only when the whole transition chain finished. */
    private fun animateEnter(view: View, fromScale: Float) {
        view.scaleX = fromScale
        view.scaleY = fromScale
        view.alpha = 0f
        var cancelled = false
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(view, View.SCALE_X, fromScale, 1f),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, fromScale, 1f),
                ObjectAnimator.ofFloat(view, View.ALPHA, 0f, 1f),
            )
            duration = Design.DURATION_MEDIUM
            interpolator = Design.EASE_DECELERATE
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationCancel(animation: Animator) {
                    cancelled = true
                }

                override fun onAnimationEnd(animation: Animator) {
                    if (!cancelled) {
                        view.scaleX = 1f
                        view.scaleY = 1f
                        view.alpha = 1f
                        isAnimating = false
                    }
                }
            })
            currentAnimator = this
            start()
        }
    }

    // --- ViewPager2 Adapter for widget stack ---

    private inner class WidgetPagerAdapter(
        private val widgetIds: List<Int>,
        private val widgetWidthDp: Int,
        private val widgetHeightDp: Int,
    ) : RecyclerView.Adapter<WidgetPagerAdapter.WidgetViewHolder>() {

        inner class WidgetViewHolder(val frame: FrameLayout) : RecyclerView.ViewHolder(frame)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WidgetViewHolder {
            val frame = FrameLayout(this@OverlayService).apply {
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

            val context = uiContext
            val widgetView = if (widgetIds.size == 1) {
                widgetHost.createSelectedWidgetView(widgetWidthDp, widgetHeightDp)
            } else {
                widgetHost.createStackWidgetView(position, widgetWidthDp, widgetHeightDp)
            }

            if (widgetView == null) {
                val unavailable = TextView(context).apply {
                    text = getString(R.string.widget_not_available)
                    gravity = Gravity.CENTER
                    setTextColor(themeColor(MaterialR.attr.colorOnSurfaceVariant))
                }
                frame.addView(
                    unavailable,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            } else {
                frame.addView(
                    widgetView,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
        }

        override fun getItemCount(): Int = widgetIds.size
    }

    // --- Stop with animation ---

    private fun animateStopOverlay() {
        if (isAnimating) {
            stopOverlay()
            return
        }
        removeScrim(animated = true)

        val currentPanel = panelView
        val currentLauncher = launcherView
        when {
            currentPanel != null -> {
                isAnimating = true
                animateExit(currentPanel, toScale = 0.5f) { stopOverlay() }
            }
            currentLauncher != null -> {
                isAnimating = true
                animateExit(currentLauncher, toScale = 0.3f) { stopOverlay() }
            }
            else -> stopOverlay()
        }
    }

    // --- Helpers ---

    private fun overlayParams(
        width: Int,
        height: Int,
        gravity: Int,
        x: Int,
        y: Int,
    ): WindowManager.LayoutParams = WindowManager.LayoutParams(
        width,
        height,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
        this.gravity = gravity
        this.x = x
        this.y = y
        title = "WidgetOverlay"
    }

    private fun startAsForeground() {
        val appIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_widget_overlay)
            .setContentTitle(getString(R.string.active_overlay_title))
            .setContentText(getString(R.string.active_overlay_text))
            .setContentIntent(appIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                FOREGROUND_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(FOREGROUND_NOTIFICATION_ID, notification)
        }
    }

    private fun stopOverlay() {
        isAnimating = false
        currentAnimator?.cancel()
        currentAnimator = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                FOREGROUND_CHANNEL_ID,
                getString(R.string.foreground_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = getString(R.string.foreground_channel_description) },
        )
    }

    private fun removeViewSafely(view: View?) {
        if (view == null) return
        try {
            view.animate().cancel()
            windowManager.removeView(view)
        } catch (_: IllegalArgumentException) {
            // The platform already detached the view.
        }
    }

    private fun screenWidthPx(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        windowManager.currentWindowMetrics.bounds.width()
    } else {
        resources.displayMetrics.widthPixels
    }

    private fun screenHeightPx(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        windowManager.currentWindowMetrics.bounds.height()
    } else {
        resources.displayMetrics.heightPixels
    }

    private fun pxToDp(value: Int): Int = (value / resources.displayMetrics.density).roundToInt()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    // --- Drag handling ---

    private inner class DragTouchListener : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var initialX = 0
        private var initialY = 0
        private var moved = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val params = launcherParams ?: return false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    initialX = params.x
                    initialY = params.y
                    moved = false
                    // Scale down slightly on press
                    view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start()
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    moved = moved || (kotlin.math.abs(dx) > dp(4) || kotlin.math.abs(dy) > dp(4))
                    params.x = (initialX + dx).roundToInt().coerceIn(0, screenWidthPx() - dp(BUBBLE_SIZE_DP))
                    params.y = (initialY + dy).roundToInt().coerceIn(0, screenHeightPx() - dp(BUBBLE_SIZE_DP))
                    windowManager.updateViewLayout(view, params)
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    // Scale back on release
                    view.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    if (!moved) view.performClick()
                    return true
                }
            }
            return false
        }
    }

    /**
     * Moves the panel window by dragging the grab handle. The panel is always an absolute
     * TOP|START window, so dragging is pure x/y math clamped to the screen; the final position
     * is persisted (in dp) for the next session.
     */
    private inner class PanelDragTouchListener : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var initialX = 0
        private var initialY = 0
        private var moved = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val params = panelParams ?: return false
            val panel = panelView ?: return false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    initialX = params.x
                    initialY = params.y
                    moved = false
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!moved && kotlin.math.abs(dx) < dp(4) && kotlin.math.abs(dy) < dp(4)) {
                        return true
                    }
                    moved = true
                    params.x = (initialX + dx).roundToInt()
                        .coerceIn(0, (screenWidthPx() - params.width).coerceAtLeast(0))
                    params.y = (initialY + dy).roundToInt()
                        .coerceIn(0, (screenHeightPx() - params.height).coerceAtLeast(0))
                    panelX = params.x
                    panelY = params.y
                    windowManager.updateViewLayout(panel, params)
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        uiPrefs.edit()
                            .putInt(KEY_PANEL_X_DP, pxToDp(params.x))
                            .putInt(KEY_PANEL_Y_DP, pxToDp(params.y))
                            .apply()
                    } else {
                        view.performClick()
                    }
                    return true
                }
            }
            return false
        }
    }

    companion object {
        const val ACTION_SHOW_LAUNCHER = "com.example.widgetoverlay.action.SHOW_LAUNCHER"
        const val ACTION_SHOW_PANEL = "com.example.widgetoverlay.action.SHOW_PANEL"
        const val ACTION_HIDE = "com.example.widgetoverlay.action.HIDE"
        private const val FOREGROUND_CHANNEL_ID = "active_overlay"
        private const val FOREGROUND_NOTIFICATION_ID = 10
        private const val BUBBLE_SIZE_DP = 56

        private const val KEY_PANEL_X_DP = "panel_x_dp"
        private const val KEY_PANEL_Y_DP = "panel_y_dp"
        private const val SCRIM_ALPHA = 0.5f
        private const val SCRIM_DURATION = 220L
        private const val REFRESH_LOADING_MS = 450L
        private const val REFRESH_SETTLE_MS = 300L

        fun intent(context: Context, action: String): Intent = Intent(context, OverlayService::class.java).apply {
            this.action = action
        }
    }
}
