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
 * Core Car Audio DSP Engine v1.2.0 (by Zydan • Full Power Vocal & Clarity Edition).
 *
 * Uses 100% FULL POWER hardware & DynamicsProcessing output (just like v1.0.0 that strongly affected the HU),
 * but redirects the power from muddy low-mid bass into crystal-clear Vocal Presence (1kHz-4kHz),
 * Crisp High-End Sparkle (8kHz-16kHz), deep 250Hz-500Hz anti-mud cuts, and tight punchy sub-bass!
 */
data class DspConfig(
    val enabled: Boolean = true,
    val presetId: String = "OEM_PIONEER",
    val preampGainDb: Float = 6.5f,          // -6 dB to +12 dB (Strong audible pre-amp boost)
    val subBassPunch: Int = 40,              // 0 to 100 (Tight punchy bass without drowning vocals)
    val vocalClarity: Int = 90,              // 0 to 100 (Strong forward vocal presence)
    val trebleSparkle: Int = 85,             // 0 to 100 (Crisp high-end detail & air)
    val loudnessComp: Boolean = false,       // Off by default so bass doesn't mask vocals
    val youtubeFixer: Boolean = true,        // Deep de-mud cut at 250-500Hz + high vocal lift
    val cabinStage: Int = 30,                // 0 to 100 stereo stage
    // 10-Band ISO frequencies: 31, 62, 125, 250, 500, 1k, 2k, 4k, 8k, 16k (in dB, -12 to +12)
    val eqBandsDb: FloatArray = floatArrayOf(3.5f, 4.0f, -1.5f, -5.5f, -3.0f, 5.5f, 7.5f, 7.0f, 6.5f, 7.0f)
) {
    companion object {
        const val CONFIG_SCHEMA_VERSION = 3 // Forces upgrade to v1.2 Full-Power Clear Vocal defaults

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
                    preampGainDb = 6.5f,
                    subBassPunch = 40,
                    vocalClarity = 90,
                    trebleSparkle = 85,
                    loudnessComp = false,
                    youtubeFixer = true,
                    cabinStage = 30,
                    // Punchy sub-bass (+3.5/+4.0dB), deep cut at muddy 125-500Hz (-5.5dB), huge vocal & treble clarity (+5.5 to +7.5dB)
                    eqBandsDb = floatArrayOf(3.5f, 4.0f, -1.5f, -5.5f, -3.0f, 5.5f, 7.5f, 7.0f, 6.5f, 7.0f)
                )
                "OEM_KENWOOD" -> DspConfig(
                    enabled = true,
                    presetId = "OEM_KENWOOD",
                    preampGainDb = 7.0f,
                    subBassPunch = 20,
                    vocalClarity = 100,
                    trebleSparkle = 85,
                    loudnessComp = false,
                    youtubeFixer = true,
                    cabinStage = 20,
                    // Maximum upfront vocal clarity, minimal bass boom, ultra-clean mid-bass cut
                    eqBandsDb = floatArrayOf(1.5f, 2.0f, -2.5f, -6.5f, -3.0f, 7.0f, 9.0f, 8.0f, 6.5f, 6.5f)
                )
                "YOUTUBE_FIXER" -> DspConfig(
                    enabled = true,
                    presetId = "YOUTUBE_FIXER",
                    preampGainDb = 8.0f,
                    subBassPunch = 35,
                    vocalClarity = 95,
                    trebleSparkle = 95,
                    loudnessComp = false,
                    youtubeFixer = true,
                    cabinStage = 35,
                    // Aggressive anti-mendem curve for YouTube regular videos
                    eqBandsDb = floatArrayOf(3.0f, 3.5f, -2.5f, -7.0f, -4.5f, 6.5f, 8.5f, 8.5f, 8.0f, 8.5f)
                )
                "YTMUSIC_JBL" -> DspConfig(
                    enabled = true,
                    presetId = "YTMUSIC_JBL",
                    preampGainDb = 6.5f,
                    subBassPunch = 55,
                    vocalClarity = 85,
                    trebleSparkle = 90,
                    loudnessComp = false,
                    youtubeFixer = true,
                    cabinStage = 35,
                    // Dynamic V-Shape with clean vocal presence and deep 250Hz mud cut
                    eqBandsDb = floatArrayOf(5.0f, 5.5f, 0.0f, -5.0f, -2.5f, 5.0f, 7.0f, 7.0f, 7.5f, 8.0f)
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

    /**
     * Releases all existing sessions and re-attaches fresh hardware AudioEffects with high priority (200)
     * so updating the APK or switching apps immediately takes control of the Head Unit audio chip.
     */
    @Synchronized
    fun forceRehookAllSessions() {
        val currentIds = activeSessions.keys.toList().ifEmpty { listOf(0) }
        releaseAll()
        attachSession(0)
        for (sid in currentIds) {
            if (sid != 0) attachSession(sid)
        }
    }

    @Synchronized
    fun attachSession(sessionId: Int) {
        if (activeSessions.containsKey(sessionId)) {
            applyToSession(sessionId, activeSessions[sessionId]!!)
            return
        }

        val effects = SessionEffects()

        // 1. Multi-Stage DynamicsProcessing (API 28+) with Priority 200
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
                val dp = DynamicsProcessing(200, sessionId, cfgBuilder.build())
                dp.enabled = currentConfig.enabled
                effects.dynamics = dp
                Log.i(TAG, "Attached DynamicsProcessing to session $sessionId")
            } catch (e: Throwable) {
                Log.w(TAG, "DynamicsProcessing unavailable on session $sessionId: ${e.message}")
            }
        }

        // 2. Standard Hardware Equalizer (100% Full Power Priority 200)
        try {
            val eq = Equalizer(200, sessionId)
            eq.enabled = currentConfig.enabled
            effects.equalizer = eq
        } catch (e: Throwable) {
            Log.w(TAG, "Equalizer unavailable on session $sessionId: ${e.message}")
        }

        // 3. Hardware BassBoost
        try {
            val bb = BassBoost(200, sessionId)
            bb.enabled = currentConfig.enabled
            effects.bassBoost = bb
        } catch (e: Throwable) {
            Log.w(TAG, "BassBoost unavailable on session $sessionId: ${e.message}")
        }

        // 4. LoudnessEnhancer (Full Power Pre-Amp Voltage Booster)
        try {
            val le = LoudnessEnhancer(sessionId)
            le.enabled = currentConfig.enabled
            effects.loudness = le
        } catch (e: Throwable) {
            Log.w(TAG, "LoudnessEnhancer unavailable on session $sessionId: ${e.message}")
        }

        // 5. Cabin Stereo Stage Virtualizer
        try {
            val virt = Virtualizer(200, sessionId)
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
     * Computes the final composite 10-band response curve (in dB) at 100% FULL POWER,
     * focusing energy on Vocal Clarity (1kHz-4kHz) and High Sparkle (8kHz-16kHz) while
     * scooping out the muddy 125Hz-500Hz frequencies that caused bass to drown out vocals.
     */
    fun computeEffectiveCurveDb(cfg: DspConfig = currentConfig): FloatArray {
        val out = FloatArray(10)
        val bassFactor = cfg.subBassPunch / 100f
        val vocalFactor = cfg.vocalClarity / 100f
        val trebleFactor = cfg.trebleSparkle / 100f

        for (i in 0 until 10) {
            var db = cfg.eqBandsDb.getOrElse(i) { 0f }

            // Sub-bass punch (31Hz & 62Hz only, while automatically scooping 250Hz mud!)
            when (i) {
                0 -> db += bassFactor * 3.5f
                1 -> db += bassFactor * 3.5f
                2 -> db -= bassFactor * 1.0f // prevents 125Hz boomy overhang
                3 -> db -= bassFactor * 2.5f // prevents bass from masking vocals
            }

            // Strong Vocal Presence Enhancer (1kHz, 2kHz, 4kHz)
            when (i) {
                5 -> db += vocalFactor * 3.5f
                6 -> db += vocalFactor * 5.0f
                7 -> db += vocalFactor * 4.5f
            }

            // Strong Treble Exciter (8kHz, 16kHz)
            when (i) {
                8 -> db += trebleFactor * 4.5f
                9 -> db += trebleFactor * 5.5f
            }

            // Optional OEM Loudness Contour
            if (cfg.loudnessComp) {
                when (i) {
                    0, 1 -> db += 2.0f
                    8, 9 -> db += 2.0f
                }
            }

            // YouTube De-Mud & Vocal Restorer (deep cut at 125-500Hz mud + strong vocal & air lift)
            if (cfg.youtubeFixer) {
                when (i) {
                    2 -> db -= 2.0f // 125Hz boom cut
                    3 -> db -= 4.0f // 250Hz mud cut
                    4 -> db -= 2.5f // 500Hz boxy kardus cut
                    5 -> db += 2.5f // 1kHz vocal lift
                    6 -> db += 3.5f // 2kHz vocal clarity lift
                    7 -> db += 3.0f // 4kHz presence lift
                    8, 9 -> db += 2.5f
                }
            }

            out[i] = db.coerceIn(-15f, 15f)
        }
        return out
    }

    private fun applyToSession(sessionId: Int, effects: SessionEffects) {
        val cfg = currentConfig
        val finalCurve = computeEffectiveCurveDb(cfg)

        // 1. Apply DynamicsProcessing at 100% FULL POWER (API 28+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val dp = effects.dynamics
            if (dp != null) {
                try {
                    dp.enabled = cfg.enabled
                    if (cfg.enabled) {
                        dp.setInputGainAllChannelsTo(cfg.preampGainDb)

                        // Full 100% curve split across Pre-EQ (40%) and Post-EQ (60%)
                        for (i in 0 until 10) {
                            val freq = DspConfig.BAND_FREQUENCIES[i]
                            val preBand = DynamicsProcessing.EqBand(true, freq, finalCurve[i] * 0.4f)
                            val postBand = DynamicsProcessing.EqBand(true, freq, finalCurve[i] * 0.6f)
                            dp.setPreEqBandAllChannelsTo(i, preBand)
                            dp.setPostEqBandAllChannelsTo(i, postBand)
                        }

                        // 4-Band Multi-Band Compressor (MBC): suppresses muddy low-mids & pushes vocals forward!
                        val mbcCutoffs = floatArrayOf(105f, 550f, 4800f, 20000f)
                        val mbcPostGains = floatArrayOf(
                            (cfg.subBassPunch / 100f) * 2.2f,  // Band 0 (<105Hz): Tight Sub-Bass
                            -2.5f,                             // Band 1 (105-550Hz): Suppress muddy bass resonance!
                            (cfg.vocalClarity / 100f) * 5.0f,  // Band 2 (550Hz-4.8kHz): HUGE Vocal Clarity Boost!
                            (cfg.trebleSparkle / 100f) * 4.0f  // Band 3 (>4.8kHz): Crisp Treble Sparkle!
                        )
                        for (b in 0 until 4) {
                            val mbcBand = DynamicsProcessing.MbcBand(
                                true,
                                mbcCutoffs[b],
                                8.0f,   // attackTime ms
                                85.0f,  // releaseTime ms
                                2.2f,   // compression ratio
                                -24.0f, // threshold dB
                                6.0f,   // soft knee width dB
                                -58.0f, // noise gate threshold
                                1.0f,   // expander ratio
                                0.0f,   // preGain dB
                                mbcPostGains[b]
                            )
                            dp.setMbcBandAllChannelsTo(b, mbcBand)
                        }

                        // Brickwall Limiter with high headroom so bass never ducks vocals
                        val limiter = DynamicsProcessing.Limiter(
                            true,
                            true,
                            0,
                            2.0f,
                            55.0f,
                            4.0f,
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

        // 2. Apply Hardware Equalizer at 100% FULL POWER (no 0.5x divider!)
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

        // 3. Apply Hardware BassBoost (Balanced 6x multiplier, max 600 permille so it punches hard without drowning vocals)
        val bb = effects.bassBoost
        if (bb != null) {
            try {
                bb.enabled = cfg.enabled && cfg.subBassPunch > 0
                if (cfg.enabled && bb.strengthSupported) {
                    val strength = (cfg.subBassPunch * 6).coerceIn(0, 600).toShort()
                    bb.setStrength(strength)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Error configuring BassBoost: ${e.message}")
            }
        }

        // 4. Apply LoudnessEnhancer Pre-Amp Gain at 100% FULL POWER (just like v1.0.0!)
        val le = effects.loudness
        if (le != null) {
            try {
                le.enabled = cfg.enabled
                if (cfg.enabled) {
                    val extraVocalBoost = if (cfg.youtubeFixer) 150 else 0
                    val targetGainMb = ((cfg.preampGainDb * 100).toInt() + extraVocalBoost).coerceIn(0, 1200)
                    le.setTargetGain(targetGainMb)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Error configuring LoudnessEnhancer: ${e.message}")
            }
        }

        // 5. Apply Cabin Stage Virtualizer
        val virt = effects.virtualizer
        if (virt != null) {
            try {
                virt.enabled = cfg.enabled && cfg.cabinStage > 0
                if (cfg.enabled && virt.strengthSupported) {
                    val strength = (cfg.cabinStage * 7).coerceIn(0, 700).toShort()
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
            preampGainDb = prefs.getFloat("preampGainDb", 6.5f),
            subBassPunch = prefs.getInt("subBassPunch", 40),
            vocalClarity = prefs.getInt("vocalClarity", 90),
            trebleSparkle = prefs.getInt("trebleSparkle", 85),
            loudnessComp = prefs.getBoolean("loudnessComp", false),
            youtubeFixer = prefs.getBoolean("youtubeFixer", true),
            cabinStage = prefs.getInt("cabinStage", 30),
            eqBandsDb = bands
        )
    }
}
