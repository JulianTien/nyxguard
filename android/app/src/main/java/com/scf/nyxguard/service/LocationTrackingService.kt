package com.scf.nyxguard.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.scf.nyxguard.R
import com.scf.nyxguard.util.AmapSdkInitializer
import com.scf.nyxguard.util.AndroidLocationProvider

class LocationTrackingService : Service() {

    private val binder = LocationBinder()
    private var locationSubscription: AndroidLocationProvider.Subscription? = null
    var onLocationUpdate: ((Location) -> Unit)? = null
    private var intervalMs: Long = DEFAULT_WALK_INTERVAL
    private var notificationText: String = ""

    inner class LocationBinder : Binder() {
        fun getService(): LocationTrackingService = this@LocationTrackingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        notificationText = getString(R.string.tracking_notification_default)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intervalMs = intent?.getLongExtra(EXTRA_INTERVAL, DEFAULT_WALK_INTERVAL) ?: DEFAULT_WALK_INTERVAL
        notificationText = intent?.getStringExtra(EXTRA_NOTIFICATION_TEXT)
            ?: getString(R.string.tracking_notification_default)
        startForeground(NOTIFICATION_ID, buildNotification())
        startLocationUpdates()
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.tracking_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.tracking_channel_description)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        // 使用 launcher Activity 作为通知点击目标
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            launchIntent ?: Intent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.tracking_notification_title))
            .setContentText(notificationText)
            .setSmallIcon(R.drawable.ic_nav_map)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun startLocationUpdates() {
        AmapSdkInitializer.ensureInitialized(this)

        locationSubscription?.stop()
        locationSubscription = AndroidLocationProvider.requestLocationUpdates(
            this,
            intervalMs
        ) { location ->
            onLocationUpdate?.invoke(location)
        }
    }

    /** SOS 模式：提升定位频率到 3 秒 */
    fun enableSOSMode() {
        updateInterval(3_000)
    }

    /** 动态更新定位频率 */
    fun updateInterval(newIntervalMs: Long) {
        intervalMs = newIntervalMs
        if (locationSubscription != null) {
            startLocationUpdates()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        locationSubscription?.stop()
        locationSubscription = null
    }

    companion object {
        const val CHANNEL_ID = "nyxguard_tracking"
        const val NOTIFICATION_ID = 1001
        const val EXTRA_INTERVAL = "interval"
        const val EXTRA_NOTIFICATION_TEXT = "notification_text"
        const val DEFAULT_WALK_INTERVAL = 10_000L
        const val DEFAULT_RIDE_INTERVAL = 5_000L
    }
}
