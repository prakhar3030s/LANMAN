package com.example.lanman.data.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.lanman.domain.engine.MessagingEngine
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class RadarService : Service() {

    @Inject
    lateinit var messagingEngine: MessagingEngine

    private val CHANNEL_ID = "channel_radar"
    private val NOTIFICATION_ID = 4747

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LocalLink is running")
            .setContentText("Listening for nearby devices")
            // A generic icon until we add a proper one, reusing standard Android drawables
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, 
                notification, 
                ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        messagingEngine.start()

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        messagingEngine.stop()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Radar Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps LocalLink running in the background to discover peers and receive messages."
                setShowBadge(false)
            }
            
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
