package io.ymusic.android

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Posts non-service user notifications for in-app updates and Play Together room activity.
 * Ensures channels exist on Android 8+ and checks Android 13+ runtime permission cleanly.
 */
internal object AppNotificationHelper {

    const val UPDATES_CHANNEL_ID = "io.ymusic.updates"
    const val PLAY_TOGETHER_CHANNEL_ID = "io.ymusic.playtogether"

    private const val UPDATE_NOTIFICATION_ID = 2001
    private const val PLAY_TOGETHER_NOTIFICATION_ID = 2002

    fun notifyUpdateOffered(context: Context, versionName: String, notes: String?): Boolean {
        if (!canPost(context)) return false
        ensureChannel(
            context = context,
            id = UPDATES_CHANNEL_ID,
            name = "App Updates",
            description = "Notifies when a new PRVN Music version is available",
        )
        val body = notes
            ?.lineSequence()
            ?.map { it.trim().trimStart('#', '-', '*', ' ') }
            ?.firstOrNull { it.isNotBlank() }
            ?: "Tap to view details and update PRVN Music."
        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_UPDATES
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pi = PendingIntent.getActivity(
            context,
            UPDATE_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = if (versionName.isNotBlank()) "PRVN Music $versionName available" else "PRVN Music update available"
        return post(
            context = context,
            id = UPDATE_NOTIFICATION_ID,
            channelId = UPDATES_CHANNEL_ID,
            iconRes = R.drawable.ic_notification_update,
            title = title,
            text = body,
            pendingIntent = pi,
        )
    }

    fun notifyUpdateReady(context: Context, versionName: String): Boolean {
        if (!canPost(context)) return false
        ensureChannel(
            context = context,
            id = UPDATES_CHANNEL_ID,
            name = "App Updates",
            description = "Notifies when a new PRVN Music version is available",
        )
        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_UPDATES
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pi = PendingIntent.getActivity(
            context,
            UPDATE_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = if (versionName.isNotBlank()) "PRVN Music $versionName ready to install" else "Update ready to install"
        return post(
            context = context,
            id = UPDATE_NOTIFICATION_ID,
            channelId = UPDATES_CHANNEL_ID,
            iconRes = R.drawable.ic_notification_update,
            title = title,
            text = "Tap to install the downloaded update.",
            pendingIntent = pi,
        )
    }

    fun clearUpdateNotification(context: Context) {
        runCatching { NotificationManagerCompat.from(context).cancel(UPDATE_NOTIFICATION_ID) }
    }

    fun notifyPlayTogether(context: Context, title: String, body: String) {
        if (!canPost(context)) return
        ensureChannel(
            context = context,
            id = PLAY_TOGETHER_CHANNEL_ID,
            name = "Play Together",
            description = "Incoming song requests and room alerts in Play Together",
        )
        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_PLAY_TOGETHER
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pi = PendingIntent.getActivity(
            context,
            PLAY_TOGETHER_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        post(
            context = context,
            id = PLAY_TOGETHER_NOTIFICATION_ID,
            channelId = PLAY_TOGETHER_CHANNEL_ID,
            iconRes = R.drawable.ic_notification_music,
            title = title,
            text = body,
            pendingIntent = pi,
        )
    }

    private fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                return false
            }
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun ensureChannel(context: Context, id: String, name: String, description: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        if (nm.getNotificationChannel(id) != null) return
        nm.createNotificationChannel(
            NotificationChannel(id, name, NotificationManager.IMPORTANCE_DEFAULT).apply {
                this.description = description
            },
        )
    }

    @SuppressLint("MissingPermission")
    private fun post(
        context: Context,
        id: Int,
        channelId: String,
        iconRes: Int,
        title: String,
        text: String,
        pendingIntent: PendingIntent,
    ): Boolean {
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(iconRes)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        return runCatching { NotificationManagerCompat.from(context).notify(id, notification) }.isSuccess
    }
}
