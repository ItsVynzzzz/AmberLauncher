package com.israadev.nuxlauncher.core.controls

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.israadev.nuxlauncher.core.controls.models.CustomControlButton
import com.israadev.nuxlauncher.core.utils.AtomicFiles
import java.io.File
import java.util.UUID

/**
 * Preset layout kontrol: banyak layout bernama yang disimpan di HP dan bisa dipasang kapan saja.
 *
 * Satu preset = satu berkas JSON di folder control_presets, dalam format ekspor Amber ([ControlLayoutIO]) ditambah
 * nama dan waktu simpan. Isinya selalu lewat [ControlLayoutIO] sebelum disimpan dan sesudah dibaca, jadi preset
 * dari berkas orang lain (termasuk hasil ubahan dari Pojav, Zalith, Mojo) dibersihkan dan dibatasi seperti impor
 * biasa. Nama berkas dibuat sendiri dari id acak yang divalidasi, bukan dari nama preset, jadi tidak ada celah
 * menembus folder. Satu berkas khusus (previous.json) menyimpan layout sebelum preset terakhir dipakai, supaya
 * penggantian bisa dibatalkan. Preset bawaan ([ControlPresetBuiltins]) disalin sekali sebagai preset biasa; daftar
 * yang sudah pernah disalin dicatat di builtins.txt, jadi yang dihapus pengguna tidak muncul lagi sampai
 * dipulihkan lewat [restoreBuiltins].
 *
 * Fungsi utama menerima folder ([File]) alih-alih Context supaya mudah diuji. Semua fungsi membaca atau menulis
 * berkas: panggil dari thread latar.
 */
object ControlPresetManager {
    const val MAX_PRESETS = 40
    const val MAX_NAME = 32

    private const val PREVIOUS_FILE = "previous.json"
    private const val OFFERED_FILE = "builtins.txt"
    private const val EDITING_FILE = "editing.txt"
    private val ID_PATTERN = Regex("^p_[a-z0-9]{6,24}$")

    class Preset(val id: String, val name: String, val buttonCount: Int, val savedAt: Long)

    sealed class SaveResult {
        class Saved(val preset: Preset) : SaveResult()
        object EmptyName : SaveResult()
        object TooMany : SaveResult()
        object Invalid : SaveResult()
        object Failed : SaveResult()
    }

    fun dir(context: Context): File = File(context.filesDir, "control_presets")

    /** Nama yang aman ditampilkan: tanpa karakter kontrol, dipangkas, dan dibatasi [MAX_NAME]. */
    fun cleanName(raw: String): String = raw.filter { !it.isISOControl() }.trim().take(MAX_NAME).trim()

    /** Daftar preset, yang terbaru di atas. Berkas rusak atau bernama tidak dikenal dilewati. */
    fun list(dir: File): List<Preset> {
        val files = dir.listFiles() ?: return emptyList()
        val out = ArrayList<Preset>()
        for (f in files) {
            if (!f.isFile || !f.name.endsWith(".json")) continue
            val id = f.name.removeSuffix(".json")
            if (!ID_PATTERN.matches(id)) continue
            val root = readRoot(f) ?: continue
            val name = cleanName(root.stringOrEmpty("presetName"))
            val buttons = root.get("buttons")
            val count = if (buttons != null && buttons.isJsonArray) buttons.asJsonArray.size() else 0
            if (name.isEmpty() || count == 0) continue
            val savedAt = root.get("savedAt")?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong
                ?: f.lastModified()
            out.add(Preset(id, name, count, savedAt))
        }
        out.sortByDescending { it.savedAt }
        return out
    }

    /**
     * Menyimpan [buttons] sebagai preset bernama [name]. Nama yang sama (huruf besar/kecil diabaikan) menimpa
     * preset lama, supaya menyimpan ulang tidak menumpuk salinan.
     */
    fun saveButtons(
        dir: File,
        name: String,
        buttons: List<CustomControlButton>,
        nowMs: Long = System.currentTimeMillis()
    ): SaveResult {
        val clean = cleanName(name)
        if (clean.isEmpty()) return SaveResult.EmptyName
        if (buttons.isEmpty()) return SaveResult.Invalid
        return try {
            if (!dir.isDirectory && !dir.mkdirs()) return SaveResult.Failed
            val existing = list(dir)
            val same = existing.firstOrNull { it.name.equals(clean, ignoreCase = true) }
            if (same == null && existing.size >= MAX_PRESETS) return SaveResult.TooMany
            val id = same?.id ?: newId(nowMs)
            val root = JsonParser.parseString(ControlLayoutIO.toJson(buttons)).asJsonObject
            root.addProperty("presetName", clean)
            root.addProperty("savedAt", nowMs)
            val text = root.toString()
            if (text.length > ControlLayoutIO.MAX_FILE_BYTES) return SaveResult.Invalid
            AtomicFiles.writeText(File(dir, "$id.json"), text)
            SaveResult.Saved(Preset(id, clean, buttons.size, nowMs))
        } catch (_: Exception) {
            SaveResult.Failed
        }
    }

    /** Seperti [saveButtons] tetapi dari teks JSON layout (misalnya hasil [ControlLayoutManager.exportJson]). */
    fun saveJson(dir: File, name: String, json: String, nowMs: Long = System.currentTimeMillis()): SaveResult {
        val r = ControlLayoutIO.parse(json)
        if (r !is ControlLayoutIO.ImportResult.Ok) return SaveResult.Invalid
        return saveButtons(dir, name, r.buttons, nowMs)
    }

    /** Tombol milik preset [id], atau null bila id tidak sah atau berkasnya hilang/rusak. */
    fun read(dir: File, id: String): List<CustomControlButton>? {
        if (!ID_PATTERN.matches(id)) return null
        return readButtons(File(dir, "$id.json"))
    }

    fun rename(dir: File, id: String, newName: String): Boolean {
        if (!ID_PATTERN.matches(id)) return false
        val clean = cleanName(newName)
        if (clean.isEmpty()) return false
        val f = File(dir, "$id.json")
        val root = readRoot(f) ?: return false
        root.addProperty("presetName", clean)
        return try {
            AtomicFiles.writeText(f, root.toString())
            true
        } catch (_: Exception) {
            false
        }
    }

    fun delete(dir: File, id: String): Boolean {
        if (!ID_PATTERN.matches(id)) return false
        if (readEditingRaw(dir) == id) clearEditing(dir)
        return try {
            File(dir, "$id.json").delete()
        } catch (_: Exception) {
            false
        }
    }

    // ---- Menimpa, menggandakan, dan mengedit ----------------------------------------------------------

    /** Mengganti isi preset [id] dengan layout [json]; nama preset tetap. */
    fun overwrite(dir: File, id: String, json: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!ID_PATTERN.matches(id)) return false
        val f = File(dir, "$id.json")
        val old = readRoot(f) ?: return false
        val name = cleanName(old.stringOrEmpty("presetName"))
        if (name.isEmpty()) return false
        val r = ControlLayoutIO.parse(json)
        if (r !is ControlLayoutIO.ImportResult.Ok) return false
        return try {
            val root = JsonParser.parseString(ControlLayoutIO.toJson(r.buttons)).asJsonObject
            root.addProperty("presetName", name)
            root.addProperty("savedAt", nowMs)
            val text = root.toString()
            if (text.length > ControlLayoutIO.MAX_FILE_BYTES) return false
            AtomicFiles.writeText(f, text)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Menggandakan preset [id] dengan nama "(salinan)", "(salinan 2)", dan seterusnya yang belum dipakai. */
    fun duplicate(dir: File, id: String, nowMs: Long = System.currentTimeMillis()): SaveResult {
        if (!ID_PATTERN.matches(id)) return SaveResult.Invalid
        val buttons = read(dir, id) ?: return SaveResult.Invalid
        val all = list(dir)
        val source = all.firstOrNull { it.id == id } ?: return SaveResult.Invalid
        if (all.size >= MAX_PRESETS) return SaveResult.TooMany
        val taken = all.map { it.name.lowercase() }.toSet()
        var suffix = " (salinan)"
        var candidate = source.name.take(MAX_NAME - suffix.length).trim() + suffix
        var n = 2
        while (candidate.lowercase() in taken) {
            if (n > 99) return SaveResult.Failed
            suffix = " (salinan $n)"
            candidate = source.name.take(MAX_NAME - suffix.length).trim() + suffix
            n++
        }
        return saveButtons(dir, candidate, buttons, nowMs)
    }

    /** Mencatat preset yang sedang diedit di editor, supaya perubahannya bisa disimpan balik dengan satu ketukan. */
    fun setEditing(dir: File, id: String): Boolean {
        if (!ID_PATTERN.matches(id)) return false
        return try {
            if (!dir.isDirectory && !dir.mkdirs()) return false
            AtomicFiles.writeText(File(dir, EDITING_FILE), id)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Id preset yang sedang diedit, atau null bila tidak ada atau presetnya sudah hilang. */
    fun editingId(dir: File): String? {
        val id = readEditingRaw(dir) ?: return null
        return if (File(dir, "$id.json").isFile) id else null
    }

    fun clearEditing(dir: File) {
        try {
            File(dir, EDITING_FILE).delete()
        } catch (_: Exception) {
        }
    }

    private fun readEditingRaw(dir: File): String? {
        val f = File(dir, EDITING_FILE)
        if (!f.isFile || f.length() > 64L) return null
        val id = try {
            f.readText(Charsets.UTF_8).trim()
        } catch (_: Exception) {
            return null
        }
        return if (ID_PATTERN.matches(id)) id else null
    }

    // ---- Preset bawaan ------------------------------------------------------------------------------

    /** Menyalin preset bawaan yang belum pernah ditawarkan. Dipanggil tiap dialog dibuka; murah bila tidak ada yang baru. */
    fun ensureBuiltins(dir: File, defaults: List<CustomControlButton>): Int {
        val offered = readOffered(dir)
        val fresh = ControlPresetBuiltins.all(defaults).filter { it.id !in offered }
        if (fresh.isEmpty()) return 0
        return try {
            if (!dir.isDirectory && !dir.mkdirs()) return 0
            val done = fresh.filter { writeBuiltin(dir, it) }
            if (done.isNotEmpty()) writeOffered(dir, offered + done.map { it.id })
            done.size
        } catch (_: Exception) {
            0
        }
    }

    /** Memulihkan preset bawaan yang sudah dihapus. Yang masih ada (mungkin sudah diubah namanya) dibiarkan. */
    fun restoreBuiltins(dir: File, defaults: List<CustomControlButton>): Int {
        return try {
            if (!dir.isDirectory && !dir.mkdirs()) return 0
            val offered = readOffered(dir)
            val restored = ControlPresetBuiltins.all(defaults).filter { !File(dir, "${it.id}.json").isFile }
                .filter { writeBuiltin(dir, it) }
            if (restored.isNotEmpty()) writeOffered(dir, offered + restored.map { it.id })
            restored.size
        } catch (_: Exception) {
            0
        }
    }

    /** Jumlah preset bawaan yang tidak ada di daftar (sudah dihapus pengguna). */
    fun missingBuiltins(dir: File): Int = ControlPresetBuiltins.IDS.count { !File(dir, "$it.json").isFile }

    private fun writeBuiltin(dir: File, b: ControlPresetBuiltins.Builtin): Boolean {
        return try {
            val root = JsonParser.parseString(ControlLayoutIO.toJson(b.buttons)).asJsonObject
            root.addProperty("presetName", b.name)
            root.addProperty("savedAt", b.order)
            AtomicFiles.writeText(File(dir, "${b.id}.json"), root.toString())
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun readOffered(dir: File): Set<String> {
        val f = File(dir, OFFERED_FILE)
        if (!f.isFile || f.length() > 4096L) return emptySet()
        return try {
            f.readLines(Charsets.UTF_8).map { it.trim() }.filter { ID_PATTERN.matches(it) }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun writeOffered(dir: File, ids: Set<String>) {
        AtomicFiles.writeText(File(dir, OFFERED_FILE), ids.sorted().joinToString("\n"))
    }

    // ---- Layout sebelum preset dipakai --------------------------------------------------------------

    /** Menyimpan [json] (layout yang akan diganti) ke slot "sebelumnya". Slot ini tidak dihitung ke [MAX_PRESETS]. */
    fun savePrevious(dir: File, json: String): Boolean {
        val r = ControlLayoutIO.parse(json)
        if (r !is ControlLayoutIO.ImportResult.Ok) return false
        return try {
            if (!dir.isDirectory && !dir.mkdirs()) return false
            val root = JsonParser.parseString(ControlLayoutIO.toJson(r.buttons)).asJsonObject
            root.addProperty("savedAt", System.currentTimeMillis())
            AtomicFiles.writeText(File(dir, PREVIOUS_FILE), root.toString())
            true
        } catch (_: Exception) {
            false
        }
    }

    fun hasPrevious(dir: File): Boolean = File(dir, PREVIOUS_FILE).isFile

    fun readPrevious(dir: File): List<CustomControlButton>? = readButtons(File(dir, PREVIOUS_FILE))

    // ---- Internal -----------------------------------------------------------------------------------

    private fun newId(nowMs: Long): String =
        "p_" + java.lang.Long.toString(nowMs, 36) + UUID.randomUUID().toString().replace("-", "").take(4)

    private fun readText(f: File): String? {
        if (!f.isFile || f.length() > ControlLayoutIO.MAX_FILE_BYTES + 4096L) return null
        return try {
            f.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            null
        }
    }

    private fun readRoot(f: File): JsonObject? {
        val text = readText(f) ?: return null
        return try {
            val e = JsonParser.parseString(text)
            if (e.isJsonObject) e.asJsonObject else null
        } catch (_: Exception) {
            null
        }
    }

    private fun readButtons(f: File): List<CustomControlButton>? {
        val text = readText(f) ?: return null
        val r = ControlLayoutIO.parse(text)
        return if (r is ControlLayoutIO.ImportResult.Ok) r.buttons else null
    }

    private fun JsonObject.stringOrEmpty(key: String): String {
        val e = get(key)
        return if (e != null && e.isJsonPrimitive) e.asString else ""
    }
}
