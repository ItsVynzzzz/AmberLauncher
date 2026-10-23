package com.israadev.nuxlauncher.core.perf

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import com.movtery.zalithlauncher.bridge.LoggerBridge
import java.io.File
import java.io.RandomAccessFile

/** Selang antar laporan kinerja di log (milidetik). */
private const val REPORT_INTERVAL_MS = 30_000L

/** Selang mencari thread render saat game masih memuat (milidetik). */
private const val FIND_INTERVAL_MS = 1_000L

/** Batas waktu menunggu thread render muncul (milidetik); lewat dari ini pemantau berhenti. */
private const val FIND_TIMEOUT_MS = 300_000L

/** Maksimal byte log GC yang dibaca sekali jalan. */
private const val MAX_GC_READ_BYTES = 256 * 1024

/** Tanda awal baris log, sama dengan baris "[JVM]" dan "[Profil]" milik launcher. */
private const val LOG_PREFIX = "\u25B7 [Kinerja] "

/**
 * Pemantau kinerja game yang hanya MENCATAT ke log. Pemantau tidak mengubah prioritas, heap,
 * atau apa pun pada game, jadi tidak bisa menurunkan FPS selain beberapa pembacaan berkas kecil
 * di /proc tiap 30 detik, dari thread sendiri yang berprioritas rendah.
 *
 * Satu baris laporan berisi: pemakaian CPU thread render, berapa lama thread itu menunggu giliran
 * CPU (tanda CPU berebut dengan thread lain), pemakaian CPU seluruh proses, ringkasan jeda GC dari
 * log GC JVM, RAM proses, dan suhu HP (Android 10 ke atas; HP yang terlalu panas menurunkan
 * kecepatan prosesornya sendiri). Dengan angka ini penyebab FPS tidak stabil bisa dibaca dari log
 * hasil uji di HP, bukan ditebak.
 *
 * @param context dipakai untuk membaca status suhu HP.
 * @param gcLogFile berkas log GC yang ditulis JVM lewat -Xlog:gc, atau null bila tidak dipakai
 *   (Java 8 tidak mendukung -Xlog).
 */
class GamePerfMonitor(context: Context, private val gcLogFile: File?) {

    private val appContext: Context = context.applicationContext

    @Volatile
    private var running = false
    private var worker: Thread? = null
    private var gcOffset = 0L
    private val ticksPerSecond: Long = readTicksPerSecond()

    /** Mulai memantau di thread latar. Aman dipanggil berulang. */
    fun start() {
        if (running) return
        running = true
        val t = Thread(Runnable { monitorLoop() }, "Amber-PerfMonitor")
        t.isDaemon = true
        worker = t
        t.start()
    }

    /** Hentikan pemantauan. */
    fun stop() {
        running = false
        worker?.interrupt()
        worker = null
    }

    private class Sample(
        val timeMs: Long,
        /** Waktu CPU thread render (utime + stime) dalam tick. -1 bila thread sudah tidak ada. */
        val renderTicks: Long,
        /** Total nanodetik thread render siap jalan tetapi menunggu CPU. -1 bila kernel tidak menyediakan. */
        val renderWaitNs: Long,
        /** Waktu CPU seluruh proses dalam tick. */
        val processTicks: Long
    )

    private class GcSummary {
        var count = 0
        var full = 0
        var maxTenthsMs = 0L
        var totalTenthsMs = 0L
    }

    private fun monitorLoop() {
        try {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
        } catch (_: Throwable) {
        }
        try {
            val tid = waitForRenderThread()
            if (tid < 0) return
            LoggerBridge.append(
                LOG_PREFIX + "pemantau aktif (thread render $tid), laporan tiap ${REPORT_INTERVAL_MS / 1000L} dtk | " +
                    "mode kinerja stabil Android: ${sustainedSupportLabel()}"
            )
            var prev = takeSample(tid)
            while (running) {
                Thread.sleep(REPORT_INTERVAL_MS)
                if (!running) break
                val now = takeSample(tid)
                if (now.renderTicks < 0L) break
                report(prev, now)
                prev = now
            }
        } catch (_: InterruptedException) {
        } catch (_: Throwable) {
        }
    }

    private fun waitForRenderThread(): Int {
        val deadline = SystemClock.elapsedRealtime() + FIND_TIMEOUT_MS
        while (running && SystemClock.elapsedRealtime() < deadline) {
            val tid = findRenderThread()
            if (tid > 0) return tid
            Thread.sleep(FIND_INTERVAL_MS)
        }
        return -1
    }

    /**
     * Cari thread render lewat namanya di /proc. Minecraft memberi nama "Render thread" (1.14 ke
     * atas) atau "Client thread" (versi lama) pada thread utamanya, dan Java meneruskan nama itu
     * ke thread aslinya di Linux.
     */
    private fun findRenderThread(): Int {
        val dirs = File("/proc/self/task").listFiles() ?: return -1
        for (dir in dirs) {
            val tid = dir.name.toIntOrNull() ?: continue
            val name = firstLine(File(dir, "comm")) ?: continue
            if (name == "Render thread" || name == "Client thread") return tid
        }
        return -1
    }

    private fun takeSample(tid: Int): Sample {
        val sched = readSchedStat(tid)
        return Sample(
            timeMs = SystemClock.elapsedRealtime(),
            renderTicks = readCpuTicks(File("/proc/self/task/$tid/stat")),
            renderWaitNs = if (sched != null) sched[1] else -1L,
            processTicks = readCpuTicks(File("/proc/self/stat"))
        )
    }

    private fun report(prev: Sample, now: Sample) {
        try {
            val wallMs = now.timeMs - prev.timeMs
            if (wallMs <= 0L) return
            val tps = ticksPerSecond
            val renderPct = (now.renderTicks - prev.renderTicks) * 100_000L / (tps * wallMs)
            val coresTenths = (now.processTicks - prev.processTicks) * 10_000L / (tps * wallMs)

            val sb = StringBuilder(LOG_PREFIX)
            sb.append(wallMs / 1000L).append(" dtk | thread render: CPU ").append(renderPct).append('%')
            if (prev.renderWaitNs >= 0L && now.renderWaitNs >= 0L) {
                val waitPct = (now.renderWaitNs - prev.renderWaitNs) * 100L / (wallMs * 1_000_000L)
                sb.append(", menunggu giliran CPU ").append(waitPct).append('%')
            }
            sb.append(" | proses: ").append(tenths(coresTenths)).append(" inti")

            val gc = drainGcLog()
            if (gc != null) {
                sb.append(" | GC: ").append(gc.count).append("x")
                if (gc.count > 0) {
                    sb.append(", terlama ").append(tenths(gc.maxTenthsMs)).append(" ms")
                    sb.append(", total ").append(tenths(gc.totalTenthsMs)).append(" ms")
                    if (gc.full > 0) sb.append(", penuh ").append(gc.full).append("x")
                }
            }

            val rssKb = readKb("/proc/self/status", "VmRSS:")
            val availKb = readKb("/proc/meminfo", "MemAvailable:")
            if (rssKb >= 0L) {
                sb.append(" | RAM proses ").append(rssKb / 1024L).append(" MB")
                if (availKb >= 0L) sb.append(", tersedia ").append(availKb / 1024L).append(" MB")
            }
            val thermal = thermalLabel()
            if (thermal != null) sb.append(" | suhu HP: ").append(thermal)
            LoggerBridge.append(sb.toString())
        } catch (_: Throwable) {
        }
    }

    /** Baca baris baru di log GC sejak laporan terakhir dan ringkas jeda GC-nya; null bila tidak ada log. */
    private fun drainGcLog(): GcSummary? {
        val file = gcLogFile ?: return null
        if (!file.exists()) return null
        val summary = GcSummary()
        try {
            RandomAccessFile(file, "r").use { raf ->
                val length = raf.length()
                if (length < gcOffset) gcOffset = 0L
                val toRead = minOf(length - gcOffset, MAX_GC_READ_BYTES.toLong()).toInt()
                if (toRead <= 0) return summary
                raf.seek(gcOffset)
                val buf = ByteArray(toRead)
                raf.readFully(buf)
                // Hanya baris utuh; sisa baris yang belum selesai dibaca pada laporan berikutnya.
                var end = toRead
                while (end > 0 && buf[end - 1] != '\n'.code.toByte()) end--
                if (end == 0) return summary
                gcOffset += end.toLong()
                val text = String(buf, 0, end, Charsets.ISO_8859_1)
                for (line in text.split('\n')) {
                    if (!line.contains("Pause ") || !line.endsWith("ms")) continue
                    val ms = line.substringBeforeLast("ms").substringAfterLast(' ').toDoubleOrNull() ?: continue
                    val tenths = (ms * 10.0).toLong()
                    summary.count++
                    summary.totalTenthsMs += tenths
                    if (tenths > summary.maxTenthsMs) summary.maxTenthsMs = tenths
                    if (line.contains("Pause Full")) summary.full++
                }
            }
        } catch (_: Throwable) {
        }
        return summary
    }

    private fun tenths(value: Long): String = "${value / 10L}.${value % 10L}"

    /**
     * Status suhu HP menurut Android (Android 10 ke atas), atau null bila tidak tersedia. Mulai dari
     * "hangat" Android sudah bisa menurunkan kecepatan prosesor; "panas berat" ke atas pasti terasa.
     */
    private fun thermalLabel(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            val power = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return null
            // 0 = normal, 1 = ringan, 2 = sedang, 3 = berat, 4 = kritis, 5 = darurat, 6 = mati
            when (power.getCurrentThermalStatus()) {
                0 -> "normal"
                1 -> "hangat"
                2 -> "panas sedang"
                3 -> "panas berat"
                4 -> "panas kritis"
                5 -> "darurat"
                6 -> "hampir mati"
                else -> null
            }
        } catch (_: Throwable) {
            null
        }
    }

    /** Apakah HP ini mendukung mode kinerja stabil Android (tidak berarti sedang aktif). */
    private fun sustainedSupportLabel(): String {
        return try {
            val power = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (power != null && power.isSustainedPerformanceModeSupported()) "didukung" else "tidak didukung"
        } catch (_: Throwable) {
            "tidak diketahui"
        }
    }
}

/** Jumlah tick CPU per detik (hampir selalu 100). */
private fun readTicksPerSecond(): Long {
    return try {
        val v = Os.sysconf(OsConstants._SC_CLK_TCK)
        if (v > 0L) v else 100L
    } catch (_: Throwable) {
        100L
    }
}

private fun firstLine(file: File): String? {
    return try {
        file.bufferedReader().use { it.readLine() }
    } catch (_: Throwable) {
        null
    }
}

/**
 * utime + stime (tick) dari satu berkas stat di /proc, atau -1 bila gagal dibaca. Nama proses di
 * baris itu boleh berisi spasi dan kurung, jadi pembacaan dimulai setelah tanda ")" terakhir.
 */
private fun readCpuTicks(statFile: File): Long {
    return try {
        val line = firstLine(statFile) ?: return -1L
        val close = line.lastIndexOf(')')
        if (close < 0) return -1L
        val fields = line.substring(close + 2).split(' ')
        // fields[0] = status (kolom 3); utime = kolom 14, stime = kolom 15.
        if (fields.size < 13) return -1L
        fields[11].toLong() + fields[12].toLong()
    } catch (_: Throwable) {
        -1L
    }
}

/** (waktu jalan ns, waktu menunggu CPU ns) dari schedstat satu thread, atau null bila kernel tidak menyediakan. */
private fun readSchedStat(tid: Int): LongArray? {
    return try {
        val line = firstLine(File("/proc/self/task/$tid/schedstat")) ?: return null
        val parts = line.trim().split(' ')
        if (parts.size < 2) null else longArrayOf(parts[0].toLong(), parts[1].toLong())
    } catch (_: Throwable) {
        null
    }
}

/** Nilai (kB) dari satu baris "Kunci:   123 kB" di berkas /proc, atau -1 bila tidak ada. */
private fun readKb(path: String, key: String): Long {
    try {
        File(path).bufferedReader().use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                if (line.startsWith(key)) {
                    val digits = line.filter { it.isDigit() }
                    return if (digits.isEmpty()) -1L else digits.toLong()
                }
            }
        }
    } catch (_: Throwable) {
    }
    return -1L
}
