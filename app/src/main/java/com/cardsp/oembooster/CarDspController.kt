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
 * Core Car Audio DSP Engine.
 *
 * Uses Android's hardware/HAL Multi-Stage DynamicsProcessing (API 28+) plus fallback/companion
 * Equalizer, BassBoost, LoudnessEnhancer, and Virtualizer across BOTH Global Mix (session 0)
 * and active player sessions (YouTube, YouTube Music, Spotify, etc.).
 */
data class DspConfig(
    val enabled: Boolean = true,
    val presetId: String = "OEM_PIONEER",
    val preampGainDb: Float = 4.5f,          // -6 dB to +12 dB
    val subBassPunch: Int = 65,              // 0 to 100 (45Hz - 80Hz psychoacoustic warmth)
    val vocalClarity: Int = 55,              // 0 to 100 (1.5kHz - 4kHz presence)
    val trebleSparkle: Int = 60,             // 0 to 100 (8kHz - 16kHz air & detail)
    val loudnessComp: Boolean = true,        // OEM Fletcher-Munson low-volume warmth
    val youtubeFixer: Boolean = true,        // De-mud 250-400Hz & expand compressed YT dynamics
    val cabinStage: Int = 35,                // 0 to 100 stereo widening
    // 10-Band ISO frequencies: 31, 62, 125, 250, 500, 1k, 2k, 4k, 8k, 16k (in dB, -12 to +12)
    val eqBandsDb: FloatArray = floatArrayOf(5.5f, 6.0f, 3.0f, -1.5f, -1.0f, 1.0f, 2.5f, 3.5f, 4.5f, 5.0f)
) {
    companion object {
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
                    preampGainDb = 5.0f,
                    subBassPunch = 75,
                    vocalClarity = 60,
                    trebleSparkle = 70,
                    loudnessComp = true,
                    youtubeFixer = false,
                    cabinStage = 40,
                    // Deep tight sub-bass, clean dip at boxy 250-500Hz, crisp high-end
                    eqBandsDb = floatArrayOf(6.0f, 6.5f, 3.5f, -2.0f, -1.5f, 1.0f, 2.5f, 4.0f, 5.5f, 6.0f)
                )
                "OEM_KENWOOD" -> DspConfig(
                    enabled = true,
                    presetId = "OEM_KENWOOD",
                    preampGainDb = 4.5f,
                    subBassPunch = 65,
                    vocalClarity = 80,
                    trebleSparkle = 55,
                    loudnessComp = true,
                    youtubeFixer = false,
                    cabinStage = 35,
                    // Warm natural mid-bass & rich forward vocals like OEM Toyota/Honda Kenwood HU
                    eqBandsDb = floatArrayOf(4.5f, 5.5f, 4.0f, 0.0f, 1.0f, 3.0f, 4.0f, 3.5f, 3.5f, 4.0f)
                )
                "YOUTUBE_FIXER" -> DspConfig(
                    enabled = true,
                    presetId = "YOUTUBE_FIXER",
                    preampGainDb = 7.0f,
                    subBassPunch = 85,
                    vocalClarity = 75,
                    trebleSparkle = 80,
                    loudnessComp = true,
                    youtubeFixer = true,
                    cabinStage = 50,
                    // Restores heavily compressed YouTube video audio: huge sub-bass & air boost, cuts muddy 250-500Hz
                    eqBandsDb = floatArrayOf(7.5f, 7.0f, 3.5f, -3.5f, -2.5f, 2.0f, 4.0f, 5.5f, 6.5f, 7.0f)
                )
                "YTMUSIC_JBL" -> DspConfig(
                    enabled = true,
                    presetId = "YTMUSIC_JBL",
                    preampGainDb = 5.5f,
                    subBassPunch = 90,
                    vocalClarity = 65,
                    trebleSparkle = 75,
                    loudnessComp = true,
                    youtubeFixer = false,
                    cabinStage = 45,
                    // Punchy modern Harman/JBL curve for YouTube Music & Spotify
                    eqBandsDb = floatArrayOf(7.0f, 7.5f, 4.5f, -1.5f, -1.0f, 1.5f, 3.0f, 4.5f, 5.5f, 6.5f)
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
        // Always attach to Session 0 (Global Output Mix on most Chinese Android Head Units: FYT, TS10, TS18, T3L, MTK)
        attachSession(0)
    }

    @Synchronized
    fun attachSession(sessionId: Int) {
        if (activeSessions.containsKey(sessionId)) {
            applyToSession(sessionId, activeSessions[sessionId]!!)
            return
        }

        val effects = SessionEffects()

        // 1. Multi-Stage DynamicsProcessing (API 28+): Pre-EQ + Multi-Band Compressor + Post-EQ + Limiter
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                val cfgBuilder = DynamicsProcessing.Config.Builder(
                    DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                    2, // stereo channels
                    true, 10, // Pre-EQ: 10 bands
                    true, 4,  // Multi-Band Compressor: 4 crossover bands (Sub, Low-Mid, Vocal, High)
                    true, 10, // Post-EQ: 10 bands
                    true      // Brickwall Soft-Knee Limiter (prevents OEM door speaker distortion)
                )
                val dp = DynamicsProcessing(100, sessionId, cfgBuilder.build())
                dp.enabled = currentConfig.enabled
                effects.dynamics = dp
                Log.i(TAG, "Attached DynamicsProcessing to session $sessionId")
            } catch (e: Throwable) {
                Log.w(TAG, "DynamicsProcessing unavailable on session $sessionId: ${e.message}")
            }
        }

        // 2. Standard Hardware Equalizer (works across all Android versions & MCU audio chips)
        try {
            val eq = Equalizer(100, sessionId)
            eq.enabled = currentConfig.enabled
            effects.equalizer = eq
        } catch (e: Throwable) {
            Log.w(TAG, "Equalizer unavailable on session $sessionId: ${e.message}")
        }

        // 3. Hardware BassBoost (Sub-harmonic enhancer)
        try {
            val bb = BassBoost(100, sessionId)
            bb.enabled = currentConfig.enabled
            effects.bassBoost = bb
        } catch (e: Throwable) {
            Log.w(TAG, "BassBoost unavailable on session $sessionId: ${e.message}")
        }

        // 4. LoudnessEnhancer (Pre-amp voltage boost for weak Android HU DACs)
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
        if (sessionId == 0) return // Never detach global mix
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
     * Computes the final composite 10-band response curve (in dB) combining:
     * - Base 10-band EQ sliders
     * - Sub-Bass Punch psychoacoustic shelf (31Hz - 125Hz)
     * - Vocal Clarity enhancer (1kHz - 4kHz)
     * - Treble Sparkle exciter (8kHz - 16kHz)
     * - OEM Loudness Compensation (Fletcher-Munson contour)
     * - YouTube Video Audio Fixer (De-boxy cut at 250-500Hz + presence lift)
     */
    fun computeEffectiveCurveDb(cfg: DspConfig = currentConfig): FloatArray {
        val out = FloatArray(10)
        val bassFactor = cfg.subBassPunch / 100f
        val vocalFactor = cfg.vocalClarity / 100f
        val trebleFactor = cfg.trebleSparkle / 100f

        for (i in 0 until 10) {
            var db = cfg.eqBandsDb.getOrElse(i) { 0f }

            // Sub-bass restorer (31Hz, 62Hz, 125Hz)
            when (i) {
                0 -> db += bassFactor * 4.5f
                1 -> db += bassFactor * 5.0f
                2 -> db += bassFactor * 2.5f
            }

            // Vocal presence (1kHz, 2kHz, 4kHz)
            when (i) {
                5 -> db += vocalFactor * 2.0f
                6 -> db += vocalFactor * 3.2f
                7 -> db += vocalFactor * 2.5f
            }

            // Treble Exciter (8kHz, 16kHz)
            when (i) {
                8 -> db += trebleFactor * 3.5f
                9 -> db += trebleFactor * 4.5f
            }

            // OEM Loudness Contour (adds warm low-end weight and silky top-end like Pioneer Loudness)
            if (cfg.loudnessComp) {
                when (i) {
                    0, 1 -> db += 2.2f
                    2 -> db += 1.2f
                    8, 9 -> db += 1.8f
                }
            }

            // YouTube Normalizer & Compression Fixer (removes muddy 250-500Hz boxiness & expands highs/lows)
            if (cfg.youtubeFixer) {
                when (i) {
                    0, 1 -> db += 2.0f
                    3 -> db -= 2.5f // 250Hz mud cut
                    4 -> db -= 2.0f // 500Hz boxy cut
                    6, 7 -> db += 2.0f
                    8, 9 -> db += 2.2f
                }
            }

            out[i] = db.coerceIn(-15f, 15f)
        }
        return out
    }

    private fun applyToSession(sessionId: Int, effects: SessionEffects) {
        val cfg = currentConfig
        val finalCurve = computeEffectiveCurveDb(cfg)

        // 1. Apply DynamicsProcessing (API 28+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val dp = effects.dynamics
            if (dp != null) {
                try {
                    dp.enabled = cfg.enabled
                    if (cfg.enabled) {
                        // Input Pre-Amp Gain
                        dp.setInputGainAllChannelsTo(cfg.preampGainDb)

                        // Split gain between Pre-EQ (40%) and Post-EQ (60%) for smooth analog-style saturation
                        for (i in 0 until 10) {
                            val freq = DspConfig.BAND_FREQUENCIES[i]
                            val preBand = DynamicsProcessing.EqBand(true, freq, finalCurve[i] * 0.4f)
                            val postBand = DynamicsProcessing.EqBand(true, freq, finalCurve[i] * 0.6f)
                            dp.setPreEqBandAllChannelsTo(i, preBand)
                            dp.setPostEqBandAllChannelsTo(i, postBand)
                        }

                        // 4-Band Multi-Band Compressor (MBC) to make weak HU sound dense, tight, and punchy
                        val mbcCutoffs = floatArrayOf(120f, 700f, 4000f, 20000f)
                        val mbcPostGains = floatArrayOf(
                            (cfg.subBassPunch / 100f) * 3.5f, // Band 0: Tight Sub/Mid-Bass punch
                            0.0f,                              // Band 1: Clean Low-Mid
                            (cfg.vocalClarity / 100f) * 2.5f,  // Band 2: Forward OEM Vocals
                            (cfg.trebleSparkle / 100f) * 2.5f  // Band 3: Crisp Highs
                        )
                        for (b in 0 until 4) {
                            val mbcBand = DynamicsProcessing.MbcBand(
                                true,
                                mbcCutoffs[b],
                                8.0f,   // attackTime ms
                                90.0f,  // releaseTime ms
                                if (cfg.youtubeFixer) 2.8f else 2.2f, // compression ratio
                                -24.0f, // threshold dB
                                6.0f,   // soft knee width dB
                                -55.0f, // noise gate threshold
                                1.0f,   // expander ratio
                                0.0f,   // preGain dB
                                mbcPostGains[b]
                            )
                            dp.setMbcBandAllChannelsTo(b, mbcBand)
                        }

                        // Soft Brickwall Limiter at -0.8 dB so door speakers NEVER clip or distort
                        val limiter = DynamicsProcessing.Limiter(
                            true,
                            true,
                            0,
                            2.0f,   // fast attack
                            60.0f,  // smooth release
                            4.0f,   // ratio
                            -1.2f,  // threshold dB
                            0.0f    // postGain dB
                        )
                        dp.setLimiterAllChannelsTo(limiter)
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Error configuring DynamicsProcessing on session $sessionId: ${e.message}")
                }
            }
        }

        // 2. Apply Hardware Equalizer (maps our 10-band curve onto the HU's native hardware EQ bands)
        val eq = effects.equalizer
        if (eq != null) {
            try {
                eq.enabled = cfg.enabled
                if (cfg.enabled) {
                    val numBands = eq.numberOfBands.toInt()
                    val range = eq.bandLevelRange
                    val minMb = range[0].toInt()
                    val maxMb = range[1].toInt()

                    for (b in 0 until numBands) {
                        val centerFreqHz = eq.getCenterFreq(b.toShort()) / 1000f
                        val interpolatedDb = interpolateGainForFreq(centerFreqHz, finalCurve)
                        val targetMillibels = (interpolatedDb * 100).toInt().coerceIn(minMb, maxMb)
                        eq.setBandLevel(b.toShort(), targetMillibels.toShort())
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Error configuring Equalizer on session $sessionId: ${e.message}")
            }
        }

        // 3. Apply Hardware BassBoost (0 - 1000 permille)
        val bb = effects.bassBoost
        if (bb != null) {
            try {
                bb.enabled = cfg.enabled
                if (cfg.enabled && bb.strengthSupported) {
                    val strength = (cfg.subBassPunch * 10).coerceIn(0, 1000).toShort()
                    bb.setStrength(strength)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Error configuring BassBoost: ${e.message}")
            }
        }

        // 4. Apply LoudnessEnhancer Pre-Amp Gain (in millibels, 1 dB = 100 mB)
        val le = effects.loudness
        if (le != null) {
            try {
                le.enabled = cfg.enabled
                if (cfg.enabled) {
                    val extraYtBoost = if (cfg.youtubeFixer) 200 else 0
                    val targetGainMb = ((cfg.preampGainDb * 100).toInt() + extraYtBoost).coerceIn(0, 1200)
                    le.setTargetGain(targetGainMb)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Error configuring LoudnessEnhancer: ${e.message}")
            }
        }

        // 5. Apply Cabin Stage Virtualizer (0 - 1000 permille)
        val virt = effects.virtualizer
        if (virt != null) {
            try {
                virt.enabled = cfg.enabled && cfg.cabinStage > 0
                if (cfg.enabled && virt.strengthSupported) {
                    val strength = (cfg.cabinStage * 10).coerceIn(0, 1000).toShort()
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
        if (!prefs.contains("presetId")) {
            return DspConfig.getPreset("OEM_PIONEER")
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
            preampGainDb = prefs.getFloat("preampGainDb", 5.0f),
            subBassPunch = prefs.getInt("subBassPunch", 75),
            vocalClarity = prefs.getInt("vocalClarity", 60),
            trebleSparkle = prefs.getInt("trebleSparkle", 70),
            loudnessComp = prefs.getBoolean("loudnessComp", true),
            youtubeFixer = prefs.getBoolean("youtubeFixer", false),
            cabinStage = prefs.getInt("cabinStage", 40),
            eqBandsDb = bands
        )
    }
}
