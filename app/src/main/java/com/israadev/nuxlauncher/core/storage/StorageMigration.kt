package com.israadev.nuxlauncher.core.storage

import java.io.File
import java.nio.file.Files

/**
 * Memindahkan seluruh folder data launcher ke lokasi baru tanpa risiko kehilangan data.
 *
 * Tanpa Android, jadi bisa diuji di JVM biasa. Aturan keselamatannya:
 *  1. Data lama tidak disentuh sampai salinan baru lengkap dan terperiksa.
 *  2. Coba rename satu folder dulu (instan dan atomik bila masih satu penyimpanan). Bila ditolak sistem,
 *     data disalin ke `<tujuan>.migrating`, diperiksa, lalu diganti namanya menjadi tujuan.
 *  3. Pembatalan dan kegagalan sebelum langkah terakhir hanya membuang folder sementara.
 *  4. [Hooks.commit] (mengganti penunjuk lokasi) dipanggil sesudah data baru siap; baru setelah itu data
 *     lama dihapus. Bila commit gagal, perubahan dikembalikan dan data lama tetap utuh.
 *
 * Tidak pernah melempar galat: semuanya dikembalikan sebagai [Result].
 */
object StorageMigration {

    /** Berkas penanda di dalam folder tujuan: isinya hasil pemindahan yang lengkap. */
    const val MARKER = ".amber-migrated"

    /** Akhiran folder sementara tempat salinan dibuat sebelum diganti namanya. */
    const val TEMP_SUFFIX = ".migrating"

    /** Cadangan ruang kosong di atas ukuran data. */
    const val SPACE_MARGIN_BYTES = 64L * 1024 * 1024

    private const val BUFFER_SIZE = 256 * 1024
    private const val PROGRESS_INTERVAL_NS = 150_000_000L
    private const val CANCEL_CHECK_EVERY = 256

    enum class Phase { PREPARING, COPYING, VERIFYING, FINISHING, CLEANING }

    class Progress(val phase: Phase, val doneBytes: Long, val totalBytes: Long)

    enum class Reason { SOURCE_MISSING, SAME_OR_NESTED, TARGET_NOT_EMPTY, NOT_ENOUGH_SPACE, IO, VERIFY }

    sealed class Result {
        /** [moved] true bila cukup rename; [leftover] true bila sebagian data lama belum terhapus. */
        class Success(val moved: Boolean, val bytes: Long, val leftover: Boolean) : Result()
        object Cancelled : Result()
        class Failed(
            val reason: Reason,
            val detail: String = "",
            val neededBytes: Long = 0L,
            val availableBytes: Long = 0L
        ) : Result()
    }

    /**
     * [isCancelled] dibaca terus selama menyalin. [onProgress] dipanggil paling sering tiap ~150 ms.
     * [freeSpace] mengembalikan sisa ruang di folder induk tujuan. [commit] dipanggil tepat setelah data
     * baru berada di tempatnya dan sebelum data lama dihapus; false berarti batalkan seluruhnya.
     */
    class Hooks(
        val isCancelled: () -> Boolean = { false },
        val onProgress: (Progress) -> Unit = {},
        val freeSpace: (File) -> Long = { it.usableSpace },
        val commit: () -> Boolean = { true },
        /** False melewati jalur cepat rename dan langsung menyalin (dipakai pengujian). */
        val tryRename: Boolean = true
    )

    private class Entry(val rel: String, val isDir: Boolean, val size: Long)

    private class Plan(val entries: List<Entry>, val totalBytes: Long)

    fun run(source: File, target: File, hooks: Hooks): Result {
        var temp: File? = null
        try {
            if (hooks.isCancelled()) return Result.Cancelled
            if (!source.isDirectory) return Result.Failed(Reason.SOURCE_MISSING)

            val src = source.absoluteFile.canonicalFile
            val dst = target.absoluteFile.canonicalFile
            if (src == dst || isInside(dst, src) || isInside(src, dst)) {
                return Result.Failed(Reason.SAME_OR_NESTED)
            }
            val parent = dst.parentFile ?: return Result.Failed(Reason.IO, "tanpa folder induk")
            if (!parent.isDirectory && !parent.mkdirs()) return Result.Failed(Reason.IO, "folder induk")

            if (dst.exists()) {
                // Folder kosong boleh diganti; yang berisi data tidak boleh ditimpa
                if (!dst.isDirectory || !dst.list().isNullOrEmpty()) return Result.Failed(Reason.TARGET_NOT_EMPTY)
                if (!dst.delete()) return Result.Failed(Reason.IO, "folder tujuan kosong tidak bisa dihapus")
            }
            val tmp = File(parent, dst.name + TEMP_SUFFIX)
            temp = tmp
            if (tmp.exists() || isSymlink(tmp)) deleteTree(tmp)

            hooks.onProgress(Progress(Phase.PREPARING, 0L, 0L))

            // Jalur cepat: satu rename. Sistem menolaknya bila beda penyimpanan atau izin; lanjut menyalin.
            if (hooks.tryRename && src.renameTo(dst)) {
                writeMarker(dst, src)
                if (!commitSafely(hooks)) {
                    val back = dst.renameTo(src)
                    return Result.Failed(Reason.IO, if (back) "commit gagal" else "commit gagal, data ada di tujuan")
                }
                return Result.Success(moved = true, bytes = 0L, leftover = false)
            }

            val plan = scan(src, hooks) ?: return Result.Cancelled
            val needed = plan.totalBytes + SPACE_MARGIN_BYTES
            val free = hooks.freeSpace(parent)
            if (free < needed) {
                return Result.Failed(Reason.NOT_ENOUGH_SPACE, neededBytes = needed, availableBytes = free)
            }

            if (!tmp.mkdirs()) return Result.Failed(Reason.IO, "folder sementara")
            val copied = copyAll(src, tmp, plan, hooks)
            if (copied < 0L) {
                deleteTree(tmp)
                return Result.Cancelled
            }

            hooks.onProgress(Progress(Phase.VERIFYING, copied, plan.totalBytes))
            if (!verify(tmp, plan, copied)) {
                deleteTree(tmp)
                return Result.Failed(Reason.VERIFY)
            }
            writeMarker(tmp, src)

            // Langkah terakhir yang bisa dibatalkan: sesudah ini data baru dipakai
            if (hooks.isCancelled()) {
                deleteTree(tmp)
                return Result.Cancelled
            }
            hooks.onProgress(Progress(Phase.FINISHING, copied, plan.totalBytes))
            if (!tmp.renameTo(dst)) {
                deleteTree(tmp)
                return Result.Failed(Reason.IO, "mengganti nama folder sementara")
            }
            if (!commitSafely(hooks)) {
                deleteTree(dst)
                return Result.Failed(Reason.IO, "commit gagal")
            }

            // Data baru sudah dipakai: apa pun yang terjadi di sini bukan lagi kegagalan pemindahan
            val removed = try {
                hooks.onProgress(Progress(Phase.CLEANING, copied, plan.totalBytes))
                deleteTree(src)
            } catch (_: Throwable) {
                false
            }
            return Result.Success(moved = false, bytes = copied, leftover = !removed)
        } catch (e: Throwable) {
            temp?.let { runCatching { deleteTree(it) } }
            return Result.Failed(Reason.IO, e.javaClass.simpleName + ": " + (e.message ?: ""))
        }
    }

    /** Ukuran yang mudah dibaca, misalnya "3.2 GB" (selalu titik desimal, apa pun bahasa HP). */
    fun formatSize(bytes: Long): String {
        val b = if (bytes < 0L) 0L else bytes
        return when {
            b >= 1024L * 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f GB", b / 1073741824.0)
            b >= 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f MB", b / 1048576.0)
            b >= 1024L -> (b / 1024L).toString() + " KB"
            else -> "$b B"
        }
    }

    private fun commitSafely(hooks: Hooks): Boolean =
        try {
            hooks.commit()
        } catch (_: Throwable) {
            false
        }

    /** Ukuran total berkas di [dir] (tanpa mengikuti symlink). Null bila dibatalkan lewat [isCancelled]. */
    fun measure(dir: File, isCancelled: () -> Boolean = { false }): Long? {
        var total = 0L
        var seen = 0
        val stack = ArrayList<File>()
        stack.add(dir)
        while (stack.isNotEmpty()) {
            val d = stack.removeAt(stack.size - 1)
            val kids = d.listFiles() ?: continue
            for (k in kids) {
                if (++seen % CANCEL_CHECK_EVERY == 0 && isCancelled()) return null
                if (isSymlink(k)) continue
                if (k.isDirectory) stack.add(k) else if (k.isFile) total += k.length()
            }
        }
        return total
    }

    private fun scan(root: File, hooks: Hooks): Plan? {
        val entries = ArrayList<Entry>()
        var total = 0L
        var seen = 0
        val stack = ArrayList<File>()
        stack.add(root)
        val prefix = root.path.length + 1
        while (stack.isNotEmpty()) {
            val d = stack.removeAt(stack.size - 1)
            val kids = d.listFiles() ?: continue
            for (k in kids) {
                if (++seen % CANCEL_CHECK_EVERY == 0 && hooks.isCancelled()) return null
                if (isSymlink(k)) continue
                val rel = k.path.substring(prefix)
                if (k.isDirectory) {
                    entries.add(Entry(rel, true, 0L))
                    stack.add(k)
                } else if (k.isFile) {
                    val size = k.length()
                    entries.add(Entry(rel, false, size))
                    total += size
                }
            }
        }
        // Folder dulu (urut dangkal ke dalam), supaya setiap berkas pasti punya folder induk
        entries.sortBy { if (it.isDir) 0 else 1 }
        return Plan(entries, total)
    }

    /** Menyalin semua isi. Mengembalikan jumlah byte tersalin, atau -1 bila dibatalkan. */
    private fun copyAll(src: File, dst: File, plan: Plan, hooks: Hooks): Long {
        val buf = ByteArray(BUFFER_SIZE)
        var done = 0L
        var lastReport = System.nanoTime()
        hooks.onProgress(Progress(Phase.COPYING, 0L, plan.totalBytes))
        var count = 0
        for (e in plan.entries) {
            if (++count % CANCEL_CHECK_EVERY == 0 && hooks.isCancelled()) return -1L
            val out = File(dst, e.rel)
            if (e.isDir) {
                if (!out.isDirectory && !out.mkdirs()) throw java.io.IOException("folder: ${e.rel}")
                continue
            }
            val from = File(src, e.rel)
            var written = 0L
            from.inputStream().use { input ->
                out.outputStream().use { output ->
                    while (true) {
                        if (hooks.isCancelled()) return -1L
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        written += n
                        done += n
                        val now = System.nanoTime()
                        if (now - lastReport >= PROGRESS_INTERVAL_NS) {
                            lastReport = now
                            hooks.onProgress(Progress(Phase.COPYING, done, plan.totalBytes))
                        }
                    }
                }
            }
            if (out.length() != written) throw java.io.IOException("ukuran berbeda: ${e.rel}")
            // Tanggal ubah dipertahankan bila sistem mengizinkan; tidak penting bila ditolak
            runCatching { out.setLastModified(from.lastModified()) }
        }
        hooks.onProgress(Progress(Phase.COPYING, done, plan.totalBytes))
        return done
    }

    /** Memastikan salinan punya jumlah berkas, folder, dan total ukuran yang sama dengan rencana. */
    private fun verify(copy: File, plan: Plan, copiedBytes: Long): Boolean {
        var files = 0
        var dirs = 0
        var bytes = 0L
        val stack = ArrayList<File>()
        stack.add(copy)
        while (stack.isNotEmpty()) {
            val d = stack.removeAt(stack.size - 1)
            val kids = d.listFiles() ?: return false
            for (k in kids) {
                if (k.isDirectory) {
                    dirs++
                    stack.add(k)
                } else if (k.isFile) {
                    files++
                    bytes += k.length()
                }
            }
        }
        val wantFiles = plan.entries.count { !it.isDir }
        val wantDirs = plan.entries.count { it.isDir }
        return files == wantFiles && dirs == wantDirs && bytes == copiedBytes
    }

    private fun writeMarker(dir: File, from: File) {
        try {
            File(dir, MARKER).writeText("from=" + from.path + "\n")
        } catch (_: Throwable) {
        }
    }

    private fun isInside(child: File, parent: File): Boolean =
        child.path.startsWith(parent.path + File.separator)

    private fun isSymlink(f: File): Boolean =
        try {
            Files.isSymbolicLink(f.toPath())
        } catch (_: Throwable) {
            false
        }

    /**
     * Menghapus [root] beserta isinya tanpa mengikuti symlink (hanya tautannya yang dihapus).
     * True bila [root] sudah tidak ada di akhir.
     */
    fun deleteTree(root: File): Boolean {
        if (!root.exists() && !isSymlink(root)) return true
        if (isSymlink(root) || !root.isDirectory) return root.delete()
        var ok = true
        val dirs = ArrayList<File>()
        val stack = ArrayList<File>()
        stack.add(root)
        while (stack.isNotEmpty()) {
            val d = stack.removeAt(stack.size - 1)
            dirs.add(d)
            val kids = d.listFiles() ?: continue
            for (k in kids) {
                if (isSymlink(k) || !k.isDirectory) {
                    if (!k.delete()) ok = false
                } else {
                    stack.add(k)
                }
            }
        }
        // Folder yang ditemukan lebih dulu adalah induk; hapus dari yang terdalam
        for (i in dirs.indices.reversed()) {
            if (!dirs[i].delete()) ok = false
        }
        return ok && !root.exists()
    }
}
