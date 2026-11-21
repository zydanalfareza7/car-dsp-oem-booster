package com.cardsp.oembooster

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.os.Build

/**
 * Listens for Car Head Unit Ignition Boot & Audio Session creation from YouTube / YT Music / Spotify.
 */
class AudioSessionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                val serviceIntent = Intent(context, DspEngineService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
            AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> {
                val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0)
                val pkg = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME)
                val serviceIntent = Intent(context, DspEngineService::class.java).apply {
                    action = DspEngineService.ACTION_ATTACH_SESSION
                    putExtra(DspEngineService.EXTRA_SESSION_ID, sessionId)
                    putExtra(DspEngineService.EXTRA_PACKAGE_NAME, pkg)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
            AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> {
                val sessionId = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, 0)
                val serviceIntent = Intent(context, DspEngineService::class.java).apply {
                    action = DspEngineService.ACTION_DETACH_SESSION
                    putExtra(DspEngineService.EXTRA_SESSION_ID, sessionId)
                }
                context.startService(serviceIntent)
            }
        }
    }
}
