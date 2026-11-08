package com.israadev.nuxlauncher.core.vulkan

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Process
import com.movtery.zalithlauncher.utils.device.VulkanChecker
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Menjalankan pengecekan Vulkan di proses `:vkcheck` sendiri (lihat AndroidManifest).
 *
 * Pengecekannya memuat driver Vulkan HP. Driver yang bermasalah bisa menutup prosesnya (atau macet), dan di
 * proses terpisah itu hanya `:vkcheck` yang mati, bukan launcher. Prosesnya hidup beberapa detik saja: begitu
 * hasilnya ditulis (atau bila macet lewat [WATCHDOG_MS]) ia mematikan dirinya sendiri, seperti proses `:forge`.
 *
 * Hubungan dengan proses utama lewat berkas di [VulkanCheck.dir]: [VulkanCheck.RESULT_NAME] (ditulis paling
 * akhir) dan [VulkanCheck.MARKER_NAME] (ada selama pengecekan; bila sudah ada saat proses ini mulai, percobaan
 * sebelumnya mati, jadi tidak diulang supaya Android tidak memulai ulang layanan ini tanpa henti).
 *
 * Semua Throwable ditangkap di sini: pengendali galat launcher (NuxApplication) berlaku di proses ini juga dan
 * akan memunculkan layar galat bila ada yang lolos.
 */
class VulkanCheckService : Service() {

    private companion object {
        /** Batas macet: lebih lama dari yang ditunggu proses utama (15 detik). */
        const val WATCHDOG_MS = 18_000L
    }

    private val started = AtomicBoolean(false)

    override fun onBind(intent: Intent?): IBinder {
        begin()
        return Binder()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        begin()
        return START_NOT_STICKY
    }

    private fun begin() {
        if (!started.compareAndSet(false, true)) return
        val app = applicationContext
        thread(name = "Amber-Vulkan-Watchdog", isDaemon = true) {
            try {
                Thread.sleep(WATCHDOG_MS)
            } catch (_: InterruptedException) {
            }
            Process.killProcess(Process.myPid())
        }
        thread(name = "Amber-Vulkan-Check") {
            try {
                runCheck(app)
            } catch (_: Throwable) {
            } finally {
                Process.killProcess(Process.myPid())
            }
        }
    }

    private fun runCheck(app: Context) {
        val dir = VulkanCheck.dir(app)
        dir.mkdirs()
        val marker = File(dir, VulkanCheck.MARKER_NAME)
        if (marker.exists()) return
        marker.writeText(System.currentTimeMillis().toString())

        val report = measure()

        val temp = File(dir, VulkanCheck.RESULT_NAME + ".tmp")
        val result = File(dir, VulkanCheck.RESULT_NAME)
        temp.writeText(report.toJson(VulkanCheck.deviceKey()))
        if (!temp.renameTo(result)) {
            result.delete()
            if (!temp.renameTo(result)) {
                temp.delete()
                return
            }
        }
        marker.delete()
    }

    private fun measure(): VulkanReport {
        val log = StringBuilder()
        val caps = try {
            VulkanChecker.check { level, message ->
                if (log.length < 600) log.append('[').append(level).append("] ").append(message).append('\n')
            }
        } catch (e: LinkageError) {
            // Pustaka libvulkan_check.so tidak ikut di build ini (atau tidak cocok): hasil pasti, bukan galat sementara.
            return VulkanReport(VulkanState.UNAVAILABLE, detail = e.javaClass.simpleName)
        }
        return if (caps == null) {
            VulkanReport(VulkanState.NO_VULKAN, detail = log.toString().trim())
        } else {
            VulkanReport(
                state = VulkanState.OK,
                major = caps.apiVersionMajor,
                minor = caps.apiVersionMinor,
                patch = caps.apiVersionPatch,
                extensions = caps.extensions.toHashSet(),
                features = HashMap(caps.features)
            )
        }
    }
}
