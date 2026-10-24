package com.israadev.nuxlauncher.core.forge

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.israadev.nuxlauncher.core.runtime.JavaRuntimeManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

/** Hasil satu kali menjalankan installer Forge/NeoForge. [exitCode] 0 = berhasil; [label] = nama loadernya. */
internal class ForgeRunResult(val exitCode: Int, val log: String, val label: String = "Forge") {

    /** Ringkasan alasan gagal untuk ditampilkan ke pengguna: baris galat terakhir dari log. */
    fun describeFailure(): String {
        val lines = log.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val interesting = lines.filter {
            it.contains("error", ignoreCase = true) || it.contains("exception", ignoreCase = true) ||
                it.contains("failed", ignoreCase = true) || it.contains("missing", ignoreCase = true) ||
                it.contains("checksum", ignoreCase = true) || it.contains("invalid", ignoreCase = true) ||
                it.startsWith("[$label]")
        }
        val reason = (interesting.ifEmpty { lines }).takeLast(3).joinToString(" | ").take(400)
        return "Installer $label gagal (kode $exitCode)" + if (reason.isNotEmpty()) ": $reason" else ""
    }
}

/**
 * Sisi proses utama dari [ForgeInstallService]: memulai service `:forge`, mengikuti log-nya untuk
 * progress, lalu menunggu kode keluar. Proses `:forge` selalu dimatikan di akhir (juga saat dibatalkan).
 */
internal object ForgeInstallRunner {

    /** Batas waktu satu kali pemasangan; lebih lama dari ini dianggap macet. */
    private const val TIMEOUT_MS = 25 * 60 * 1000L
    private const val POLL_MS = 300L

    /** Menjalankan [command] dari [installerJar] dengan runtime Java [runtimeName]; [label] = nama loader untuk log dan notifikasi. */
    suspend fun run(
        context: Context,
        installerJar: File,
        command: InstallerCommand,
        runtimeName: String,
        label: String,
        onLine: (String) -> Unit
    ): ForgeRunResult = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val work = ForgeInstallService.workDir(app)
        work.deleteRecursively()
        work.mkdirs()
        val logFile = File(work, ForgeInstallService.LOG_NAME)
        val resultFile = File(work, ForgeInstallService.RESULT_NAME)

        val intent = Intent(app, ForgeInstallService::class.java)
            .putExtra(ForgeInstallService.EXTRA_INSTALLER, installerJar.absolutePath)
            .putExtra(ForgeInstallService.EXTRA_MAIN_CLASS, command.mainClass)
            .putExtra(ForgeInstallService.EXTRA_ARGS, command.args.toTypedArray())
            .putExtra(ForgeInstallService.EXTRA_LABEL, label)
            .putExtra(
                ForgeInstallService.EXTRA_RUNTIME_HOME,
                JavaRuntimeManager.getRuntimeHome(app, runtimeName).absolutePath
            )

        // Terikat hanya untuk mendeteksi proses :forge mati mendadak (misalnya dimatikan karena RAM habis)
        val died = CompletableDeferred<Unit>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                try {
                    binder?.linkToDeath({ died.complete(Unit) }, 0)
                } catch (_: RemoteException) {
                    died.complete(Unit)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                died.complete(Unit)
            }
        }

        val tail = ArrayDeque<String>()
        var offset = 0L
        fun drain() {
            offset = readNewLines(logFile, offset) { line ->
                tail.addLast(line)
                if (tail.size > 300) tail.removeFirst()
                onLine(line)
            }
        }

        var bound = false
        try {
            // Foreground service membuat pemasangan tahan dihentikan sistem. Android 12+ menolaknya bila
            // aplikasi sedang di latar belakang; itu tidak fatal karena bindService di bawah tetap
            // membuat service dan memulai installer.
            try {
                ContextCompat.startForegroundService(app, intent)
            } catch (_: Exception) {
            }
            bound = app.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            if (!bound) {
                return@withContext ForgeRunResult(-1, "[$label] Proses pemasangan tidak bisa dimulai", label)
            }

            val startedAt = SystemClock.elapsedRealtime()
            var exitCode = -1
            while (true) {
                currentCoroutineContext().ensureActive()
                drain()
                if (resultFile.exists()) {
                    delay(150) // beri waktu baris log terakhir tertulis
                    drain()
                    exitCode = resultFile.readText().trim().toIntOrNull() ?: -1
                    break
                }
                if (died.isCompleted) {
                    delay(500)
                    drain()
                    exitCode = resultFile.takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull() ?: -1
                    if (exitCode != 0 && !resultFile.exists()) {
                        tail.addLast("[$label] Proses installer berhenti mendadak (kemungkinan RAM HP habis)")
                    }
                    break
                }
                if (SystemClock.elapsedRealtime() - startedAt > TIMEOUT_MS) {
                    exitCode = -2
                    tail.addLast("[$label] Waktu pemasangan habis")
                    break
                }
                delay(POLL_MS)
            }
            ForgeRunResult(exitCode, tail.joinToString("\n"), label)
        } finally {
            if (bound) {
                try {
                    app.unbindService(connection)
                } catch (_: Exception) {
                }
            }
            // onDestroy service mematikan proses :forge
            try {
                app.stopService(intent)
            } catch (_: Exception) {
            }
        }
    }

    /** Membaca baris baru dari [file] mulai [offset]; mengembalikan offset baru (hanya baris lengkap). */
    private fun readNewLines(file: File, offset: Long, onLine: (String) -> Unit): Long {
        if (!file.exists()) return offset
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val available = raf.length() - offset
                if (available <= 0L) return@use offset
                val bytes = ByteArray(available.coerceAtMost(1L shl 20).toInt())
                raf.seek(offset)
                raf.readFully(bytes)
                val lastNewline = bytes.lastIndexOf('\n'.code.toByte())
                if (lastNewline < 0) return@use offset // baris belum lengkap
                String(bytes, 0, lastNewline + 1, Charsets.UTF_8)
                    .split('\n')
                    .forEach { line ->
                        val l = line.trimEnd('\r')
                        if (l.isNotEmpty()) onLine(l)
                    }
                offset + lastNewline + 1
            }
        } catch (_: Exception) {
            offset
        }
    }
}
