package com.israadev.nuxlauncher.core.plugin

import android.content.pm.ApplicationInfo
import com.israadev.nuxlauncher.core.crash.LauncherLog as Log
import com.israadev.nuxlauncher.core.utils.NuxVersionUtils
import java.io.File

/**
 * Plugin native library: APK terpisah yang hanya membawa pustaka .so (mis. sqlite/zstd versi bionic) untuk
 * mod yang pustaka bawaannya dibangun untuk glibc dan tidak bisa dimuat di Android (Distant Horizons dkk).
 * Formatnya sama dengan plugin native Zalith/FCL, jadi APK plugin yang sudah ada langsung dikenali.
 *
 * Penanda di AndroidManifest (meta-data):
 *  - `FCLNativePlugin` = true (wajib)
 *  - `environment`     = argumen JVM dipisah spasi, mis. `-Dorg.sqlite.lib.path={nativeLibraryDir}`
 *  - `des`             = nama tampilan
 *  - `minMCVer` / `maxMCVer` = rentang versi Minecraft yang didukung (opsional)
 *
 * Soal beban: plugin dikenali oleh [com.israadev.nuxlauncher.core.renderer.NuxRendererPluginManager] di dalam
 * putaran pemindaian aplikasi yang SUDAH ada (satu kali ambil daftar aplikasi, tanpa panggilan IPC tambahan).
 * Bila tidak ada plugin terpasang, [planFor] mengembalikan rencana kosong: tidak ada path, argumen, atau
 * pemuatan pustaka yang ditambahkan, dan tidak ada layanan atau thread latar baru.
 */
object NativePluginManager {
    private const val TAG = "NativePlugin"
    private const val META_FLAG = "FCLNativePlugin"

    data class NativePlugin(
        val packageName: String,
        val displayName: String,
        val nativeLibDir: String,
        val minMcVersion: String?,
        val maxMcVersion: String?,
        /** Argumen JVM yang sudah dibersihkan (hanya `-D`, tanpa kunci terlindungi, placeholder sudah diganti). */
        val jvmArgs: List<String>
    )

    /** Hasil keputusan untuk satu kali peluncuran. */
    class LaunchPlan(
        val active: List<NativePlugin>,
        /** Folder pustaka yang ditambahkan ke library path, tanpa duplikat. */
        val libPaths: List<String>,
        /** Argumen JVM tambahan, tanpa duplikat. */
        val jvmArgs: List<String>,
        /** Keterangan plugin yang terpasang tetapi tidak dipakai kali ini (untuk log). */
        val skipped: List<String>
    ) {
        val isEmpty: Boolean get() = active.isEmpty()
    }

    @Volatile
    private var plugins: List<NativePlugin> = emptyList()

    fun getPlugins(): List<NativePlugin> = plugins

    /** Dipanggil pemindai aplikasi di akhir tiap pemindaian. Mengganti seluruh daftar. */
    fun setPlugins(list: List<NativePlugin>) {
        plugins = list.sortedBy { it.displayName.lowercase() }
    }

    /**
     * Mengenali plugin dari satu aplikasi. Murah: aplikasi tanpa metadata (hampir semuanya) langsung selesai.
     * [label] hanya dipanggil bila plugin tidak punya `des`, karena membaca label membuka resource aplikasi.
     */
    fun parse(info: ApplicationInfo, label: () -> String): NativePlugin? {
        // Seperti plugin renderer: hanya APK yang dipasang pengguna dan menandai dirinya lewat metadata.
        if ((info.flags and ApplicationInfo.FLAG_SYSTEM) != 0) return null
        val meta = info.metaData ?: return null
        if (!meta.getBoolean(META_FLAG, false)) return null

        val libDir = info.nativeLibraryDir?.takeIf { it.isNotBlank() } ?: return null
        val pkg = info.packageName
        val name = meta.getString("des")?.takeIf { it.isNotBlank() } ?: label()

        @Suppress("DEPRECATION")
        fun versionOf(key: String): String? {
            if (!meta.containsKey(key)) return null
            val v = when (val raw = meta.get(key)) {
                is String -> raw
                is Number -> raw.toString()
                else -> null
            }
            return v?.trim()?.takeIf { it.isNotEmpty() }
        }

        return NativePlugin(
            packageName = pkg,
            displayName = name,
            nativeLibDir = libDir,
            minMcVersion = versionOf("minMCVer"),
            maxMcVersion = versionOf("maxMCVer"),
            jvmArgs = parseArgs(meta.getString("environment").orEmpty(), libDir, pkg)
        )
    }

    /**
     * Memutuskan plugin mana yang dipakai untuk [mcVersion]. Plugin yang dimatikan pengguna, di luar rentang
     * versi, atau foldernya tidak ada dilewati (dicatat di [LaunchPlan.skipped]) tanpa menghentikan peluncuran.
     */
    fun planFor(mcVersion: String, disabled: Collection<String>): LaunchPlan {
        val all = plugins
        if (all.isEmpty()) return LaunchPlan(emptyList(), emptyList(), emptyList(), emptyList())

        val active = ArrayList<NativePlugin>(all.size)
        val skipped = ArrayList<String>()
        for (p in all) {
            when {
                p.packageName in disabled -> skipped.add("${p.displayName}: dimatikan di Pengaturan")
                !supports(p, mcVersion) -> skipped.add("${p.displayName}: di luar rentang versi Minecraft ($mcVersion)")
                !File(p.nativeLibDir).isDirectory -> skipped.add("${p.displayName}: folder pustaka tidak ada")
                else -> active.add(p)
            }
        }
        return LaunchPlan(
            active = active,
            libPaths = active.map { it.nativeLibDir }.distinct(),
            jvmArgs = active.flatMap { it.jvmArgs }.distinct(),
            skipped = skipped
        )
    }

    /** Versi Minecraft tidak dikenal (kosong atau "Unknown") dianggap didukung supaya tidak memblokir tanpa alasan. */
    fun supports(p: NativePlugin, mcVersion: String): Boolean {
        if (mcVersion.isBlank() || mcVersion.equals("Unknown", ignoreCase = true)) return true
        val min = p.minMcVersion
        if (min != null && NuxVersionUtils.isLowerVer(mcVersion, min)) return false
        val max = p.maxMcVersion
        if (max != null && NuxVersionUtils.isBiggerVer(mcVersion, max)) return false
        return true
    }

    private fun parseArgs(environment: String, libDir: String, pkg: String): List<String> =
        NativePluginArgs.sanitize(environment, libDir) { reason ->
            Log.w(TAG, "$pkg: argumen ditolak - $reason")
        }
}
