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
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.roundToInt

class OverlayService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var widgetHost: WidgetHostController

    private var launcherView: View? = null
    private var panelView: View? = null
    private var launcherParams: WindowManager.LayoutParams? = null
    private var panelParams: WindowManager.LayoutParams? = null
    private var isAnimating = false
    private var viewPager: ViewPager2? = null
    private var pageIndicator: LinearLayout? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        widgetHost = WidgetHostController(this)
        createNotificationChannels()
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
        removeViewSafely(panelView)
        removeViewSafely(launcherView)
        panelView = null
        launcherView = null
        widgetHost.stopListening()
        super.onDestroy()
    }

    // --- Launcher ---

    private fun showLauncher() {
        if (isAnimating) return

        // If panel is visible, animate transition to launcher
        val currentPanel = panelView
        if (currentPanel != null) {
            animatePanelToLauncher(currentPanel)
            return
        }

        if (launcherView != null) return

        val button = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = roundedDrawable(color = getColor(R.color.md_theme_primaryContainer), radiusDp = 28)
            elevation = dp(6).toFloat()
            setPadding(dp(12), dp(10), dp(12), dp(10))
            contentDescription = "선택한 위젯 열기"
            setOnClickListener { showPanel() }
            setOnTouchListener(DragTouchListener())
        }

        // Widget icon
        button.addView(TextView(this).apply {
            text = "Widgets"
            setTextColor(getColor(R.color.md_theme_onPrimaryContainer))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            gravity = Gravity.CENTER
        })
        launcherView = button
        launcherParams = overlayParams(
            width = dp(64),
            height = dp(64),
            gravity = Gravity.TOP or Gravity.START,
            x = dp(16),
            y = dp(180),
        )

        // Start invisible for animation
        button.alpha = 0f
        button.scaleX = 0.3f
        button.scaleY = 0.3f
        windowManager.addView(button, launcherParams)

        // Animate appear: scale up + fade in
        val scaleX = ObjectAnimator.ofFloat(button, View.SCALE_X, 0.3f, 1f)
        val scaleY = ObjectAnimator.ofFloat(button, View.SCALE_Y, 0.3f, 1f)
        val alpha = ObjectAnimator.ofFloat(button, View.ALPHA, 0f, 1f)

        AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = ANIMATION_DURATION_MEDIUM
            interpolator = OvershootInterpolator(1.2f)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isAnimating = false
                }
            })
            isAnimating = true
            start()
        }
    }

    private fun animatePanelToLauncher(panel: View) {
        isAnimating = true
        val scaleX = ObjectAnimator.ofFloat(panel, View.SCALE_X, 1f, 0.3f)
        val scaleY = ObjectAnimator.ofFloat(panel, View.SCALE_Y, 1f, 0.3f)
        val alpha = ObjectAnimator.ofFloat(panel, View.ALPHA, 1f, 0f)

        AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = ANIMATION_DURATION_SHORT
            interpolator = DecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    removeViewSafely(panel)
                    panelView = null
                    isAnimating = false
                    showLauncher()
                }
            })
            start()
        }
    }

    // --- Panel ---

    private fun showPanel() {
        if (isAnimating) return

        // If launcher is visible, animate transition to panel
        val currentLauncher = launcherView
        if (currentLauncher != null) {
            animateLauncherToPanel(currentLauncher)
            return
        }

        if (panelView != null) return

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
        val panelWidth = minOf(dp(380), screenWidth - dp(24)).coerceAtLeast(dp(260))
        val panelHeight = minOf(dp(500), screenHeightPx() - dp(80)).coerceAtLeast(dp(280))
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(16))
            background = roundedDrawable(getColor(R.color.md_theme_surface), 24)
            elevation = dp(16).toFloat()
        }

        // Header
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }

        // Title
        val title = TextView(this).apply {
            text = getString(R.string.overlay_panel_title)
            setTextColor(getColor(R.color.md_theme_onSurface))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setPadding(dp(4), 0, dp(4), 0)
        }
        header.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(headerButton(getString(R.string.refresh)) { rebuildPanel() })
        header.addView(headerButton(getString(R.string.minimize)) { showLauncher() })
        header.addView(headerButton(getString(R.string.close)) { showLauncher() })
        container.addView(header)

        // ViewPager2 for widget stack
        val widgetWidthDp = pxToDp(panelWidth - dp(32))
        val widgetHeightDp = pxToDp(panelHeight - dp(100))

        viewPager = ViewPager2(this).apply {
            adapter = WidgetPagerAdapter(widgetIds, widgetWidthDp, widgetHeightDp)

            // Add page change listener for indicator
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    updatePageIndicator(position, widgetIds.size)
                }
            })
        }

        // Container for ViewPager with styling
        val viewPagerContainer = FrameLayout(this).apply {
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = GradientDrawable().apply {
                setColor(getColor(R.color.md_theme_surfaceVariant))
                cornerRadius = dp(16).toFloat()
                setStroke(dp(1), getColor(R.color.md_theme_outlineVariant))
            }
            addView(viewPager, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }
        container.addView(viewPagerContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0, 1f
        ))

        // Page indicator dots (only if multiple widgets)
        if (hasMultipleWidgets) {
            pageIndicator = LinearLayout(this).apply {
                gravity = Gravity.CENTER
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(8), 0, 0)
            }
            updatePageIndicator(0, widgetIds.size)
            container.addView(pageIndicator)
        }

        panelView = container
        panelParams = overlayParams(
            width = panelWidth,
            height = panelHeight,
            gravity = Gravity.CENTER,
            x = 0,
            y = 0,
        )

        // Start invisible for animation
        container.alpha = 0f
        container.scaleX = 0.5f
        container.scaleY = 0.5f
        windowManager.addView(container, panelParams)

        // Animate appear: scale up + fade in
        val scaleX = ObjectAnimator.ofFloat(container, View.SCALE_X, 0.5f, 1f)
        val scaleY = ObjectAnimator.ofFloat(container, View.SCALE_Y, 0.5f, 1f)
        val alpha = ObjectAnimator.ofFloat(container, View.ALPHA, 0f, 1f)

        AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = ANIMATION_DURATION_MEDIUM
            interpolator = DecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    isAnimating = false
                }
            })
            isAnimating = true
            start()
        }
    }

    private fun updatePageIndicator(currentPage: Int, totalPages: Int) {
        pageIndicator?.removeAllViews()
        for (i in 0 until totalPages) {
            val dot = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).apply {
                    marginStart = if (i > 0) dp(4) else 0
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (i == currentPage) getColor(R.color.md_theme_primary) else getColor(R.color.md_theme_outlineVariant))
                }
            }
            pageIndicator?.addView(dot)
        }
    }

    // ViewPager2 Adapter for widget stack
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

            // Use appropriate method based on whether we're using stack or selected widget
            val widgetView = if (widgetIds.size == 1) {
                // Single widget - use selected widget view
                widgetHost.createSelectedWidgetView(widgetWidthDp, widgetHeightDp)
            } else {
                // Multiple widgets - use stack view
                widgetHost.createStackWidgetView(position, widgetWidthDp, widgetHeightDp)
            }

            if (widgetView == null) {
                val unavailable = TextView(this@OverlayService).apply {
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

    private fun animateLauncherToPanel(launcher: View) {
        isAnimating = true
        // Scale down launcher while fading out
        val scaleX = ObjectAnimator.ofFloat(launcher, View.SCALE_X, 1f, 0.3f)
        val scaleY = ObjectAnimator.ofFloat(launcher, View.SCALE_Y, 1f, 0.3f)
        val alpha = ObjectAnimator.ofFloat(launcher, View.ALPHA, 1f, 0f)

        AnimatorSet().apply {
            playTogether(scaleX, scaleY, alpha)
            duration = ANIMATION_DURATION_SHORT
            interpolator = DecelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    removeViewSafely(launcher)
                    launcherView = null
                    isAnimating = false
                    showPanel()
                }
            })
            start()
        }
    }

    private fun rebuildPanel() {
        val currentPanel = panelView ?: return
        isAnimating = true

        // Quick fade out then rebuild
        val alpha = ObjectAnimator.ofFloat(currentPanel, View.ALPHA, 1f, 0f)
        alpha.duration = ANIMATION_DURATION_SHORT
        alpha.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                removeViewSafely(currentPanel)
                panelView = null
                isAnimating = false
                showPanel()
            }
        })
        alpha.start()
    }

    // --- Stop with animation ---

    private fun animateStopOverlay() {
        if (isAnimating) {
            stopOverlay()
            return
        }

        val currentPanel = panelView
        val currentLauncher = launcherView

        when {
            currentPanel != null -> {
                isAnimating = true
                val scaleX = ObjectAnimator.ofFloat(currentPanel, View.SCALE_X, 1f, 0.5f)
                val scaleY = ObjectAnimator.ofFloat(currentPanel, View.SCALE_Y, 1f, 0.5f)
                val alpha = ObjectAnimator.ofFloat(currentPanel, View.ALPHA, 1f, 0f)

                AnimatorSet().apply {
                    playTogether(scaleX, scaleY, alpha)
                    duration = ANIMATION_DURATION_MEDIUM
                    interpolator = DecelerateInterpolator()
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            stopOverlay()
                        }
                    })
                    start()
                }
            }
            currentLauncher != null -> {
                isAnimating = true
                val scaleX = ObjectAnimator.ofFloat(currentLauncher, View.SCALE_X, 1f, 0.3f)
                val scaleY = ObjectAnimator.ofFloat(currentLauncher, View.SCALE_Y, 1f, 0.3f)
                val alpha = ObjectAnimator.ofFloat(currentLauncher, View.ALPHA, 1f, 0f)

                AnimatorSet().apply {
                    playTogether(scaleX, scaleY, alpha)
                    duration = ANIMATION_DURATION_SHORT
                    interpolator = DecelerateInterpolator()
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            stopOverlay()
                        }
                    })
                    start()
                }
            }
            else -> stopOverlay()
        }
    }

    // --- Helpers ---

    private fun headerButton(label: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = 12f
        minWidth = 0
        minimumWidth = 0
        setPadding(dp(8), dp(4), dp(8), dp(4))
        setTextColor(getColor(R.color.md_theme_primary))
        background = GradientDrawable().apply {
            setColor(getColor(R.color.md_theme_primaryContainer))
            cornerRadius = dp(16).toFloat()
        }
        setOnClickListener { action() }
    }

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

    private fun roundedDrawable(color: Int, radiusDp: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
    private fun pxToDp(value: Int): Int = (value / resources.displayMetrics.density).roundToInt()

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
                    params.x = (initialX + dx).roundToInt().coerceIn(0, screenWidthPx() - dp(64))
                    params.y = (initialY + dy).roundToInt().coerceIn(0, screenHeightPx() - dp(64))
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

    companion object {
        const val ACTION_SHOW_LAUNCHER = "com.example.widgetoverlay.action.SHOW_LAUNCHER"
        const val ACTION_SHOW_PANEL = "com.example.widgetoverlay.action.SHOW_PANEL"
        const val ACTION_HIDE = "com.example.widgetoverlay.action.HIDE"
        private const val FOREGROUND_CHANNEL_ID = "active_overlay"
        private const val FOREGROUND_NOTIFICATION_ID = 10

        private const val ANIMATION_DURATION_SHORT = 200L
        private const val ANIMATION_DURATION_MEDIUM = 300L

        fun intent(context: Context, action: String): Intent = Intent(context, OverlayService::class.java).apply {
            this.action = action
        }
    }
}
