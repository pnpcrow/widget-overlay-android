package com.example.widgetoverlay

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import kotlin.math.roundToInt

class BubbleActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetLabel = intent.getStringExtra(EXTRA_WIDGET_LABEL)
            ?: WidgetHostController(this).selectedWidgetLabel()
            ?: getString(R.string.no_widget)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(28), dp(24), dp(24))
            background = GradientDrawable().apply {
                setColor(getColor(R.color.md_theme_surface))
                cornerRadius = dp(28).toFloat()
            }
        }

        // Title with Material style
        root.addView(TextView(this).apply {
            text = getString(R.string.bubble_title)
            textSize = 20f
            setTextColor(getColor(R.color.md_theme_onSurface))
            setTypeface(null, android.graphics.Typeface.BOLD)
        })

        // Subtitle
        root.addView(TextView(this).apply {
            text = getString(R.string.widget_selected, widgetLabel)
            textSize = 15f
            setTextColor(getColor(R.color.md_theme_onSurfaceVariant))
            setPadding(0, dp(16), 0, dp(24))
        })

        // Material Button
        root.addView(MaterialButton(this).apply {
            text = getString(R.string.open_in_app)
            setTextColor(getColor(R.color.md_theme_onPrimary))
            setBackgroundColor(getColor(R.color.md_theme_primary))
            cornerRadius = dp(20)
            isAllCaps = false
            setOnClickListener {
                startActivity(Intent(this@BubbleActivity, MainActivity::class.java))
                finish()
            }
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        // Animate appear
        root.alpha = 0f
        root.translationY = dp(40).toFloat()
        root.scaleX = 0.8f
        root.scaleY = 0.8f
        setContentView(root)

        val alpha = ObjectAnimator.ofFloat(root, View.ALPHA, 0f, 1f)
        val translateY = ObjectAnimator.ofFloat(root, View.TRANSLATION_Y, dp(40).toFloat(), 0f)
        val scaleX = ObjectAnimator.ofFloat(root, View.SCALE_X, 0.8f, 1f)
        val scaleY = ObjectAnimator.ofFloat(root, View.SCALE_Y, 0.8f, 1f)

        AnimatorSet().apply {
            playTogether(alpha, translateY, scaleX, scaleY)
            duration = 300L
            interpolator = OvershootInterpolator(1.1f)
            start()
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    companion object {
        const val EXTRA_WIDGET_LABEL = "extra_widget_label"
    }
}
