package com.cardsp.oembooster

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class DspEngineService : Service() {

    companion object {
        const val CHANNEL_ID = "car_dsp_oem_channel"
        const val NOTIF_ID = 1001
        const val ACTION_ATTACH_SESSION = "com.cardsp.oembooster.ATTACH_SESSION"
        const val ACTION_DETACH_SESSION = "com.cardsp.oembooster.DETACH_SESSION"
        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
    }

    inner class LocalBinder : Binder() {
        fun getService(): DspEngineService = this@DspEngineService
    }

    private val binder = LocalBinder()
    lateinit var dspController: CarDspController
        private set

    var lastActiveAppPackage: String = "Global System Mix (All Apps)"
        private set

    private val runtimeSessionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0)
            val pkg = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME) ?: "Unknown App"
            when (intent.action) {
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                    lastActiveAppPackage = formatPackageName(pkg)
                    dspController.attachSession(sessionId)
                    updateNotification()
                }
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                    if (sessionId != 0) {
                        dspController.detachSession(sessionId)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        dspController = CarDspController(applicationContext)
        startForeground(NOTIF_ID, buildNotification())

        // Register dynamic broadcast receiver for apps like YouTube Music, Spotify, Poweramp
        val filter = IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(runtimeSessionReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(runtimeSessionReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ATTACH_SESSION -> {
                val sid = intent.getIntExtra(EXTRA_SESSION_ID, 0)
                val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME)
                if (!pkg.isNullOrBlank()) {
                    lastActiveAppPackage = formatPackageName(pkg)
                }
                dspController.attachSession(sid)
                updateNotification()
            }
            ACTION_DETACH_SESSION -> {
                val sid = intent.getIntExtra(EXTRA_SESSION_ID, 0)
                if (sid != 0) dspController.detachSession(sid)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        try {
            unregisterReceiver(runtimeSessionReceiver)
        } catch (_: Throwable) {}
        dspController.releaseAll()
        super.onDestroy()
    }

    fun updateNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, buildNotification())
    }

    private fun formatPackageName(pkg: String): String {
        return when {
            pkg.contains("youtube.music", ignoreCase = true) -> "YouTube Music"
            pkg.contains("youtube", ignoreCase = true) -> "YouTube Video"
            pkg.contains("spotify", ignoreCase = true) -> "Spotify"
            pkg.contains("chrome", ignoreCase = true) -> "Chrome Browser"
            else -> pkg.substringAfterLast('.')
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Car DSP OEM Audio Engine",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps OEM Sound Restorer & Pre-Amp DSP active in background"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val contentIntent = PendingIntent.getActivity(this, 0, openIntent, pendingFlags)
        val cfg = dspController.currentConfig
        val statusText = if (cfg.enabled) {
            "Active Preset: ${cfg.presetId} (+${cfg.preampGainDb}dB) • $lastActiveAppPackage"
        } else {
            "DSP Bypassed (Stock Android Sound)"
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentTitle("Car DSP OEM Sound Restorer")
            .setContentText(statusText)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
