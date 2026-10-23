package com.israadev.nuxlauncher.core.settings

import android.app.ActivityManager
import android.content.Context
import com.google.gson.Gson
import com.israadev.nuxlauncher.core.models.LauncherSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

object SettingsManager {
    private val gson = Gson()
    private val _settings = MutableStateFlow(LauncherSettings())
    val settings: StateFlow<LauncherSettings> = _settings.asStateFlow()

    private fun getSettingsFile(context: Context): File {
        return File(context.filesDir, "settings.json")
    }

    fun init(context: Context) {
        val file = getSettingsFile(context)
        if (file.exists()) {
            try {
                val json = file.readText()
                val loaded = gson.fromJson(json, LauncherSettings::class.java)
                if (loaded != null) {
                    _settings.value = loaded
                    return
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        _settings.value = defaultSettingsFor(context)
        save(context)
    }

    fun updateSettings(context: Context, newSettings: LauncherSettings) {
        _settings.value = newSettings
        save(context)
    }

    fun resetToDefaults(context: Context) {
        _settings.value = defaultSettingsFor(context)
        save(context)
    }

    /**
     * Pengaturan awal untuk HP ini: RAM game dan skala resolusi menyesuaikan spek HP. Hanya dipakai
     * saat pertama kali dibuka dan saat reset; pengaturan yang sudah tersimpan tidak pernah diubah.
     */
    private fun defaultSettingsFor(context: Context): LauncherSettings {
        val totalRamMb = getTotalDeviceMemoryMb(context)
        val minSidePx = try {
            val metrics = context.resources.displayMetrics
            minOf(metrics.widthPixels, metrics.heightPixels)
        } catch (_: Throwable) {
            0
        }
        return LauncherSettings(
            ramMb = recommendedRamMb(totalRamMb),
            resolutionRatio = recommendedResolutionRatio(totalRamMb, minSidePx)
        )
    }

    /**
     * Skala resolusi game (persen) yang disarankan. Layar HP sekarang jauh lebih rapat daripada yang
     * sanggup digambar GPU kelas bawah dengan lancar, jadi sisi terpendek layar diturunkan ke sasaran
     * menurut RAM HP: di bawah 4 GB = 540 piksel, di bawah 6 GB = 720 piksel, selebihnya 1080 piksel.
     * Layar yang sudah sekecil itu tetap 100%. Hasilnya kelipatan 5 supaya cocok dengan slider.
     */
    fun recommendedResolutionRatio(totalRamMb: Int, minSidePx: Int): Int {
        if (minSidePx <= 0) return 100
        val targetSide = when {
            totalRamMb < 4096 -> 540
            totalRamMb < 6144 -> 720
            else -> 1080
        }
        if (minSidePx <= targetSide) return 100
        val ratio = 100.0 * targetSide / minSidePx
        val stepped = (Math.ceil(ratio / 5.0) * 5.0).toInt()
        return stepped.coerceIn(40, 100)
    }

    /**
     * RAM game (MB) yang disarankan untuk HP dengan total RAM [totalRamMb]. Dipakai saat pertama
     * kali dibuka dan saat reset, supaya hasilnya selalu sama. HP 4 GB (melapor sekitar 3700 MB)
     * tetap mendapat 1280 MB seperti sebelumnya; HP di bawah 3 GB mendapat 1024 MB agar tidak
     * kehabisan memori.
     */
    fun recommendedRamMb(totalRamMb: Int): Int = when {
        totalRamMb >= 12000 -> 4096
        totalRamMb >= 8000 -> 3072
        totalRamMb >= 6000 -> 2560
        totalRamMb >= 4000 -> 2048
        totalRamMb >= 3000 -> 1280
        else -> 1024
    }

    private fun save(context: Context) {
        try {
            val file = getSettingsFile(context)
            file.writeText(gson.toJson(_settings.value))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getTotalDeviceMemoryMb(context: Context): Int {
        return try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 4096
            val memInfo = ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            (memInfo.totalMem / (1024 * 1024)).toInt()
        } catch (_: Throwable) {
            4096
        }
    }

    fun getAvailableDeviceMemoryMb(context: Context): Int {
        return try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 2048
            val memInfo = ActivityManager.MemoryInfo()
            actManager.getMemoryInfo(memInfo)
            (memInfo.availMem / (1024 * 1024)).toInt()
        } catch (_: Throwable) {
            2048
        }
    }

    fun getCpuCoreCount(): Int {
        return Runtime.getRuntime().availableProcessors()
    }
}
