package com.israadev.nuxlauncher.core.horizon

import android.content.Context
import com.israadev.nuxlauncher.core.crash.LauncherLog
import java.io.File
import org.json.JSONObject

/**
 * Menyatukan pengaturan Rust di Amber (Pengaturan > Rust) dengan mesin Starling.
 *
 * Tab itu semula hanya menulis `android-launcher.json` milik Horizon. Starling tidak membaca berkas itu: pengaturan
 * grafisnya ada di `horizon-graphics.txt` (satu baris `kunci:nilai`, sebelah options.txt; `graphics.rs` di sumbernya).
 * Saat PLAY, pilihan Amber yang punya padanan di Starling ditulis ke berkas itu ([desired]):
 *
 * | Amber | horizon-graphics.txt |
 * |---|---|
 * | Renderer | `graphicsApi` (kompatibel = auto, Vulkan cepat = vulkan, OpenGL ES = opengl) |
 * | Skala resolusi | `resolutionScale` |
 * | Batas pekerja chunk | `workers` |
 * | Hemat baterai | `batteryMode` |
 * | Matikan animasi game | `animatedTextures` (di Starling hanya tekstur beranimasi) |
 * | Pemangkasan daun | `leafCulling` (`solid` / `off`) |
 *
 * Pilihan lain di tab itu (arah layar, laju layar, immersive, poni, server, dan seterusnya) diatur oleh kelas Java
 * Horizon dan belum diteruskan ke Starling.
 *
 * Aturan menimpa: pemain juga bisa mengubah nilai yang sama dari menu di dalam game, jadi Amber tidak menimpa tiap PLAY.
 * Nilai terakhir yang Amber tulis dicatat di `amber-applied.json`; sebuah kunci hanya ditulis ulang bila pilihannya di
 * Amber berubah sejak itu. Pada PLAY pertama (belum ada catatan), hanya pilihan yang berbeda dari bawaan Amber yang
 * ditulis, jadi pemain yang tidak pernah menyentuh tab itu tidak kehilangan apa pun di Starling. Kunci lain di berkas
 * (preset, HDR, shader, dan seterusnya) tidak disentuh.
 */
object StarlingSettingsSync {

    private const val TAG = "StarlingSettings"
    const val GRAPHICS_FILE = "horizon-graphics.txt"
    private const val APPLIED_FILE = "amber-applied.json"
    private const val TEMP_SUFFIX = ".amber-tmp"

    /** Nilai horizon-graphics.txt yang diwakili pilihan Amber [s]. Urutan dan kuncinya tetap. */
    internal fun desired(s: RustSettings): Map<String, String> = linkedMapOf(
        "graphicsApi" to when (s.renderer) {
            "vulkan" -> "vulkan"
            "open-gl" -> "opengl"
            else -> "auto"
        },
        "resolutionScale" to s.renderScale.coerceIn(25, 100).toString(),
        "workers" to s.workerLimit.coerceIn(0, 64).toString(),
        "batteryMode" to (s.perfMode == RustSettingsStore.PERF_BATTERY).toString(),
        "animatedTextures" to (!s.gameAnimationsOff).toString(),
        "leafCulling" to (if (s.leafCulling) "solid" else "off")
    )

    /**
     * Kunci yang perlu ditulis: nilai di [wanted] yang berbeda dari yang terakhir ditulis Amber ([applied]), atau dari
     * bawaan Amber ([defaults]) bila belum pernah menulis ([applied] null atau tidak memuat kuncinya).
     */
    internal fun changes(
        wanted: Map<String, String>,
        defaults: Map<String, String>,
        applied: Map<String, String>?
    ): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        for ((key, value) in wanted) {
            val before = applied?.get(key) ?: defaults[key]
            if (value != before) result[key] = value
        }
        return result
    }

    /**
     * Teks horizon-graphics.txt dengan [changes] diterapkan: baris kunci yang sudah ada diganti nilainya (baris ganda
     * untuk kunci yang sama dibuang, mesin memakai yang terakhir), kunci yang belum ada ditambahkan di akhir. Baris
     * lain dibiarkan apa adanya. Mesin membaca barisnya dengan `kunci:nilai` (tanpa spasi di sekitar titik dua yang
     * dituliskan mesin sendiri).
     */
    internal fun merge(text: String, changes: Map<String, String>): String {
        if (changes.isEmpty()) return text
        val done = HashSet<String>()
        val out = ArrayList<String>()
        for (line in text.split("\n")) {
            val key = line.substringBefore(':', "").trim()
            val value = changes[key]
            if (line.contains(':') && value != null) {
                if (done.add(key)) out.add("$key:$value")
            } else {
                out.add(line)
            }
        }
        // Teks yang berakhir dengan baris baru menyisakan satu elemen kosong di ujung: tambahan masuk sebelum itu.
        var end = out.size
        if (end > 0 && out[end - 1].isEmpty()) end--
        val extra = changes.entries.filter { it.key !in done }.map { "${it.key}:${it.value}" }
        out.addAll(end, extra)
        val joined = out.joinToString("\n")
        return if (joined.endsWith("\n")) joined else "$joined\n"
    }

    /**
     * Menulis pilihan Amber ke folder game Starling sebelum mesin dibuka. Berjalan di thread pemanggil (mesin membaca
     * berkasnya begitu mulai). Tidak pernah melempar: gagal berarti Starling memakai pengaturannya sendiri.
     */
    fun sync(context: Context) {
        try {
            val app = context.applicationContext
            val dir = HorizonContent.gameHome(StarlingEngine.engineHome(app))
            if (!dir.isDirectory && !dir.mkdirs()) return
            val wanted = desired(RustSettingsStore.readNow(app))
            val appliedFile = File(dir, APPLIED_FILE)
            val toWrite = changes(wanted, desired(RustSettings()), readApplied(appliedFile))
            if (toWrite.isNotEmpty()) {
                val graphics = File(dir, GRAPHICS_FILE)
                val before = if (graphics.isFile) graphics.readText() else ""
                if (!replace(dir, graphics, merge(before, toWrite))) return
            }
            val record = JSONObject()
            for ((key, value) in wanted) record.put(key, value)
            replace(dir, appliedFile, record.toString(2))
        } catch (e: Exception) {
            LauncherLog.w(TAG, "Pengaturan Amber tidak diteruskan ke Starling: ${e.message}", e)
        }
    }

    private fun readApplied(file: File): Map<String, String>? {
        if (!file.isFile) return null
        return try {
            val json = JSONObject(file.readText())
            val map = HashMap<String, String>()
            for (key in json.keys()) map[key] = json.optString(key)
            map
        } catch (e: Exception) {
            // Catatan rusak: dianggap belum pernah menulis, jadi hanya yang bukan bawaan yang ditulis.
            LauncherLog.w(TAG, "$APPLIED_FILE rusak, dianggap kosong: ${e.message}")
            null
        }
    }

    /** Tulis ke berkas sementara lalu pindahkan, supaya mesin tidak pernah membaca separuh isi. */
    private fun replace(dir: File, target: File, text: String): Boolean {
        val temp = File(dir, target.name + TEMP_SUFFIX)
        return try {
            temp.writeText(text)
            if (temp.renameTo(target)) {
                true
            } else {
                if (target.exists()) target.delete()
                val moved = temp.renameTo(target)
                if (!moved) temp.delete()
                moved
            }
        } catch (e: Exception) {
            temp.delete()
            LauncherLog.w(TAG, "${target.name} gagal ditulis: ${e.message}", e)
            false
        }
    }
}
