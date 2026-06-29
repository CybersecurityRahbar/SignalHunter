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

    private lateinit var compassView: CompassView
    private lateinit var tvThreatValue: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvDirectionHint: TextView
    private lateinit var tvError: TextView
    private lateinit var btnCalibrate: Button

    private lateinit var sensorManager: SensorManager
    private val magnetValues = FloatArray(3)

    private var baselineX = 0f
    private var baselineY = 0f
    private var baselineZ = 0f
    private var isCalibrated = false

    // تعريف الدالة الخارجية (سيتم تحميل المكتبة لاحقاً)
    external fun calculateThreatLevel(
        x: FloatArray, y: FloatArray, z: FloatArray,
        baselineX: Float, baselineY: Float, baselineZ: Float
    ): Float

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ربط عناصر الواجهة أولاً
        compassView = findViewById(R.id.compassView)
        tvThreatValue = findViewById(R.id.tvThreatValue)
        tvStatus = findViewById(R.id.tvStatus)
        tvDirectionHint = findViewById(R.id.tvDirectionHint)
        tvError = findViewById(R.id.tvError)
        btnCalibrate = findViewById(R.id.btnCalibrate)

        // **الآن نحمل المكتبة بأمان**
        try {
            System.loadLibrary("signalhunter")
            tvError.text = "✅ مكتبة C++ محملة بنجاح"
            tvError.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        } catch (e: UnsatisfiedLinkError) {
            tvError.text = "❌ فشل تحميل المكتبة: ${e.message}"
            tvError.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            e.printStackTrace()
        }

        // تهيئة المستشعرات
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val magnetSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        // طلب الأذونات
        checkAndRequestPermissions()

        // زر المعايرة
        btnCalibrate.setOnClickListener {
            if (isCalibrated) {
                baselineX = magnetValues[0]
                baselineY = magnetValues[1]
                baselineZ = magnetValues[2]
                tvStatus.text = "🔄 تمت إعادة المعايرة"
                tvError.text = ""  // مسح أي خطأ سابق
            } else {
                baselineX = magnetValues[0]
                baselineY = magnetValues[1]
                baselineZ = magnetValues[2]
                isCalibrated = true
                tvStatus.text = "✅ جاهز للكشف"
                tvError.text = ""
                tvDirectionHint.text = "🧭 حرك الهاتف حول الغرفة"
            }
        }

        if (magnetSensor != null) {
            sensorManager.registerListener(this, magnetSensor, SensorManager.SENSOR_DELAY_FASTEST)
        } else {
            tvStatus.text = "❌ هذا الجهاز لا يدعم المغناطيسومتر!"
        }
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS)
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.BODY_SENSORS)
        }
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 101)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                magnetValues[0] = it.values[0]
                magnetValues[1] = it.values[1]
                magnetValues[2] = it.values[2]

                compassView.updateDirection(magnetValues)

                if (isCalibrated) {
                    try {
                        val threat = calculateThreatLevel(
                            floatArrayOf(magnetValues[0]),
                            floatArrayOf(magnetValues[1]),
                            floatArrayOf(magnetValues[2]),
                            baselineX, baselineY, baselineZ
                        )

                        runOnUiThread {
                            tvThreatValue.text = String.format("%.2f", threat)
                            tvError.text = ""  // مسح أي خطأ سابق

                            when {
                                threat > 50.0f -> {
                                    tvStatus.text = "🔴 تهديد عالي (كاميرا / واي فاي)"
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
                    } catch (e: UnsatisfiedLinkError) {
                        runOnUiThread {
                            tvError.text = "❌ خطأ في مكتبة C++: ${e.message}"
                            tvError.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                        }
                        e.printStackTrace()
                    } catch (e: Exception) {
                        runOnUiThread {
                            tvError.text = "❌ خطأ عام: ${e.message}"
                            tvError.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                        }
                        e.printStackTrace()
                    }
                } else {
                    runOnUiThread {
                        tvStatus.text = "⏳ اضغط على 'معايرة' أولاً"
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        super.onDestroy()
        sensorManager.unregisterListener(this)
    }
}
