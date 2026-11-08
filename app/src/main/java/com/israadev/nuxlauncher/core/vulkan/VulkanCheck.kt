package com.israadev.nuxlauncher.core.vulkan

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import com.israadev.nuxlauncher.core.crash.LauncherLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** Keadaan hasil pengecekan Vulkan. */
enum class VulkanState {
    /** Vulkan terbaca; versi, ekstensi, dan fiturnya ada di laporan. */
    OK,

    /** HP tidak punya driver Vulkan yang bisa dibuka. */
    NO_VULKAN,

    /** Pemeriksa Vulkan tidak ada di build ini (mis. build 32-bit). */
    UNAVAILABLE,

    /** Proses pemeriksa mati atau tidak menjawab: driver Vulkan HP kemungkinan bermasalah. */
    NO_RESPONSE
}

/** Laporan dukungan Vulkan HP ini. */
class VulkanReport(
    val state: VulkanState,
    val major: Int = 0,
    val minor: Int = 0,
    val patch: Int = 0,
    val extensions: Set<String> = emptySet(),
    val features: Map<String, Boolean> = emptyMap(),
    val detail: String = ""
) {
    val versionText: String get() = "$major.$minor.$patch"

    /** Vulkan 1.2 ke atas: syarat dasar Minecraft yang memakai Vulkan. */
    val meetsBaseline: Boolean get() = major > 1 || (major == 1 && minor >= 2)

    fun has(name: String, isFeature: Boolean): Boolean =
        if (isFeature) features[name] == true else name in extensions

    internal fun toJson(key: String): String {
        val json = JSONObject()
        json.put("format", VulkanCheck.FORMAT)
        json.put("key", key)
        json.put("state", state.name)
        json.put("major", major)
        json.put("minor", minor)
        json.put("patch", patch)
        json.put("extensions", JSONArray(extensions.toList()))
        val featureJson = JSONObject()
        for ((name, supported) in features) featureJson.put(name, supported)
        json.put("features", featureJson)
        json.put("detail", detail)
        return json.toString()
    }

    internal companion object {
        /** Null bila teksnya rusak, formatnya lama, atau bukan hasil untuk HP/versi Android ini. */
        fun fromJson(text: String, key: String): VulkanReport? = try {
            val json = JSONObject(text)
            if (json.optInt("format", -1) != VulkanCheck.FORMAT || json.optString("key") != key) {
                null
            } else {
                val ext = json.optJSONArray("extensions")
                val extSet = HashSet<String>()
                if (ext != null) for (i in 0 until ext.length()) extSet.add(ext.optString(i))
                val featureJson = json.optJSONObject("features")
                val featureMap = HashMap<String, Boolean>()
                if (featureJson != null) {
                    val names = featureJson.keys()
                    while (names.hasNext()) {
                        val name = names.next()
                        featureMap[name] = featureJson.optBoolean(name, false)
                    }
                }
                VulkanReport(
                    state = VulkanState.valueOf(json.getString("state")),
                    major = json.optInt("major"),
                    minor = json.optInt("minor"),
                    patch = json.optInt("patch"),
                    extensions = extSet,
                    features = featureMap,
                    detail = json.optString("detail")
                )
            }
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Pengecekan otomatis dukungan Vulkan HP, dipakai untuk memperingatkan sebelum pemain mengunduh Minecraft 26.x.
 *
 * Pengecekannya memuat driver Vulkan HP lewat pustaka native. Driver yang bermasalah bisa menutup prosesnya,
 * jadi pengecekan SELALU berjalan di proses `:vkcheck` sendiri ([VulkanCheckService]) dan launcher hanya membaca
 * berkas hasilnya. Hasil yang pasti (OK, tidak ada Vulkan, pemeriksa tidak ada) disimpan per HP dan versi
 * Android, jadi pengecekan hanya berjalan sekali. Proses yang mati atau macet tidak disimpan (diulang pada
 * peluncuran launcher berikutnya) tetapi dicatat selama sesi ini supaya tidak berulang terus.
 */
object VulkanCheck {
    /** Naikkan bila format berkas hasil atau cara menilainya berubah, supaya hasil lama dibuang. */
    internal const val FORMAT = 1

    internal const val DIR_NAME = "vulkan-check"
    internal const val RESULT_NAME = "result.json"

    /** Ada selama pengecekan berjalan: bila masih ada saat proses mulai lagi, percobaan sebelumnya mati. */
    internal const val MARKER_NAME = "check.running"

    private const val TAG = "Vulkan"
    private const val WAIT_MS = 15_000L
    private const val POLL_MS = 150L

    private val lock = Mutex()

    @Volatile
    private var sessionFailure: VulkanReport? = null

    internal fun dir(context: Context): File = File(context.filesDir, DIR_NAME)

    /** Hasil hanya berlaku untuk HP dan versi Android yang sama. */
    internal fun deviceKey(): String = "$FORMAT|${Build.FINGERPRINT}|${Build.VERSION.SDK_INT}"

    /** Hasil tersimpan yang masih berlaku, tanpa memulai pengecekan. */
    fun cached(context: Context): VulkanReport? = read(File(dir(context), RESULT_NAME))

    /**
     * Hasil tersimpan, atau menjalankan pengecekan di proses terpisah dan menunggunya (paling lama sekitar 15
     * detik). Aman dipanggil dari mana saja; panggilan bersamaan menunggu pengecekan yang sama.
     */
    suspend fun ensure(context: Context): VulkanReport = lock.withLock {
        cached(context) ?: sessionFailure ?: withContext(Dispatchers.IO) {
            runIsolated(context.applicationContext)
        }
    }

    private fun read(file: File): VulkanReport? = try {
        if (file.isFile) VulkanReport.fromJson(file.readText(), deviceKey()) else null
    } catch (_: Exception) {
        null
    }

    private suspend fun runIsolated(app: Context): VulkanReport {
        val dir = dir(app)
        val resultFile = File(dir, RESULT_NAME)
        try {
            dir.mkdirs()
            resultFile.delete()
            // Percobaan baru: marker dari percobaan yang mati sebelumnya tidak berlaku lagi.
            File(dir, MARKER_NAME).delete()
        } catch (_: Exception) {
        }

        val gone = AtomicBoolean(false)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {}
            override fun onServiceDisconnected(name: ComponentName?) {
                gone.set(true)
            }

            override fun onBindingDied(name: ComponentName?) {
                gone.set(true)
            }
        }
        val bound = try {
            app.bindService(Intent(app, VulkanCheckService::class.java), connection, Context.BIND_AUTO_CREATE)
        } catch (e: Throwable) {
            LauncherLog.w(TAG, "Proses pemeriksa Vulkan tidak bisa dimulai: ${e.message}")
            false
        }
        if (!bound) return fail("proses pemeriksa tidak bisa dimulai")

        try {
            val deadline = SystemClock.elapsedRealtime() + WAIT_MS
            while (true) {
                read(resultFile)?.let { return it.also { r -> logResult(r) } }
                if (gone.get()) {
                    // Prosesnya sudah tidak ada: lihat berkas hasil sekali lagi (ditulis sebelum ia mati).
                    delay(200)
                    read(resultFile)?.let { return it.also { r -> logResult(r) } }
                    return fail("proses pemeriksa berhenti sebelum selesai")
                }
                if (SystemClock.elapsedRealtime() >= deadline) return fail("pemeriksa tidak menjawab dalam ${WAIT_MS / 1000} detik")
                delay(POLL_MS)
            }
        } finally {
            try {
                app.unbindService(connection)
            } catch (_: Throwable) {
            }
        }
    }

    private fun fail(why: String): VulkanReport {
        LauncherLog.w(TAG, "Pengecekan Vulkan tidak selesai: $why")
        return VulkanReport(VulkanState.NO_RESPONSE, detail = why).also { sessionFailure = it }
    }

    private fun logResult(report: VulkanReport) {
        val summary = when (report.state) {
            VulkanState.OK -> "Vulkan ${report.versionText}, ${report.extensions.size} ekstensi"
            VulkanState.NO_VULKAN -> "HP tidak punya driver Vulkan yang bisa dibuka"
            VulkanState.UNAVAILABLE -> "pemeriksa tidak ada di build ini (${report.detail})"
            VulkanState.NO_RESPONSE -> "tidak ada jawaban"
        }
        LauncherLog.event(TAG, "Pengecekan Vulkan: $summary")
    }
}
