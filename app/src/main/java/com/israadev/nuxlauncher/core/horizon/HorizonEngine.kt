package com.israadev.nuxlauncher.core.horizon

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import com.israadev.nuxlauncher.core.crash.LauncherLog
import com.israadev.nuxlauncher.core.models.Instance
import com.israadev.nuxlauncher.ui.i18n.Toast
import java.io.File

/**
 * Mesin Rust (Horizon): pengganti SELURUH client Java Minecraft, bukan loader mod seperti Fabric atau
 * Forge. Di jalur ini tidak ada JVM, client.jar, maupun library Java, dan mod Java tidak bisa dijalankannya.
 *
 * Cara kerja (tahap 1):
 * - Mesin berupa satu pustaka native (libhorizon_android.so) dari proyek Horizon Android (MIT). Pustaka itu
 *   dijalankan oleh [ACTIVITY_CLASS] di proses sendiri (:horizon). Proses terpisah itu penting: mesin
 *   memanggil process::exit saat selesai, dan itu tidak boleh ikut mematikan launcher.
 * - Pustaka dan data tabelnya TIDAK disimpan di repo. scripts/fetch-horizon-engine.sh menaruhnya saat build
 *   (lihat docs/rust-engine.md). Tanpa keduanya instance Rust tidak bisa dibuat: [availability] menolaknya.
 * - Sumber daya game (sekitar 500 MiB) diunduh dari Mojang oleh mesinnya sendiri saat pertama dibuka, lewat
 *   layar peluncur milik Horizon. Akun juga diatur di sana; belum dihubungkan ke akun launcher.
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

    /**
     * Membuka mesin Rust. Tidak ada JVM dan tidak ada sesi Microsoft dari launcher: peluncur milik Horizon
     * yang tampil dan mengurus unduhan sumber daya, akun, dan dunia. Mengembalikan false bila tidak terbuka.
     */
    fun launch(context: Context, instance: Instance): Boolean {
        val status = availability(context)
        if (status is Availability.Unavailable) {
            Toast.makeText(context, status.reason, Toast.LENGTH_LONG).show()
            return false
        }
        if (status is Availability.Ready && status.lowRam) {
            Toast.makeText(context, LOW_RAM_WARNING, Toast.LENGTH_LONG).show()
        }
        LauncherLog.event("Peluncuran", "Membuka mesin Rust (Horizon) untuk instance ${instance.name}")
        return try {
            context.startActivity(
                Intent()
                    .setClassName(context.packageName, ACTIVITY_CLASS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (e: Exception) {
            LauncherLog.e("Peluncuran", "Mesin Rust (Horizon) gagal dibuka", e)
            Toast.makeText(context, "Mesin Rust gagal dibuka: ${e.message}", Toast.LENGTH_LONG).show()
            false
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
