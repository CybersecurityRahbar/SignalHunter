package com.example.signalhunter.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class CompassView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var currentAngle = 0f // الزاوية الحالية بالراديان
    private var centerX = 0f
    private var centerY = 0f
    private var radius = 0f

    init {
        paint.style = Paint.Style.FILL_AND_STROKE
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = (w / 2).toFloat()
        centerY = (h / 2).toFloat()
        radius = if (w < h) w * 0.4f else h * 0.4f
    }

    // دالة لتحديث اتجاه البوصلة من البيانات الخام (يستدعيها الـ MainActivity)
    fun updateDirection(magnetData: FloatArray) {
        // حساب الزاوية بين المحور X والمجال المغناطيسي
        // atan2(Y, X) تعطينا الزاوية بالنسبة للشمال المغناطيسي
        currentAngle = atan2(magnetData[1].toDouble(), magnetData[0].toDouble()).toFloat()
        invalidate() // إعادة الرسم
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. رسم الخلفية الدائرية (رمادي غامق)
        paint.color = Color.parseColor("#2A2A2A")
        paint.style = Paint.Style.FILL
        canvas.drawCircle(centerX, centerY, radius, paint)

        // 2. رسم الحافة الخارجية (درجات رمادية)
        paint.color = Color.parseColor("#444444")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 4f
        canvas.drawCircle(centerX, centerY, radius, paint)

        // 3. رسم علامات الاتجاهات الأساسية (N, S, E, W)
        paint.color = Color.WHITE
        paint.textSize = radius * 0.2f
        paint.textAlign = Paint.Align.CENTER
        paint.style = Paint.Style.FILL
        canvas.drawText("N", centerX, centerY - radius + 30, paint) // شمال
        canvas.drawText("S", centerX, centerY + radius - 10, paint) // جنوب
        canvas.drawText("E", centerX + radius - 20, centerY + 10, paint) // شرق
        canvas.drawText("W", centerX - radius + 20, centerY + 10, paint) // غرب

        // 4. رسم الإبرة الحمراء (تتجه حسب زاوية المغناطيسومتر)
        // حفظ حالة الرسم لتطبيق الدوران
        canvas.save()
        canvas.rotate(Math.toDegrees(currentAngle.toDouble()).toFloat(), centerX, centerY)

        // إبرة حمراء طويلة
        paint.color = Color.RED
        paint.style = Paint.Style.FILL
        paint.strokeWidth = 6f

        // رسم مثلث للإبرة يشير للأعلى (الشمال المغناطيسي)
        path.reset()
        path.moveTo(centerX, centerY - radius * 0.85f) // رأس الإبرة
        path.lineTo(centerX - 15f, centerY - radius * 0.4f)
        path.lineTo(centerX + 15f, centerY - radius * 0.4f)
        path.close()
        canvas.drawPath(path, paint)

        // إبرة بيضاء صغيرة للاتجاه المعاكس (الجنوب)
        paint.color = Color.WHITE
        path.reset()
        path.moveTo(centerX, centerY + radius * 0.7f)
        path.lineTo(centerX - 10f, centerY + radius * 0.35f)
        path.lineTo(centerX + 10f, centerY + radius * 0.35f)
        path.close()
        canvas.drawPath(path, paint)

        // 5. رسم دائرة صغيرة في المنتصف
        paint.color = Color.BLACK
        paint.style = Paint.Style.FILL
        canvas.drawCircle(centerX, centerY, 12f, paint)

        paint.color = Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        canvas.drawCircle(centerX, centerY, 12f, paint)

        // استعادة حالة الرسم
        canvas.restore()
    }
}
