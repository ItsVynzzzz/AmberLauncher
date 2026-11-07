package com.israadev.nuxlauncher.core.renderer

import android.content.Context
import com.israadev.nuxlauncher.core.crash.LauncherLog
import org.json.JSONObject
import java.io.File

/**
 * Menyiapkan config MobileGlues supaya MobileGlues mau membacanya.
 *
 * MobileGlues hanya membaca config.json bila ia mengenali launcher yang memanggilnya: salah satu variabel
 * lingkungan MG_PLUGIN_STATUS, FCL_VERSION_CODE, ZALITH_VERSION_CODE, PGW_VERSION_CODE, atau MG_DIR_PATH (folder
 * config sendiri) harus ada. Bila tidak ada satu pun, ia mencatat "Unsupported launcher detected, force using
 * default config" lalu memaksa semua pilihan ke bawaan, termasuk maxGlslCacheSize = 0. Artinya cache terjemahan
 * shader (GLSL ke ESSL) mati dan SEMUA shader diterjemahkan ulang di CPU setiap kali game dibuka, yang terasa di
 * waktu muat dan patah-patah di HP berprosesor lemah.
 *
 * Launcher memakai jalur resmi MG_DIR_PATH: folder milik launcher berisi config.json dan cache terjemahan shader.
 * Semua kunci config ditulis eksplisit dengan nilai yang SAMA dengan bawaan paksa MobileGlues tadi, kecuali
 * maxGlslCacheSize yang dinyalakan. Kunci yang tidak ditulis dibaca MobileGlues sebagai nilai kosong (-1), yang
 * untuk sebagian pilihan berarti "mati", jadi semuanya sengaja ditulis lengkap.
 */
object MobileGluesConfig {
    private const val TAG = "MobileGlues"

    /** Variabel lingkungan yang dibaca MobileGlues untuk folder config, log, dan cache shadernya. */
    const val ENV_DIR = "MG_DIR_PATH"

    const val LIBRARY_NAME = "libmobileglues.so"

    /** Batas cache terjemahan shader (MiB). Sama dengan bawaan MobileGlues di platform lain. */
    const val CACHE_MIB = 30

    private const val DIR_NAME = "mobileglues"
    private const val CONFIG_FILE = "config.json"
    private const val CACHE_FILE = "glsl_cache.tmp"
    private const val KEY_FILE = "cache-key.txt"

    /** True bila pustaka ini MobileGlues, baik yang bawaan APK maupun milik plugin. Menerima nama atau path. */
    fun isMobileGlues(libraryName: String?): Boolean =
        !libraryName.isNullOrBlank() && File(libraryName).name == LIBRARY_NAME

    /**
     * Menyiapkan folder config MobileGlues dan mengembalikannya, atau null bila tidak bisa (dalam hal itu
     * launcher TIDAK mengatur MG_DIR_PATH, jadi MobileGlues berjalan seperti sebelumnya).
     *
     * - config.json hanya dibuat bila belum ada atau rusak; berkas yang sudah ada tidak ditimpa.
     * - Cache shader dibuang bila pustaka MobileGlues berganti (pembaruan plugin atau launcher), karena kunci
     *   cache hanya isi shader sumbernya, bukan versi penerjemahnya.
     */
    fun prepare(context: Context, library: File): File? {
        return try {
            val dir = File(context.filesDir, DIR_NAME)
            if (!dir.isDirectory && !dir.mkdirs()) {
                LauncherLog.w(TAG, "Folder config MobileGlues tidak bisa dibuat: $dir")
                return null
            }
            val config = File(dir, CONFIG_FILE)
            if (!isValidJson(config) && !writeDefaultConfig(dir, config)) return null
            dropStaleCache(context, dir, library)
            dir
        } catch (e: Throwable) {
            LauncherLog.w(TAG, "Config MobileGlues gagal disiapkan: ${e.message}", e)
            null
        }
    }

    private fun isValidJson(file: File): Boolean {
        if (!file.isFile) return false
        return try {
            JSONObject(file.readText())
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun writeDefaultConfig(dir: File, config: File): Boolean {
        val json = JSONObject()
        json.put("enableANGLE", 0)
        json.put("enableNoError", 0)
        json.put("enableExtComputeShader", 0)
        json.put("enableExtTimerQuery", 1)
        json.put("enableExtDirectStateAccess", 1)
        json.put("maxGlslCacheSize", CACHE_MIB)
        json.put("angleDepthClearFixMode", 0)
        json.put("customGLVersion", 0)
        json.put("fsr1Setting", 0)
        json.put("hideMGEnvLevel", 0)

        val temp = File(dir, "$CONFIG_FILE.tmp")
        temp.writeText(json.toString(2))
        if (!temp.renameTo(config)) {
            if (config.exists()) config.delete()
            if (!temp.renameTo(config)) {
                temp.delete()
                LauncherLog.w(TAG, "config.json MobileGlues tidak bisa disimpan: $config")
                return false
            }
        }
        // Dibaca ulang: bila hasilnya tidak valid, MG_DIR_PATH tidak dipasang (MobileGlues tanpa config
        // dan dengan MG_DIR_PATH akan memakai nilai yang lebih buruk dari bawaan paksanya).
        return isValidJson(config)
    }

    /** Cache shader dibuang bila pustaka MobileGlues berbeda dari yang membuat cache itu. */
    private fun dropStaleCache(context: Context, dir: File, library: File) {
        val key = if (library.isFile) {
            "${library.length()}-${library.lastModified()}"
        } else {
            "app-${appUpdateTime(context)}"
        }
        val keyFile = File(dir, KEY_FILE)
        val old = try {
            keyFile.readText().trim()
        } catch (_: Exception) {
            ""
        }
        if (old != key) {
            File(dir, CACHE_FILE).delete()
            File(dir, "$CACHE_FILE.new").delete()
            keyFile.writeText(key)
        }
    }

    @Suppress("DEPRECATION")
    private fun appUpdateTime(context: Context): Long = try {
        context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
    } catch (_: Exception) {
        0L
    }
}
