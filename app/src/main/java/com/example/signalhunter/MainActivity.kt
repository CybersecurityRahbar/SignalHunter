package com.example.signalhunter

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.signalhunter.ui.CompassView

class MainActivity : AppCompatActivity(), SensorEventListener {

    // عناصر الواجهة
    private lateinit var compassView: CompassView
    private lateinit var tvThreatValue: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvDirectionHint: TextView
    private lateinit var btnCalibrate: Button

    // المستشعرات
    private lateinit var sensorManager: SensorManager
    private val magnetValues = FloatArray(3) // X, Y, Z

    // متغيرات المعايرة (Baseline)
    private var baselineX = 0f
    private var baselineY = 0f
    private var baselineZ = 0f
    private var isCalibrated = false

    // تحميل مكتبة الـ C++ (سنكتبها في الرسالة التالية)
    init {
        System.loadLibrary("signalhunter")
    }

    // تعريف الدالة الخارجية من لغة C++
    external fun calculateThreatLevel(
        x: FloatArray,
        y: FloatArray,
        z: FloatArray,
        baselineX: Float,
        baselineY: Float,
        baselineZ: Float
    ): Float

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ربط العناصر
        compassView = findViewById(R.id.compassView)
        tvThreatValue = findViewById(R.id.tvThreatValue)
        tvStatus = findViewById(R.id.tvStatus)
        tvDirectionHint = findViewById(R.id.tvDirectionHint)
        btnCalibrate = findViewById(R.id.btnCalibrate)

        // إعداد مستشعر المغناطيسومتر
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val magnetSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        // طلب الأذونات (خاصة لهواتف Android 11+)
        checkAndRequestPermissions()

        // عند الضغط على زر المعايرة: نحفظ القراءة الحالية كخط أساس (لتصفية مجال الأرض)
        btnCalibrate.setOnClickListener {
            if (isCalibrated) {
                // إعادة المعايرة
                baselineX = magnetValues[0]
                baselineY = magnetValues[1]
                baselineZ = magnetValues[2]
                tvStatus.text = "🔄 تمت إعادة المعايرة"
                tvDirectionHint.text = "🧭 قم بتدوير الهاتف ببطء"
            } else {
                // أول معايرة
                baselineX = magnetValues[0]
                baselineY = magnetValues[1]
                baselineZ = magnetValues[2]
                isCalibrated = true
                tvStatus.text = "✅ جاهز للكشف"
                tvDirectionHint.text = "🧭 حرك الهاتف حول الغرفة"
            }
        }

        // تسجيل المستمع للمستشعر بأعلى سرعة ممكنة (SENSOR_DELAY_FASTEST)
        if (magnetSensor != null) {
            sensorManager.registerListener(
                this,
                magnetSensor,
                SensorManager.SENSOR_DELAY_FASTEST
            )
        } else {
            tvStatus.text = "❌ هذا الجهاز لا يدعم المغناطيسومتر!"
        }
    }

    // دالة طلب الأذونات
    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ يحتاج إذن دقيق للموقع لقراءة الـ WiFi و Bluetooth
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        // إذن المستشعرات الجسدية (مطلوب في بعض الأجهزة)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.BODY_SENSORS)
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissions.toTypedArray(),
                101
            )
        }
    }

    // تنفيذ واجهة SensorEventListener
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                // تخزين القراءات الخام
                magnetValues[0] = it.values[0]
                magnetValues[1] = it.values[1]
                magnetValues[2] = it.values[2]

                // 1. تحديث اتجاه البوصلة
                compassView.updateDirection(magnetValues)

                // 2. إذا تمت المعايرة، قم بتحليل البيانات عبر C++
                if (isCalibrated) {
                    // استدعاء دالة C++ لحساب مستوى الخطر (تفرق بين الكاميرا والواي فاي)
                    val threat = calculateThreatLevel(
                        floatArrayOf(magnetValues[0]),
                        floatArrayOf(magnetValues[1]),
                        floatArrayOf(magnetValues[2]),
                        baselineX,
                        baselineY,
                        baselineZ
                    )

                    // تحديث واجهة المستخدم (يجب أن يكون على الـ UI Thread)
                    runOnUiThread {
                        // عرض الرقم
                        tvThreatValue.text = String.format("%.2f", threat)

                        // تغيير الحالة حسب شدة الخطر
                        when {
                            threat > 50.0f -> {
                                tvStatus.text = "🔴 تهديد عالي (كاميرا / واي فاي نشط)"
                                tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                                tvDirectionHint.text = "⚠️ مصدر الإشارة قريب جداً!"
                            }
                            threat > 20.0f -> {
                                tvStatus.text = "🟠 نشاط مشبوه (بلوتوث / ميكروفون)"
                                tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                                tvDirectionHint.text = "📡 حرك الهاتف لتحديد المصدر"
                            }
                            threat > 5.0f -> {
                                tvStatus.text = "🟡 تقلبات طفيفة (أجهزة كهربائية)"
                                tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_light))
                                tvDirectionHint.text = "🧭 ابحث في اتجاه مختلف"
                            }
                            else -> {
                                tvStatus.text = "🟢 بيئة آمنة"
                                tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
                                tvDirectionHint.text = "✅ لا يوجد نشاط كهرومغناطيسي خفي"
                            }
                        }
                    }
                } else {
                    // إذا لم تتم المعايرة، نطلب من المستخدم الضغط على الزر
                    runOnUiThread {
                        tvStatus.text = "⏳ اضغط على 'معايرة' أولاً"
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // يمكن تجاهلها، أو استخدامها لتقليل الضوضاء إذا كانت الدقة منخفضة
    }

    override fun onDestroy() {
        super.onDestroy()
        // إلغاء التسجيل لتوفير البطارية
        sensorManager.unregisterListener(this)
    }
}
