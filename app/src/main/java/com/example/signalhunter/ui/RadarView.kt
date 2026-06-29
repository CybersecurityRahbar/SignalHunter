package com.example.signalhunter.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

class RadarView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var threatAngle = 0f
    private var threatDistance = 0f
    private var isThreatDetected = false

    fun updateThreat(angleRad: Float, strength: Float) {
        threatAngle = angleRad
        threatDistance = (strength / 90f).coerceIn(0f, 1f)
        isThreatDetected = strength > 3f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val centerX = width / 2f
        val centerY = height / 2f
        val maxRadius = (width.coerceAtMost(height) / 2f) * 0.9f

        // خلفية سوداء
        paint.color = Color.parseColor("#1A1A1A")
        paint.style = Paint.Style.FILL
        canvas.drawCircle(centerX, centerY, maxRadius, paint)

        // حلقات المسافة (0, 5, 10 متر)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.parseColor("#33FFFFFF")
        for (i in 1..3) {
            val radius = (maxRadius / 3) * i
            canvas.drawCircle(centerX, centerY, radius, paint)
            paint.style = Paint.Style.FILL
            paint.textSize = 18f
            paint.color = Color.parseColor("#88FFFFFF")
            canvas.drawText("${i * 5}m", centerX + radius + 10, centerY + 10, paint)
            paint.style = Paint.Style.STROKE
        }

        // خطوط الزوايا
        paint.color = Color.parseColor("#44FFFFFF")
        paint.strokeWidth = 1f
        for (i in 0..11) {
            val angle = Math.toRadians((i * 30).toDouble()).toFloat()
            val startX = centerX + (maxRadius * 0.8f) * cos(angle)
            val startY = centerY + (maxRadius * 0.8f) * sin(angle)
            val endX = centerX + maxRadius * cos(angle)
            val endY = centerY + maxRadius * sin(angle)
            canvas.drawLine(startX, startY, endX, endY, paint)
        }

        // رسم النقطة الحمراء (الهدف)
        if (isThreatDetected) {
            val threatX = centerX + (maxRadius * threatDistance) * cos(threatAngle)
            val threatY = centerY + (maxRadius * threatDistance) * sin(threatAngle)

            paint.style = Paint.Style.FILL
            paint.color = Color.RED
            paint.alpha = 200
            canvas.drawCircle(threatX, threatY, 20f, paint)

            // وميض النبض
            paint.color = Color.RED
            paint.alpha = 100
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            canvas.drawCircle(threatX, threatY, 30f + (System.currentTimeMillis() % 1000) / 20f, paint)

            // السهم الموجه (إبرة)
            canvas.save()
            canvas.rotate(Math.toDegrees(threatAngle.toDouble()).toFloat(), centerX, centerY)
            paint.color = Color.parseColor("#FF5722")
            paint.style = Paint.Style.FILL
            paint.alpha = 255
            val path = Path()
            path.moveTo(centerX, centerY - maxRadius * 0.6f)
            path.lineTo(centerX - 25f, centerY - maxRadius * 0.25f)
            path.lineTo(centerX + 25f, centerY - maxRadius * 0.25f)
            path.close()
            canvas.drawPath(path, paint)
            canvas.restore()

            // إطار أحمر وامض
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 4f
            paint.color = Color.RED
            paint.alpha = (150 + (System.currentTimeMillis() % 500)).toInt()
            canvas.drawCircle(centerX, centerY, maxRadius, paint)

        } else {
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#66FFFFFF")
            paint.textSize = 22f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("🔍 قم بالتدوير للبحث", centerX, centerY + 10, paint)
        }

        // نقطة المركز (موقعك)
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawCircle(centerX, centerY, 12f, paint)
        paint.color = Color.CYAN
        canvas.drawCircle(centerX, centerY, 6f, paint)
    }
}
