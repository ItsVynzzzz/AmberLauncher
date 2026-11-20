package com.israadev.nuxlauncher.core.horizon

import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Build
import com.israadev.nuxlauncher.BuildConfig
import com.israadev.nuxlauncher.core.account.AccountManager
import com.israadev.nuxlauncher.core.account.microsoft.MicrosoftAuthService
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
 * Mesin Rust kedua: Starling (penerus Horizon Android, MIT), berdampingan dengan [HorizonEngine] yang tidak berubah.
 * Instance dengan loader [LOADER_ID] dijalankan mesin ini, bukan JVM dan bukan Horizon.
 *
 * Bagian yang sengaja terpisah dari Horizon, supaya Starling bebas diubah tanpa mengusik Horizon:
 * - Pustaka `libstarling_android.so` dan kelas Java `dev.horizon.android.GameActivity` (proses :starling) dibangun
 *   dari sumber Starling oleh CI dan dipasang ke APK saat build (scripts/fetch-horizon-engine.sh, AMBER_ENGINE=starling).
 * - Folder data sendiri: files/starling (Horizon memakai files/horizon).
 * - Akun diteruskan lewat antarmuka peluncur yang sudah ada di Starling, tanpa patch Rust: PLAY menulis
 *   accounts/accounts.json (pemain offline yang dipilih), [SESSION_FILE] (sesi Microsoft, dibaca sekali oleh mesin lalu
 *   dihapus), dan [ARGS_FILE] (horizon-args.txt: --session-file dan AMBER_EMBEDDED=1 yang mematikan pemberitahuan
 *   pembaruan Starling).
 * - Unduhan berkas resmi Minecraft (sekitar 500 MiB) dikerjakan launcher dengan dialog unduhan Amber ([StarlingFiles]),
 *   bukan layar Starling; mesin hanya melihat penanda "berkas lengkap" yang sama dengan buatannya sendiri. Penyiapan game
 *   di HP sesudahnya (Java 25, horizon-prepare; sekali saja) masih memakai layar milik Starling.
 *   Menu, kontrol, dan pengaturan di dalam game juga masih milik Starling; pengaturan Rust di tab Pengaturan Amber
 *   hanya untuk Horizon. Menyatukannya dengan UI Amber adalah tahap berikutnya.
 */
object StarlingEngine {
    /** Nilai `Instance.loader` untuk instance yang dijalankan Starling. */
    const val LOADER_ID = "starling"

    /** Nama di rel loader layar Buat Instance. */
    const val DISPLAY_NAME = "Starling"

    /** Nama lengkap untuk label instance (kartu Home, daftar instance). */
    const val LABEL = "Starling (Rust)"

    /** Versi Minecraft yang dikerjakan Starling (sama dengan Horizon: dipatok di research-lock.json sumbernya). */
    const val SUPPORTED_MC_VERSION = HorizonEngine.SUPPORTED_MC_VERSION

    /**
     * Nama kelas WAJIB persis ini: fungsi JNI di libstarling_android.so terikat pada nama paket dan kelasnya. Kelasnya
     * ada di sumber Starling (dev.horizon.android), bukan di repo ini; patch amber4 hanya mengubahnya sedikit.
     */
    const val ACTIVITY_CLASS = "dev.horizon.android.GameActivity"

    // Kunci Intent yang dibaca GameActivity (third_party/horizon-patches/amber4).
    private const val EXTRA_DISCORD_RPC = "amber_discord_rpc"
    private const val EXTRA_MC_VERSION = "amber_mc_version"

    private const val LIBRARY_FILE = "libstarling_android.so"

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

    /** True bila [loader] (nilai `Instance.loader`) adalah Starling. */
    fun isEngine(loader: String?): Boolean = loader?.trim().equals(LOADER_ID, ignoreCase = true)

    /** True bila pustaka Starling ikut terpasang di APK ini (hanya cek satu berkas, aman dari komposisi UI). */
    fun isBundled(context: Context): Boolean =
        File(context.applicationInfo.nativeLibraryDir, LIBRARY_FILE).isFile

    fun availability(context: Context): HorizonEngine.Availability {
        if (Build.VERSION.SDK_INT < MIN_SDK) {
            return HorizonEngine.Availability.Unavailable("Starling butuh Android 9 (API 28) ke atas.")
        }
        // Starling hanya dibangun untuk arm64.
        if (!android.os.Process.is64Bit() || "arm64-v8a" !in Build.SUPPORTED_64_BIT_ABIS) {
            return HorizonEngine.Availability.Unavailable("Starling hanya tersedia untuk HP 64-bit (arm64).")
        }
        if (!isBundled(context)) {
            return HorizonEngine.Availability.Unavailable(
                "Starling belum disertakan di build ini. Cara menambahkannya ada di docs/rust-engine.md."
            )
        }
        return HorizonEngine.Availability.Ready(lowRam = totalRamBytes(context) < LOW_RAM_BYTES)
    }

    /** Folder data mesin: sama dengan getFilesDir() milik GameActivity di proses :starling. */
    internal fun engineHome(context: Context): File = File(context.filesDir, HOME_DIR)

    /**
     * True bila berkas game resmi sudah lengkap (diunduh launcher atau mesin; penandanya sama). Bila belum, launcher
     * mengunduhnya lewat tombol UNDUH ([StarlingFiles]); tanpa patokan, PLAY pertama menampilkan layar unduhan Starling.
     */
    fun gameInstalled(context: Context): Boolean =
        File(engineHome(context), "minecraft/starling-files.json").isFile

    /** Nama pemain offline untuk mesin: 1 sampai 16 huruf, angka, atau garis bawah (aturan akun offline Starling). */
    private fun validEngineName(name: String): Boolean =
        name.length in 1..16 && name.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' }

    /**
     * Membuka Starling dengan akun yang dipilih di launcher (offline atau Microsoft). Akun Microsoft yang token-nya
     * sudah lama diperbarui dulu, seperti GameLauncher. false bila permintaan ditolak; true bila peluncuran dimulai.
     */
    fun launch(context: Context, instance: Instance, account: UserAccount): Boolean {
        val status = availability(context)
        if (status is HorizonEngine.Availability.Unavailable) {
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
        if (status is HorizonEngine.Availability.Ready && status.lowRam) {
            Toast.makeText(context, HorizonEngine.LOW_RAM_WARNING, Toast.LENGTH_LONG).show()
        }
        // Ketukan ganda pada tombol main tidak boleh memulai dua peluncuran sekaligus.
        if (!starting.compareAndSet(false, true)) {
            LauncherLog.event("Peluncuran", "Ketukan PLAY diabaikan: Starling sedang dibuka")
            return true
        }
        val appContext = context.applicationContext
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
            writeLaunchFiles(context, account)
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
    private fun writeLaunchFiles(context: Context, account: UserAccount) {
        val dir = engineHome(context)
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Folder data mesin tidak bisa dibuat")
        val microsoft = account.safeAccountType == "microsoft"
        writeAccountsFile(dir, if (microsoft) null else account.username)
        val session = File(dir, SESSION_FILE)
        val args = StringBuilder("AMBER_EMBEDDED=1\n")
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
        writePrivate(File(dir, ARGS_FILE), args.toString())
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
