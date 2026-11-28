package com.israadev.nuxlauncher.core.horizon

import android.content.Context
import com.israadev.nuxlauncher.core.crash.LauncherLog
import java.io.File

/**
 * Slot Horizon sudah dihapus: dunia, resource pack, shader, mod, dan options.txt miliknya (files/horizon) disalin sekali
 * ke folder game Starling. Aslinya TIDAK dihapus, dan berkas yang sudah ada di Starling tidak ditimpa. Dunia atau folder
 * yang namanya sudah dipakai disalin sebagai "nama (Horizon)".
 */
internal object HorizonImport {
    private const val TAG = "HorizonImport"
    private const val OLD_DIR = "horizon"
    private const val DONE_MARKER = "amber-horizon-copied"
    private const val TEMP_PREFIX = ".amber-tmp-"
    private val FOLDERS = listOf("saves", "resourcepacks", "shaderpacks", HorizonMods.FOLDER, "screenshots")
    private val FILES = listOf("options.txt", "servers.dat")

    fun needed(context: Context): Boolean =
        File(context.filesDir, OLD_DIR).isDirectory && !File(StarlingEngine.engineHome(context), DONE_MARKER).exists()

    /** Dipanggil di thread latar. Tidak pernah melempar; bila gagal sebagian, dicoba lagi pada PLAY berikutnya. */
    fun run(context: Context) {
        try {
            val old = HorizonContent.gameHome(File(context.filesDir, OLD_DIR))
            val home = StarlingEngine.engineHome(context)
            val target = HorizonContent.gameHome(home)
            for (folder in FOLDERS) {
                for (child in File(old, folder).listFiles().orEmpty()) {
                    copyEntry(child, File(target, folder))
                }
            }
            for (name in FILES) {
                val source = File(old, name)
                if (source.isFile && !File(target, name).exists()) copyEntry(source, target)
            }
            if (!home.isDirectory) home.mkdirs()
            File(home, DONE_MARKER).writeText("")
        } catch (e: Exception) {
            LauncherLog.w(TAG, "Data Horizon lama belum tersalin semua: ${e.message}", e)
        }
    }

    /** Menyalin [source] ke dalam [parent] lewat nama sementara, supaya salinan setengah jadi tidak pernah dipakai. */
    private fun copyEntry(source: File, parent: File) {
        if (!parent.isDirectory && !parent.mkdirs()) throw java.io.IOException("Folder ${parent.name} tidak bisa dibuat")
        var target = File(parent, source.name)
        if (target.exists()) {
            // Berkas dengan nama sama dibiarkan; dunia atau folder disalin dengan nama lain.
            if (!source.isDirectory) return
            target = File(parent, "${source.name} (Horizon)")
            if (target.exists()) return
        }
        val temp = File(parent, TEMP_PREFIX + source.name)
        temp.deleteRecursively()
        if (!source.copyRecursively(temp, overwrite = true) || !temp.renameTo(target)) {
            temp.deleteRecursively()
            throw java.io.IOException("${source.name} gagal disalin")
        }
    }
}
