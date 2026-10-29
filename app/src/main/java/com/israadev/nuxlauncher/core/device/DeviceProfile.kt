package com.israadev.nuxlauncher.core.device

import android.content.Context
import android.os.Build
import android.view.Display
import android.view.WindowManager
import com.israadev.nuxlauncher.core.models.LauncherSettings
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Setelan performa yang diturunkan dari kemampuan HP (RAM dan layar), BUKAN dari nama model.
 *
 * Tidak ada lagi profil khusus per model HP: semua HP lewat jalur yang sama, jadi perilaku bawaan
 * launcher terlihat apa adanya di HP mana pun, termasuk HP pengembang.
 */
object DeviceProfiles {
    const val GC_SERIAL = "serial"
    const val GC_DEFAULT = "default"

    /** Mode GC bawaan: "auto" = bawaan launcher (G1 dengan generasi muda dibatasi di RAM kecil). */
    const val GC_AUTO = "auto"

    /** Layar 90/120 Hz dikunci ke frekuensi ini bila pengguna menyalakan "Kunci layar 60 Hz". */
    const val GENERAL_LOCK_HZ = 60f

    /** Layar dianggap "tinggi" (layak punya saklar kunci 60 Hz) bila mendukung frekuensi sebesar ini. */
    private const val HIGH_REFRESH_MIN_HZ = 85f

    /** Dengan Serial dan heap sekecil ini (MB) atau kurang, code cache JIT dikecilkan (cara Battly). */
    private const val SERIAL_CODE_CACHE_HEAP_LIMIT_MB = 1280
    private const val SERIAL_CODE_CACHE_FLAG = "-XX:ReservedCodeCacheSize=48M"

    /** Heap awal lama (MB) yang tersimpan di settings.json; nilai sekecil ini dianggap "otomatis". */
    private const val LEGACY_INITIAL_HEAP_MB = 256

    /** Batas generasi muda G1 (persen heap): GC lebih jarang tanpa RAM yang membengkak. */
    private const val G1_MAX_YOUNG_PERCENT = 30

    /** HP dengan RAM total di bawah ini (MB) memakai batas generasi muda G1. */
    private const val GENERAL_YOUNG_CAP_RAM_MB = 6000

    /** Satu baris identitas HP untuk log, membantu membaca laporan dari HP yang berbeda. */
    fun describeDevice(): String {
        return try {
            "${Build.MANUFACTURER} ${Build.MODEL} (product=${Build.PRODUCT}, board=${Build.BOARD})"
        } catch (_: Throwable) {
            "tidak diketahui"
        }
    }

    /** True bila HP ini termasuk RAM kecil (di bawah sekitar 6 GB): pilihan mode GC ditawarkan. */
    fun isSmallRamPhone(totalRamMb: Int): Boolean = totalRamMb < GENERAL_YOUNG_CAP_RAM_MB

    fun normalizeGeneralGcMode(mode: String?): String {
        return when (mode) {
            GC_SERIAL -> GC_SERIAL
            GC_DEFAULT -> GC_DEFAULT
            else -> GC_AUTO
        }
    }

    /**
     * Heap awal otomatis. Heap awal kecil membuat G1 memulai dengan generasi muda mungil dan
     * mengumpulkan sampah berkali-kali sampai heap membesar pelan-pelan. Bila nilai tersimpan
     * masih bawaan lama (256 MB), heap awal disamakan dengan heap maksimum.
     */
    fun autoInitialHeapMb(requestedInitMb: Int, maxMb: Int): Int {
        return if (requestedInitMb <= LEGACY_INITIAL_HEAP_MB) maxMb else minOf(requestedInitMb, maxMb)
    }

    /**
     * Heap awal yang dipakai game. Java 8 memakai GC Parallel secara bawaan dan tidak
     * dibatasi generasi mudanya di sini, jadi nilainya dibiarkan seperti pengaturan.
     */
    fun generalInitialHeapMb(requestedInitMb: Int, maxMb: Int, isJava8: Boolean): Int {
        if (isJava8) return minOf(requestedInitMb, maxMb)
        return autoInitialHeapMb(requestedInitMb, maxMb)
    }

    /**
     * Flag GC JVM. Mode "auto": batas generasi muda G1 di HP RAM kecil (kosong di
     * Java 8 dan di HP RAM besar). Mode "serial": GC Serial, hanya bila HP berRAM kecil, dan boleh
     * juga di Java 8. Mode "default": tanpa flag GC dari launcher. Selalu kosong bila pengguna sudah
     * memilih GC sendiri lewat argumen JVM kustom (dua GC sekaligus membuat JVM gagal start).
     */
    fun generalJvmFlags(totalRamMb: Int, isJava8: Boolean, userArgs: String, gcMode: String = GC_AUTO): List<String> {
        if (userSelectsCollector(userArgs)) return emptyList()
        return when (normalizeGeneralGcMode(gcMode)) {
            GC_DEFAULT -> emptyList()
            GC_SERIAL -> {
                if (totalRamMb >= GENERAL_YOUNG_CAP_RAM_MB) emptyList() else serialFlags()
            }
            else -> {
                if (isJava8) return emptyList()
                if (totalRamMb >= GENERAL_YOUNG_CAP_RAM_MB) return emptyList()
                listOf(
                    "-XX:+IgnoreUnrecognizedVMOptions",
                    "-XX:+UnlockExperimentalVMOptions",
                    "-XX:G1MaxNewSizePercent=$G1_MAX_YOUNG_PERCENT"
                )
            }
        }
    }

    private fun serialFlags(): List<String> = listOf(
        // Jaring pengaman: flag yang tidak dikenal runtime Java ini diabaikan, tidak membuat game gagal start.
        "-XX:+IgnoreUnrecognizedVMOptions",
        // Tanpa thread GC latar dan tanpa barrier G1: paling hemat CPU di prosesor lemah.
        "-XX:+UseSerialGC",
        // Generasi muda 25% dari heap: jeda GC pendek dan memori terbatas.
        "-XX:NewRatio=3"
    )

    /**
     * Code cache JIT 48 MB untuk mode Serial dengan heap kecil (cara Battly): memori yang dicadangkan
     * lebih sedikit. Tidak dipasang bila heap lebih besar (mod banyak butuh code cache lebih luas) atau
     * bila pengguna sudah mengatur code cache sendiri.
     */
    fun serialCodeCacheFlags(serialActive: Boolean, heapMaxMb: Int, userArgs: String): List<String> {
        if (!serialActive) return emptyList()
        if (heapMaxMb > SERIAL_CODE_CACHE_HEAP_LIMIT_MB) return emptyList()
        if (userArgs.contains("ReservedCodeCacheSize")) return emptyList()
        return listOf(SERIAL_CODE_CACHE_FLAG)
    }

    /** True bila flag GC yang akan dipasang memilih Serial (dipakai untuk memutuskan code cache). */
    fun flagsSelectSerial(flags: List<String>): Boolean = flags.contains("-XX:+UseSerialGC")

    private fun userSelectsCollector(userArgs: String): Boolean {
        if (userArgs.isBlank()) return false
        return userArgs.split(" ").any { raw ->
            val token = raw.trim()
            token.startsWith("-XX:+Use") && token.endsWith("GC")
        }
    }

    @Suppress("DEPRECATION")
    private fun displayOf(context: Context): Display? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.display
            } else {
                (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay
            }
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * ID mode layar dengan resolusi sekarang dan frekuensi paling dekat [targetHz]
     * (selisih kurang dari 1 Hz), atau 0 bila tidak ada.
     */
    fun findDisplayModeId(context: Context, targetHz: Float): Int {
        val display = displayOf(context) ?: return 0
        return try {
            val current = display.mode
            var bestId = 0
            var bestDiff = Float.MAX_VALUE
            for (mode in display.supportedModes) {
                if (mode.physicalWidth != current.physicalWidth || mode.physicalHeight != current.physicalHeight) continue
                val diff = abs(mode.refreshRate - targetHz)
                if (diff < 1.0f && diff < bestDiff) {
                    bestId = mode.modeId
                    bestDiff = diff
                }
            }
            bestId
        } catch (_: Throwable) {
            0
        }
    }

    /** True bila layar HP ini mendukung 85 Hz ke atas (90/120 Hz), jadi kunci 60 Hz ada gunanya. */
    fun supportsHighRefresh(context: Context): Boolean {
        val display = displayOf(context) ?: return false
        return try {
            display.supportedModes.any { it.refreshRate >= HIGH_REFRESH_MIN_HZ }
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Frekuensi layar yang diminta selama game, atau 0 bila layar tidak dikunci.
     * Hanya mengikuti saklar [LauncherSettings.lockRefresh60].
     */
    fun refreshLockTarget(settings: LauncherSettings): Float {
        return if (settings.lockRefresh60) GENERAL_LOCK_HZ else 0f
    }

    /** Frekuensi layar yang sedang aktif, mis. "60Hz"; kosong bila layar tidak dikunci launcher. */
    fun refreshRateLabel(context: Context, generalLock: Boolean = false): String {
        if (!generalLock) return ""
        val display = displayOf(context) ?: return ""
        return try {
            "${display.refreshRate.roundToInt()}Hz"
        } catch (_: Throwable) {
            ""
        }
    }
}
