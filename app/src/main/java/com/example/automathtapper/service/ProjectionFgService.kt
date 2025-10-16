package com.example.automathtapper.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ProjectionFgService : Service() {
    companion object {
        @Volatile var ready: Boolean = false
            private set

        fun ensureRunning(ctx: Context) {
            val i = Intent(ctx, ProjectionFgService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(i)
            } else {
                ctx.startService(i)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val channelId = "projection_fg"
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(channelId, "Screen capture", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }
        val notif = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("AutoMathTapper")
            .setContentText("Ready for screen capture")
            .setOngoing(true)
            .build()
        startForeground(1001, notif)
        ready = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        ready = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
