package com.israadev.nuxlauncher.core.storage

import java.io.File

/**
 * Aturan memilih folder data launcher. Tanpa Android, jadi bisa diuji di JVM biasa; [DataLocation]
 * hanya menyuplai folder-folder kandidat dan menyimpan hasilnya.
 *
 * Ada dua mode, disimpan di satu berkas penunjuk kecil di penyimpanan internal aplikasi:
 *  - [MODE_NEW]: folder milik AmberLauncher sendiri di Android/data (tanpa izin akses semua file).
 *  - [MODE_LEGACY]: lokasi lama (folder nuxlauncher di penyimpanan utama, atau cadangannya), dipakai
 *    pengguna lama sampai mereka memindahkan datanya.
 *
 * Pengguna baru langsung memakai [MODE_NEW]. Pengguna lama (ada data di lokasi lama) tetap [MODE_LEGACY]
 * sampai pemindahan berhasil, jadi pembaruan aplikasi tidak pernah membuat datanya "hilang".
 */
object DataLocationLogic {

    const val NEW_DIR_NAME = "amberlauncher"
    const val LEGACY_DIR_NAME = "nuxlauncher"
    const val MODE_NEW = "new"
    const val MODE_LEGACY = "legacy"

    /** Folder-folder yang dikenal. Kandidat lama boleh null bila penyimpanannya tidak tersedia. */
    class Candidates(
        val newDir: File,
        val legacyPublic: File?,
        val legacyAppExternal: File?,
        val legacyInternal: File
    ) {
        /** Urutan sama dengan pencarian lama: penyimpanan utama, folder aplikasi di luar, lalu internal. */
        val legacyList: List<File> get() = listOfNotNull(legacyPublic, legacyAppExternal, legacyInternal)
    }

    class Decision(val mode: String, val root: File, val persist: Boolean)

    /** True bila [dir] berisi data launcher sungguhan (bukan folder kosong atau hanya log). */
    fun hasData(dir: File): Boolean {
        try {
            if (File(dir, "instances.json").length() > 0L) return true
            for (name in DATA_FOLDERS) {
                if (!File(dir, name).list().isNullOrEmpty()) return true
            }
        } catch (_: Throwable) {
        }
        return false
    }

    private val DATA_FOLDERS = listOf("instances", "versions", "libraries", "assets")

    fun readPointer(file: File): String? =
        try {
            file.readText().trim().takeIf { it == MODE_NEW || it == MODE_LEGACY }
        } catch (_: Throwable) {
            null
        }

    /** Menulis penunjuk lewat berkas sementara lalu rename, supaya tidak pernah setengah tertulis. */
    fun writePointer(file: File, mode: String): Boolean =
        try {
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(mode)
            if (!tmp.renameTo(file)) {
                file.writeText(mode)
                tmp.delete()
            }
            readPointer(file) == mode
        } catch (_: Throwable) {
            false
        }

    /**
     * Memutuskan folder data dari penunjuk yang tersimpan ([pointerMode], null bila belum ada atau tidak
     * dikenali) dan keadaan folder-folder kandidat. [Decision.persist] true berarti penunjuk perlu ditulis.
     */
    fun decide(pointerMode: String?, c: Candidates): Decision {
        when (pointerMode) {
            MODE_NEW -> return Decision(MODE_NEW, c.newDir, persist = false)
            MODE_LEGACY -> {
                // Pemulihan: pemindahan sudah selesai tetapi proses dimatikan sebelum penunjuk sempat diganti
                val finished = File(c.newDir, StorageMigration.MARKER).isFile ||
                    (hasData(c.newDir) && c.legacyList.none { hasData(it) })
                return if (finished) {
                    Decision(MODE_NEW, c.newDir, persist = true)
                } else {
                    Decision(MODE_LEGACY, legacyRoot(c), persist = false)
                }
            }
        }
        return if (c.legacyList.any { hasData(it) }) {
            Decision(MODE_LEGACY, legacyRoot(c), persist = true)
        } else {
            Decision(MODE_NEW, c.newDir, persist = true)
        }
    }

    /**
     * Folder lama yang dipakai: kandidat pertama yang berisi data. Bila tidak ada yang berisi, memakai aturan
     * lama (penyimpanan utama bila bisa ditulis, lalu folder aplikasi di luar, lalu internal).
     */
    fun legacyRoot(c: Candidates): File {
        c.legacyList.firstOrNull { hasData(it) }?.let { return it }
        c.legacyPublic?.let { dir ->
            try {
                if ((dir.exists() || dir.mkdirs()) && dir.canWrite()) return dir
            } catch (_: Throwable) {
            }
        }
        c.legacyAppExternal?.let { dir ->
            try {
                if (dir.exists() || dir.mkdirs()) return dir
            } catch (_: Throwable) {
            }
        }
        c.legacyInternal.mkdirs()
        return c.legacyInternal
    }

    /**
     * Folder lama yang masih berisi data dan bisa dipindahkan, atau null. Hanya bermakna di [MODE_LEGACY].
     */
    fun legacySource(c: Candidates): File? = c.legacyList.firstOrNull { hasData(it) }

    /** True bila [path] adalah salah satu folder lama yang dikenal (dipakai sebelum menghapus sisa data lama). */
    fun isKnownLegacy(path: File, c: Candidates): Boolean =
        try {
            val canon = path.absoluteFile.canonicalFile
            c.legacyList.any { it.absoluteFile.canonicalFile == canon }
        } catch (_: Throwable) {
            false
        }
}
