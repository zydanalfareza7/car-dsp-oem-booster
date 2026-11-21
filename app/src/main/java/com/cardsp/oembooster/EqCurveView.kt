package com.cardsp.oembooster

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View

/**
 * Custom Car Dashboard Frequency Response Curve Visualizer.
 * Renders the 10-band composite DSP response curve (Pre-Amp + Sub-Bass + Vocal + Treble + EQ)
 * in real time so the driver can see how the sound is shaped.
 */
class EqCurveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var curveDb = FloatArray(10) { 0f }
    private var preampDb = 0f
    private var isDspEnabled = true

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#223042")
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private val zeroLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#475569")
        strokeWidth = 2.5f
        style = Paint.Style.STROKE
    }

    private val curvePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF9100")
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8")
        textSize = 22f
    }

    fun updateCurve(bandsDb: FloatArray, preampGainDb: Float, enabled: Boolean) {
        if (bandsDb.size == 10) {
            curveDb = bandsDb.copyOf()
        }
        preampDb = preampGainDb
        isDspEnabled = enabled
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val padLeft = 48f
        val padRight = 32f
        val padTop = 28f
        val padBottom = 38f
        val plotW = w - padLeft - padRight
        val plotH = h - padTop - padBottom
        val midY = padTop + plotH / 2f

        // Horizontal grid lines (+12dB, +6dB, 0dB, -6dB, -12dB)
        val dbLevels = listOf(12f, 6f, 0f, -6f, -12f)
        for (db in dbLevels) {
            val y = midY - (db / 15f) * (plotH / 2f)
            canvas.drawLine(padLeft, y, w - padRight, y, if (db == 0f) zeroLinePaint else gridPaint)
            canvas.drawText(
                if (db > 0) "+${db.toInt()}" else "${db.toInt()}",
                6f,
                y + 7f,
                textPaint
            )
        }

        // Compute 10 band points
        val pointsX = FloatArray(10)
        val pointsY = FloatArray(10)
        val stepX = plotW / 9f

        for (i in 0 until 10) {
            pointsX[i] = padLeft + i * stepX
            val effectiveDb = if (isDspEnabled) curveDb[i].coerceIn(-15f, 15f) else 0f
            pointsY[i] = midY - (effectiveDb / 15f) * (plotH / 2f)

            // Vertical grid line & frequency label
            canvas.drawLine(pointsX[i], padTop, pointsX[i], h - padBottom, gridPaint)
            val lbl = DspConfig.BAND_LABELS[i].replace("Hz", "")
            val tw = textPaint.measureText(lbl)
            canvas.drawText(lbl, pointsX[i] - tw / 2f, h - 10f, textPaint)
        }

        // Smooth cubic path through points
        val path = Path()
        val fillPath = Path()
        path.moveTo(pointsX[0], pointsY[0])
        fillPath.moveTo(pointsX[0], midY)
        fillPath.lineTo(pointsX[0], pointsY[0])

        for (i in 0 until 9) {
            val cx1 = (pointsX[i] + pointsX[i + 1]) / 2f
            val cy1 = pointsY[i]
            val cx2 = (pointsX[i] + pointsX[i + 1]) / 2f
            val cy2 = pointsY[i + 1]
            path.cubicTo(cx1, cy1, cx2, cy2, pointsX[i + 1], pointsY[i + 1])
            fillPath.cubicTo(cx1, cy1, cx2, cy2, pointsX[i + 1], pointsY[i + 1])
        }

        fillPath.lineTo(pointsX[9], midY)
        fillPath.close()

        val activeColor = if (isDspEnabled) Color.parseColor("#00E5FF") else Color.parseColor("#64748B")
        curvePaint.color = activeColor

        fillPaint.shader = LinearGradient(
            0f, padTop, 0f, h - padBottom,
            if (isDspEnabled) Color.parseColor("#6600E5FF") else Color.parseColor("#2264748B"),
            Color.parseColor("#0500E5FF"),
            Shader.TileMode.CLAMP
        )

        canvas.drawPath(fillPath, fillPaint)
        canvas.drawPath(path, curvePaint)

        for (i in 0 until 10) {
            canvas.drawCircle(pointsX[i], pointsY[i], 7f, dotPaint)
        }
    }
}
