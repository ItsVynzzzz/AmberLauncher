package com.israadev.nuxlauncher.core.settings

import android.app.ActivityManager
import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonParser
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
                val parsed = JsonParser.parseString(json)
                val loaded = gson.fromJson(parsed, LauncherSettings::class.java)
                if (loaded != null) {
                    val hasMouseButtonCameraLook = parsed.isJsonObject &&
                        parsed.asJsonObject.has("mouseButtonCameraLook")
                    _settings.value = if (hasMouseButtonCameraLook) {
                        loaded
                    } else {
                        loaded.copy(mouseButtonCameraLook = true)
                    }
                    return
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val initialSettings = LauncherSettings(ramMb = recommendedRamMb(getTotalDeviceMemoryMb(context)))
        _settings.value = initialSettings
        save(context)
    }

    fun updateSettings(context: Context, newSettings: LauncherSettings) {
        _settings.value = newSettings
        save(context)
    }

    fun resetToDefaults(context: Context) {
        val defaultSettings = LauncherSettings(ramMb = recommendedRamMb(getTotalDeviceMemoryMb(context)))
        _settings.value = defaultSettings
        save(context)
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
