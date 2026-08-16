package com.example.widgetoverlay.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.AttrRes
import androidx.annotation.DrawableRes
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import com.example.widgetoverlay.R
import com.google.android.material.R as MaterialR
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.tbuonomo.viewpagerdotsindicator.BaseDotsIndicator
import com.tbuonomo.viewpagerdotsindicator.OnPageChangeListenerHelper
import com.tbuonomo.viewpagerdotsindicator.WormDotsIndicator
import kotlin.math.roundToInt

/**
 * Design constants normalized to the Material 3 shape, type, and motion scales.
 */
object Design {
    // Corner radii (M3 shape scale)
    const val RADIUS_SMALL = 8
    const val RADIUS_MEDIUM = 12
    const val RADIUS_LARGE = 16
    const val RADIUS_XLARGE = 28

    // Motion durations (M3 tokens)
    const val DURATION_SHORT = 200L
    const val DURATION_MEDIUM = 300L

    // Emphasized easing (M3 motion tokens)
    val EASE_DECELERATE = android.view.animation.PathInterpolator(0.05f, 0.7f, 0.1f, 1f)
    val EASE_ACCELERATE = android.view.animation.PathInterpolator(0.3f, 0f, 0.8f, 0.15f)
}

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

fun Context.sp(value: Int): Float =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value.toFloat(), resources.displayMetrics)

fun Context.themeColor(@AttrRes attr: Int): Int = AppTheme.color(this, attr)

fun colorWithAlpha(color: Int, alpha: Float): Int =
    ColorUtils.setAlphaComponent(color, (alpha * 255).roundToInt())

fun Context.roundedRect(
    color: Int,
    radiusDp: Int,
    strokeColor: Int? = null,
    strokeWidthDp: Int = 1,
): GradientDrawable = GradientDrawable().apply {
    setColor(color)
    cornerRadius = dp(radiusDp).toFloat()
    if (strokeColor != null) setStroke(dp(strokeWidthDp), strokeColor)
}

/**
 * Pads the view by system bar / display cutout insets for edge-to-edge display
 * (enforced on Android 15+ with targetSdk 35+).
 */
fun applySystemBarsInsets(root: View) {
    ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
        WindowInsetsCompat.CONSUMED
    }
}

/**
 * Material buttons built on the theme's own button styles so tinting, ripples, and dynamic
 * colors stay intact. Never call setBackgroundColor on a MaterialButton - it destroys the
 * MaterialShapeDrawable; use backgroundTintList instead.
 */
fun Context.filledButton(text: CharSequence, onClick: () -> Unit): MaterialButton =
    MaterialButton(this).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { onClick() }
    }

fun Context.tonalButton(text: CharSequence, onClick: () -> Unit): MaterialButton =
    MaterialButton(this).apply {
        this.text = text
        isAllCaps = false
        backgroundTintList = ColorStateList.valueOf(themeColor(MaterialR.attr.colorSecondaryContainer))
        setTextColor(themeColor(MaterialR.attr.colorOnSecondaryContainer))
        setOnClickListener { onClick() }
    }

fun Context.outlinedButton(text: CharSequence, onClick: () -> Unit): MaterialButton =
    MaterialButton(this, null, MaterialR.attr.materialButtonOutlinedStyle).apply {
        this.text = text
        isAllCaps = false
        setOnClickListener { onClick() }
    }

fun Context.textButton(text: CharSequence, onClick: () -> Unit): MaterialButton =
    MaterialButton(this).apply {
        this.text = text
        isAllCaps = false
        backgroundTintList = ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
        setTextColor(themeColor(MaterialR.attr.colorPrimary))
        setOnClickListener { onClick() }
    }

/** 40dp circular tonal icon button for compact surfaces like the overlay panel header. */
fun Context.tonalIconButton(
    @DrawableRes iconRes: Int,
    contentDescription: CharSequence,
    onClick: () -> Unit,
): FrameLayout = FrameLayout(this).apply {
    layoutParams = ViewGroup.LayoutParams(dp(40), dp(40))
    background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(themeColor(MaterialR.attr.colorSurfaceContainerHighest))
    }
    val typedValue = TypedValue()
    theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, typedValue, true)
    if (typedValue.resourceId != 0) {
        foreground = androidx.core.content.ContextCompat.getDrawable(
            this@tonalIconButton, typedValue.resourceId
        )
    }
    isClickable = true
    isFocusable = true
    this.contentDescription = contentDescription
    setOnClickListener { onClick() }
    addView(ImageView(this@tonalIconButton).apply {
        setImageResource(iconRes)
        imageTintList = ColorStateList.valueOf(themeColor(MaterialR.attr.colorOnSurfaceVariant))
    }, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
}

/** Filled M3 card: surfaceContainerLow tone, 12dp corners, no stroke. */
fun Context.materialCard(): MaterialCardView = MaterialCardView(this).apply {
    layoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = dp(12) }
    setCardBackgroundColor(themeColor(MaterialR.attr.colorSurfaceContainerLow))
    radius = dp(Design.RADIUS_MEDIUM).toFloat()
    cardElevation = 0f
    strokeWidth = 0
}

/**
 * Worm-style pager indicator (dotsindicator library), themed with M3 color roles.
 * Attaches itself to the given ViewPager2 and tracks page changes on its own.
 */
fun Context.wormIndicator(viewPager: ViewPager2): WormDotsIndicator =
    (LayoutInflater.from(this).inflate(R.layout.view_worm_indicator, null) as WormDotsIndicator).apply {
        setPointsColor(themeColor(MaterialR.attr.colorOutlineVariant))
        setDotIndicatorColor(themeColor(MaterialR.attr.colorPrimary))
        attachTo(viewPager)
    }

/**
 * Worm indicator bound to a looping ViewPager2 (adapter count = real count x repeats).
 * The proxy reports the REAL count and modulo-mapped positions, so dots and worm stay
 * in sync while the pager itself scrolls endlessly.
 */
fun Context.wormIndicatorLoop(
    pager: ViewPager2,
    realCount: () -> Int,
): WormDotsIndicator =
    (LayoutInflater.from(this).inflate(R.layout.view_worm_indicator, null) as WormDotsIndicator).apply {
        setPointsColor(themeColor(MaterialR.attr.colorOutlineVariant))
        setDotIndicatorColor(themeColor(MaterialR.attr.colorPrimary))
        this.pager = LoopPagerProxy(pager, realCount)
    }

private class LoopPagerProxy(
    private val pager: ViewPager2,
    private val realCount: () -> Int,
) : BaseDotsIndicator.Pager {

    private fun mod(value: Int): Int {
        val m = realCount().coerceAtLeast(1)
        return ((value % m) + m) % m
    }

    override val isNotEmpty: Boolean get() = realCount() > 0

    override val isEmpty: Boolean get() = realCount() == 0

    override val currentItem: Int get() = mod(pager.currentItem)

    override val count: Int get() = realCount()

    override fun setCurrentItem(index: Int, smooth: Boolean) {
        val count = realCount().coerceAtLeast(1)
        val base = (pager.currentItem / count) * count
        pager.setCurrentItem(base + ((index % count) + count) % count, smooth)
    }

    override fun removeOnPageChangeListener() {
        // The callback is tied to the pager's lifecycle; nothing to detach here.
    }

    override fun addOnPageChangeListener(helper: OnPageChangeListenerHelper) {
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                helper.onPageScrolled(mod(position), 0f)
            }
        })
    }
}
