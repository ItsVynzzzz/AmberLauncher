package com.movtery.zalithlauncher.utils.device

import androidx.annotation.Keep

/**
 * Hasil mentah pustaka native libvulkan_check.so (bawaan APK).
 *
 * Nama kelas ini, urutan, dan tipe parameter konstruktornya terikat pada kode native lewat JNI
 * (`(IIILjava/util/List;Ljava/util/Map;)V`): jangan diubah.
 */
@Keep
data class VulkanCapabilities(
    val apiVersionMajor: Int,
    val apiVersionMinor: Int,
    val apiVersionPatch: Int,
    /** Nama semua ekstensi perangkat yang disediakan driver. */
    val extensions: List<String>,
    /** Fitur perangkat Vulkan yang diperiksa pustaka native, nama fitur ke didukung atau tidak. */
    val features: Map<String, Boolean>
)

/** Penerima baris log dari pustaka native. Nama metode `log` terikat pada kode native (JNI). */
@Keep
fun interface VulkanLogCallback {
    fun log(level: String, message: String)
}

/**
 * Pintu ke pustaka native libvulkan_check.so. Nama objek dan kedua fungsi native terikat pada nama simbol JNI
 * di pustaka itu (`Java_com_movtery_zalithlauncher_utils_device_VulkanChecker_*`): jangan diubah.
 *
 * Memuat driver Vulkan HP di proses pemanggilnya, dan driver yang bermasalah bisa menutup proses itu.
 * Karena itu pemanggilnya HANYA proses terpisah `:vkcheck` (lihat core/vulkan/VulkanCheckService).
 */
@Keep
object VulkanChecker {

    /**
     * Menanyakan driver Vulkan bawaan HP (tanpa driver kustom). Null bila Vulkan tidak ada atau tidak bisa
     * dibuka. Melempar [UnsatisfiedLinkError] bila pustakanya tidak ikut di build ini.
     */
    fun check(onLog: (level: String, message: String) -> Unit): VulkanCapabilities? {
        System.loadLibrary("vulkan_check")
        nativeSetLogCallback(VulkanLogCallback { level, message -> onLog(level, message) })
        return nativeCheckVulkan(null, null, null)
    }

    @Keep
    @JvmStatic
    private external fun nativeSetLogCallback(callback: VulkanLogCallback?)

    @Keep
    @JvmStatic
    private external fun nativeCheckVulkan(
        driverPath: String?,
        nativeDir: String?,
        cacheDir: String?
    ): VulkanCapabilities?
}
