package com.israadev.nuxlauncher.core.controls

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.view.Choreographer
import android.view.Surface
import android.view.WindowManager
import com.israadev.nuxlauncher.core.settings.SettingsManager
import org.lwjgl.glfw.CallbackBridge
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sqrt

/**
 * Kamera dengan gyro: gerakan memutar HP diubah menjadi geseran kamera, seperti HP adalah jendela ke dunia game
 * (HP diputar ke kanan, pandangan ikut ke kanan; HP dimiringkan ke atas, pandangan ke atas).
 *
 * Tiga jenis sumber gerakan ([Kind]), dipilih otomatis:
 * - [Kind.HARDWARE]: sensor gyroscope asli. Jalurnya sengaja tetap sederhana dan langsung (dibaca di thread utama,
 *   langsung dikirim ke game), karena sudah akurat dan cepat.
 * - [Kind.VIRTUAL_RATE]: sensor gyroscope yang ternyata buatan perangkat lunak (dihitung sistem dari sensor lain).
 * - [Kind.ORIENTATION]: HP tanpa gyroscope sama sekali, tetapi punya vektor rotasi dari akselerometer + magnetometer
 *   ("gyro software"). Gerakannya dihitung dari selisih arah HP antar dua pembacaan.
 * Dua jenis software itu lebih berisik dan lebih lambat dari gyro asli (datangnya dari sensor lain yang disaring
 * sistem), jadi dikerjakan dengan cara yang tidak menambah lambat dan tidak menambah getar:
 * - Pembacaan diproses di thread sendiri, bukan di thread utama yang sibuk menggambar menu dan UI.
 * - Hasilnya dikumpulkan dan dikirim ke game satu kali per frame layar (Choreographer), bukan sepotong-sepotong.
 * - Disaring dengan filter adaptif (One Euro): saat HP nyaris diam gerakannya diredam kuat (tidak bergoyang),
 *   saat HP digerakkan cepat peredamnya dilepas (tidak terasa tertinggal).
 *
 * Ringan untuk HP kelas bawah: sensor hanya didaftarkan selama [start] dan dilepas lagi oleh [stop]. Pemanggil hanya
 * memanggil [start] saat gyro dinyalakan dan layar game sedang tampil. Pengaturan (sensitivitas, balik Y, kehalusan)
 * dibaca langsung dari [SettingsManager] tiap pembacaan, jadi perubahan dari menu langsung berlaku.
 *
 * [canLook] dipanggil sebelum mengirim: true hanya saat game sedang menangkap kursor (bermain di dunia) dan tidak ada
 * menu atau lapisan launcher yang menutupi game. Di menu game gerakan HP tidak menggeser kursor.
 */
class GyroLook(
    private val context: Context,
    private val canLook: () -> Boolean
) : SensorEventListener {

    enum class Kind { NONE, HARDWARE, VIRTUAL_RATE, ORIENTATION }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val gyro: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    // Vektor rotasi hanya dicari bila tidak ada gyroscope: HP bergyro selalu memakai gyroscope-nya.
    private val rotationSensor: Sensor? = if (gyro != null) null else {
        sensorManager?.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
            ?: sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    }

    private val sensor: Sensor? = gyro ?: rotationSensor

    val kind: Kind = when {
        gyro != null -> if (looksVirtual(gyro)) Kind.VIRTUAL_RATE else Kind.HARDWARE
        rotationSensor != null -> Kind.ORIENTATION
        else -> Kind.NONE
    }

    /** HP ini punya sumber gerakan yang bisa dipakai. Tanpa itu, [start] tidak melakukan apa-apa. */
    val supported: Boolean get() = kind != Kind.NONE

    /** Gyro buatan perangkat lunak (bukan gyroscope asli): lebih berisik dan lebih lambat. */
    val isSoftware: Boolean get() = kind == Kind.VIRTUAL_RATE || kind == Kind.ORIENTATION

    /** Kehalusan bawaan (persen) untuk sumber ini, dipakai selama pengguna belum mengubahnya sendiri. */
    val defaultSmoothing: Int get() = defaultSmoothingFor(kind)

    @Volatile private var running = false
    private var thread: HandlerThread? = null
    private var choreographer: Choreographer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // Keadaan hitungan. Dikunci karena jalur software mengisinya dari thread sensor, sedangkan start/stop dari thread utama.
    private val lock = Any()
    private var lastNs = 0L
    private var havePrevQuat = false
    private var qw = 1f
    private var qx = 0f
    private var qy = 0f
    private var qz = 0f
    private var rawX = 0.0
    private var rawY = 0.0
    private var outX = 0.0
    private var outY = 0.0
    private val filterX = OneEuro()
    private val filterY = OneEuro()
    private var moveX = 0f
    private var moveY = 0f
    private var pendX = 0f
    private var pendY = 0f

    @Volatile private var rotation = Surface.ROTATION_90
    private var rotationCheckedNs = 0L

    /** Mulai membaca sensor. Aman dipanggil berulang. Panggil dari thread utama. */
    fun start() {
        val manager = sensorManager ?: return
        val source = sensor ?: return
        if (running) return
        synchronized(lock) { resetLocked() }
        rotationCheckedNs = 0L
        rotation = readRotation()
        try {
            if (kind == Kind.HARDWARE) {
                running = manager.registerListener(this, source, SensorManager.SENSOR_DELAY_GAME, mainHandler)
            } else {
                val worker = HandlerThread("amber-gyro", android.os.Process.THREAD_PRIORITY_DISPLAY)
                worker.start()
                // Disimpan sebelum mendaftar supaya stop() ikut menutupnya bila pendaftaran gagal di tengah jalan.
                thread = worker
                val ok = manager.registerListener(this, source, SOFTWARE_PERIOD_US, 0, Handler(worker.looper))
                if (ok) {
                    running = true
                    val c = Choreographer.getInstance()
                    choreographer = c
                    c.postFrameCallback(frameCallback)
                } else {
                    stop()
                }
            }
        } catch (_: Throwable) {
            stop()
        }
    }

    /** Berhenti membaca sensor. Aman dipanggil berulang. */
    fun stop() {
        val wasRunning = running
        running = false
        if (wasRunning) {
            try {
                sensorManager?.unregisterListener(this)
            } catch (_: Throwable) {
            }
        }
        choreographer?.removeFrameCallback(frameCallback)
        choreographer = null
        thread?.quitSafely()
        thread = null
        synchronized(lock) {
            pendX = 0f
            pendY = 0f
        }
    }

    override fun onAccuracyChanged(changed: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent) {
        if (kind == Kind.HARDWARE) refreshRotationIfDue(event.timestamp)
        var sendX = 0f
        var sendY = 0f
        var send = false
        synchronized(lock) {
            if (computeLocked(event)) {
                if (kind == Kind.HARDWARE) {
                    sendX = moveX
                    sendY = moveY
                    send = true
                } else {
                    pendX += moveX
                    pendY += moveY
                }
            }
        }
        // Jalur gyro asli: langsung kirim (thread ini = thread utama). Jalur software dikirim oleh frameCallback.
        if (send && canLook()) CallbackBridge.sendCursorDelta(sendX, sendY)
    }

    /** Sekali per frame layar di thread utama: kirim semua gerakan yang terkumpul sejak frame lalu. */
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            refreshRotationIfDue(frameTimeNanos)
            var x = 0f
            var y = 0f
            synchronized(lock) {
                x = pendX
                y = pendY
                pendX = 0f
                pendY = 0f
            }
            if ((x != 0f || y != 0f) && canLook()) CallbackBridge.sendCursorDelta(x, y)
            choreographer?.postFrameCallback(this)
        }
    }

    private fun resetLocked() {
        lastNs = 0L
        havePrevQuat = false
        rawX = 0.0
        rawY = 0.0
        outX = 0.0
        outY = 0.0
        filterX.reset()
        filterY.reset()
        pendX = 0f
        pendY = 0f
    }

    /**
     * Menghitung geseran kursor ([moveX], [moveY], piksel) dari satu pembacaan. Mengembalikan false bila tidak ada
     * yang perlu dikirim. Dipanggil dengan [lock] terpegang.
     */
    private fun computeLocked(event: SensorEvent): Boolean {
        val now = event.timestamp
        val prev = lastNs
        lastNs = now
        val dt = if (prev == 0L) 0f else ((now - prev) / 1_000_000_000f)
        val v = event.values

        // Putaran HP (radian) sejak pembacaan lalu, pada sumbu HP: x ke kanan, y ke atas (saat portrait).
        val gx: Float
        val gy: Float
        if (kind == Kind.ORIENTATION) {
            val x = v[0]
            val y = v[1]
            val z = v[2]
            val w = if (v.size >= 4) v[3] else sqrt(max(0f, 1f - x * x - y * y - z * z))
            if (!havePrevQuat) {
                qw = w; qx = x; qy = y; qz = z
                havePrevQuat = true
                return false
            }
            // Selisih arah = konjugat(arah lalu) x arah sekarang, dinyatakan di kerangka HP.
            val rw = qw * w + qx * x + qy * y + qz * z
            val rx = qw * x - qx * w - qy * z + qz * y
            val ry = qw * y + qx * z - qy * w - qz * x
            val rz = qw * z - qx * y + qy * x - qz * w
            qw = w; qx = x; qy = y; qz = z
            // Jeda panjang (mis. kembali dari aplikasi lain): selisihnya bukan gerakan sungguhan.
            if (dt <= 0f || dt > MAX_DT_SEC) return false
            val flip = if (rw < 0f) -1f else 1f
            val n = sqrt(rx * rx + ry * ry + rz * rz)
            val k = if (n < 1e-6f) 2f * flip else flip * 2f * atan2(n, abs(rw)) / n
            gx = rx * k
            gy = ry * k
        } else {
            if (prev == 0L || dt <= 0f) return false
            // Selang antar dua pembacaan yang masih dipercaya; lebih lama dari ini dianggap jeda.
            if (dt > MAX_DT_SEC) return false
            gx = deadZone(v[0]) * dt
            gy = deadZone(v[1]) * dt
        }

        val s = SettingsManager.settings.value
        if (!s.gyroEnabled) return false

        // Ke sumbu layar: pitch = putaran mengelilingi sumbu mendatar layar, yaw = mengelilingi sumbu tegak layar.
        val pitch: Float
        val yaw: Float
        when (rotation) {
            Surface.ROTATION_90 -> { pitch = -gy; yaw = gx }
            Surface.ROTATION_180 -> { pitch = -gx; yaw = -gy }
            Surface.ROTATION_270 -> { pitch = gy; yaw = -gx }
            else -> { pitch = gx; yaw = gy }
        }

        // Putar ke kanan (yaw negatif) = kursor ke kanan (positif). Miring ke atas (pitch positif) = kursor ke atas
        // (negatif); "Balik Y" membalik arah ini. Satuan masih radian.
        var mx = -yaw.toDouble()
        var my = -pitch.toDouble()

        val smoothing = effectiveSmoothing(s.gyroSmoothing, kind)
        if (smoothing > 0) {
            // Disaring pada sudut yang sudah terkumpul (bukan pada selisihnya), lalu selisih sudut tersaring itu
            // yang dikirim. Sudut hasil selisih arah tidak melayang, dan sisa tarikan setelah HP berhenti hanya
            // sepersekian detik.
            val cutoff = minCutoffHz(smoothing)
            val step = dt.coerceIn(MIN_FILTER_DT_SEC, MAX_DT_SEC).toDouble()
            rawX += mx
            rawY += my
            val fx = filterX.filter(rawX, step, cutoff)
            val fy = filterY.filter(rawY, step, cutoff)
            mx = fx - outX
            my = fy - outY
            outX = fx
            outY = fy
        }

        val gain = BASE_PIXELS_PER_RADIAN * (s.gyroSensitivity.coerceIn(25, 400) / 100f)
        moveX = (mx * gain).toFloat()
        moveY = (my * gain * (if (s.gyroInvertY) -1f else 1f)).toFloat()
        return moveX != 0f || moveY != 0f
    }

    /**
     * Zona mati lembut untuk kecepatan putar gyroscope: gyro HP yang diam masih melaporkan getaran kecil, yang tanpa ini
     * membuat kamera melayang pelan sendiri. Nilai di bawah batas dibuang, sisanya dikurangi batas supaya tidak ada
     * loncatan.
     */
    private fun deadZone(v: Float): Float {
        val a = abs(v)
        return if (a <= DEAD_ZONE_RAD_PER_SEC) 0f else (a - DEAD_ZONE_RAD_PER_SEC) * sign(v)
    }

    /** Arah layar bisa berubah di tengah game (HP dibalik ke landscape sebaliknya): diperiksa tiap setengah detik. */
    private fun refreshRotationIfDue(nowNs: Long) {
        if (nowNs - rotationCheckedNs > ROTATION_CHECK_NS) {
            rotationCheckedNs = nowNs
            rotation = readRotation()
        }
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

    /**
     * Filter One Euro untuk satu sumbu: peredam lolos-rendah yang batas frekuensinya naik mengikuti kecepatan sinyal.
     * Lambat = batas rendah = halus; cepat = batas tinggi = nyaris tanpa keterlambatan.
     */
    private class OneEuro {
        private var ready = false
        private var xPrev = 0.0
        private var dxPrev = 0.0

        fun reset() {
            ready = false
            xPrev = 0.0
            dxPrev = 0.0
        }

        fun filter(x: Double, dt: Double, minCutoff: Double): Double {
            if (!ready) {
                ready = true
                xPrev = x
                dxPrev = 0.0
                return x
            }
            val dx = (x - xPrev) / dt
            val dxHat = dxPrev + alpha(dt, DERIVATIVE_CUTOFF_HZ) * (dx - dxPrev)
            val cutoff = minCutoff + BETA * abs(dxHat)
            val xHat = xPrev + alpha(dt, cutoff) * (x - xPrev)
            xPrev = xHat
            dxPrev = dxHat
            return xHat
        }

        private fun alpha(dt: Double, cutoff: Double): Double {
            val tau = 1.0 / (2.0 * PI * cutoff)
            return 1.0 / (1.0 + tau / dt)
        }
    }

    companion object {
        /** Jenis sumber gerakan yang dipakai HP ini, tanpa mendaftarkan sensor apa pun. */
        fun detect(context: Context): Kind = GyroLook(context) { false }.kind

        /** Kehalusan bawaan (persen): gyro asli tanpa penyaring, gyro software cukup halus supaya tidak bergoyang. */
        fun defaultSmoothingFor(kind: Kind): Int = when (kind) {
            Kind.VIRTUAL_RATE, Kind.ORIENTATION -> 60
            else -> 0
        }

        /** Kehalusan yang berlaku: nilai pilihan pengguna (0 - 100), atau bawaan sumbernya bila belum dipilih (-1). */
        fun effectiveSmoothing(stored: Int, kind: Kind): Int =
            if (stored in 0..100) stored else defaultSmoothingFor(kind)

        /** Batas frekuensi terendah filter (Hz) untuk kehalusan 1 - 100: dari sekitar 6 Hz (tipis) turun ke sekitar 0,7 Hz (tebal). */
        fun minCutoffHz(smoothing: Int): Double = 6.0 * 0.12.pow(smoothing.coerceIn(0, 100) / 100.0)

        /**
         * Gyroscope yang dilaporkan sistem tetapi sebenarnya buatan perangkat lunak. Tidak ada tanda resminya, jadi
         * ditebak dari nama/pembuat sensor atau laju yang terlalu lambat untuk gyroscope asli.
         * "Corrected Gyroscope" milik Android sengaja tidak dihitung: itu gyroscope asli yang dikoreksi biasnya.
         */
        private fun looksVirtual(sensor: Sensor): Boolean {
            val label = (sensor.name.orEmpty() + " " + sensor.vendor.orEmpty()).lowercase()
            if (VIRTUAL_WORDS.any { label.contains(it) }) return true
            return sensor.minDelay > SLOW_MIN_DELAY_US
        }

        private val VIRTUAL_WORDS = listOf("software", "virtual", "emulat", "fake")

        /** Laju minta (mikrodetik) untuk sensor software: sekitar 100 Hz, sistem membatasi sesuai kemampuan sensornya. */
        private const val SOFTWARE_PERIOD_US = 10_000

        /** Gyroscope yang paling cepatnya hanya sekitar 33 Hz dianggap bukan gyroscope asli. */
        private const val SLOW_MIN_DELAY_US = 30_000

        /**
         * Piksel kursor per radian putaran pada sensitivitas 100%: satu derajat kamera Minecraft pada
         * sensitivitas bawaan kira-kira 6,7 piksel (0,15 derajat per piksel), jadi 180 / pi / 0,15.
         */
        private const val BASE_PIXELS_PER_RADIAN = 382f

        /** Batas zona mati, sekitar 0,7 derajat per detik. */
        private const val DEAD_ZONE_RAD_PER_SEC = 0.012f

        /** Selang antar dua pembacaan yang masih dipercaya; lebih lama dari ini dianggap jeda (mis. kembali dari aplikasi lain). */
        private const val MAX_DT_SEC = 0.1f
        private const val MIN_FILTER_DT_SEC = 0.002f

        private const val ROTATION_CHECK_NS = 500_000_000L

        /** Filter One Euro: seberapa cepat batas frekuensi naik mengikuti kecepatan (per radian/detik), dan penyaring kecepatannya. */
        private const val BETA = 4.0
        private const val DERIVATIVE_CUTOFF_HZ = 5.0
    }
}
