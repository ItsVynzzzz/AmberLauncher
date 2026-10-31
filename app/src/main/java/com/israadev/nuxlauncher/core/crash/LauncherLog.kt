package com.israadev.nuxlauncher.core.crash

import android.content.Context
import android.os.Build
import com.israadev.nuxlauncher.core.update.UpdateChecker
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.RandomAccessFile
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Buku harian kejadian di dalam launcher (di luar game): galat yang tidak tertangani, layar yang dibuka,
 * pesan toast, persiapan peluncuran, unduhan, login, dan peringatan sistem.
 *
 * Log game ([GameLogArchive]) baru ada setelah Minecraft berjalan, sehingga masalah yang terjadi sebelum
 * itu (mis. unduhan gagal, runtime Java tidak siap, galat saat menekan PLAY) tidak tercatat di mana pun.
 * Buku harian ini menutup celah itu. Isinya disimpan di berkas `launcher-events.txt` (folder data
 * pribadi) dan ikut ditempel ke laporan tiap sesi game, atau dibagikan sendiri lewat Pengaturan > Log game.
 *
 * Ringan: pemanggil hanya memasukkan satu objek kecil ke antrean; penulisan berkas dikerjakan satu thread
 * latar berprioritas rendah. Semua proses (utama, game, Forge) boleh menulis ke berkas yang sama; tiap
 * baris diberi nama proses. Token dan kata sandi disamarkan sebelum ditulis. Tidak pernah melempar galat.
 */
object LauncherLog {

    const val FILE_NAME = "launcher-events.txt"

    private const val TRIM_ABOVE_BYTES = 1024L * 1024L
    private const val KEEP_BYTES = 384L * 1024L
    private const val MAX_PENDING = 2000
    private const val MAX_TEXT_CHARS = 1200
    private const val MAX_TRACE_LINES = 60
    private const val TIME_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS"
    private const val TIME_LEN = 23
    private const val CONTINUATION = "    "

    private class Entry(
        val time: Long,
        val level: Char,
        val tag: String,
        val text: String?,
        val lazyText: (() -> String)?,
        val error: Throwable?
    )

    private val queue = ConcurrentLinkedQueue<Entry>()
    private val pending = AtomicInteger(0)
    private val dropped = AtomicInteger(0)
    private val scheduled = AtomicBoolean(false)
    private val writeLock = Any()

    @Volatile private var target: File? = null
    @Volatile private var procName: String = "main"
    @Volatile private var started = false

    private val executor by lazy {
        Executors.newSingleThreadExecutor { r ->
            val t = Thread(r, "amber-launcher-log")
            t.isDaemon = true
            t.priority = Thread.MIN_PRIORITY
            t
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Memulai
    // ---------------------------------------------------------------------------------------------

    /** Dipanggil sekali dari NuxApplication.onCreate (tiap proses). Murah: pekerjaan berat di thread latar. */
    fun init(context: Context) {
        if (started) return
        started = true
        try {
            val app = context.applicationContext ?: context
            target = File(app.filesDir, FILE_NAME)
            procName = detectProcess()
            addLazy('I', "Proses") {
                "=== Proses " + procName + " dimulai: AmberLauncher " + UpdateChecker.currentVersionName(app) +
                    " (build " + UpdateChecker.currentVersionCode(app) + ", " +
                    (if (UpdateChecker.wants32BitApk()) "32-bit" else "64-bit") + ") | " +
                    Build.MANUFACTURER + " " + Build.MODEL + ", Android " + Build.VERSION.RELEASE +
                    " (API " + Build.VERSION.SDK_INT + ") ==="
            }
        } catch (_: Throwable) {
        }
    }

    private fun detectProcess(): String {
        return try {
            val bytes = File("/proc/self/cmdline").inputStream().use { it.readBytes() }
            val name = String(bytes, Charsets.UTF_8).substringBefore(Char.MIN_VALUE).trim()
            if (name.contains(':')) name.substringAfter(':') else "main"
        } catch (_: Throwable) {
            "main"
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Mencatat
    // ---------------------------------------------------------------------------------------------

    /** Hanya ke logcat (tidak disimpan). Pengganti android.util.Log.d. */
    fun d(tag: String, msg: String) {
        android.util.Log.d(tag, msg)
    }

    /** Ke logcat dan buku harian. Pengganti android.util.Log.i. */
    fun i(tag: String, msg: String) {
        android.util.Log.i(tag, msg)
        add('I', tag, msg, null)
    }

    /** Ke logcat dan buku harian. Pengganti android.util.Log.w. */
    fun w(tag: String, msg: String, tr: Throwable? = null) {
        if (tr != null) android.util.Log.w(tag, msg, tr) else android.util.Log.w(tag, msg)
        add('W', tag, msg, tr)
    }

    /** Ke logcat dan buku harian. Pengganti android.util.Log.e. */
    fun e(tag: String, msg: String, tr: Throwable? = null) {
        if (tr != null) android.util.Log.e(tag, msg, tr) else android.util.Log.e(tag, msg)
        add('E', tag, msg, tr)
    }

    /** Hanya ke buku harian (tanpa logcat): kejadian biasa seperti layar dibuka atau toast. */
    fun event(tag: String, msg: String) {
        add('I', tag, msg, null)
    }

    /** Galat tak tertangani: ditulis sekarang juga (proses akan dimatikan setelah ini). */
    fun crash(thread: Thread, tr: Throwable) {
        try {
            add('E', "Crash", "Galat tak tertangani di thread \"" + thread.name + "\": " + tr.javaClass.name + ": " + tr.message, tr)
            drain()
        } catch (_: Throwable) {
        }
    }

    private fun add(level: Char, tag: String, text: String?, tr: Throwable?) {
        enqueue(Entry(System.currentTimeMillis(), level, tag, text, null, tr))
    }

    private fun addLazy(level: Char, tag: String, block: () -> String) {
        enqueue(Entry(System.currentTimeMillis(), level, tag, null, block, null))
    }

    private fun enqueue(entry: Entry) {
        try {
            if (target == null) return
            if (pending.get() >= MAX_PENDING) {
                // Terlalu banyak pesan sekaligus: pesan ini dibuang, jumlahnya dicatat saat penulisan berikutnya.
                dropped.incrementAndGet()
                return
            }
            pending.incrementAndGet()
            queue.add(entry)
            if (scheduled.compareAndSet(false, true)) {
                executor.execute {
                    scheduled.set(false)
                    drain()
                }
            }
        } catch (_: Throwable) {
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Menulis berkas
    // ---------------------------------------------------------------------------------------------

    private fun drain() {
        synchronized(writeLock) {
            val file = target ?: return
            if (queue.isEmpty()) return
            val sb = StringBuilder()
            val clock = SimpleDateFormat(TIME_PATTERN, Locale.US)
            var entry = queue.poll()
            while (entry != null) {
                pending.decrementAndGet()
                appendEntry(sb, clock, entry)
                entry = queue.poll()
            }
            val lost = dropped.getAndSet(0)
            if (lost > 0) {
                sb.append(clock.format(Date())).append(' ').append(procName)
                    .append(" W/LauncherLog: ").append(lost).append(" pesan dibuang karena terlalu banyak sekaligus\n")
            }
            try {
                FileOutputStream(file, true).use { it.write(sb.toString().toByteArray(Charsets.UTF_8)) }
                if (file.length() > TRIM_ABOVE_BYTES) trimFile(file)
            } catch (_: Throwable) {
                // Pencatatan tidak boleh mengganggu aplikasi.
            }
        }
    }

    private fun appendEntry(sb: StringBuilder, clock: SimpleDateFormat, entry: Entry) {
        val body = try {
            entry.text ?: entry.lazyText?.invoke().orEmpty()
        } catch (t: Throwable) {
            "(gagal membuat pesan: " + t.javaClass.simpleName + ")"
        }
        val head = clock.format(Date(entry.time)) + " " + procName + " " + entry.level + "/" + entry.tag + ": "
        var first = true
        for (raw in body.split('\n')) {
            val line = LogRedactor.redactLine(raw.trimEnd('\r')).take(MAX_TEXT_CHARS)
            sb.append(if (first) head else CONTINUATION).append(line).append('\n')
            first = false
        }
        val error = entry.error
        if (error != null) appendTrace(sb, error)
    }

    private fun appendTrace(sb: StringBuilder, tr: Throwable) {
        val sw = StringWriter()
        try {
            val pw = PrintWriter(sw)
            tr.printStackTrace(pw)
            pw.flush()
        } catch (_: Throwable) {
            sw.write(tr.toString())
        }
        var count = 0
        for (raw in sw.toString().split('\n')) {
            if (raw.isBlank()) continue
            val line = LogRedactor.redactLine(raw.trimEnd('\r')).take(MAX_TEXT_CHARS)
            if (count < MAX_TRACE_LINES) {
                sb.append(CONTINUATION).append(line).append('\n')
                count++
            } else if (line.startsWith("Caused by:")) {
                // Sebab asli selalu disimpan walau jejaknya panjang.
                sb.append(CONTINUATION).append(line).append('\n')
            }
        }
    }

    /**
     * Memotong berkas menjadi bagian akhirnya saja. Dikerjakan tanpa mengunci proses lain, jadi bila dua
     * proses memotong bersamaan beberapa baris bisa hilang; untuk buku harian itu tidak masalah.
     */
    private fun trimFile(file: File) {
        try {
            val len = file.length()
            if (len <= KEEP_BYTES) return
            val tail = ByteArray(KEEP_BYTES.toInt())
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(len - KEEP_BYTES)
                raf.readFully(tail)
            }
            var start = 0
            val newline = '\n'.code.toByte()
            while (start < tail.size && tail[start] != newline) start++
            start++
            if (start >= tail.size) return
            FileOutputStream(file, false).use {
                it.write("(bagian lama log dipotong)\n".toByteArray(Charsets.UTF_8))
                it.write(tail, start, tail.size - start)
            }
        } catch (_: Throwable) {
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Membaca (untuk laporan)
    // ---------------------------------------------------------------------------------------------

    /**
     * Isi buku harian dengan waktu >= [sinceMillis] (0 = semuanya), dibatasi [maxBytes] bagian akhir berkas.
     * Antrean proses ini ditulis dulu supaya kejadian terbaru ikut terbaca.
     */
    fun recentText(context: Context, sinceMillis: Long, maxBytes: Long): String {
        try {
            drain()
        } catch (_: Throwable) {
        }
        return try {
            val file = File(context.applicationContext.filesDir, FILE_NAME)
            val len = file.length()
            if (len <= 0L) return ""
            val startAt = maxOf(0L, len - maxBytes)
            val buf = ByteArray((len - startAt).toInt())
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(startAt)
                raf.readFully(buf)
            }
            var text = String(buf, Charsets.UTF_8)
            if (startAt > 0L) {
                val nl = text.indexOf('\n')
                if (nl >= 0) text = text.substring(nl + 1)
            }
            if (sinceMillis <= 0L) return text.trimEnd('\n')

            val clock = SimpleDateFormat(TIME_PATTERN, Locale.US)
            val out = StringBuilder()
            var keep = false
            for (line in text.split('\n')) {
                val t = parseTime(clock, line)
                if (t != null) keep = t >= sinceMillis
                if (keep) out.append(line).append('\n')
            }
            out.toString().trimEnd('\n')
        } catch (_: Throwable) {
            ""
        }
    }

    /** Mengosongkan buku harian (dipakai tombol "Hapus semua log"). */
    fun clear(context: Context) {
        try {
            synchronized(writeLock) {
                while (queue.poll() != null) pending.decrementAndGet()
                File(context.applicationContext.filesDir, FILE_NAME).delete()
            }
        } catch (_: Throwable) {
        }
    }

    private fun parseTime(clock: SimpleDateFormat, line: String): Long? {
        if (line.length <= TIME_LEN || line[4] != '-' || line[10] != ' ' || line[19] != '.') return null
        return try {
            clock.parse(line.substring(0, TIME_LEN))?.time
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Peringatan dan galat sistem milik aplikasi ini dari logcat (mencakup pesan pustaka dan jejak
     * printStackTrace yang tidak lewat buku harian). Kosong bila HP tidak mengizinkan. Jangan dipanggil
     * dari thread utama: menunggu paling lama 4 detik.
     */
    fun logcatWarnings(maxLines: Int): String {
        var proc: Process? = null
        return try {
            val p = ProcessBuilder("logcat", "-d", "-v", "threadtime", "-t", maxLines.toString(), "*:W")
                .redirectErrorStream(true)
                .start()
            proc = p
            val watchdog = Thread {
                try {
                    Thread.sleep(4000L)
                    p.destroy()
                } catch (_: InterruptedException) {
                }
            }
            watchdog.isDaemon = true
            watchdog.start()
            val text = p.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            watchdog.interrupt()
            text
        } catch (_: Throwable) {
            ""
        } finally {
            try {
                proc?.destroy()
            } catch (_: Throwable) {
            }
        }
    }
}
