package com.example.widgetoverlay

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat

/** Creates optional system-surface notifications. These never contain third-party widget RemoteViews. */
class SurfaceNotifier(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun notificationsAllowed(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun postBubbleSummary(widgetLabel: String?) {
        if (!notificationsAllowed()) return
        createChannels()
        val bubbleIntent = PendingIntent.getActivity(
            context,
            701,
            Intent(context, BubbleActivity::class.java).putExtra(BubbleActivity.EXTRA_WIDGET_LABEL, widgetLabel),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val contentIntent = PendingIntent.getActivity(
            context,
            702,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val bubble = NotificationCompat.BubbleMetadata.Builder(
            bubbleIntent,
            IconCompat.createWithResource(context, R.drawable.ic_widget_overlay),
        )
            .setDesiredHeight(520)
            .setAutoExpandBubble(false)
            .build()
        val notification = NotificationCompat.Builder(context, BUBBLE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_widget_overlay)
            .setContentTitle(context.getString(R.string.bubble_title))
            .setContentText(context.getString(R.string.bubble_text))
            .setContentIntent(contentIntent)
            .setBubbleMetadata(bubble)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(BUBBLE_NOTIFICATION_ID, notification)
    }

    /**
     * The promoted request is used only on API 36+. The system, user settings, and OEM may still
     * decline promotion, in which case the same notification remains a normal ongoing notification.
     */
    fun postLiveUpdate(progress: Int) {
        if (!notificationsAllowed()) return
        createChannels()
        val boundedProgress = progress.coerceIn(0, 100)
        val builder = NotificationCompat.Builder(context, LIVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_widget_overlay)
            .setContentTitle(context.getString(R.string.live_title))
            .setContentText(context.getString(R.string.live_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, boundedProgress, false)
        if (Build.VERSION.SDK_INT >= 36) {
            builder.setRequestPromotedOngoing(true)
        }
        val notification = builder.build()
        manager.notify(LIVE_NOTIFICATION_ID, notification)
    }

    fun completeLiveUpdate() {
        if (!notificationsAllowed()) return
        createChannels()
        val notification = NotificationCompat.Builder(context, LIVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_widget_overlay)
            .setContentTitle(context.getString(R.string.live_complete_title))
            .setContentText(context.getString(R.string.live_complete_text))
            .setAutoCancel(true)
            .build()
        manager.notify(LIVE_NOTIFICATION_ID, notification)
    }

    private fun createChannels() {
        manager.createNotificationChannel(
            NotificationChannel(
                BUBBLE_CHANNEL_ID,
                context.getString(R.string.bubble_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = context.getString(R.string.bubble_channel_description) },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                LIVE_CHANNEL_ID,
                context.getString(R.string.live_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.live_channel_description) },
        )
    }

    companion object {
        private const val BUBBLE_CHANNEL_ID = "widget_bubble"
        private const val LIVE_CHANNEL_ID = "widget_live_update"
        private const val BUBBLE_NOTIFICATION_ID = 30
        private const val LIVE_NOTIFICATION_ID = 31
    }
}

