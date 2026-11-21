package com.cardsp.oembooster

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.os.Build
import android.util.Log

/**
 * Core Car Audio DSP Engine v1.1.0 (by Zydan).
 *
 * Calibrated specifically to eliminate Double-Bass Stacking & Bass Masking on Android Head Units,
 * bringing forward crystal-clear OEM vocals, de-mudding 125Hz-400Hz cabin resonance,
 * and delivering tight, controlled sub-bass punch without drowning the singer.
 */
data class DspConfig(
    val enabled: Boolean = true,
    val presetId: String = "OEM_PIONEER",
    val preampGainDb: Float = 3.5f,          // -6 dB to +12 dB
    val subBassPunch: Int = 25,              // 0 to 100 (calibrated tight sub-bass without masking vocals)
    val vocalClarity: Int = 85,              // 0 to 100 (1kHz - 4kHz forward vocal presence)
    val trebleSparkle: Int = 75,             // 0 to 100 (8kHz - 16kHz crisp air & detail)
    val loudnessComp: Boolean = false,       // Off by default to prevent excessive low-mid boom
    val youtubeFixer: Boolean = true,        // Cuts 250-500Hz mud & lifts compressed vocals
    val cabinStage: Int = 20,                // 0 to 100 (kept moderate so center vocals stay focused)
    // 10-Band ISO frequencies: 31, 62, 125, 250, 500, 1k, 2k, 4k, 8k, 16k (in dB, -12 to +12)
    val eqBandsDb: FloatArray = floatArrayOf(1.5f, 2.0f, -0.5f, -3.5f, -1.5f, 3.5f, 5.0f, 4.5f, 3.5f, 4.0f)
) {
    companion object {
        const val CONFIG_SCHEMA_VERSION = 2 // Forces upgrade from v1.0 bass-heavy defaults

        val BAND_FREQUENCIES = floatArrayOf(
            31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f
        )
        val BAND_LABELS = arrayOf(
            "31Hz", "62Hz", "125Hz", "250Hz", "500Hz", "1kHz", "2kHz", "4kHz", "8kHz", "16kHz"
        )

        fun getPreset(id: String): DspConfig {
            return when (id) {
                "OEM_PIONEER" -> DspConfig(
                    enabled = true,
                    presetId = "OEM_PIONEER",
                    preampGainDb = 3.5f,
                    subBassPunch = 25,
                    vocalClarity = 85,
                    trebleSparkle = 75,
                    loudnessComp = false,
                    youtubeFixer = true,
                    cabinStage = 20,
                    // Tight sub-bass (+1.5/+2.0dB), deep cut at muddy 250Hz (-3.5dB), forward clear vocals (+3.5 to +5.0dB)
                    eqBandsDb = floatArrayOf(1.5f, 2.0f, -0.5f, -3.5f, -1.5f, 3.5f, 5.0f, 4.5f, 3.5f, 4.0f)
                )
                "OEM_KENWOOD" -> DspConfig(
                    enabled = true,
                    presetId = "OEM_KENWOOD",
                    preampGainDb = 3.5f,
                    subBassPunch = 15,
                    vocalClarity = 95,
                    trebleSparkle = 70,
                    loudnessComp = false,
                    youtubeFixer = true,
                    cabinStage = 15,
                    // Ultra-clear front-stage vocal focus, zero bass mud
                    eqBandsDb = floatArrayOf(0.5f, 1.5f, -1.0f, -4.0f, -1.0f, 4.5f, 6.0f, 5.0f, 3.5f, 3.5f)
                )
                "YOUTUBE_FIXER" -> DspConfig(
                    enabled = true,
                    presetId = "YOUTUBE_FIXER",
                    preampGainDb = 4.5f,
                    subBassPunch = 20,
                    vocalClarity = 90,
                    trebleSparkle = 85,
                    loudnessComp = false,
                    youtubeFixer = true,
                    cabinStage = 25,
                    // Maximum de-mudding for compressed YouTube videos: strips 125-500Hz boxiness, boosts vocal & high air
                    eqBandsDb = floatArrayOf(1.5f, 2.0f, -1.5f, -4.5f, -2.5f, 4.0f, 5.5f, 5.5f, 4.5f, 5.0f)
                )
                "YTMUSIC_JBL" -> DspConfig(
                    enabled = true,
                    presetId = "YTMUSIC_JBL",
                    preampGainDb = 3.5f,
                    subBassPunch = 35,
                    vocalClarity = 80,
                    trebleSparkle = 80,
                    loudnessComp = false,
                    youtubeFixer = false,
                    cabinStage = 25,
                    // Modern JBL punch with clean mid-bass cut so vocals stay crisp & upfront
                    eqBandsDb = floatArrayOf(2.5f, 3.0f, 0.5f, -3.0f, -1.5f, 3.0f, 4.5f, 4.5f, 4.0f, 4.5f)
                )
                "FLAT_BYPASS" -> DspConfig(
                    enabled = true,
                    presetId = "FLAT_BYPASS",
                    preampGainDb = 0.0f,
                    subBassPunch = 0,
                    vocalClarity = 0,
                    trebleSparkle = 0,
                    loudnessComp = false,
                    youtubeFixer = false,
                    cabinStage = 0,
                    eqBandsDb = FloatArray(10) { 0.0f }
                )
                else -> getPreset("OEM_PIONEER")
            }
        }
    }
}

class CarDspController(private val context: Context) {

    private val TAG = "CarDspController"

    private class SessionEffects(
        var dynamics: DynamicsProcessing? = null,
        var equalizer: Equalizer? = null,
        var bassBoost: BassBoost? = null,
        var loudness: LoudnessEnhancer? = null,
        var virtualizer: Virtualizer? = null
    )

    private val activeSessions = mutableMapOf<Int, SessionEffects>()
    var currentConfig: DspConfig = loadConfig()
        private set

    init {
        attachSession(0)
    }

    @Synchronized
    fun attachSession(sessionId: Int) {
        if (activeSessions.containsKey(sessionId)) {
            applyToSession(sessionId, activeSessions[sessionId]!!)
            return
        }

        val effects = SessionEffects()

        // 1. Multi-Stage DynamicsProcessing (API 28+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val cfgBuilder = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2,
                    true, 10, // Pre-EQ: 10 bands
                    true, 4,  // Multi-Band Compressor: 4 crossover bands
                    true, 10, // Post-EQ: 10 bands
                    true      // Soft-Knee Limiter
                )
                val dp = DynamicsProcessing(100, sessionId, cfgBuilder.build())
                dp.enabled = currentConfig.enabled
                effects.dynamics = dp
                Log.i(TAG, "Attached DynamicsProcessing to session $sessionId")
            } catch (e: Throwable) {
                Log.w(TAG, "DynamicsProcessing unavailable on session $sessionId: ${e.message}")
            }
        }

        // 2. Standard Hardware Equalizer
        try {
            val eq = Equalizer(100, sessionId)
            eq.enabled = currentConfig.enabled
            effects.equalizer = eq
        } catch (e: Throwable) {
            Log.w(TAG, "Equalizer unavailable on session $sessionId: ${e.message}")
        }

        // 3. Hardware BassBoost
        try {
            val bb = BassBoost(100, sessionId)
            bb.enabled = currentConfig.enabled
            effects.bassBoost = bb
        } catch (e: Throwable) {
            Log.w(TAG, "BassBoost unavailable on session $sessionId: ${e.message}")
        }

        // 4. LoudnessEnhancer
        try {
            val le = LoudnessEnhancer(sessionId)
            le.enabled = currentConfig.enabled
            effects.loudness = le
        } catch (e: Throwable) {
            Log.w(TAG, "LoudnessEnhancer unavailable on session $sessionId: ${e.message}")
        }

        // 5. Cabin Stereo Stage Virtualizer
        try {
            val virt = Virtualizer(100, sessionId)
            virt.enabled = currentConfig.enabled
            effects.virtualizer = virt
        } catch (e: Throwable) {
            Log.w(TAG, "Virtualizer unavailable on session $sessionId: ${e.message}")
        }

        activeSessions[sessionId] = effects
        applyToSession(sessionId, effects)
    }

    @Synchronized
    fun detachSession(sessionId: Int) {
        if (sessionId == 0) return
        activeSessions.remove(sessionId)?.let { releaseEffects(it) }
    }

    @Synchronized
    fun updateConfig(newConfig: DspConfig) {
        currentConfig = newConfig
        saveConfig(newConfig)
        activeSessions.forEach { (sessionId, effects) ->
            applyToSession(sessionId, effects)
        }
    }

    /**
     * Computes the final composite 10-band response curve (in dB).
     * Calibrated in v1.1.0 so sub-bass NEVER overwhelms vocal midrange (1kHz-4kHz).
     */
    fun computeEffectiveCurveDb(cfg: DspConfig = currentConfig): FloatArray {
        val out = FloatArray(10)
        val bassFactor = cfg.subBassPunch / 100f
        val vocalFactor = cfg.vocalClarity / 100f
        val trebleFactor = cfg.trebleSparkle / 100f

        for (i in 0 until 10) {
            var db = cfg.eqBandsDb.getOrElse(i) { 0f }

            // Controlled Sub-bass warmth (only 31Hz & 62Hz, and gently cleans 250Hz mud when bass increases)
            when (i) {
                0 -> db += bassFactor * 2.0f
                1 -> db += bassFactor * 2.2f
                2 -> db += bassFactor * 0.5f
                3 -> db -= bassFactor * 1.0f // Automatic anti-mud dip at 250Hz
            }

            // Forward Vocal Clarity enhancer (500Hz body + 1kHz-4kHz presence)
            when (i) {
                4 -> db += vocalFactor * 0.8f
                5 -> db += vocalFactor * 2.8f
                6 -> db += vocalFactor * 3.8f
                7 -> db += vocalFactor * 3.0f
            }

            // Crisp Treble Exciter (8kHz, 16kHz)
            when (i) {
                8 -> db += trebleFactor * 2.5f
                9 -> db += trebleFactor * 3.2f
            }

            // Subtle OEM Loudness Contour (if user turns it on)
            if (cfg.loudnessComp) {
                when (i) {
                    0, 1 -> db += 1.2f
                    8, 9 -> db += 1.2f
                }
            }

            // YouTube De-Mud & Vocal Restorer (cuts boxy 125-500Hz & lifts vocal intelligibility)
            if (cfg.youtubeFixer) {
                when (i) {
                    2 -> db -= 1.0f // 125Hz boom cut
                    3 -> db -= 2.2f // 250Hz mud cut
                    4 -> db -= 1.5f // 500Hz boxy kardus cut
                    5 -> db += 1.5f // 1kHz vocal body lift
                    6 -> db += 2.2f // 2kHz vocal clarity lift
                    7 -> db += 1.8f // 4kHz detail lift
                    8, 9 -> db += 1.5f
                }
            }

            out[i] = db.coerceIn(-12f, 12f)
        }
        return out
    }

    private fun applyToSession(sessionId: Int, effects: SessionEffects) {
        val cfg = currentConfig
        val finalCurve = computeEffectiveCurveDb(cfg)
        val hasDp = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && effects.dynamics != null)

        // 1. Apply DynamicsProcessing (API 28+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val dp = effects.dynamics
            if (dp != null) {
                try {
                    dp.enabled = cfg.enabled
                    if (cfg.enabled) {
                        dp.setInputGainAllChannelsTo(cfg.preampGainDb)

                        // If Hardware Equalizer is also present on this HU, split the curve 50/50
                        // between DynamicsProcessing and Hardware Equalizer so they NEVER double-stack!
                        val dpCurveScale = if (effects.equalizer != null) 0.5f else 1.0f

                        for (i in 0 until 10) {
                            val freq = DspConfig.BAND_FREQUENCIES[i]
                            val scaledGain = finalCurve[i] * dpCurveScale
                            val preBand = DynamicsProcessing.EqBand(true, freq, scaledGain * 0.45f)
                            val postBand = DynamicsProcessing.EqBand(true, freq, scaledGain * 0.55f)
                            dp.setPreEqBandAllChannelsTo(i, preBand)
                            dp.setPostEqBandAllChannelsTo(i, postBand)
                        }

                        // 4-Band Multi-Band Compressor (MBC) calibrated to bring vocals upfront
                        val mbcCutoffs = floatArrayOf(110f, 550f, 4500f, 20000f)
                        val mbcPostGains = floatArrayOf(
                            (cfg.subBassPunch / 100f) * 1.2f,  // Band 0 (<110Hz): Tight controlled sub-bass
                            -1.0f,                             // Band 1 (110-550Hz): Keep boxy low-mids clean
                            (cfg.vocalClarity / 100f) * 3.2f,  // Band 2 (550Hz-4.5kHz): Upfront crystal-clear vocals!
                            (cfg.trebleSparkle / 100f) * 2.0f  // Band 3 (>4.5kHz): Silky highs
                        )
                        for (b in 0 until 4) {
                            val mbcBand = DynamicsProcessing.MbcBand(
                                true,
                                mbcCutoffs[b],
                                10.0f,  // attackTime ms
                                85.0f,  // releaseTime ms
                                2.0f,   // gentle musical compression ratio
                                -22.0f, // threshold dB
                                6.0f,   // soft knee width dB
                                -60.0f, // noise gate threshold
                                1.0f,   // expander ratio
                                0.0f,   // preGain dB
                                mbcPostGains[b]
                            )
                            dp.setMbcBandAllChannelsTo(b, mbcBand)
                        }

                        // Transparent Limiter at -0.5 dB so bass doesn't duck the vocals
                        val limiter = DynamicsProcessing.Limiter(
                            true,
                            true,
                            0,
                            3.0f,
                            50.0f,
                            3.5f,
                            -0.5f,
                            0.0f
                        )
                        dp.setLimiterAllChannelsTo(limiter)
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Error configuring DynamicsProcessing on session $sessionId: ${e.message}")
                }
            }
        }

        // 2. Apply Hardware Equalizer (scaled so it doesn't double-stack with DynamicsProcessing)
        val eq = effects.equalizer
        if (eq != null) {
            try {
                eq.enabled = cfg.enabled
                if (cfg.enabled) {
                    val eqScale = if (hasDp) 0.5f else 1.0f
                    val numBands = eq.numberOfBands.toInt()
                    val range = eq.bandLevelRange
                    val minMb = range[0].toInt()
                    val maxMb = range[1].toInt()

                    for (b in 0 until numBands) {
                        val centerFreqHz = eq.getCenterFreq(b.toShort()) / 1000f
                        val interpolatedDb = interpolateGainForFreq(centerFreqHz, finalCurve) * eqScale
                        val targetMillibels = (interpolatedDb * 100).toInt().coerceIn(minMb, maxMb)
                        eq.setBandLevel(b.toShort(), targetMillibels.toShort())
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Error configuring Equalizer on session $sessionId: ${e.message}")
            }
        }

        // 3. Apply Hardware BassBoost (Gentle scaling 0 - 350 max permille so it never drowns vocals)
        val bb = effects.bassBoost
        if (bb != null) {
            try {
                bb.enabled = cfg.enabled && cfg.subBassPunch > 0
                if (cfg.enabled && bb.strengthSupported) {
                    val strength = (cfg.subBassPunch * 3.5f).toInt().coerceIn(0, 350).toShort()
                    bb.setStrength(strength)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Error configuring BassBoost: ${e.message}")
            }
        }

        // 4. Apply LoudnessEnhancer Pre-Amp Gain (only if DynamicsProcessing inputGain isn't already doing it)
        val le = effects.loudness
        if (le != null) {
            try {
                le.enabled = cfg.enabled
                if (cfg.enabled) {
                    val gainScale = if (hasDp) 45 else 100
                    val targetGainMb = (cfg.preampGainDb * gainScale).toInt().coerceIn(0, 800)
                    le.setTargetGain(targetGainMb)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Error configuring LoudnessEnhancer: ${e.message}")
            }
        }

        // 5. Apply Cabin Stage Virtualizer (Capped at 400 permille max so center vocals never get hollow)
        val virt = effects.virtualizer
        if (virt != null) {
            try {
                virt.enabled = cfg.enabled && cfg.cabinStage > 0
                if (cfg.enabled && virt.strengthSupported) {
                    val strength = (cfg.cabinStage * 4).coerceIn(0, 400).toShort()
                    virt.setStrength(strength)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Error configuring Virtualizer: ${e.message}")
            }
        }
    }

    private fun interpolateGainForFreq(freqHz: Float, curveDb: FloatArray): Float {
        val freqs = DspConfig.BAND_FREQUENCIES
        if (freqHz <= freqs.first()) return curveDb.first()
        if (freqHz >= freqs.last()) return curveDb.last()
        for (i in 0 until freqs.size - 1) {
            if (freqHz >= freqs[i] && freqHz <= freqs[i + 1]) {
                val ratio = (freqHz - freqs[i]) / (freqs[i + 1] - freqs[i])
                return curveDb[i] + ratio * (curveDb[i + 1] - curveDb[i])
            }
        }
        return 0f
    }

    fun getActiveSessionCount(): Int = activeSessions.size

    fun isDynamicsProcessingActive(): Boolean {
        return activeSessions.values.any { it.dynamics != null }
    }

    @Synchronized
    fun releaseAll() {
        activeSessions.values.forEach { releaseEffects(it) }
        activeSessions.clear()
    }

    private fun releaseEffects(effects: SessionEffects) {
        try { effects.dynamics?.release() } catch (_: Throwable) {}
        try { effects.equalizer?.release() } catch (_: Throwable) {}
        try { effects.bassBoost?.release() } catch (_: Throwable) {}
        try { effects.loudness?.release() } catch (_: Throwable) {}
        try { effects.virtualizer?.release() } catch (_: Throwable) {}
    }

    private fun saveConfig(cfg: DspConfig) {
        val prefs = context.getSharedPreferences("car_dsp_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt("schema_version", DspConfig.CONFIG_SCHEMA_VERSION)
            putBoolean("enabled", cfg.enabled)
            putString("presetId", cfg.presetId)
            putFloat("preampGainDb", cfg.preampGainDb)
            putInt("subBassPunch", cfg.subBassPunch)
            putInt("vocalClarity", cfg.vocalClarity)
            putInt("trebleSparkle", cfg.trebleSparkle)
            putBoolean("loudnessComp", cfg.loudnessComp)
            putBoolean("youtubeFixer", cfg.youtubeFixer)
            putInt("cabinStage", cfg.cabinStage)
            putString("eqBandsDb", cfg.eqBandsDb.joinToString(","))
            apply()
        }
    }

    private fun loadConfig(): DspConfig {
        val prefs = context.getSharedPreferences("car_dsp_prefs", Context.MODE_PRIVATE)
        // Automatically migrate from v1.0.0 bass-heavy settings to v1.1.0 clear-vocal settings
        val savedSchema = prefs.getInt("schema_version", 1)
        if (!prefs.contains("presetId") || savedSchema < DspConfig.CONFIG_SCHEMA_VERSION) {
            val fresh = DspConfig.getPreset("OEM_PIONEER")
            saveConfig(fresh)
            return fresh
        }

        val bandsStr = prefs.getString("eqBandsDb", null)
        val bands = if (!bandsStr.isNullOrBlank()) {
            val parts = bandsStr.split(",")
            if (parts.size == 10) FloatArray(10) { parts[it].toFloatOrNull() ?: 0f }
            else DspConfig.getPreset("OEM_PIONEER").eqBandsDb
        } else {
            DspConfig.getPreset("OEM_PIONEER").eqBandsDb
        }

        return DspConfig(
            enabled = prefs.getBoolean("enabled", true),
            presetId = prefs.getString("presetId", "OEM_PIONEER") ?: "OEM_PIONEER",
            preampGainDb = prefs.getFloat("preampGainDb", 3.5f),
            subBassPunch = prefs.getInt("subBassPunch", 25),
            vocalClarity = prefs.getInt("vocalClarity", 85),
            trebleSparkle = prefs.getInt("trebleSparkle", 75),
            loudnessComp = prefs.getBoolean("loudnessComp", false),
            youtubeFixer = prefs.getBoolean("youtubeFixer", true),
            cabinStage = prefs.getInt("cabinStage", 20),
            eqBandsDb = bands
        )
    }
}
