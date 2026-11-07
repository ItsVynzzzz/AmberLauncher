package com.israadev.nuxlauncher.core.horizon

import android.content.Context
import com.israadev.nuxlauncher.core.crash.LauncherLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pengaturan khusus mesin Rust (Horizon). Pengaturan ini tidak sama dengan pengaturan jalur Java (Fabric, Forge,
 * dan kawan-kawan): tidak ada JVM, RAM Java, argumen JVM, atau driver Zink di sini.
 *
 * Semua nilainya disimpan di berkas config milik mesin sendiri (`android-launcher.json`, lihat [RustSettingsStore]),
 * dengan nama kunci dan nilai persis seperti yang dibaca mesin. Nilai yang tidak dikenal mesin membuat SELURUH config
 * jatuh ke bawaan, jadi setiap nilai di bawah ini hanya boleh salah satu dari yang tercantum.
 */
data class RustSettings(
    /** `vulkan-compatibility` (bawaan, paling aman), `vulkan` (batching GPU), `open-gl` (eksperimental). */
    val renderer: String = RustSettingsStore.RENDERER_COMPAT,
    /** Skala resolusi render, 25 sampai 100 persen. */
    val renderScale: Int = 100,
    /** Batas pekerja pembuat dan perakit chunk. 0 = tanpa batas tambahan. */
    val workerLimit: Int = 0,
    /** Anggaran cache RAM sekali pakai (MiB). 0 = bawaan mesin (80 MiB). */
    val cacheBudgetMib: Int = 0,
    /** Permintaan laju layar (Hz). 0 = bawaan sistem. */
    val displayHz: Int = 0,
    /** Petunjuk performa ke Android (hanya berguna di HP yang mendukungnya). */
    val performanceHints: Boolean = false,
    /** `auto`, `touch`, `keyboard-mouse`, atau `controller`. */
    val inputHost: String = "auto",
    /** `landscape`, `portrait`, `reverse-landscape`, `reverse-portrait`, atau `auto`. */
    val orientation: String = "landscape",
    /** Sembunyikan bilah navigasi dan status saat bermain. Bawaan mesin sejak 0.7.8: mati (laporan macet). */
    val immersive: Boolean = false,
    /** Hindari area poni/lubang kamera. */
    val avoidCutout: Boolean = false,
    /** `native`, `wide` (16:9), `classic` (4:3), atau `square` (1:1). */
    val aspect: String = "native",
    /** Alamat server (`host` atau `host:port`) yang langsung dimasuki saat PLAY. Kosong = dunia sendiri. */
    val server: String = ""
)

/**
 * Membaca dan menulis config mesin Rust, langsung di berkas yang dibaca mesin: `<folder data mesin>/android-launcher.json`.
 *
 * - Hanya kunci milik tab Rust yang diubah. Kunci lain di berkas itu (profil, nama, proxy, dan seterusnya, yang
 *   mungkin ditambah versi mesin yang lebih baru) dibiarkan apa adanya.
 * - Berkas tidak dibuat sampai pemain benar-benar mengubah sesuatu. Selama itu mesin memakai bawaannya.
 * - Penulisan satu per satu berurutan di satu utas latar (bukan di utas UI) dan atomik (tulis ke berkas sementara
 *   lalu dipindah), jadi mesin tidak pernah membaca separuh isi.
 * - Perubahan berlaku pada PLAY berikutnya; mesin membaca config saat dibuka.
 */
object RustSettingsStore {
    const val RENDERER_COMPAT = "vulkan-compatibility"

    private const val TAG = "RustSettings"
    private const val CONFIG_FILE = "android-launcher.json"
    private const val TEMP_FILE = "android-launcher.amber-tmp"

    val RENDERERS = setOf(RENDERER_COMPAT, "vulkan", "open-gl")
    val INPUT_HOSTS = setOf("auto", "touch", "keyboard-mouse", "controller")
    val ORIENTATIONS = setOf("landscape", "portrait", "reverse-landscape", "reverse-portrait", "auto")
    val ASPECTS = setOf("native", "wide", "classic", "square")

    /** Laju layar yang diterima mesin (DISPLAY_RATES di mesin); nilai lain dibuang mesin menjadi 0. */
    val DISPLAY_RATES = listOf(0, 30, 60, 90, 120, 144, 165)

    /** Pilihan cepat skala render (PRESETS di mesin). */
    val RENDER_SCALE_PRESETS = listOf(100, 85, 75, 67, 50, 25)

    /** Pilihan anggaran cache (MiB) seperti di layar peluncur Horizon. */
    val CACHE_BUDGETS = listOf(0, 16, 32, 64, 128, 256)

    const val MAX_WORKERS = 16
    private const val MAX_SERVER_CHARS = 260

    private val state = MutableStateFlow(RustSettings())
    val settings: StateFlow<RustSettings> = state.asStateFlow()

    // Satu utas untuk semua berkas: urutan tulis sama dengan urutan ketukan, dan UI tidak pernah menunggu disk.
    private val io = Executors.newSingleThreadExecutor { task ->
        Thread(task, "rust-settings").apply { isDaemon = true }
    }

    // Naik setiap ada perubahan dari pemain. Hasil baca yang mulai sebelum perubahan itu dibuang, supaya
    // pembacaan lama tidak menimpa pilihan yang baru saja diketuk.
    private val revision = AtomicInteger(0)
    private val lock = Any()

    /** Folder data mesin. Sama dengan [HorizonEngine.engineHome]; satu sumber agar tidak bisa berselisih. */
    private fun configFile(context: Context): File = File(HorizonEngine.engineHome(context), CONFIG_FILE)

    /** Membaca ulang dari berkas (dipanggil saat tab dibuka). Hasilnya masuk ke [settings]. */
    fun reload(context: Context) {
        val file = configFile(context.applicationContext)
        val startedAt = revision.get()
        io.execute {
            val loaded = try {
                parse(readJson(file))
            } catch (e: Exception) {
                LauncherLog.w(TAG, "Config mesin Rust tidak terbaca, memakai bawaan: ${e.message}", e)
                RustSettings()
            }
            synchronized(lock) {
                if (revision.get() == startedAt) state.value = loaded
            }
        }
    }

    /** Mengubah pengaturan: tampilan langsung berubah, berkas ditulis di latar. */
    fun update(context: Context, change: (RustSettings) -> RustSettings) {
        val file = configFile(context.applicationContext)
        val next = synchronized(lock) {
            val candidate = sanitize(change(state.value))
            // Slider yang digeser mengirim nilai sama berkali-kali; tidak perlu menulis ulang berkas.
            if (candidate == state.value) return
            revision.incrementAndGet()
            state.value = candidate
            candidate
        }
        io.execute { write(file, next, reset = false) }
    }

    /** Mengembalikan semua kunci milik tab Rust ke bawaan mesin (kuncinya dihapus dari berkas). */
    fun reset(context: Context) {
        val file = configFile(context.applicationContext)
        synchronized(lock) {
            revision.incrementAndGet()
            state.value = RustSettings()
        }
        io.execute { write(file, RustSettings(), reset = true) }
    }

    // ---- baca ----

    private fun readJson(file: File): JSONObject {
        if (!file.isFile) return JSONObject()
        return try {
            JSONObject(file.readText())
        } catch (e: Exception) {
            // Berkas rusak: mesin sendiri juga memperlakukannya sebagai kosong.
            LauncherLog.w(TAG, "android-launcher.json rusak, dianggap kosong: ${e.message}")
            JSONObject()
        }
    }

    private fun parse(json: JSONObject): RustSettings {
        val defaults = RustSettings()
        return sanitize(
            RustSettings(
                renderer = json.optString("renderer", defaults.renderer),
                renderScale = json.optInt("render_scale", defaults.renderScale),
                workerLimit = json.optInt("worker_limit", defaults.workerLimit),
                cacheBudgetMib = json.optInt("cache_budget_mib", defaults.cacheBudgetMib),
                displayHz = json.optInt("display_hz", defaults.displayHz),
                performanceHints = json.optBoolean("performance_hints", defaults.performanceHints),
                inputHost = json.optString("input_host", defaults.inputHost),
                orientation = json.optString("orientation", defaults.orientation),
                immersive = json.optBoolean("immersive", defaults.immersive),
                avoidCutout = json.optBoolean("avoid_cutout", defaults.avoidCutout),
                aspect = json.optString("aspect", defaults.aspect),
                server = if (json.isNull("server")) "" else json.optString("server", "")
            )
        )
    }

    /** Memaksa setiap nilai ke yang boleh dibaca mesin. Nilai asing kembali ke bawaan, angka dijepit. */
    private fun sanitize(s: RustSettings): RustSettings {
        val defaults = RustSettings()
        return s.copy(
            renderer = if (s.renderer in RENDERERS) s.renderer else defaults.renderer,
            renderScale = s.renderScale.coerceIn(25, 100),
            workerLimit = s.workerLimit.coerceIn(0, 64),
            cacheBudgetMib = s.cacheBudgetMib.coerceIn(0, 1024),
            displayHz = if (s.displayHz in DISPLAY_RATES) s.displayHz else 0,
            inputHost = if (s.inputHost in INPUT_HOSTS) s.inputHost else defaults.inputHost,
            orientation = if (s.orientation in ORIENTATIONS) s.orientation else defaults.orientation,
            aspect = if (s.aspect in ASPECTS) s.aspect else defaults.aspect,
            server = cleanServer(s.server)
        )
    }

    /** Alamat server tanpa spasi dan tanpa karakter kontrol; dibatasi panjangnya. Kosong tetap kosong. */
    fun cleanServer(raw: String): String =
        raw.filter { !it.isWhitespace() && it.code >= 32 }.take(MAX_SERVER_CHARS)

    // ---- tulis ----

    private fun write(file: File, s: RustSettings, reset: Boolean) {
        try {
            val dir = file.parentFile ?: return
            if (!dir.isDirectory && !dir.mkdirs()) {
                LauncherLog.w(TAG, "Folder data mesin tidak bisa dibuat: $dir")
                return
            }
            if (reset && !file.isFile) return // tidak ada yang perlu dihapus
            val json = readJson(file)
            if (reset) {
                for (key in OWN_KEYS) json.remove(key)
            } else {
                json.put("renderer", s.renderer)
                json.put("render_scale", s.renderScale)
                json.put("worker_limit", s.workerLimit)
                json.put("cache_budget_mib", s.cacheBudgetMib)
                json.put("display_hz", s.displayHz)
                json.put("performance_hints", s.performanceHints)
                json.put("input_host", s.inputHost)
                json.put("orientation", s.orientation)
                json.put("immersive", s.immersive)
                json.put("avoid_cutout", s.avoidCutout)
                json.put("aspect", s.aspect)
                json.put("server", s.server)
            }
            val temp = File(dir, TEMP_FILE)
            temp.writeText(json.toString(2))
            // rename(2) di Android menimpa berkas lama secara atomik, jadi mesin tidak pernah melihat berkas hilang.
            // Bila sebuah HP menolak menimpa, hapus yang lama lalu pindahkan.
            if (!temp.renameTo(file)) {
                if (file.exists()) file.delete()
                if (!temp.renameTo(file)) {
                    temp.delete()
                    LauncherLog.w(TAG, "Config mesin Rust tidak bisa disimpan: $file")
                }
            }
        } catch (e: Exception) {
            LauncherLog.w(TAG, "Config mesin Rust gagal ditulis: ${e.message}", e)
        }
    }

    private val OWN_KEYS = listOf(
        "renderer", "render_scale", "worker_limit", "cache_budget_mib", "display_hz", "performance_hints",
        "input_host", "orientation", "immersive", "avoid_cutout", "aspect", "server"
    )
}
