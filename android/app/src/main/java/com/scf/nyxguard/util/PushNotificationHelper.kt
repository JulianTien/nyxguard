package com.scf.nyxguard.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.scf.nyxguard.R

object PushNotificationHelper {

    private const val TAG = "PushNotificationHelper"
    private const val CHANNEL_ID = "nyxguard_guardian_alerts"

    fun show(context: Context, title: String?, body: String?, route: GuardianDeepLink) {
        ensureChannel(context)

        val notificationTitle = title?.trim().orEmpty()
            .ifBlank { context.getString(R.string.push_default_title) }
        val notificationBody = body?.trim().orEmpty()
            .ifBlank { context.getString(R.string.push_default_body) }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_map)
            .setContentTitle(notificationTitle)
            .setContentText(notificationBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notificationBody))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(buildContentIntent(context, route))
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(route.stableNotificationId(), notification)
        }.onFailure { error ->
            Log.w(TAG, "Unable to display guardian notification", error)
        }
    }

    private fun buildContentIntent(context: Context, route: GuardianDeepLink): PendingIntent {
        val intent = route.toIntent(context)
        return PendingIntent.getActivity(
            context,
            route.stableNotificationId(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.push_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.push_channel_description)
        }
        manager.createNotificationChannel(channel)
    }
}
