package com.israadev.nuxlauncher.core.horizon

import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Build
import com.israadev.nuxlauncher.BuildConfig
import com.israadev.nuxlauncher.core.account.AccountManager
import com.israadev.nuxlauncher.core.account.microsoft.MicrosoftAuthService
import com.israadev.nuxlauncher.core.controls.ControlLayoutManager
import com.israadev.nuxlauncher.core.crash.GameLogArchive
import com.israadev.nuxlauncher.core.crash.LauncherLog
import com.israadev.nuxlauncher.core.models.Instance
import com.israadev.nuxlauncher.core.models.UserAccount
import com.israadev.nuxlauncher.core.settings.SettingsManager
import com.israadev.nuxlauncher.ui.i18n.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Mesin Rust Amber: Starling (penerus Horizon Android, MIT), pengganti SELURUH client Java Minecraft. Slot "Horizon"
 * yang lama sudah dihapus; instance lama dengan loader "horizon" dijalankan mesin ini, dan dunianya disalin sekali
 * ([HorizonImport]).
 *
 * - Pustaka `libhorizon_android.so` dan kelas Java `io.github.juanluci.starling.GameActivity` (proses :starling)
 *   dibangun dari sumber resmi Starling oleh CI dan dipasang ke APK saat build (scripts/fetch-horizon-engine.sh).
 * - Folder data: files/starling.
 * - Akun diteruskan lewat antarmuka peluncur Starling: PLAY menulis accounts/accounts.json (pemain offline yang
 *   dipilih), [SESSION_FILE] (sesi Microsoft, dibaca sekali oleh mesin lalu dihapus), dan [ARGS_FILE]
 *   (horizon-args.txt). [PLAY_MARKER] membuat mesin langsung masuk game, tanpa layar peluncurnya sendiri.
 * - Unduhan berkas resmi Minecraft (sekitar 500 MiB) dikerjakan launcher ([StarlingFiles]); penyiapan game di HP
 *   sesudahnya (sekali saja) memakai layar milik Starling.
 */
object StarlingEngine {
    /** Nilai `Instance.loader` untuk instance yang dijalankan Starling. */
    const val LOADER_ID = "starling"

    /** Nama di rel loader layar Buat Instance. */
    const val DISPLAY_NAME = "Starling"

    /** Nama lengkap untuk label instance (kartu Home, daftar instance). */
    const val LABEL = "Starling (Rust)"

    /** Versi Minecraft yang dikerjakan Starling (sama dengan Horizon: dipatok di research-lock.json sumbernya). */
    const val SUPPORTED_MC_VERSION = "26.3"

    const val LOW_RAM_WARNING =
        "RAM HP ini terbatas untuk mesin Rust (disarankan 4 GB ke atas). Game bisa tertutup sendiri."

    /** Nilai `Instance.loader` milik slot Horizon yang sudah dihapus. */
    private const val LEGACY_LOADER_ID = "horizon"

    sealed interface Availability {
        /** Siap dijalankan. [lowRam] = RAM HP di bawah yang disarankan: tetap boleh, hanya diberi peringatan. */
        data class Ready(val lowRam: Boolean) : Availability

        /** Tidak bisa dijalankan di build atau HP ini; [reason] ditampilkan apa adanya ke pengguna. */
        data class Unavailable(val reason: String) : Availability
    }

    /**
     * Nama kelas WAJIB persis ini: fungsi JNI di pustaka mesin terikat pada nama paket dan kelasnya. Kelasnya ada di
     * sumber Starling, bukan di repo ini (diubah sedikit oleh third_party/horizon-patches/amber6).
     */
    const val ACTIVITY_CLASS = "io.github.juanluci.starling.GameActivity"

    // Kunci Intent yang dibaca GameActivity (third_party/horizon-patches/amber6).
    private const val EXTRA_DISCORD_RPC = "amber_discord_rpc"
    private const val EXTRA_MC_VERSION = "amber_mc_version"
    private const val EXTRA_INSTANCE_NAME = "amber_instance"

    private const val LIBRARY_FILE = "libhorizon_android.so"

    /** Penanda "tombol PLAY ditekan" yang dibaca mesin (ha_launcher): tanpanya mesin menampilkan peluncurnya sendiri. */
    private const val PLAY_MARKER = "launcher-play"
    private const val OPEN_MARKER = "launcher-open"

    /** Nama folder data mesin di filesDir; juga dipakai [HorizonMods] untuk mengenali mesin Starling dari folder datanya. */
    const val HOME_DIR = "starling"
    private const val MIN_SDK = 28

    /** Antarmuka peluncur mesin: argumen dan variabel lingkungan satu per baris, dibaca dari folder data mesin. */
    private const val ARGS_FILE = "horizon-args.txt"

    /** Sesi Microsoft untuk `--session-file`: dibaca sekali oleh mesin lalu dihapus; ditolak bila lebih tua dari dua menit. */
    private const val SESSION_FILE = "amber-session.json"

    private const val SESSION_REFRESH_TIMEOUT_MS = 12_000L
    private const val FAST_START_MAX_TOKEN_AGE_MS = 16L * 60L * 60L * 1000L
    private const val LOW_RAM_BYTES = 3_400L * 1024L * 1024L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val starting = AtomicBoolean(false)
    private val refreshing = AtomicBoolean(false)

    /** True bila [loader] (nilai `Instance.loader`) adalah mesin Rust, termasuk instance dari slot Horizon lama. */
    fun isEngine(loader: String?): Boolean {
        val id = loader?.trim() ?: return false
        return id.equals(LOADER_ID, ignoreCase = true) || id.equals(LEGACY_LOADER_ID, ignoreCase = true)
    }

    fun isRust(loader: String?): Boolean = isEngine(loader)

    /** Folder data mesin untuk instance dengan loader [loader]. Hanya ada satu mesin. */
    fun homeFor(context: Context, @Suppress("UNUSED_PARAMETER") loader: String?): File = engineHome(context)

    /** True bila pustaka Starling ikut terpasang di APK ini (hanya cek satu berkas, aman dari komposisi UI). */
    fun isBundled(context: Context): Boolean =
        File(context.applicationInfo.nativeLibraryDir, LIBRARY_FILE).isFile

    fun availability(context: Context): Availability {
        if (Build.VERSION.SDK_INT < MIN_SDK) {
            return Availability.Unavailable("Mesin Rust butuh Android 9 (API 28) ke atas.")
        }
        // APK 64-bit membawa mesin arm64; APK 32-bit membawa mesin armeabi-v7a (eksperimental).
        if (android.os.Process.is64Bit()) {
            if ("arm64-v8a" !in Build.SUPPORTED_64_BIT_ABIS) {
                return Availability.Unavailable("Mesin Rust hanya tersedia untuk HP 64-bit (arm64).")
            }
        } else if ("armeabi-v7a" !in Build.SUPPORTED_32_BIT_ABIS) {
            return Availability.Unavailable("Mesin Rust 32-bit hanya tersedia untuk HP ARM (armeabi-v7a).")
        }
        if (!isBundled(context)) {
            return Availability.Unavailable(
                "Mesin Rust belum disertakan di build ini. Cara menambahkannya ada di docs/rust-engine.md."
            )
        }
        return Availability.Ready(lowRam = totalRamBytes(context) < LOW_RAM_BYTES)
    }

    /** Folder data mesin: sama dengan getFilesDir() milik GameActivity di proses :starling. */
    internal fun engineHome(context: Context): File = File(context.filesDir, HOME_DIR)

    /**
     * True bila berkas game resmi sudah lengkap (diunduh launcher atau mesin; penandanya sama). Bila belum, launcher
     * mengunduhnya lewat tombol UNDUH ([StarlingFiles]); tanpa patokan, PLAY pertama menampilkan layar unduhan Starling.
     */
    fun gameInstalled(context: Context): Boolean =
        File(engineHome(context), "minecraft/starling-files.json").isFile

    /** Akhiran nama proses mesin (AndroidManifest: android:process=":starling"). */
    const val PROCESS_SUFFIX = ":starling"

    /** True bila proses mesin Starling sedang berjalan (di depan maupun di latar). */
    fun isProcessAlive(context: Context): Boolean = try {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val name = context.packageName + PROCESS_SUFFIX
        manager.runningAppProcesses.orEmpty().any { it.processName == name }
    } catch (_: Exception) {
        false
    }

    /**
     * Mencatat sesi Starling untuk Pengaturan > Log game ([GameLogArchive]): sesi sebelumnya (Java maupun Starling)
     * disimpan dulu, lalu sesi ini dicatat. Laporannya disusun dari berkas milik mesin (logs/latest.log, performance/,
     * horizon-graphics.txt) setelah sesi selesai. Dilewati bila mesin masih berjalan: PLAY lagi hanya membawanya ke
     * depan, sesinya tetap yang sama. Tidak pernah melempar.
     */
    private fun beginLogSession(context: Context, instance: Instance) {
        try {
            if (isProcessAlive(context)) return
            GameLogArchive.archiveIfNeeded(context)
            GameLogArchive.beginSession(
                context,
                System.currentTimeMillis(),
                instance.name,
                SUPPORTED_MC_VERSION,
                instance.loader,
                instance.loaderVersion,
                HorizonContent.gameHome(engineHome(context)).absolutePath,
                GameLogArchive.ENGINE_STARLING
            )
        } catch (e: Exception) {
            LauncherLog.w("StarlingEngine", "Sesi log Starling tidak dicatat: ${e.message}", e)
        }
    }

    /** Daftar mod native yang dikompilasi ke dalam pustaka mesin ini oleh build-nya (ditulis build_apk.assets). */
    private const val COMPILED_NATIVE_ASSET = "native-mods/compiled.txt"

    @Volatile
    private var compiledNative: Set<String>? = null

    /**
     * Id mod native yang BENAR-BENAR ada di dalam pustaka mesin yang terpasang di APK ini. Dibangun oleh
     * scripts/build-horizon-engine.sh (native_mods di horizon-engine.properties) dan dicatat di
     * `assets/native-mods/compiled.txt`. Pustaka tanpa mod menghasilkan himpunan kosong: modul bawaan yang tidak ada di pustaka tidak
     * boleh tampil sebagai saklar yang tidak berbuat apa-apa.
     */
    fun compiledNativeMods(context: Context): Set<String> {
        compiledNative?.let { return it }
        val ids = try {
            context.applicationContext.assets.open(COMPILED_NATIVE_ASSET).use { it.readBytes().toString(Charsets.UTF_8) }
                .split(Regex("\\s+"))
                .filter { Regex("[a-z0-9_]{1,64}").matches(it) }
                .toSet()
        } catch (_: IOException) {
            emptySet()
        }
        compiledNative = ids
        return ids
    }

    /**
     * Mesin milik Amber tidak memuat pustaka game dari server pembangun pihak ketiga (Compile di dalam game, sekarang
     * dimatikan oleh patch amber6): sisa pilihan dari mesin sebelumnya dibuang supaya yang jalan selalu pustaka bawaan
     * APK, yang memuat mod native hasil build Amber.
     */
    private fun dropForeignGameLibrary(context: Context) {
        try {
            File(engineHome(context), "native/current").delete()
        } catch (_: Exception) {
        }
    }

    /** Nama pemain offline untuk mesin: 1 sampai 16 huruf, angka, atau garis bawah (aturan akun offline Starling). */
    private fun validEngineName(name: String): Boolean =
        name.length in 1..16 && name.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' }

    /**
     * Membuka Starling dengan akun yang dipilih di launcher (offline atau Microsoft). Akun Microsoft yang token-nya
     * sudah lama diperbarui dulu, seperti GameLauncher. false bila permintaan ditolak; true bila peluncuran dimulai.
     */
    fun launch(context: Context, instance: Instance, account: UserAccount): Boolean {
        val status = availability(context)
        if (status is Availability.Unavailable) {
            Toast.makeText(context, status.reason, Toast.LENGTH_LONG).show()
            return false
        }
        val type = account.safeAccountType
        if (type != "offline" && type != "microsoft") {
            Toast.makeText(
                context,
                "Starling baru mendukung akun Offline dan Microsoft. Pilih salah satunya di tab Akun.",
                Toast.LENGTH_LONG
            ).show()
            return false
        }
        if (type == "offline" && !validEngineName(account.username)) {
            Toast.makeText(
                context,
                "Nama akun offline untuk Starling harus 1 sampai 16 huruf, angka, atau garis bawah. Buat akun offline baru di tab Akun.",
                Toast.LENGTH_LONG
            ).show()
            return false
        }
        if (type == "microsoft" && (account.safeAccessToken == "0" || account.uuid.isBlank())) {
            Toast.makeText(
                context,
                "Akun Microsoft ini belum login lengkap. Masuk lagi lewat tab Akun.",
                Toast.LENGTH_LONG
            ).show()
            return false
        }
        if (status is Availability.Ready && status.lowRam) {
            Toast.makeText(context, LOW_RAM_WARNING, Toast.LENGTH_LONG).show()
        }
        // Ketukan ganda pada tombol main tidak boleh memulai dua peluncuran sekaligus.
        if (!starting.compareAndSet(false, true)) {
            LauncherLog.event("Peluncuran", "Ketukan PLAY diabaikan: Starling sedang dibuka")
            return true
        }
        val appContext = context.applicationContext
        if (HorizonImport.needed(appContext)) {
            Toast.makeText(appContext, "Menyalin dunia dari mesin Horizon lama (sekali saja)...", Toast.LENGTH_LONG).show()
            scope.launch {
                withContext(Dispatchers.IO) { HorizonImport.run(appContext) }
                continueLaunch(appContext, instance, account)
            }
            return true
        }
        return continueLaunch(appContext, instance, account)
    }

    /** Lanjutan [launch]: memperbarui sesi Microsoft bila perlu, lalu membuka mesin. */
    private fun continueLaunch(appContext: Context, instance: Instance, account: UserAccount): Boolean {
        if (!MicrosoftAuthService.needsRefresh(account)) {
            return start(appContext, instance, account)
        }
        val tokenAge = account.tokenIssuedAt?.let { System.currentTimeMillis() - it }
        if (tokenAge != null && tokenAge in 0L..FAST_START_MAX_TOKEN_AGE_MS) {
            LauncherLog.event("Peluncuran", "Token Microsoft masih sah (umur ${tokenAge / 60_000} menit): Starling langsung dibuka, sesi diperbarui di latar")
            refreshInBackground(appContext, account)
            return start(appContext, instance, account)
        }
        Toast.makeText(appContext, "Memperbarui sesi Microsoft...", Toast.LENGTH_SHORT).show()
        scope.launch {
            var launchAccount = account
            try {
                val job = async(Dispatchers.IO) {
                    MicrosoftAuthService.refreshMicrosoftAccount(account).also { result ->
                        result.getOrNull()?.let { fresh -> AccountManager.updateAccount(appContext, fresh) }
                    }
                }
                val result = withTimeoutOrNull(SESSION_REFRESH_TIMEOUT_MS) { job.await() }
                val fresh = result?.getOrNull()
                if (fresh != null) {
                    launchAccount = fresh
                    LauncherLog.event("Peluncuran", "Sesi Microsoft diperbarui sebelum membuka Starling")
                } else {
                    LauncherLog.w("StarlingEngine", "Sesi Microsoft tidak diperbarui: ${result?.exceptionOrNull()?.message ?: "melebihi batas waktu"}", result?.exceptionOrNull())
                    Toast.makeText(
                        appContext,
                        "Sesi Microsoft belum bisa diperbarui. Game tetap dijalankan, tetapi fitur online mungkin butuh login ulang.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                LauncherLog.w("StarlingEngine", "Pembaruan sesi Microsoft gagal", e)
            }
            start(appContext, instance, launchAccount)
        }
        return true
    }

    /** Memperbarui sesi Microsoft tanpa membuat peluncuran menunggu; hasilnya dipakai peluncuran berikutnya. */
    private fun refreshInBackground(context: Context, account: UserAccount) {
        if (!refreshing.compareAndSet(false, true)) return
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    MicrosoftAuthService.refreshMicrosoftAccount(account).getOrNull()
                        ?.let { fresh -> AccountManager.updateAccount(context, fresh) }
                }
            } catch (e: Exception) {
                LauncherLog.w("StarlingEngine", "Pembaruan sesi Microsoft di latar gagal", e)
            } finally {
                refreshing.set(false)
            }
        }
    }

    /** Menulis berkas akun/argumen lalu membuka Starling. Selalu melepas penanda [starting]. */
    private fun start(context: Context, instance: Instance, account: UserAccount): Boolean {
        return try {
            val firstRun = !gameInstalled(context)
            dropForeignGameLibrary(context)
            StarlingSettingsSync.sync(context)
            // Kontrol Amber: tata letak Amber ditulis ke horizon-touch.json milik mesin, jadi mesin sendiri yang menggambar
            // dan memproses tombolnya (tanpa jendela Android tambahan di atas game). Dimatikan di Pengaturan Rust: berkas
            // mesin dikembalikan.
            RustTouchLayout.sync(
                context,
                amberControls(),
                ControlLayoutManager.presetButtons(context, instance.controlPresetId),
                engineDir = engineHome(context),
                // Pemutar makro Amber ada di mesin (patch amber6/0008).
                macros = true,
                // Tombol MENU Amber membuka sidebar Amber yang digambar mesin (patch amber6/0007), bukan Esc.
                amberMenu = amberControls()
            )
            // Warna UI mesin mengikuti aksen Amber.
            RustSettingsStore.syncTheme(context, SettingsManager.settings.value.accentColor)
            beginLogSession(context, instance)
            writeLaunchFiles(context, instance, account)
            if (firstRun) {
                Toast.makeText(
                    context,
                    "Pertama kali dibuka: layar Starling menyiapkan game (unduhan sekitar 500 MiB). Ikuti layarnya sampai selesai.",
                    Toast.LENGTH_LONG
                ).show()
            }
            LauncherLog.event(
                "Peluncuran",
                "Membuka Starling untuk instance ${instance.name}, akun=${account.safeAccountType}"
            )
            context.startActivity(
                Intent()
                    .setClassName(context.packageName, ACTIVITY_CLASS)
                    // Discord Rich Presence: pilihan pemain dititipkan ke proses :starling lewat Intent.
                    .putExtra(
                        EXTRA_DISCORD_RPC,
                        BuildConfig.HAS_DISCORD_SDK && SettingsManager.settings.value.discordRpcEnabled
                    )
                    .putExtra(EXTRA_MC_VERSION, SUPPORTED_MC_VERSION)
                    .putExtra(EXTRA_INSTANCE_NAME, instance.name)
                    // REORDER_TO_FRONT: bila mesin masih berjalan di latar, kembali ke sana, bukan membuka yang kedua.
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                // Tanpa animasi peralihan: layar mesin muncul seketika begitu siap.
                ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle()
            )
            true
        } catch (e: Exception) {
            LauncherLog.e("Peluncuran", "Starling gagal dibuka", e)
            Toast.makeText(context, "Starling gagal dibuka: ${e.message}", Toast.LENGTH_LONG).show()
            false
        } finally {
            starting.set(false)
        }
    }

    /**
     * Menyiapkan akun lewat antarmuka peluncur mesin (tanpa patch Rust):
     * - accounts/accounts.json: pemain offline yang dipilih (format Starling: accounts, selected, offline,
     *   offline_selected). Untuk akun Microsoft tidak ada pemain yang dipilih, jadi mesin tidak menimpa sesi dari berkas.
     * - [SESSION_FILE]: {"name","uuid","access_token","user_type":"msa","xuid"} untuk `--session-file`.
     * - [ARGS_FILE]: AMBER_EMBEDDED=1 dan `--session-file` bila perlu.
     * Semua ditulis ke berkas sementara lalu dipindah, dan hanya bisa dibaca aplikasi ini (mesin menolak berkas sesi
     * yang bisa dibaca pengguna lain).
     */
    /** Tombol layar Starling mengikuti tata letak Amber: pengaturan yang sama dengan "Kontrol Amber" mesin Rust (bawaan mati). */
    private fun amberControls(): Boolean = SettingsManager.settings.value.rustAmberControls

    private fun writeLaunchFiles(context: Context, instance: Instance, account: UserAccount) {
        val dir = engineHome(context)
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Folder data mesin tidak bisa dibuat")
        val microsoft = account.safeAccountType == "microsoft"
        writeAccountsFile(dir, if (microsoft) null else account.username)
        val session = File(dir, SESSION_FILE)
        val args = StringBuilder("AMBER_EMBEDDED=1\n")
        // Sidebar Amber di dalam mesin (patch amber6/0007): menggantikan tombol tiga garis dan menu cepat mesin. Menyala
        // bersama "Kontrol Amber". Nama instance dan versi tampil di bawah judul "Menu".
        if (amberControls()) {
            args.append("AMBER_SIDEBAR=1\n")
            args.append("AMBER_INSTANCE=").append(instance.name.replace('\n', ' ').replace('\r', ' ').trim()).append('\n')
            args.append("AMBER_MC_VERSION=").append(SUPPORTED_MC_VERSION).append('\n')
        }
        // "Rekam performa" di Pengaturan > Rust: patch amber6/0004 memulai perekaman di peluncuran (laporan di performance/).
        if (RustSettingsStore.readNow(context).recordPerformance) args.append("AMBER_PERF_RECORD=1\n")
        if (microsoft) {
            val json = JSONObject()
                .put("name", account.username)
                .put("uuid", account.uuid)
                .put("access_token", account.safeAccessToken)
                .put("user_type", "msa")
            account.xuid?.takeIf { it.isNotBlank() }?.let { json.put("xuid", it) }
            writePrivate(session, json.toString())
            args.append("--session-file\n").append(session.absolutePath).append('\n')
        } else {
            session.delete()
        }
        // "Langsung masuk server" di Pengaturan > Rust: mesin hanya menerimanya sebagai argumen.
        val server = RustSettingsStore.readNow(context).server.trim()
        if (server.isNotEmpty() && server.none { it.isWhitespace() || it.isISOControl() }) {
            args.append("--server\n").append(server).append('\n')
        }
        writePrivate(File(dir, ARGS_FILE), args.toString())
        File(dir, OPEN_MARKER).delete()
        File(dir, PLAY_MARKER).writeText("")
    }

    /** accounts/accounts.json milik mesin: memilih [offlineName] sebagai pemain, atau tidak memilih siapa pun (null). */
    private fun writeAccountsFile(home: File, offlineName: String?) {
        val folder = File(home, "accounts")
        if (!folder.isDirectory && !folder.mkdirs()) throw IOException("Folder akun mesin tidak bisa dibuat")
        val file = File(folder, "accounts.json")
        // Akun yang sudah ada (mis. dibuat di dalam mesin) dipertahankan; hanya pilihan pemain yang diubah.
        val json = try {
            JSONObject(file.readText())
        } catch (_: Exception) {
            JSONObject()
        }
        val offline = json.optJSONArray("offline") ?: JSONArray()
        if (offlineName != null && (0 until offline.length()).none { offline.optString(it) == offlineName }) {
            offline.put(offlineName)
        }
        if (!json.has("accounts")) json.put("accounts", JSONArray())
        json.put("offline", offline)
        json.put("selected", JSONObject.NULL)
        json.put("offline_selected", offlineName ?: JSONObject.NULL)
        writePrivate(file, json.toString())
    }

    /** Menulis [text] ke [target] lewat berkas sementara, dengan izin hanya untuk aplikasi ini. */
    private fun writePrivate(target: File, text: String) {
        val temp = File(target.parentFile, "${target.name}.tmp")
        temp.writeText(text)
        temp.setReadable(false, false)
        temp.setReadable(true, true)
        temp.setWritable(false, false)
        temp.setWritable(true, true)
        target.delete()
        if (!temp.renameTo(target)) {
            temp.delete()
            throw IOException("${target.name} tidak bisa disimpan")
        }
    }

    private fun totalRamBytes(context: Context): Long = try {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        ActivityManager.MemoryInfo().also { manager.getMemoryInfo(it) }.totalMem
    } catch (_: Throwable) {
        // Tidak terbaca: jangan memberi peringatan yang belum tentu benar
        Long.MAX_VALUE
    }
}
