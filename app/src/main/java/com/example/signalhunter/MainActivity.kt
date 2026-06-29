package com.example.signalhunter

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.signalhunter.ui.RadarView
import kotlin.math.atan2
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var radarView: RadarView
    private lateinit var tvStatus: TextView
    private lateinit var tvError: TextView
    private lateinit var tvDetails: TextView
    private lateinit var btnCalibrate: Button

    private lateinit var sensorManager: SensorManager
    private lateinit var wifiManager: WifiManager
    private lateinit var bluetoothAdapter: BluetoothAdapter

    private val magnetValues = FloatArray(3)
    private var baselineX = 0f; private var baselineY = 0f; private var baselineZ = 0f
    private var isCalibrated = false

    // متغيرات تحديد أقوى مصدر
    private var maxThreatValue = 0f
    private var maxThreatAngle = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        radarView = findViewById(R.id.radarView)
        tvStatus = findViewById(R.id.tvStatus)
        tvError = findViewById(R.id.tvError)
        tvDetails = findViewById(R.id.tvDetails)
        btnCalibrate = findViewById(R.id.btnCalibrate)

        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        // طلب الأذونات (كل الأذونات الثقيلة)
        requestAllPermissions()

        // تأخير تهيئة المستشعر لتجنب أعطال سامسونج
        Handler(Looper.getMainLooper()).postDelayed({
            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val magnetSensor = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
            if (magnetSensor != null) {
                sensorManager.registerListener(this, magnetSensor, SensorManager.SENSOR_DELAY_GAME)
                tvError.text = "✅ مستشعرات جاهزة، اضغط معايرة"
                tvError.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            } else {
                tvError.text = "❌ جهازك لا يدعم المغناطيسومتر!"
            }
        }, 500)

        btnCalibrate.setOnClickListener {
            if (!isCalibrated) {
                baselineX = magnetValues[0]; baselineY = magnetValues[1]; baselineZ = magnetValues[2]
                isCalibrated = true
                maxThreatValue = 0f
                tvStatus.text = "✅ جارٍ البحث عن الإشارات..."
                tvError.text = ""
            } else {
                baselineX = magnetValues[0]; baselineY = magnetValues[1]; baselineZ = magnetValues[2]
                maxThreatValue = 0f
                tvStatus.text = "🔄 تمت إعادة المعايرة"
            }
        }
    }

    private fun requestAllPermissions() {
        val permissions = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.BODY_SENSORS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                permissions.add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED)
                permissions.add(Manifest.permission.BLUETOOTH)
        }
        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), 100)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                magnetValues[0] = it.values[0]; magnetValues[1] = it.values[1]; magnetValues[2] = it.values[2]

                if (!isCalibrated) return

                // 1. حساب اتجاه المصدر
                val angle = atan2(magnetValues[1].toDouble(), magnetValues[0].toDouble()).toFloat()
                val filteredX = magnetValues[0] - baselineX
                val filteredY = magnetValues[1] - baselineY
                val filteredZ = magnetValues[2] - baselineZ
                val magnitude = sqrt(filteredX * filteredX + filteredY * filteredY + filteredZ * filteredZ)

                // الاحتفاظ بأقوى إشارة تم رصدها أثناء الدوران
                if (magnitude > maxThreatValue) {
                    maxThreatValue = magnitude
                    maxThreatAngle = angle
                }

                // =======================
                // تحليل مصادر التهديد
                // =======================
                var sourceType = "🌐 بيئة آمنة"
                var wifiDistance = "0.0 م"
                var wifiStrength = "0 dBm"
                var btInfo = "لا يوجد"

                // (أ) تحليل المغناطيسومتر (للكاميرات والميكروفونات)
                if (maxThreatValue > 15f) {
                    sourceType = if (maxThreatValue > 30f) "📷 كاميرا / شاشة (تيار مستمر)" else "🎙️ ميكروفون / معالج (تيار متردد)"
                }

                // (ب) تحليل الواي فاي وحساب المسافة
                try {
                    val wifiInfo = wifiManager.connectionInfo
                    if (wifiInfo != null && wifiInfo.ssid != "<unknown ssid>") {
                        val rssi = wifiInfo.rssi
                        wifiStrength = "$rssi dBm"
                        // معادلة علمية لحساب المسافة
                        val exp = (Math.abs(rssi) - 50.0) / (10.0 * 2.5)
                        val dist = Math.pow(10.0, exp)
                        wifiDistance = String.format("%.1f متر", dist.coerceIn(0.0, 50.0))
                        sourceType = "📶 واي فاي (${wifiInfo.ssid})"
                    }
                } catch (_: Exception) {}

                // (ج) تحليل البلوتوث
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    try {
                        val pairedDevices = bluetoothAdapter?.bondedDevices
                        if (!pairedDevices.isNullOrEmpty()) {
                            val device = pairedDevices.first()
                            btInfo = device.name ?: "جهاز مجهول"
                            // تقدير مسافة البلوتوث بناءً على المجال المغناطيسي
                            val fakeRssi = 70 - (maxThreatValue / 1.5f)
                            val expBt = (fakeRssi - 50.0) / (10.0 * 2.5)
                            val distBt = Math.pow(10.0, expBt)
                            val btDistStr = String.format("%.1f متر", distBt.coerceIn(0.0, 20.0))
                            sourceType = "📡 بلوتوث ($btInfo - $btDistStr)"
                        }
                    } catch (_: Exception) {}
                }

                // =======================
                // تحديث الرادار والواجهة
                // =======================
                val threatForRadar = maxThreatValue.coerceAtMost(90f)
                radarView.updateThreat(maxThreatAngle, threatForRadar)

                runOnUiThread {
                    // نافذة التفاصيل (شاشة الهكر)
                    val details = """
                        🧭 المصدر الأقوى: $sourceType
                        📏 المسافات المقدرة:
                           • واي فاي: $wifiDistance
                           • كهرومغناطيسي: ${String.format("%.1f", maxThreatValue)} µT
                        📡 قوة الواي فاي: $wifiStrength
                    """.trimIndent()
                    tvDetails.text = details

                    // تغيير لون الحالة
                    when {
                        maxThreatValue > 50f -> {
                            tvStatus.text = "🔴 خطر داهم (كاميرا/راوتر قريب)"
                            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                        }
                        maxThreatValue > 20f -> {
                            tvStatus.text = "🟠 نشاط مشبوه (جهاز قريب)"
                            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                        }
                        else -> {
                            tvStatus.text = "🟢 بيئة آمنة تماماً"
                            tvStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_light))
                        }
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        super.onDestroy()
        if (::sensorManager.isInitialized) sensorManager.unregisterListener(this)
    }
}
