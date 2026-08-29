package com.wattmeter.monitor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import java.util.Locale

class ChargeMonitorService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val channelId = "watt_meter_channel"
    private val notificationId = 42

    private val updateRunnable = object : Runnable {
        override fun run() {
            updateNotification()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(notificationId, buildNotification(BatteryReader.read(this)))
        handler.post(updateRunnable)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateRunnable)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateNotification() {
        val reading = BatteryReader.read(this)
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(notificationId, buildNotification(reading))
    }

    private fun buildNotification(reading: BatteryReading): Notification {
        val contentText = when {
            !reading.isCharging -> "Not charging \u2022 ${reading.batteryPercent}%"
            !reading.currentReliable -> "Charging (wattage unavailable on this device) \u2022 ${reading.batteryPercent}%"
            else -> String.format(
                Locale.US,
                "%.1f W \u2022 %.2f V \u2022 %.2f A \u2022 %d%%",
                reading.watts, reading.voltageVolts, reading.currentAmps, reading.batteryPercent
            )
        }

        val title = if (reading.isCharging && reading.currentReliable) {
            String.format(Locale.US, "%.1f W charging", reading.watts)
        } else {
            "Watt Meter"
        }

        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Charging Monitor",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "Shows live charging wattage while connected to a charger"
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}
