package com.israadev.nuxlauncher.core.device

import android.content.Context
import android.os.Build
import android.view.Display
import android.view.WindowManager
import com.israadev.nuxlauncher.core.models.LauncherSettings
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Profil performa khusus satu model HP.
 *
 * Profil HANYA aktif bila HP cocok persis dengan model yang terdaftar. Di HP lain
 * [DeviceProfiles.detect] mengembalikan null, jadi semua perilaku launcher tetap seperti biasa
 * (tidak ada flag JVM tambahan, tidak ada batas heap, layar tidak disentuh, kartu profil
 * tidak muncul di Pengaturan).
 *
 * Dasar spek Oppo A3x 4G (CPH2641): Snapdragon 6s 4G Gen 1 (SM6115, 11 nm; 4x Cortex-A73 +
 * 4x Cortex-A53), GPU Adreno 610, RAM 4 GB LPDDR4X, penyimpanan eMMC 5.1 (lambat), layar
 * 720x1604 90 Hz. Prosesornya lemah dan RAM-nya sempit, jadi yang dikejar: heap tidak boleh
 * membengkak (HP mulai menukar memori), jeda GC pendek, dan frame rate stabil 60 Hz.
 */
data class DeviceProfile(
    val id: String,
    val displayName: String,
    val specSummary: String,
    /** Batas atas heap Java (MB) di HP RAM kecil; 0 = tidak dibatasi. */
    val smallRamHeapCapMb: Int,
    /** RAM total (MB) di bawah angka ini dianggap RAM kecil (HP 4 GB melapor sekitar 3700 MB). */
    val smallRamThresholdMb: Int,
    /** Frekuensi layar yang dikunci selama game berjalan. */
    val refreshRateHz: Float
)

object DeviceProfiles {
    const val GC_BALANCED = "balanced"
    const val GC_SERIAL = "serial"
    const val GC_DEFAULT = "default"

    /** Mode GC HP tanpa profil: "auto" = bawaan launcher (G1 dengan generasi muda dibatasi di RAM kecil). */
    const val GC_AUTO = "auto"

    /** Layar 90/120 Hz dikunci ke frekuensi ini di HP tanpa profil bila pengguna menyalakannya. */
    const val GENERAL_LOCK_HZ = 60f

    /** Layar dianggap "tinggi" (layak punya saklar kunci 60 Hz) bila mendukung frekuensi sebesar ini. */
    private const val HIGH_REFRESH_MIN_HZ = 85f

    /** Dengan Serial dan heap sekecil ini (MB) atau kurang, code cache JIT dikecilkan (cara Battly). */
    private const val SERIAL_CODE_CACHE_HEAP_LIMIT_MB = 1280
    private const val SERIAL_CODE_CACHE_FLAG = "-XX:ReservedCodeCacheSize=48M"

    /** Heap awal minimum untuk mode Serial: Serial hanya membesarkan heap lewat Full GC yang mahal. */
    private const val SERIAL_MIN_INITIAL_MB = 768

    /** Heap awal lama (MB) yang tersimpan di settings.json; nilai sekecil ini dianggap "otomatis". */
    private const val LEGACY_INITIAL_HEAP_MB = 256

    /** Batas generasi muda G1 (persen heap): GC lebih jarang tanpa RAM yang membengkak. */
    private const val G1_MAX_YOUNG_PERCENT = 30

    /** HP dengan RAM total di bawah ini (MB) memakai batas generasi muda G1 walau tanpa profil. */
    private const val GENERAL_YOUNG_CAP_RAM_MB = 6000

    private val OPPO_A3X_4G = DeviceProfile(
        id = "oppo-a3x-4g",
        displayName = "Oppo A3x 4G",
        specSummary = "Snapdragon 6s 4G Gen 1, Adreno 610, RAM 4 GB, layar 90 Hz",
        smallRamHeapCapMb = 1536,
        smallRamThresholdMb = 5000,
        refreshRateHz = 60f
    )

    private val detected: DeviceProfile? by lazy { matchDevice() }

    /** Profil untuk HP ini, atau null bila HP ini tidak punya profil khusus. */
    fun detect(): DeviceProfile? = detected

    private fun matchDevice(): DeviceProfile? {
        return try {
            val maker = Build.MANUFACTURER.orEmpty().trim()
            val model = Build.MODEL.orEmpty().trim()
            val product = Build.PRODUCT.orEmpty().trim()
            val isOppo = maker.equals("OPPO", ignoreCase = true)
            val isA3x4g = model.startsWith("CPH2641", ignoreCase = true) ||
                product.startsWith("CPH2641", ignoreCase = true)
            if (isOppo && isA3x4g) OPPO_A3X_4G else null
        } catch (_: Throwable) {
            null
        }
    }

    /** Satu baris identitas HP untuk log, membantu bila profil tidak cocok dengan HP. */
    fun describeDevice(): String {
        return try {
            "${Build.MANUFACTURER} ${Build.MODEL} (product=${Build.PRODUCT}, board=${Build.BOARD})"
        } catch (_: Throwable) {
            "tidak diketahui"
        }
    }

    fun normalizeGcMode(mode: String?): String {
        return when (mode) {
            GC_SERIAL -> GC_SERIAL
            GC_DEFAULT -> GC_DEFAULT
            else -> GC_BALANCED
        }
    }

    /** True bila HP ini termasuk RAM kecil (di bawah sekitar 6 GB): pilihan mode GC umum ditawarkan. */
    fun isSmallRamPhone(totalRamMb: Int): Boolean = totalRamMb < GENERAL_YOUNG_CAP_RAM_MB

    fun normalizeGeneralGcMode(mode: String?): String {
        return when (mode) {
            GC_SERIAL -> GC_SERIAL
            GC_DEFAULT -> GC_DEFAULT
            else -> GC_AUTO
        }
    }

    /** True bila batas heap profil berlaku di HP ini (RAM kecil). */
    fun heapCapApplies(profile: DeviceProfile, totalRamMb: Int): Boolean {
        return profile.smallRamHeapCapMb > 0 && totalRamMb < profile.smallRamThresholdMb
    }

    /**
     * Heap (awal, maksimum) dalam MB yang dipakai game. Heap maksimum dibatasi di HP RAM kecil;
     * heap awal tidak pernah melebihi heap maksimum (kalau melebihi, JVM menolak berjalan).
     */
    fun resolveHeap(
        profile: DeviceProfile,
        requestedInitMb: Int,
        requestedMaxMb: Int,
        totalRamMb: Int,
        gcMode: String
    ): Pair<Int, Int> {
        var maxMb = requestedMaxMb
        if (heapCapApplies(profile, totalRamMb)) {
            maxMb = minOf(maxMb, profile.smallRamHeapCapMb)
        }
        var initMb = requestedInitMb
        if (gcMode == GC_SERIAL) {
            initMb = maxOf(initMb, minOf(SERIAL_MIN_INITIAL_MB, maxMb))
        } else if (gcMode == GC_BALANCED) {
            initMb = autoInitialHeapMb(initMb, maxMb)
        }
        initMb = minOf(initMb, maxMb)
        return Pair(initMb, maxMb)
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
     * Heap awal untuk HP tanpa profil. Java 8 memakai GC Parallel secara bawaan dan tidak
     * dibatasi generasi mudanya di sini, jadi nilainya dibiarkan seperti pengaturan.
     */
    fun generalInitialHeapMb(requestedInitMb: Int, maxMb: Int, isJava8: Boolean): Int {
        if (isJava8) return minOf(requestedInitMb, maxMb)
        return autoInitialHeapMb(requestedInitMb, maxMb)
    }

    /**
     * Flag JVM untuk HP tanpa profil. Mode "auto": batas generasi muda G1 di HP RAM kecil (kosong di
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

    /**
     * Flag JVM tambahan sesuai mode GC. Kosong bila mode Bawaan, atau bila pengguna sudah memilih
     * GC sendiri lewat argumen JVM kustom (dua GC sekaligus membuat JVM gagal start).
     * Semua flag valid di OpenJDK 8, 17, 21 dan 25.
     */
    fun jvmFlags(gcMode: String, userArgs: String): List<String> {
        val mode = normalizeGcMode(gcMode)
        if (mode == GC_DEFAULT) return emptyList()
        if (userSelectsCollector(userArgs)) return emptyList()
        return if (mode == GC_SERIAL) {
            serialFlags()
        } else {
            listOf(
                "-XX:+IgnoreUnrecognizedVMOptions",
                "-XX:+UseG1GC",
                "-XX:+UnlockExperimentalVMOptions",
                // Hanya batas atas generasi muda supaya RAM tidak membengkak. Flag target jeda dan
                // ukuran minimum generasi muda dari versi lama dilepas: di uji beban mirip game
                // keduanya membuat GC lebih sering tanpa jeda yang lebih pendek.
                "-XX:G1MaxNewSizePercent=$G1_MAX_YOUNG_PERCENT"
            )
        }
    }

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
     * Frekuensi layar yang diminta selama game, atau 0 bila layar tidak dikunci. HP berprofil
     * mengikuti setelan profilnya; HP lain mengikuti saklar [LauncherSettings.lockRefresh60].
     */
    fun refreshLockTarget(settings: LauncherSettings): Float {
        val profile = detect()
        if (profile != null) {
            return if (settings.deviceProfileEnabled && settings.deviceProfileLockRefresh) profile.refreshRateHz else 0f
        }
        return if (settings.lockRefresh60) GENERAL_LOCK_HZ else 0f
    }

    /** Frekuensi layar yang sedang aktif, mis. "60Hz"; kosong bila layar tidak dikunci launcher. */
    fun refreshRateLabel(context: Context, generalLock: Boolean = false): String {
        if (detect() == null && !generalLock) return ""
        val display = displayOf(context) ?: return ""
        return try {
            "${display.refreshRate.roundToInt()}Hz"
        } catch (_: Throwable) {
            ""
        }
    }
}
