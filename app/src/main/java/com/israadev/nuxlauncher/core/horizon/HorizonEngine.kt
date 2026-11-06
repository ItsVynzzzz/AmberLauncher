package com.israadev.nuxlauncher.core.horizon

import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Build
import com.israadev.nuxlauncher.core.account.AccountManager
import com.israadev.nuxlauncher.core.account.microsoft.MicrosoftAuthService
import com.israadev.nuxlauncher.core.crash.LauncherLog
import com.israadev.nuxlauncher.core.models.Instance
import com.israadev.nuxlauncher.core.models.UserAccount
import com.israadev.nuxlauncher.ui.i18n.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Mesin Rust (Horizon): pengganti SELURUH client Java Minecraft, bukan loader mod seperti Fabric atau
 * Forge. Di jalur ini tidak ada JVM, client.jar, maupun library Java, dan mod Java tidak bisa dijalankannya.
 *
 * Cara kerja:
 * - Mesin berupa satu pustaka native (libhorizon_android.so) dari proyek Horizon Android (MIT). Pustaka itu
 *   dijalankan oleh [ACTIVITY_CLASS] di proses sendiri (:horizon). Proses terpisah itu penting: mesin
 *   memanggil process::exit saat selesai, dan itu tidak boleh ikut mematikan launcher.
 * - Pustaka dan data tabelnya TIDAK disimpan di repo. scripts/fetch-horizon-engine.sh menaruhnya saat build
 *   (lihat docs/rust-engine.md). Tanpa keduanya instance Rust tidak bisa dibuat: [availability] menolaknya.
 * - PLAY menulis [LAUNCH_FILE] (akun yang dipilih di launcher: offline atau Microsoft) ke folder data mesin lalu
 *   membuka [ACTIVITY_CLASS]. Mesin yang dikompilasi dengan perubahan Amber (patch_set di horizon-engine.properties)
 *   membaca berkas itu, memasang sumber daya game bila belum ada, langsung bermain dengan akun tadi, dan menutup
 *   dirinya saat game selesai sehingga pemain kembali ke launcher. Mesin tanpa perubahan itu mengabaikan berkasnya
 *   dan menampilkan layar peluncur Horizon seperti biasa.
 * - Sumber daya game (sekitar 500 MiB) diunduh dari Mojang oleh mesinnya sendiri saat pertama dibuka.
 */
object HorizonEngine {
    /** Nilai `Instance.loader` untuk instance yang dijalankan mesin Rust. */
    const val LOADER_ID = "horizon"

    /** Nama di rel loader layar Buat Instance. */
    const val DISPLAY_NAME = "Rust"

    /** Nama lengkap untuk label instance (kartu Home, daftar instance). */
    const val LABEL = "Rust (Horizon)"

    /** Satu-satunya versi Minecraft yang dikerjakan mesin ini (dipatok di research-lock.json milik Horizon). */
    const val SUPPORTED_MC_VERSION = "26.3"

    /**
     * Nama kelas WAJIB persis ini: fungsi JNI di libhorizon_android.so terikat pada nama paket dan kelasnya,
     * jadi kelasnya tidak boleh dipindah ke paket launcher.
     */
    const val ACTIVITY_CLASS = "dev.horizon.android.HorizonActivity"

    /** Peringatan RAM kecil; dipakai bersama oleh layar Buat Instance dan saat peluncuran. */
    const val LOW_RAM_WARNING =
        "RAM HP ini terbatas untuk mesin Rust (disarankan 4 GB ke atas). Game bisa tertutup sendiri."

    private const val LIBRARY_FILE = "libhorizon_android.so"
    private const val MIN_SDK = 28

    /** Berkas permintaan peluncuran di folder data mesin ([engineHome]); dibaca lalu dihapus oleh mesin. */
    private const val LAUNCH_FILE = "amber-launch.json"

    // Batas menunggu pembaruan sesi Microsoft sebelum game tetap dijalankan (sama dengan GameLauncher).
    private const val SESSION_REFRESH_TIMEOUT_MS = 12_000L

    // Token Minecraft berlaku 24 jam. Token yang umurnya di bawah batas ini tetap dipakai langsung dan diperbarui di
    // latar untuk peluncuran berikutnya, supaya menu tidak menunggu jaringan sampai 12 detik. Masih tersisa 8 jam
    // lebih untuk masuk server online.
    private const val FAST_START_MAX_TOKEN_AGE_MS = 16L * 60L * 60L * 1000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val starting = AtomicBoolean(false)
    private val refreshing = AtomicBoolean(false)

    // Android melaporkan RAM sedikit di bawah angka di kotak HP, jadi HP "4 GB" biasanya terbaca sekitar 3,6 GiB.
    private const val LOW_RAM_BYTES = 3_400L * 1024L * 1024L

    sealed interface Availability {
        /** Siap dijalankan. [lowRam] = RAM HP di bawah yang disarankan: tetap boleh, hanya diberi peringatan. */
        data class Ready(val lowRam: Boolean) : Availability

        /** Tidak bisa dijalankan di build atau HP ini; [reason] ditampilkan apa adanya ke pengguna. */
        data class Unavailable(val reason: String) : Availability
    }

    /** True bila [loader] (nilai `Instance.loader`) adalah mesin Rust. */
    fun isEngine(loader: String?): Boolean = loader?.trim().equals(LOADER_ID, ignoreCase = true)

    /**
     * True bila pustaka mesin ikut terpasang di APK ini. Memeriksa folder pustaka native aplikasi, yang
     * terisi karena build memakai useLegacyPackaging (lihat app/build.gradle.kts). Hanya cek satu berkas,
     * jadi aman dipanggil dari komposisi UI.
     */
    fun isBundled(context: Context): Boolean =
        File(context.applicationInfo.nativeLibraryDir, LIBRARY_FILE).isFile

    fun availability(context: Context): Availability {
        if (Build.VERSION.SDK_INT < MIN_SDK) {
            return Availability.Unavailable("Mesin Rust butuh Android 9 (API 28) ke atas.")
        }
        if ("arm64-v8a" !in Build.SUPPORTED_64_BIT_ABIS) {
            return Availability.Unavailable("Mesin Rust hanya tersedia untuk HP 64-bit (arm64).")
        }
        if (!isBundled(context)) {
            return Availability.Unavailable(
                "Mesin Rust belum disertakan di build ini. Cara menambahkannya ada di docs/rust-engine.md."
            )
        }
        return Availability.Ready(lowRam = totalRamBytes(context) < LOW_RAM_BYTES)
    }

    /** Folder data mesin: sama dengan getFilesDir() milik HorizonActivity di proses :horizon. */
    internal fun engineHome(context: Context): File = File(context.filesDir, "horizon")

    /**
     * True bila sumber daya game sudah terpasang di folder mesin. Bila belum, PLAY pertama mengunduh sekitar
     * 500 MiB lebih dulu. Hanya memeriksa satu folder, jadi aman dipanggil dari komposisi UI.
     */
    fun gameInstalled(context: Context): Boolean =
        File(engineHome(context), ".local/horizon-client/$SUPPORTED_MC_VERSION").isDirectory

    /** Nama pemain untuk mesin: 1 sampai 16 karakter ASCII yang tercetak, tanpa spasi (aturan Minecraft di mesin). */
    private fun validEngineName(name: String): Boolean =
        name.length in 1..16 && name.all { it.code in 33..126 }

    /**
     * Membuka mesin Rust dengan akun yang dipilih di launcher (offline atau Microsoft). Tidak ada JVM. Akun
     * Microsoft yang token-nya sudah lama diperbarui dulu, seperti GameLauncher. Mengembalikan false bila
     * permintaannya ditolak (mesin tidak ada, jenis akun belum didukung); true bila peluncuran dimulai.
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
                "Mesin Rust baru mendukung akun Offline dan Microsoft. Pilih salah satunya di tab Akun.",
                Toast.LENGTH_LONG
            ).show()
            return false
        }
        if (type == "offline" && !validEngineName(account.username)) {
            Toast.makeText(
                context,
                "Nama akun offline untuk mesin Rust harus 1 sampai 16 karakter tanpa spasi. Buat akun offline baru di tab Akun.",
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
            LauncherLog.event("Peluncuran", "Ketukan PLAY diabaikan: mesin Rust sedang dibuka")
            return true
        }
        val appContext = context.applicationContext
        if (!MicrosoftAuthService.needsRefresh(account)) {
            return start(appContext, instance, account)
        }
        val tokenAge = account.tokenIssuedAt?.let { System.currentTimeMillis() - it }
        if (tokenAge != null && tokenAge in 0L..FAST_START_MAX_TOKEN_AGE_MS) {
            LauncherLog.event("Peluncuran", "Token Microsoft masih sah (umur ${tokenAge / 60_000} menit): mesin Rust langsung dibuka, sesi diperbarui di latar")
            refreshInBackground(appContext, account)
            return start(appContext, instance, account)
        }
        Toast.makeText(appContext, "Memperbarui sesi Microsoft...", Toast.LENGTH_SHORT).show()
        scope.launch {
            var launchAccount = account
            try {
                // Hasil disimpan di dalam tugas ini, jadi tetap tersimpan walau selesai setelah batas waktu.
                val job = async(Dispatchers.IO) {
                    MicrosoftAuthService.refreshMicrosoftAccount(account).also { result ->
                        result.getOrNull()?.let { fresh -> AccountManager.updateAccount(appContext, fresh) }
                    }
                }
                val result = withTimeoutOrNull(SESSION_REFRESH_TIMEOUT_MS) { job.await() }
                val fresh = result?.getOrNull()
                if (fresh != null) {
                    launchAccount = fresh
                    LauncherLog.event("Peluncuran", "Sesi Microsoft diperbarui sebelum membuka mesin Rust")
                } else {
                    LauncherLog.w("HorizonEngine", "Sesi Microsoft tidak diperbarui: ${result?.exceptionOrNull()?.message ?: "melebihi batas waktu"}", result?.exceptionOrNull())
                    Toast.makeText(
                        appContext,
                        "Sesi Microsoft belum bisa diperbarui. Game tetap dijalankan, tetapi fitur online mungkin butuh login ulang.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                LauncherLog.w("HorizonEngine", "Pembaruan sesi Microsoft gagal", e)
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
                LauncherLog.w("HorizonEngine", "Pembaruan sesi Microsoft di latar gagal", e)
            } finally {
                refreshing.set(false)
            }
        }
    }

    /** Menulis permintaan peluncuran lalu membuka mesin. Selalu melepas penanda [starting]. */
    private fun start(context: Context, instance: Instance, account: UserAccount): Boolean {
        return try {
            val firstRun = !gameInstalled(context)
            writeLaunchFile(context, account)
            if (firstRun) {
                Toast.makeText(
                    context,
                    "Pertama kali dibuka: mesin mengunduh sumber daya game (sekitar 500 MiB). Biarkan sampai selesai, game langsung dimulai setelahnya.",
                    Toast.LENGTH_LONG
                ).show()
            }
            LauncherLog.event(
                "Peluncuran",
                "Membuka mesin Rust (Horizon) untuk instance ${instance.name}, akun=${account.safeAccountType}"
            )
            context.startActivity(
                Intent()
                    .setClassName(context.packageName, ACTIVITY_CLASS)
                    // REORDER_TO_FRONT: bila mesin masih berjalan di latar, kembali ke sana, bukan membuka yang kedua.
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                // Tanpa animasi peralihan: layar mesin muncul seketika begitu siap.
                ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle()
            )
            true
        } catch (e: Exception) {
            LauncherLog.e("Peluncuran", "Mesin Rust (Horizon) gagal dibuka", e)
            Toast.makeText(context, "Mesin Rust gagal dibuka: ${e.message}", Toast.LENGTH_LONG).show()
            false
        } finally {
            starting.set(false)
        }
    }

    /**
     * Berkas permintaan untuk mesin: {"version":1,"type":"offline|microsoft","name","uuid","access_token","xuid"}.
     * Ditulis ke berkas sementara lalu dipindah, supaya mesin tidak pernah membaca separuh isinya. Hanya bisa
     * dibaca aplikasi ini; mesin menghapusnya begitu dibaca dan menolaknya bila lebih tua dari dua menit.
     */
    private fun writeLaunchFile(context: Context, account: UserAccount) {
        val dir = engineHome(context)
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Folder data mesin tidak bisa dibuat")
        val microsoft = account.safeAccountType == "microsoft"
        val json = JSONObject()
            .put("version", 1)
            .put("type", if (microsoft) "microsoft" else "offline")
            .put("name", account.username)
        if (microsoft) {
            json.put("uuid", account.uuid)
            json.put("access_token", account.safeAccessToken)
            account.xuid?.takeIf { it.isNotBlank() }?.let { json.put("xuid", it) }
        }
        val target = File(dir, LAUNCH_FILE)
        val temp = File(dir, "$LAUNCH_FILE.tmp")
        temp.writeText(json.toString())
        temp.setReadable(false, false)
        temp.setReadable(true, true)
        temp.setWritable(false, false)
        temp.setWritable(true, true)
        target.delete()
        if (!temp.renameTo(target)) {
            temp.delete()
            throw IOException("Berkas peluncuran tidak bisa disimpan")
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
