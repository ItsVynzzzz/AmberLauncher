package com.israadev.nuxlauncher.core.renderer

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import java.io.File

object NuxRendererPluginManager {
    private const val TAG = "NuxRendererPlugin"

    /**
     * Plugin renderer yang dikenal. Hanya paket dengan nama persis ini yang boleh dikenali tanpa string
     * renderer lama di AndroidManifest (mis. MobileGL yang memakai fclPlugin_V2).
     */
    private val KNOWN_PLUGIN_PACKAGES = setOf(
        "top.mobilegl.plugin",
        "com.fcl.plugin.mobileglues",
        "com.bzlzhh.plugin.ngg",
        "com.bzlzhh.plugin.ngg.angleless"
    )

    private val pluginRenderers = mutableListOf<NuxRendererInfo>()

    fun getPluginRenderers(): List<NuxRendererInfo> = synchronized(pluginRenderers) {
        pluginRenderers.toList()
    }

    /** Waktu (ms) pemindaian terakhir selesai; 0 = belum pernah dipindai di proses ini. */
    @Volatile
    var lastScanMs: Long = 0L
        private set

    /** Pindai hanya bila belum pernah atau hasilnya sudah lebih tua dari [maxAgeMs]. */
    fun scanPluginsIfStale(context: Context, maxAgeMs: Long = 5 * 60_000L) {
        val age = System.currentTimeMillis() - lastScanMs
        if (lastScanMs == 0L || age > maxAgeMs) scanPlugins(context)
    }

    /**
     * Memindai plugin renderer dari aplikasi terpasang. Pekerjaan berat (membaca semua aplikasi),
     * JANGAN dipanggil dari thread utama: pakai Dispatchers.IO / thread latar.
     */
    @Synchronized
    fun scanPlugins(context: Context): List<NuxRendererInfo> {
        val detected = mutableListOf<NuxRendererInfo>()
        val pm = context.packageManager
        val processedPackages = mutableSetOf<String>()

        fun processAppInfo(info: ApplicationInfo) {
            val pkg = info.packageName
            if (pkg == context.packageName || !processedPackages.add(pkg)) return

            try {
                // Metadata sudah ikut terambil lewat GET_META_DATA: tidak perlu panggilan IPC lagi per aplikasi.
                val metaData = info.metaData
                val nativeLibDir = info.nativeLibraryDir

                // Sama seperti Zalith: plugin renderer adalah APK yang dipasang pengguna, dan harus
                // menandai dirinya lewat metadata. Nama paket atau isi folder native TIDAK dipakai
                // menebak, supaya launcher lain (Pojav, Zalith, dll) dan aplikasi biasa tidak ikut terdaftar.
                if ((info.flags and ApplicationInfo.FLAG_SYSTEM) != 0) return

                val hasLegacyPlugin = metaData != null && (
                    metaData.getBoolean("fclPlugin", false) ||
                    metaData.getBoolean("zalithRendererPlugin", false)
                ) && !metaData.getString("renderer").isNullOrBlank()
                if (!hasLegacyPlugin && pkg !in KNOWN_PLUGIN_PACKAGES) return

                // Label (butuh membuka resource aplikasi, mahal) hanya dimuat bila benar-benar dipakai.
                val appLabel by lazy(LazyThreadSafetyMode.NONE) {
                    runCatching { info.loadLabel(pm).toString() }.getOrDefault(pkg)
                }

                // 1. Metadata plugin FCL / Zalith (penanda fclPlugin atau zalithRendererPlugin + string renderer)
                if (metaData != null && hasLegacyPlugin) {
                    val rendererString = metaData.getString("renderer") ?: ""
                    val des = metaData.getString("des") ?: appLabel
                    val pojavEnvString = metaData.getString("pojavEnv") ?: ""

                    val parts = rendererString.split(":")
                    var rendererId = if (parts.isNotEmpty() && parts[0].isNotBlank()) parts[0] else "opengles3"
                    val glName = if (parts.size > 1 && parts[1].isNotBlank()) parts[1] else "libgl4es.so"
                    val rawEglName = if (parts.size > 2 && parts[2].isNotBlank()) parts[2] else null
                    val eglName = if (rawEglName != null && rawEglName.startsWith("/")) "$nativeLibDir$rawEglName" else rawEglName

                    val envMap = mutableMapOf<String, String>()
                    val dlopenList = mutableListOf<String>()

                    if (pojavEnvString.isNotBlank()) {
                        pojavEnvString.split(":").forEach { envPair ->
                            if (envPair.contains("=")) {
                                val split = envPair.split("=", limit = 2)
                                val k = split[0].trim()
                                val v = split[1].trim()
                                when (k) {
                                    "POJAV_RENDERER" -> {
                                        rendererId = v
                                    }
                                    "DLOPEN" -> {
                                        v.split(",").forEach { dl ->
                                            if (dl.isNotBlank()) dlopenList.add(dl.trim())
                                        }
                                    }
                                    "LIB_MESA_NAME", "MESA_LIBRARY" -> {
                                        envMap[k] = "$nativeLibDir/$v"
                                    }
                                    else -> envMap[k] = v
                                }
                            }
                        }
                    }

                    // MobileGlues, MobileGL, and OpenGL ES based plugins must map POJAV_RENDERER to opengles3 for libpojavexec bridge
                    if (rendererId.equals("MobileGlues", ignoreCase = true) ||
                        rendererId.equals("MobileGL", ignoreCase = true) ||
                        (!rendererId.startsWith("opengles") && !rendererId.startsWith("vulkan") && !rendererId.startsWith("gallium") && rendererId != "custom_gallium")) {
                        rendererId = "opengles3"
                    }

                    val minVer = metaData.getString("minMCVer")
                    val maxVer = metaData.getString("maxMCVer")

                    val pluginInfo = NuxRendererInfo(
                        id = "plugin_$pkg",
                        displayName = des,
                        badge = "Plugin: $appLabel",
                        summary = if (pkg == "top.mobilegl.plugin" || des.equals("MobileGL", ignoreCase = true)) 
                            "Pustaka grafis MobileGL (Vulkan & GLES backend)" 
                        else "Renderer eksternal dari APK: $appLabel ($pkg)",
                        compatibility = "Minecraft (APK Plugin)",
                        rendererId = rendererId,
                        libraryName = glName,
                        eglName = eglName,
                        envVariables = envMap,
                        isPlugin = true,
                        pluginPackageName = pkg,
                        pluginNativePath = nativeLibDir,
                        dlopenLibs = dlopenList,
                        minMCVersion = minVer,
                        maxMCVersion = maxVer
                    )
                    detected.add(pluginInfo)
                    Log.i(TAG, "Found renderer plugin (metadata): ${pluginInfo.displayName} ($pkg)")
                    return
                }

                // 2. Paket plugin yang dikenal tetapi tanpa string renderer lama (mis. MobileGL V2):
                // ambil pustaka grafis dari folder native-nya. Folder harus berisi pustaka (.so).
                val nativeDirFile = File(nativeLibDir)
                if (nativeDirFile.exists() && nativeDirFile.isDirectory) {
                    val soFiles = nativeDirFile.listFiles { f -> f.extension == "so" } ?: emptyArray()

                    if (soFiles.isNotEmpty()) {
                        val glName = soFiles.firstOrNull { it.name.startsWith("libmobilegl", ignoreCase = true) || it.name.startsWith("libgl") }?.name ?: "libgl4es.so"
                        val eglName = soFiles.firstOrNull { it.name.contains("egl", ignoreCase = true) }?.name
                        val dlopenList = soFiles.map { it.name }

                        val pluginInfo = NuxRendererInfo(
                            id = "plugin_$pkg",
                            displayName = appLabel,
                            badge = "Plugin Eksternal",
                            summary = "Renderer APK: $appLabel ($pkg)",
                            compatibility = "Minecraft (APK Plugin)",
                            rendererId = "opengles3",
                            libraryName = glName,
                            eglName = eglName,
                            envVariables = mapOf(
                                "POJAVEXEC_EGL" to (eglName ?: glName),
                                "LIBGL_EGL" to (eglName ?: glName)
                            ),
                            isPlugin = true,
                            pluginPackageName = pkg,
                            pluginNativePath = nativeLibDir,
                            dlopenLibs = dlopenList
                        )
                        detected.add(pluginInfo)
                        Log.i(TAG, "Found renderer plugin (heuristic): ${pluginInfo.displayName} ($pkg)")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error checking package $pkg for renderer plugin", e)
            }
        }

        // Satu kali mengambil daftar aplikasi terpasang; dipakai juga oleh pemindai V2.
        // (Cara queryIntentActivities dibuang karena hasilnya hanya bagian dari daftar ini.)
        var installed: List<ApplicationInfo> = emptyList()
        try {
            installed = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (appInfo in installed) {
                processAppInfo(appInfo)
            }
        } catch (e: Exception) {
            Log.w(TAG, "getInstalledApplications failed", e)
        }

        synchronized(pluginRenderers) {
            pluginRenderers.clear()
            pluginRenderers.addAll(detected)
        }
        NuxRendererRegistry.setPluginRenderers(detected)
        try {
            com.israadev.nuxlauncher.core.renderer.v2.NuxRendererV2Manager.scanV2Plugins(context, installed)
        } catch (e: Exception) {
            Log.w(TAG, "scanV2Plugins failed", e)
        }
        lastScanMs = System.currentTimeMillis()
        return detected
    }
}
