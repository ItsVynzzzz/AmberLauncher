package com.israadev.nuxlauncher.core.controls

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.WindowManager
import com.israadev.nuxlauncher.core.settings.SettingsManager
import org.lwjgl.glfw.CallbackBridge
import kotlin.math.abs
import kotlin.math.sign

/**
 * Kamera dengan gyro: gerakan memutar HP diubah menjadi geseran kamera, seperti HP adalah jendela ke dunia game
 * (HP diputar ke kanan, pandangan ikut ke kanan; HP dimiringkan ke atas, pandangan ke atas).
 *
 * Ringan untuk HP kelas bawah:
 * - Sensor hanya didaftarkan selama [start] dan dilepas lagi oleh [stop]. Pemanggil hanya memanggil [start] saat
 *   gyro dinyalakan dan layar game sedang tampil, jadi tanpa gyro tidak ada pekerjaan sama sekali.
 * - Sekitar 50 pembacaan per detik, masing-masing hanya beberapa perkalian. Pembacaan dikirim ke thread utama,
 *   thread yang sama dengan sentuhan kamera, karena [CallbackBridge.sendCursorDelta] mengubah posisi kursor
 *   tanpa kunci dan tidak boleh dipanggil dari dua thread sekaligus.
 * - Pengaturan (sensitivitas, balik Y) dibaca langsung dari [SettingsManager] tiap pembacaan, jadi perubahan
 *   dari menu dalam game langsung berlaku tanpa mendaftarkan ulang sensor.
 *
 * [canLook] dipanggil tiap pembacaan: true hanya saat game sedang menangkap kursor (bermain di dunia) dan tidak ada
 * menu atau lapisan launcher yang menutupi game. Di menu game gerakan HP tidak menggeser kursor.
 */
class GyroLook(
    private val context: Context,
    private val canLook: () -> Boolean
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val handler = Handler(Looper.getMainLooper())

    /** HP ini punya sensor gyroscope. Tanpa sensor, [start] tidak melakukan apa-apa. */
    val supported: Boolean get() = sensor != null

    private var running = false
    private var lastNs = 0L
    private var rotation = Surface.ROTATION_90
    private var rotationCheckedNs = 0L

    /** Mulai membaca sensor. Aman dipanggil berulang. Panggil dari thread utama. */
    fun start() {
        val manager = sensorManager ?: return
        val gyro = sensor ?: return
        if (running) return
        lastNs = 0L
        rotationCheckedNs = 0L
        running = try {
            manager.registerListener(this, gyro, SensorManager.SENSOR_DELAY_GAME, handler)
        } catch (_: Throwable) {
            false
        }
    }

    /** Berhenti membaca sensor. Aman dipanggil berulang. */
    fun stop() {
        if (!running) return
        running = false
        try {
            sensorManager?.unregisterListener(this)
        } catch (_: Throwable) {
        }
    }

    override fun onAccuracyChanged(changed: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent) {
        val now = event.timestamp
        val prev = lastNs
        // Waktu terakhir selalu diperbarui, juga saat sedang tidak boleh menggeser: begitu boleh lagi
        // (menu ditutup, layar dibuka), selang waktunya kecil dan kamera tidak melompat.
        lastNs = now
        if (prev == 0L) return
        val dt = ((now - prev) / 1_000_000_000f).coerceIn(0f, MAX_DT_SEC)
        if (dt <= 0f) return
        if (!canLook()) return

        val s = SettingsManager.settings.value
        if (!s.gyroEnabled) return

        // Arah layar bisa berubah di tengah game (HP dibalik ke landscape sebaliknya): diperiksa tiap setengah detik.
        if (now - rotationCheckedNs > ROTATION_CHECK_NS) {
            rotationCheckedNs = now
            rotation = readRotation()
        }

        // Kecepatan putar (rad/detik) pada sumbu HP: x ke kanan, y ke atas (saat portrait), z keluar layar.
        val gx = event.values[0]
        val gy = event.values[1]
        // Ke sumbu layar: pitch = putaran mengelilingi sumbu mendatar layar, yaw = mengelilingi sumbu tegak layar.
        val pitchRate: Float
        val yawRate: Float
        when (rotation) {
            Surface.ROTATION_90 -> { pitchRate = -gy; yawRate = gx }
            Surface.ROTATION_180 -> { pitchRate = -gx; yawRate = -gy }
            Surface.ROTATION_270 -> { pitchRate = gy; yawRate = -gx }
            else -> { pitchRate = gx; yawRate = gy }
        }

        val gain = BASE_PIXELS_PER_RADIAN * (s.gyroSensitivity.coerceIn(25, 400) / 100f)
        // Putar ke kanan (yawRate negatif) = kursor ke kanan (positif). Miring ke atas (pitchRate positif) =
        // kursor ke atas (negatif); "Balik Y" membalik arah ini.
        val moveX = -deadZone(yawRate) * dt * gain
        val moveY = -deadZone(pitchRate) * dt * gain * (if (s.gyroInvertY) -1f else 1f)
        if (moveX == 0f && moveY == 0f) return
        CallbackBridge.sendCursorDelta(moveX, moveY)
    }

    /**
     * Zona mati lembut: gyro HP yang diam masih melaporkan getaran kecil, yang tanpa ini membuat kamera
     * melayang pelan sendiri. Nilai di bawah batas dibuang, sisanya dikurangi batas supaya tidak ada loncatan.
     */
    private fun deadZone(v: Float): Float {
        val a = abs(v)
        return if (a <= DEAD_ZONE_RAD_PER_SEC) 0f else (a - DEAD_ZONE_RAD_PER_SEC) * sign(v)
    }

    @Suppress("DEPRECATION")
    private fun readRotation(): Int = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display?.rotation ?: Surface.ROTATION_90
        } else {
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
        }
    } catch (_: Throwable) {
        Surface.ROTATION_90
    }

    private companion object {
        /**
         * Piksel kursor per radian putaran pada sensitivitas 100%: satu derajat kamera Minecraft pada
         * sensitivitas bawaan kira-kira 6,7 piksel (0,15 derajat per piksel), jadi 180 / pi / 0,15.
         */
        const val BASE_PIXELS_PER_RADIAN = 382f

        /** Batas zona mati, sekitar 0,7 derajat per detik. */
        const val DEAD_ZONE_RAD_PER_SEC = 0.012f

        /** Selang antar dua pembacaan yang masih dipercaya; lebih lama dari ini dianggap jeda (mis. kembali dari aplikasi lain). */
        const val MAX_DT_SEC = 0.1f

        const val ROTATION_CHECK_NS = 500_000_000L
    }
}
