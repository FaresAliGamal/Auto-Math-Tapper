package com.example.automathtapper.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.automathtapper.ErrorBus
import com.example.automathtapper.ErrorOverlay

class ProjectionFgService : Service() {
    companion object {
        @Volatile var ready: Boolean = false
        fun ensureRunning(ctx: android.content.Context) {
            try {
                val i = Intent(ctx, ProjectionFgService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    ctx.startForegroundService(i)
                else
                    ctx.startService(i)
            } catch (e: Throwable) { ErrorBus.post("FGS: " + (e.message ?: e.toString())) }
        }
    }
    override fun onCreate() {
        try {
            super.onCreate()
            ErrorOverlay.init(applicationContext)
            val cid = "proj_fg"
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (nm.getNotificationChannel(cid) == null)
                    nm.createNotificationChannel(NotificationChannel(cid, "Projection FG", NotificationManager.IMPORTANCE_LOW))
            }
            val notif: Notification = NotificationCompat.Builder(this, cid)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("Projection FG")
                .setContentText("Ready")
                .setOngoing(true)
                .build()
            ErrorBus.post("Starting FGS")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(1001, notif, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            } else {
                startForeground(1001, notif)
            }
            ready = true
        } catch (e: Throwable) { ErrorBus.post("FGS: " + (e.message ?: e.toString())) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        try {
            ready = false
            super.onDestroy()
        } catch (e: Throwable) { ErrorBus.post("FGS: " + (e.message ?: e.toString())) }
    }

}
