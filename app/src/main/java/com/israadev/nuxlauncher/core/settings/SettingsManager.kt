package com.israadev.nuxlauncher.core.settings

import android.app.ActivityManager
import android.content.Context
import com.google.gson.Gson
import com.israadev.nuxlauncher.core.models.LauncherSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object SettingsManager {
    /** Nomor gelombang penyederhanaan bawaan terbaru (lihat [applyPerfDefaults]). */
    private const val PERF_REV = 1

    private val gson = Gson()
    private val _settings = MutableStateFlow(LauncherSettings())
    val settings: StateFlow<LauncherSettings> = _settings.asStateFlow()

    private fun getSettingsFile(context: Context): File {
        return File(context.filesDir, "settings.json")
    }

    // --- Penulisan ke disk -------------------------------------------------------------------
    // Menyimpan = tulis berkas sementara + fsync + rename. Di penyimpanan HP kelas bawah itu bisa memakan
    // puluhan hingga ratusan milidetik, dan dulu dikerjakan di thread UI pada SETIAP langkah slider atau
    // ketukan keyboard, sehingga geser slider terasa tersendat. Di proses utama penulisan kini ditunda
    // sebentar dan digabung (hanya nilai terbaru yang ditulis) di thread latar; [flush] menuntaskannya.
    private const val WRITE_DELAY_MS = 300L
    private val writeLock = Any()
    private val writeScheduled = AtomicBoolean(false)
    @Volatile private var settingsFile: File? = null
    @Volatile private var initialized = false
    @Volatile private var deferWrites = false
    private var lastWrittenJson: String? = null

    private val writer: ScheduledThreadPoolExecutor by lazy {
        ScheduledThreadPoolExecutor(1) { r ->
            Thread(r, "settings-writer").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
        }.apply {
            setKeepAliveTime(5, TimeUnit.SECONDS)
            allowCoreThreadTimeOut(true)
        }
    }

    /**
     * Menyalakan penulisan tertunda untuk proses ini. Dipanggil hanya oleh proses utama (launcher); proses
     * game dan alur pemulihan tetap menulis langsung supaya tidak ada perubahan yang hilang saat proses ditutup.
     */
    fun enableDeferredWrites() {
        deferWrites = true
    }

    /** Menuntaskan penulisan yang masih tertunda. Murah bila tidak ada yang tertunda (tidak menyentuh disk). */
    fun flush() {
        if (!initialized) return
        writeNow()
    }

    fun init(context: Context) {
        // Init ulang (mis. dari alur pemulihan) membaca berkas lagi: tuntaskan dulu penulisan yang tertunda
        // supaya perubahan terakhir di memori tidak tertimpa isi berkas yang lebih lama.
        flush()
        val file = getSettingsFile(context)
        settingsFile = file
        // Isi berkas bisa saja diubah proses lain (mis. tombol HUD di dalam game): jangan lewati tulis berikutnya
        // hanya karena nilainya sama dengan yang dulu kita tulis.
        synchronized(writeLock) { lastWrittenJson = null }
        if (file.exists()) {
            try {
                val json = file.readText()
                val loaded = gson.fromJson(json, LauncherSettings::class.java)
                if (loaded != null) {
                    val migrated = applyPerfDefaults(loaded)
                    _settings.value = migrated
                    initialized = true
                    if (migrated !== loaded) save(context)
                    return
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        _settings.value = defaultSettingsFor(context)
        initialized = true
        save(context)
    }

    fun updateSettings(context: Context, newSettings: LauncherSettings) {
        _settings.value = newSettings
        save(context)
    }

    fun resetToDefaults(context: Context) {
        _settings.value = defaultSettingsFor(context)
        save(context)
    }

    /**
     * Penyederhanaan bawaan demi HP kelas bawah, diterapkan sekali per berkas pengaturan: animasi
     * latar Beranda (GIF/video) dan cahaya ambient dimatikan. Keduanya bisa dinyalakan lagi di
     * Pengaturan > Tampilan dan tidak akan dimatikan ulang, karena [LauncherSettings.perfRev] sudah naik.
     */
    private fun applyPerfDefaults(s: LauncherSettings): LauncherSettings {
        if (s.perfRev >= PERF_REV) return s
        return s.copy(homeBgAnimate = false, homeAmbientEnabled = false, perfRev = PERF_REV)
    }

    /**
     * Pengaturan awal untuk HP ini: RAM game dan skala resolusi menyesuaikan spek HP. Hanya dipakai
     * saat pertama kali dibuka dan saat reset; pengaturan yang sudah tersimpan tidak diubah, selain
     * penyederhanaan sekali jalan di [applyPerfDefaults].
     */
    private fun defaultSettingsFor(context: Context): LauncherSettings {
        val totalRamMb = getTotalDeviceMemoryMb(context)
        val minSidePx = try {
            val metrics = context.resources.displayMetrics
            minOf(metrics.widthPixels, metrics.heightPixels)
        } catch (_: Throwable) {
            0
        }
        return LauncherSettings(
            ramMb = recommendedRamMb(totalRamMb),
            resolutionRatio = recommendedResolutionRatio(totalRamMb, minSidePx),
            perfRev = PERF_REV
        )
    }

    /**
     * Skala resolusi game (persen) yang disarankan. Layar HP sekarang jauh lebih rapat daripada yang
     * sanggup digambar GPU kelas bawah dengan lancar, jadi sisi terpendek layar diturunkan ke sasaran
     * menurut RAM HP: di bawah 4 GB = 540 piksel, di bawah 6 GB = 720 piksel, selebihnya 1080 piksel.
     * Layar yang sudah sekecil itu tetap 100%. Hasilnya kelipatan 5 supaya cocok dengan slider.
     */
    fun recommendedResolutionRatio(totalRamMb: Int, minSidePx: Int): Int {
        if (minSidePx <= 0) return 100
        val targetSide = when {
            totalRamMb < 4096 -> 540
            totalRamMb < 6144 -> 720
            else -> 1080
        }
        if (minSidePx <= targetSide) return 100
        val ratio = 100.0 * targetSide / minSidePx
        val stepped = (Math.ceil(ratio / 5.0) * 5.0).toInt()
        return stepped.coerceIn(40, 100)
    }

    /**
     * RAM game (MB) yang disarankan untuk HP dengan total RAM [totalRamMb]. Dipakai saat pertama
     * kali dibuka dan saat reset, supaya hasilnya selalu sama. HP 4 GB (melapor sekitar 3700 MB)
     * tetap mendapat 1280 MB seperti sebelumnya; HP 3 GB mendapat 1024 MB dan HP 2 GB ke bawah 768 MB,
     * karena sisa RAM kosongnya kecil dan Android mudah mematikan game bila heap terlalu besar.
     */
    fun recommendedRamMb(totalRamMb: Int): Int = when {
        totalRamMb >= 12000 -> 4096
        totalRamMb >= 8000 -> 3072
        totalRamMb >= 6000 -> 2560
        totalRamMb >= 4000 -> 2048
        totalRamMb >= 3000 -> 1280
        totalRamMb >= 2500 -> 1024
        else -> 768
    }

    private fun save(context: Context) {
        if (settingsFile == null) settingsFile = getSettingsFile(context)
        if (!deferWrites) {
            writeNow()
            return
        }
        // Gabungkan: selama satu jadwal menunggu, perubahan berikutnya cukup memperbarui nilai di memori;
        // yang ditulis nanti selalu nilai terbaru.
        if (writeScheduled.compareAndSet(false, true)) {
            try {
                writer.schedule(Runnable {
                    writeScheduled.set(false)
                    writeNow()
                }, WRITE_DELAY_MS, TimeUnit.MILLISECONDS)
            } catch (e: Exception) {
                writeScheduled.set(false)
                writeNow()
            }
        }
    }

    /** Menulis nilai terbaru bila berbeda dari yang terakhir ditulis. Aman dipanggil dari thread mana pun. */
    private fun writeNow() {
        val file = settingsFile ?: return
        synchronized(writeLock) {
            try {
                val json = gson.toJson(_settings.value)
                if (json == lastWrittenJson) return
                com.israadev.nuxlauncher.core.utils.AtomicFiles.writeText(file, json)
                lastWrittenJson = json
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getTotalDeviceMemoryMb(context: Context): Int {
        return try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 4096
            val memInfo = ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            (memInfo.totalMem / (1024 * 1024)).toInt()
        } catch (_: Throwable) {
            4096
        }
    }

    fun getAvailableDeviceMemoryMb(context: Context): Int {
        return try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 2048
            val memInfo = ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            (memInfo.availMem / (1024 * 1024)).toInt()
        } catch (_: Throwable) {
            2048
        }
    }

    fun getCpuCoreCount(): Int {
        return Runtime.getRuntime().availableProcessors()
    }
}
