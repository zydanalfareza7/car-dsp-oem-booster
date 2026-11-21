package com.cardsp.oembooster

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin

class MainActivity : AppCompatActivity() {

    private var dspService: DspEngineService? = null
    private var isBound = false

    // UI Elements
    private lateinit var tvStatusBadge: TextView
    private lateinit var switchMasterDsp: Switch
    private lateinit var eqCurveView: EqCurveView

    private val presetButtons = mutableMapOf<String, Button>()

    private lateinit var sbPreamp: SeekBar
    private lateinit var tvPreampVal: TextView

    private lateinit var sbSubBass: SeekBar
    private lateinit var tvSubBassVal: TextView

    private lateinit var sbVocal: SeekBar
    private lateinit var tvVocalVal: TextView

    private lateinit var sbTreble: SeekBar
    private lateinit var tvTrebleVal: TextView

    private lateinit var sbStage: SeekBar
    private lateinit var tvStageVal: TextView

    private lateinit var switchLoudness: Switch
    private lateinit var switchYoutubeFixer: Switch

    private val eqBandSliders = ArrayList<SeekBar>(10)
    private val eqBandValueTexts = ArrayList<TextView>(10)

    private var suppressUiCallbacks = false
    private var isTestPlaying = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as DspEngineService.LocalBinder
            dspService = binder.getService()
            isBound = true
            // Force re-hook hardware AudioEffects whenever app opens after update
            dspService?.dspController?.forceRehookAllSessions()
            syncUiFromConfig()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            dspService = null
            isBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
            }
        }

        setContentView(buildCarDashboardLayout())

        // Start & Bind background Foreground DSP Service
        val serviceIntent = Intent(this, DspEngineService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onDestroy() {
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
        super.onDestroy()
    }

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()

    private fun cardBg(fillHex: String = "#141C2B", strokeHex: String = "#26354D", radiusDp: Int = 12): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(Color.parseColor(fillHex))
            setStroke(dp(1), Color.parseColor(strokeHex))
        }
    }

    private fun buildCarDashboardLayout(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0A0F19"))
            setPadding(dp(14), dp(10), dp(14), dp(10))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // ================= TOP HEADER BAR =================
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = cardBg("#111827", "#1F2937", 10)
            setPadding(dp(14), dp(8), dp(14), dp(8))
        }

        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val tvTitle = TextView(this).apply {
            text = "CAR DSP • OEM SOUND RESTORER"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
        }
        val tvByZydan = TextView(this).apply {
            text = "by Zydan • v1.3 Maknyus"
            setTextColor(Color.parseColor("#F59E0B"))
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            background = cardBg("#1E293B", "#F59E0B", 6)
            setPadding(dp(8), dp(2), dp(8), dp(2))
        }
        titleRow.addView(tvTitle)
        titleRow.addView(tvByZydan, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { leftMargin = dp(10) })

        tvStatusBadge = TextView(this).apply {
            text = "Engine: 100% Full Power Vocal & Clarity Restorer Active"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 12f
        }
        titleCol.addView(titleRow)
        titleCol.addView(tvStatusBadge)
        header.addView(titleCol)

        // Force Re-Hook Audio Chip Button
        val btnRehook = Button(this).apply {
            text = "⚡ Reset / Boost Chip Audio"
            setTextColor(Color.parseColor("#F59E0B"))
            textSize = 12f
            isAllCaps = false
            background = cardBg("#1E293B", "#F59E0B", 8)
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener {
                dspService?.dspController?.forceRehookAllSessions()
                syncUiFromConfig()
                Toast.makeText(this@MainActivity, "Chip Audio HU Berhasil Di-Rehook 100% Full Power!", Toast.LENGTH_SHORT).show()
            }
        }
        header.addView(btnRehook, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { rightMargin = dp(8) })

        // Speaker Polarity / Bass Phase Tester Button
        val btnPhaseTest = Button(this).apply {
            text = "🔊 Test Bass & Fasa Speaker"
            setTextColor(Color.WHITE)
            textSize = 12f
            isAllCaps = false
            background = cardBg("#1E293B", "#38BDF8", 8)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            setOnClickListener { runBassPhaseTest() }
        }
        header.addView(btnPhaseTest, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { rightMargin = dp(12) })

        // Master ON/OFF Switch
        switchMasterDsp = Switch(this).apply {
            text = "DSP ON  "
            setTextColor(Color.parseColor("#10B981"))
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            isChecked = true
            setOnCheckedChangeListener { _, isChecked ->
                if (suppressUiCallbacks) return@setOnCheckedChangeListener
                text = if (isChecked) "DSP ON  " else "BYPASS  "
                setTextColor(if (isChecked) Color.parseColor("#10B981") else Color.parseColor("#EF4444"))
                updateDsp { it.copy(enabled = isChecked) }
            }
        }
        header.addView(switchMasterDsp)
        root.addView(header, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) })

        // ================= PRESET BAR (ONE-TAP OEM PROFILES) =================
        val presetScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        val presetRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val presets = listOf(
            "OEM_PIONEER" to "🎛️ OEM Pioneer (Warm & Tight Bass)",
            "YOUTUBE_FIXER" to "🎬 YouTube Biasa Fixer (Anti-Mendem)",
            "YTMUSIC_JBL" to "🎵 YT Music / JBL Punch",
            "OEM_KENWOOD" to "🎤 OEM Kenwood (Vocal Tebal)",
            "FLAT_BYPASS" to "➖ Flat Reset"
        )

        for ((id, label) in presets) {
            val btn = Button(this).apply {
                text = label
                isAllCaps = false
                textSize = 13f
                setTextColor(Color.WHITE)
                setPadding(dp(14), dp(8), dp(14), dp(8))
                setOnClickListener {
                    val presetCfg = DspConfig.getPreset(id).copy(enabled = switchMasterDsp.isChecked)
                    applyNewConfigAndSyncUi(presetCfg)
                }
            }
            presetButtons[id] = btn
            presetRow.addView(btn, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(42)
            ).apply { rightMargin = dp(8) })
        }
        presetScroll.addView(presetRow)
        root.addView(presetScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(10) })

        // ================= MAIN 2-COLUMN COCKPIT =================
        val mainScroll = ScrollView(this).apply {
            isFillViewport = true
        }
        val cockpitRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 10f
        }

        // -------- LEFT COLUMN (Pre-Amp, OEM Restorer Sliders & Switches) --------
        val leftCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg("#131B2E", "#23324B", 12)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 4.6f).apply {
                rightMargin = dp(10)
            }
        }

        val tvLeftHeader = TextView(this).apply {
            text = "1. OEM PRE-AMP & SOUND RESTORER"
            setTextColor(Color.parseColor("#F59E0B"))
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
        }
        leftCol.addView(tvLeftHeader, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) })

        // Slider 1: Pre-Amp Gain (-6dB to +12dB -> progress 0..180)
        val preampPair = createLabeledSeekBar(
            leftCol,
            "Pre-Amp Voltage Gain (Menguatkan IC HU Lemah)",
            "+5.0 dB",
            180,
            110
        ) { progress ->
            val gainDb = (progress - 60) / 10f
            tvPreampVal.text = String.format("%+.1f dB", gainDb)
            if (!suppressUiCallbacks) updateDsp { it.copy(preampGainDb = gainDb, presetId = "CUSTOM") }
        }
        sbPreamp = preampPair.first
        tvPreampVal = preampPair.second

        // Slider 2: Sub-Bass Punch (45-80Hz)
        val subBassPair = createLabeledSeekBar(
            leftCol,
            "Sub-Bass Punch (45Hz–80Hz Bass Bulat OEM)",
            "75%",
            100,
            75
        ) { progress ->
            tvSubBassVal.text = "$progress%"
            if (!suppressUiCallbacks) updateDsp { it.copy(subBassPunch = progress, presetId = "CUSTOM") }
        }
        sbSubBass = subBassPair.first
        tvSubBassVal = subBassPair.second

        // Slider 3: Vocal Clarity
        val vocalPair = createLabeledSeekBar(
            leftCol,
            "Vocal Presence (Suara Penyanyi Jelas & Dekat)",
            "60%",
            100,
            60
        ) { progress ->
            tvVocalVal.text = "$progress%"
            if (!suppressUiCallbacks) updateDsp { it.copy(vocalClarity = progress, presetId = "CUSTOM") }
        }
        sbVocal = vocalPair.first
        tvVocalVal = vocalPair.second

        // Slider 4: Treble Sparkle
        val treblePair = createLabeledSeekBar(
            leftCol,
            "Treble Exciter (10kHz–16kHz Anti-Mendem)",
            "70%",
            100,
            70
        ) { progress ->
            tvTrebleVal.text = "$progress%"
            if (!suppressUiCallbacks) updateDsp { it.copy(trebleSparkle = progress, presetId = "CUSTOM") }
        }
        sbTreble = treblePair.first
        tvTrebleVal = treblePair.second

        // Slider 5: Cabin Stage Width
        val stagePair = createLabeledSeekBar(
            leftCol,
            "3D Cabin Soundstage (Lebar Panggung Dashboard)",
            "40%",
            100,
            40
        ) { progress ->
            tvStageVal.text = "$progress%"
            if (!suppressUiCallbacks) updateDsp { it.copy(cabinStage = progress, presetId = "CUSTOM") }
        }
        sbStage = stagePair.first
        tvStageVal = stagePair.second

        // Toggles: OEM Loudness Contour & YouTube Compression Fixer
        switchLoudness = Switch(this).apply {
            text = "OEM Loudness Compensation (Bass Tetap Bulat di Volume Kecil)"
            setTextColor(Color.parseColor("#E2E8F0"))
            textSize = 12f
            isChecked = true
            setPadding(0, dp(6), 0, dp(6))
            setOnCheckedChangeListener { _, checked ->
                if (!suppressUiCallbacks) updateDsp { it.copy(loudnessComp = checked, presetId = "CUSTOM") }
            }
        }
        leftCol.addView(switchLoudness)

        switchYoutubeFixer = Switch(this).apply {
            text = "YouTube Video De-Mud & Restorer (Bersihkan 250-400Hz Kardus)"
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 12f
            isChecked = false
            setPadding(0, dp(4), 0, dp(4))
            setOnCheckedChangeListener { _, checked ->
                if (!suppressUiCallbacks) updateDsp { it.copy(youtubeFixer = checked, presetId = "CUSTOM") }
            }
        }
        leftCol.addView(switchYoutubeFixer)

        cockpitRow.addView(leftCol)

        // -------- RIGHT COLUMN (Live Curve Visualizer + 10-Band Master EQ) --------
        val rightCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg("#131B2E", "#23324B", 12)
            setPadding(dp(14), dp(12), dp(14), dp(12))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 5.4f)
        }

        val tvRightHeader = TextView(this).apply {
            text = "2. KURVA DSP AKUSTIK & 10-BAND GRAPHIC EQ"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
        }
        rightCol.addView(tvRightHeader, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(6) })

        eqCurveView = EqCurveView(this).apply {
            background = cardBg("#0B111E", "#1E293B", 8)
        }
        rightCol.addView(eqCurveView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(150)
        ).apply { bottomMargin = dp(10) })

        // 10-Band Fine Tuning Sliders
        val tvEqSub = TextView(this).apply {
            text = "Fine-Tune 10-Band Equalizer (-12 dB s/d +12 dB):"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 12f
        }
        rightCol.addView(tvEqSub, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(6) })

        for (i in 0 until 10) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val tvFreq = TextView(this).apply {
                text = DspConfig.BAND_LABELS[i]
                setTextColor(Color.parseColor("#CBD5E1"))
                textSize = 12f
                width = dp(52)
            }
            val tvGain = TextView(this).apply {
                text = "+0.0 dB"
                setTextColor(Color.parseColor("#00E5FF"))
                textSize = 12f
                gravity = Gravity.END
                width = dp(58)
            }
            val sb = SeekBar(this).apply {
                max = 240 // -12.0dB to +12.0dB in 0.1dB steps
                progress = 120
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        val db = (progress - 120) / 10f
                        tvGain.text = String.format("%+.1f dB", db)
                        if (fromUser && !suppressUiCallbacks) {
                            updateDsp { old ->
                                val newBands = old.eqBandsDb.copyOf()
                                newBands[i] = db
                                old.copy(eqBandsDb = newBands, presetId = "CUSTOM")
                            }
                        }
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            }

            eqBandSliders.add(sb)
            eqBandValueTexts.add(tvGain)

            row.addView(tvFreq)
            row.addView(sb)
            row.addView(tvGain)
            rightCol.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(2) })
        }

        cockpitRow.addView(rightCol)
        mainScroll.addView(cockpitRow)
        root.addView(mainScroll, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        return root
    }

    private fun createLabeledSeekBar(
        parent: LinearLayout,
        label: String,
        initialValueText: String,
        maxProgress: Int,
        initialProgress: Int,
        onChanged: (Int) -> Unit
    ): Pair<SeekBar, TextView> {
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val tvLabel = TextView(this).apply {
            text = label
            setTextColor(Color.parseColor("#E2E8F0"))
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val tvVal = TextView(this).apply {
            text = initialValueText
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
        }
        headerRow.addView(tvLabel)
        headerRow.addView(tvVal)
        parent.addView(headerRow)

        val sb = SeekBar(this).apply {
            max = maxProgress
            progress = initialProgress
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    onChanged(progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        parent.addView(sb, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(6) })

        return sb to tvVal
    }

    private fun updateDsp(modifier: (DspConfig) -> DspConfig) {
        val svc = dspService ?: return
        val updated = modifier(svc.dspController.currentConfig)
        svc.dspController.updateConfig(updated)
        svc.updateNotification()
        refreshCurveAndPresetHighlight(updated)
    }

    private fun applyNewConfigAndSyncUi(newCfg: DspConfig) {
        val svc = dspService ?: return
        svc.dspController.updateConfig(newCfg)
        svc.updateNotification()
        syncUiFromConfig()
    }

    private fun syncUiFromConfig() {
        val svc = dspService ?: return
        val cfg = svc.dspController.currentConfig
        suppressUiCallbacks = true

        switchMasterDsp.isChecked = cfg.enabled
        switchMasterDsp.text = if (cfg.enabled) "DSP ON  " else "BYPASS  "
        switchMasterDsp.setTextColor(
            if (cfg.enabled) Color.parseColor("#10B981") else Color.parseColor("#EF4444")
        )

        sbPreamp.progress = ((cfg.preampGainDb * 10).toInt() + 60).coerceIn(0, 180)
        tvPreampVal.text = String.format("%+.1f dB", cfg.preampGainDb)

        sbSubBass.progress = cfg.subBassPunch
        tvSubBassVal.text = "${cfg.subBassPunch}%"

        sbVocal.progress = cfg.vocalClarity
        tvVocalVal.text = "${cfg.vocalClarity}%"

        sbTreble.progress = cfg.trebleSparkle
        tvTrebleVal.text = "${cfg.trebleSparkle}%"

        sbStage.progress = cfg.cabinStage
        tvStageVal.text = "${cfg.cabinStage}%"

        switchLoudness.isChecked = cfg.loudnessComp
        switchYoutubeFixer.isChecked = cfg.youtubeFixer

        for (i in 0 until 10) {
            val db = cfg.eqBandsDb.getOrElse(i) { 0f }
            eqBandSliders[i].progress = ((db * 10).toInt() + 120).coerceIn(0, 240)
            eqBandValueTexts[i].text = String.format("%+.1f dB", db)
        }

        suppressUiCallbacks = false
        refreshCurveAndPresetHighlight(cfg)
    }

    private fun refreshCurveAndPresetHighlight(cfg: DspConfig) {
        val svc = dspService ?: return
        val compositeCurve = svc.dspController.computeEffectiveCurveDb(cfg)
        eqCurveView.updateCurve(compositeCurve, cfg.preampGainDb, cfg.enabled)

        val engineMode = if (svc.dspController.isDynamicsProcessingActive()) {
            "Multi-Stage DynamicsProcessing + Limiter"
        } else {
            "Hardware Global Equalizer + BassBoost"
        }
        tvStatusBadge.text = "Engine: $engineMode • Sessions: ${svc.dspController.getActiveSessionCount()} • ${svc.lastActiveAppPackage}"

        presetButtons.forEach { (id, btn) ->
            val selected = (id == cfg.presetId)
            btn.background = if (selected) {
                cardBg("#0284C7", "#38BDF8", 8)
            } else {
                cardBg("#1E293B", "#334155", 8)
            }
        }
    }

    /**
     * Plays a 65Hz Warm Sub-Bass test tone:
     * 1.5s Left Speaker only -> 1.5s Right Speaker only -> 2.0s Both Speakers (Center).
     * If Center sounds WEAKER/THINNER than Left or Right alone, one door speaker wire (+/-) is reversed!
     */
    private fun runBassPhaseTest() {
        if (isTestPlaying) return
        isTestPlaying = true
        Toast.makeText(
            this,
            "Tes Fasa Bass 65Hz: KIRI (1.5d) -> KANAN (1.5d) -> TENGAH (2d). Jika saat TENGAH bass hilang, kabel +/- speaker pintu terbalik!",
            Toast.LENGTH_LONG
        ).show()

        thread {
            try {
                val sampleRate = 44100
                val totalSeconds = 5
                val totalSamples = sampleRate * totalSeconds
                val buffer = ShortArray(totalSamples * 2) // Stereo interleaved
                val freq = 65.0 // 65Hz OEM mid-sub bass punch frequency

                for (i in 0 until totalSamples) {
                    val t = i.toDouble() / sampleRate
                    val wave = (sin(2.0 * PI * freq * t) * 26000).toInt().toShort()
                    when {
                        t < 1.5 -> { // Left Only
                            buffer[i * 2] = wave
                            buffer[i * 2 + 1] = 0
                        }
                        t < 3.0 -> { // Right Only
                            buffer[i * 2] = 0
                            buffer[i * 2 + 1] = wave
                        }
                        else -> { // Center (Both Left + Right in-phase)
                            buffer[i * 2] = wave
                            buffer[i * 2 + 1] = wave
                        }
                    }
                }

                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .build()
                    )
                    .setBufferSizeInBytes(buffer.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(buffer, 0, buffer.size)
                dspService?.dspController?.attachSession(audioTrack.audioSessionId)
                audioTrack.play()
                Thread.sleep(5200)
                audioTrack.stop()
                audioTrack.release()
            } catch (_: Throwable) {
            } finally {
                isTestPlaying = false
            }
        }
    }
}
