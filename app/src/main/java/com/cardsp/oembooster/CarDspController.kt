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
 * Core Car Audio DSP Engine v1.3.0 (by Zydan • Golden OEM Maknyus Edition).
 *
 * Combines the deep, warm, full-bodied Sub-Bass & Mid-Bass engine from v1.0.0
 * with balanced, natural OEM Vocal Clarity and silky Treble—zero hollow/kopong cuts,
 * zero vocal drowning. Pure warm & punchy OEM car audio character!
 */
data class DspConfig(
    val enabled: Boolean = true,
    val presetId: String = "OEM_PIONEER",
    val preampGainDb: Float = 5.5f,          // -6 dB to +12 dB
    val subBassPunch: Int = 55,              // 0 to 100 (Warm, round, punchy bass)
    val vocalClarity: Int = 65,              // 0 to 100 (Clear, warm, full-bodied vocals)
    val trebleSparkle: Int = 65,             // 0 to 100 (Crisp detail without harshness)
    val loudnessComp: Boolean = true,        // Adds rich OEM low-volume fullness
    val youtubeFixer: Boolean = false,       // Optional de-mud (gentle, never makes sound hollow)
    val cabinStage: Int = 30,                // 0 to 100 stereo stage
    // 10-Band ISO frequencies: 31, 62, 125, 250, 500, 1k, 2k, 4k, 8k, 16k (in dB, -12 to +12)
    val eqBandsDb: FloatArray = floatArrayOf(4.5f, 5.0f, 3.0f, 0.0f, 0.5f, 2.5f, 3.5f, 4.0f, 4.5f, 5.0f)
) {
    companion object {
        const val CONFIG_SCHEMA_VERSION = 4 // Forces upgrade to v1.3 Golden Maknyus defaults

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
                    preampGainDb = 5.5f,
                    subBassPunch = 55,
                    vocalClarity = 65,
                    trebleSparkle = 65,
                    loudnessComp = true,
                    youtubeFixer = false,
                    cabinStage = 30,
                    // Golden OEM Curve: Deep punchy bass (31-125Hz), warm full body (250-500Hz never hollow!), clear vocals (1k-4k), crisp highs
                    eqBandsDb = floatArrayOf(4.5f, 5.0f, 3.0f, 0.0f, 0.5f, 2.5f, 3.5f, 4.0f, 4.5f, 5.0f)
                )
                "OEM_KENWOOD" -> DspConfig(
                    enabled = true,
                    presetId = "OEM_KENWOOD",
                    preampGainDb = 5.5f,
                    subBassPunch = 48,
                    vocalClarity = 78,
                    trebleSparkle = 65,
                    loudnessComp = true,
                    youtubeFixer = false,
                    cabinStage = 25,
                    // Thick, sweet, intimate vocals with warm mid-bass punch (like OEM Toyota/Honda Kenwood)
                    eqBandsDb = floatArrayOf(3.5f, 4.5f, 3.0f, 0.5f, 1.5f, 3.5f, 4.5f, 4.0f, 4.0f, 4.5f)
                )
                "YOUTUBE_FIXER" -> DspConfig(
                    enabled = true,
                    presetId = "YOUTUBE_FIXER",
                    preampGainDb = 6.5f,
                    subBassPunch = 55,
                    vocalClarity = 75,
                    trebleSparkle = 75,
                    loudnessComp = true,
                    youtubeFixer = true,
                    cabinStage = 35,
                    // Restores YouTube regular videos: keeps punchy bass (31-125Hz), gently cleans 250Hz (-1.0dB), boosts vocal & treble clarity
                    eqBandsDb = floatArrayOf(4.5f, 5.0f, 2.5f, -1.0f, 0.5f, 3.0f, 4.5f, 4.5f, 5.0f, 5.5f)
                )
                "YTMUSIC_JBL" -> DspConfig(
                    enabled = true,
                    presetId = "YTMUSIC_JBL",
                    preampGainDb = 6.0f,
                    subBassPunch = 68,
                    vocalClarity = 68,
                    trebleSparkle = 75,
                    loudnessComp = true,
                    youtubeFixer = false,
                    cabinStage = 35,
                    // Extra punchy JBL / Harman curve for YT Music & Spotify with full-bodied bass & clear vocals
                    eqBandsDb = floatArrayOf(5.5f, 6.0f, 3.5f, 0.0f, 0.5f, 2.5f, 3.5f, 4.5f, 5.0f, 5.5f)
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
                val dp = DynamicsProcessing(200, sessionId, cfgBuilder.build())
                dp.enabled = currentConfig.enabled
                effects.dynamics = dp
                Log.i(TAG, "Attached DynamicsProcessing to session $sessionId")
            } catch (e: Throwable) {
                Log.w(TAG, "DynamicsProcessing unavailable on session $sessionId: ${e.message}")
            }
        }

        // 2. Standard Hardware Equalizer
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
     * Computes the final composite 10-band response curve (in dB).
     * Pure additive & transparent musical shaping:
     * - Sub-Bass lifts 31Hz, 62Hz, and 125Hz (warm punchy body, NEVER cuts mid-bass!)
     * - Vocal Clarity lifts 500Hz (warm vocal chest) + 1kHz, 2kHz, 4kHz (clear articulation)
     * - Treble Sparkle lifts 8kHz, 16kHz (silky air)
     */
    fun computeEffectiveCurveDb(cfg: DspConfig = currentConfig): FloatArray {
        val out = FloatArray(10)
        val bassFactor = cfg.subBassPunch / 100f
        val vocalFactor = cfg.vocalClarity / 100f
        val trebleFactor = cfg.trebleSparkle / 100f

        for (i in 0 until 10) {
            var db = cfg.eqBandsDb.getOrElse(i) { 0f }

            // Warm Sub-Bass & Mid-Bass Punch (31Hz, 62Hz, 125Hz) - just like v1.0 but balanced!
            when (i) {
                0 -> db += bassFactor * 3.2f
                1 -> db += bassFactor * 3.5f
                2 -> db += bassFactor * 2.0f // 125Hz gives the meaty drum/bass body so it's never hollow!
            }

            // Full-bodied Vocal Presence (500Hz chest warmth + 1kHz-4kHz clarity)
            when (i) {
                4 -> db += vocalFactor * 1.2f // 500Hz keeps vocals thick and natural
                5 -> db += vocalFactor * 2.8f
                6 -> db += vocalFactor * 3.5f
                7 -> db += vocalFactor * 3.0f
            }

            // Silky Treble Exciter (8kHz, 16kHz)
            when (i) {
                8 -> db += trebleFactor * 3.2f
                9 -> db += trebleFactor * 3.8f
            }

            // OEM Loudness Contour (warm low-volume fullness)
            if (cfg.loudnessComp) {
                when (i) {
                    0, 1 -> db += 1.5f
                    2 -> db += 0.8f
                    6, 7 -> db += 1.0f // keeps vocals level with bass when Loudness is ON!
                    8, 9 -> db += 1.5f
                }
            }

            // YouTube Video Enhancer (gentle -1.2dB cleanup at 250Hz + extra vocal & bass energy)
            if (cfg.youtubeFixer) {
                when (i) {
                    0, 1 -> db += 1.2f
                    3 -> db -= 1.2f // gentle cleanup at 250Hz without making it hollow
                    5, 6, 7 -> db += 1.8f
                    8, 9 -> db += 1.8f
                }
            }

            out[i] = db.coerceIn(-14f, 14f)
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
                        dp.setInputGainAllChannelsTo(cfg.preampGainDb)

                        for (i in 0 until 10) {
                            val freq = DspConfig.BAND_FREQUENCIES[i]
                            val preBand = DynamicsProcessing.EqBand(true, freq, finalCurve[i] * 0.4f)
                            val postBand = DynamicsProcessing.EqBand(true, freq, finalCurve[i] * 0.6f)
                            dp.setPreEqBandAllChannelsTo(i, preBand)
                            dp.setPostEqBandAllChannelsTo(i, postBand)
                        }

                        // 4-Band Multi-Band Compressor (MBC): ALL 4 BANDS POSITIVE & BALANCED!
                        // Band 0 (Sub-Bass), Band 1 (Warm Mid-Bass & Lower Vocal Body), Band 2 (Clear Vocals), Band 3 (Crisp Highs)
                        val mbcCutoffs = floatArrayOf(120f, 650f, 4500f, 20000f)
                        val mbcPostGains = floatArrayOf(
                            (cfg.subBassPunch / 100f) * 2.8f,  // Band 0 (<120Hz): Deep round Sub-Bass
                            ((cfg.subBassPunch + cfg.vocalClarity) / 200f) * 2.2f, // Band 1 (120-650Hz): Warm full-bodied meat (NEVER NEGATIVE!)
                            (cfg.vocalClarity / 100f) * 3.5f,  // Band 2 (650Hz-4.5kHz): Clear upfront Vocals
                            (cfg.trebleSparkle / 100f) * 3.0f  // Band 3 (>4.5kHz): Crisp Silky Treble
                        )
                        for (b in 0 until 4) {
                            val mbcBand = DynamicsProcessing.MbcBand(
                                true,
                                mbcCutoffs[b],
                                10.0f,  // smooth musical attackTime ms
                                90.0f,  // natural releaseTime ms
                                2.0f,   // warm analog-style 2:1 compression ratio
                                -23.0f, // threshold dB
                                6.0f,   // soft knee width dB
                                -58.0f, // noise gate threshold
                                1.0f,   // expander ratio
                                0.0f,   // preGain dB
                                mbcPostGains[b]
                            )
                            dp.setMbcBandAllChannelsTo(b, mbcBand)
                        }

                        // Soft-Knee Limiter at -0.8 dB
                        val limiter = DynamicsProcessing.Limiter(
                            true,
                            true,
                            0,
                            2.5f,
                            60.0f,
                            3.8f,
                            -0.8f,
                            0.0f
                        )
                        dp.setLimiterAllChannelsTo(limiter)
                    }
                } catch (e: Throwable) {
                    Log.w(TAG, "Error configuring DynamicsProcessing on session $sessionId: ${e.message}")
                }
            }
        }

        // 2. Apply Hardware Equalizer
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

        // 3. Apply Hardware BassBoost (Sweet spot 6.5x multiplier: warm & punchy without swallowing vocals)
        val bb = effects.bassBoost
        if (bb != null) {
            try {
                bb.enabled = cfg.enabled && cfg.subBassPunch > 0
                if (cfg.enabled && bb.strengthSupported) {
                    val strength = (cfg.subBassPunch * 6.5f).toInt().coerceIn(0, 650).toShort()
                    bb.setStrength(strength)
                }
            } catch (e: Throwable) {
                Log.w(TAG, "Error configuring BassBoost: ${e.message}")
            }
        }

        // 4. Apply LoudnessEnhancer Pre-Amp Gain
        val le = effects.loudness
        if (le != null) {
            try {
                le.enabled = cfg.enabled
                if (cfg.enabled) {
                    val extraYtBoost = if (cfg.youtubeFixer) 150 else 0
                    val targetGainMb = ((cfg.preampGainDb * 100).toInt() + extraYtBoost).coerceIn(0, 1100)
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
            preampGainDb = prefs.getFloat("preampGainDb", 5.5f),
            subBassPunch = prefs.getInt("subBassPunch", 55),
            vocalClarity = prefs.getInt("vocalClarity", 65),
            trebleSparkle = prefs.getInt("trebleSparkle", 65),
            loudnessComp = prefs.getBoolean("loudnessComp", true),
            youtubeFixer = prefs.getBoolean("youtubeFixer", false),
            cabinStage = prefs.getInt("cabinStage", 30),
            eqBandsDb = bands
        )
    }
}
