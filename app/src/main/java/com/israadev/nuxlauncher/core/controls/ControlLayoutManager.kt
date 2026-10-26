package com.israadev.nuxlauncher.core.controls

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.israadev.nuxlauncher.core.controls.models.ControlShowMode
import com.israadev.nuxlauncher.core.controls.models.CustomControlButton
import com.movtery.inputmap.keycodes.LwjglGlfwKeycode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.Executors

object ControlLayoutManager {
    private val gson = Gson()
    private val _buttons = MutableStateFlow<List<CustomControlButton>>(emptyList())
    val buttons: StateFlow<List<CustomControlButton>> = _buttons.asStateFlow()

    private fun getLayoutFile(context: Context): File {
        val dir = File(context.filesDir, "controls")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "default_layout.json")
    }

    fun init(context: Context) {
        val file = getLayoutFile(context)
        if (file.exists()) {
            try {
                val json = file.readText()
                val loaded = parseButtons(json)
                if (!loaded.isNullOrEmpty()) {
                    // Layout bawaan versi lama yang belum pernah diubah: ganti ke layout baru.
                    // Layout yang sudah diatur pengguna tidak disentuh.
                    if (isUntouchedDefault(loaded)) {
                        val fresh = getDefaultButtons()
                        _buttons.value = fresh
                        save(context, fresh)
                    } else {
                        _buttons.value = loaded
                    }
                    return
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Data internal tidak ada (mis. aplikasi baru dipasang ulang): coba pulihkan dari cadangan.
        val restored = readBackup()
        if (restored != null) {
            if (isUntouchedDefault(restored)) {
                val fresh = getDefaultButtons()
                _buttons.value = fresh
                save(context, fresh)
                return
            }
            _buttons.value = restored
            // Salin ke penyimpanan internal tanpa membuat cadangan baru.
            try { file.writeText(gson.toJson(restored)) } catch (_: Exception) {}
            return
        }

        // Tidak ada cadangan: pakai bawaan di memori saja. Berkas internal sengaja TIDAK
        // ditulis supaya pemulihan dicoba lagi nanti (mis. setelah izin penyimpanan diberikan).
        _buttons.value = getDefaultButtons()
    }

    private fun parseButtons(json: String): List<CustomControlButton>? {
        val type = object : TypeToken<List<CustomControlButton>>() {}.type
        return gson.fromJson<List<CustomControlButton>>(json, type)
    }

    // ---- Cadangan otomatis ------------------------------------------------------------
    // Disimpan di luar Android/data supaya tetap ada walau aplikasi dihapus.
    // Ditulis hanya saat pengguna menyimpan layout (jarang, berkas kecil) dan di thread latar.
    private const val BACKUP_DIR = "Documents/LauncherBackup"
    private const val BACKUP_FILE = "controls_layout.json"
    private const val BACKUP_PREV_FILE = "controls_layout.prev.json"

    private val backupExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "controls-backup").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }
    }

    private fun backupDir(): File =
        File(Environment.getExternalStorageDirectory(), BACKUP_DIR)

    private fun backupAsync(json: String) {
        backupExecutor.execute {
            try {
                val dir = backupDir()
                if (!dir.exists() && !dir.mkdirs()) return@execute
                val target = File(dir, BACKUP_FILE)
                // Lewati kalau isinya sudah sama (tidak perlu menulis ulang).
                if (target.isFile && target.readText() == json) return@execute
                val tmp = File(dir, "$BACKUP_FILE.tmp")
                tmp.writeText(json)
                if (target.exists()) {
                    // Simpan 1 versi sebelumnya sebagai cadangan kedua.
                    val prev = File(dir, BACKUP_PREV_FILE)
                    prev.delete()
                    target.renameTo(prev)
                }
                if (!tmp.renameTo(target)) {
                    target.writeText(json)
                    tmp.delete()
                }
            } catch (_: Throwable) {
                // Cadangan bersifat opsional: gagal (mis. tanpa izin penyimpanan) tidak mengganggu.
            }
        }
    }

    private fun readBackup(): List<CustomControlButton>? {
        for (name in listOf(BACKUP_FILE, BACKUP_PREV_FILE)) {
            try {
                val f = File(backupDir(), name)
                if (f.isFile && f.length() > 2) {
                    val list = parseButtons(f.readText())
                    if (!list.isNullOrEmpty()) return list
                }
            } catch (_: Throwable) {}
        }
        return null
    }

    /**
     * Tata letak bawaan: sedikit tombol, besar, mudah dijangkau jempol.
     * Kiri: joystick + jongkok. Kanan: serang, pakai, lompat, gulir hotbar.
     * Atas: lencana FPS, ESC/chat/inventori/F5 di tengah, tombol sistem kecil di kanan.
     * Dua mode: tombol gerak dan aksi hanya tampil saat main; saat menu (inventori, chat, jeda)
     * tersisa ESC, inventori, dan tombol Shift untuk memindah barang cepat.
     */
    fun getDefaultButtons(): List<CustomControlButton> {
        return listOf(
            CustomControlButton(
                id = "sys_fps", name = "FPS",
                xPercent = 11f, yPercent = 9f, widthDp = 84, heightDp = 26,
                opacity = 0.9f, cornerRadiusDp = 13,
                isSystem = true, systemAction = "FPS"
            ),

            // Tombol pintas di tengah atas
            CustomControlButton(
                id = "key_esc", name = "ESC", icon = "pause",
                keyCode = LwjglGlfwKeycode.GLFW_KEY_ESCAPE,
                xPercent = 41f, yPercent = 9f, widthDp = 38, heightDp = 34,
                opacity = 0.9f, cornerRadiusDp = 12
            ),
            CustomControlButton(
                id = "key_t", name = "CHAT", icon = "chat",
                keyCode = LwjglGlfwKeycode.GLFW_KEY_T,
                xPercent = 47f, yPercent = 9f, widthDp = 38, heightDp = 34,
                opacity = 0.9f, cornerRadiusDp = 12,
                showMode = ControlShowMode.GAME
            ),
            CustomControlButton(
                id = "key_inv", name = "INV", icon = "inventory",
                keyCode = LwjglGlfwKeycode.GLFW_KEY_E,
                xPercent = 53f, yPercent = 9f, widthDp = 38, heightDp = 34,
                opacity = 0.9f, cornerRadiusDp = 12
            ),
            CustomControlButton(
                id = "key_f5", name = "F5",
                keyCode = LwjglGlfwKeycode.GLFW_KEY_F5,
                xPercent = 59f, yPercent = 9f, widthDp = 38, heightDp = 34,
                opacity = 0.9f, cornerRadiusDp = 12,
                showMode = ControlShowMode.GAME
            ),

            // Tombol sistem kecil di kanan atas
            CustomControlButton(
                id = "sys_keyboard", name = "KEYBOARD",
                xPercent = 87f, yPercent = 9f, widthDp = 32, heightDp = 32,
                opacity = 0.9f, cornerRadiusDp = 16,
                isSystem = true, systemAction = "KEYBOARD"
            ),
            CustomControlButton(
                id = "sys_hide_gui", name = "HIDE GUI",
                xPercent = 91.5f, yPercent = 9f, widthDp = 32, heightDp = 32,
                opacity = 0.9f, cornerRadiusDp = 16,
                isSystem = true, systemAction = "HIDE_GUI"
            ),
            CustomControlButton(
                id = "sys_close", name = "MENU",
                xPercent = 96f, yPercent = 9f, widthDp = 32, heightDp = 32,
                opacity = 0.9f, cornerRadiusDp = 16,
                isSystem = true, systemAction = "MENU"
            ),

            // Kiri: gerak
            CustomControlButton(
                id = "joy_move", name = "JOYSTICK", isJoystick = true,
                xPercent = 15f, yPercent = 70f, widthDp = 120, heightDp = 120,
                opacity = 0.9f, cornerRadiusDp = 60,
                showMode = ControlShowMode.GAME
            ),
            CustomControlButton(
                id = "key_sneak", name = "SNEAK", icon = "sneak",
                keyCode = LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT, isToggle = true,
                xPercent = 29.5f, yPercent = 82f, widthDp = 52, heightDp = 52,
                opacity = 0.9f, cornerRadiusDp = 26,
                showMode = ControlShowMode.GAME
            ),

            // Saat menu terbuka (inventori, peti): tahan Shift lalu ketuk barang = pindah cepat.
            CustomControlButton(
                id = "key_shift_menu", name = "SHIFT",
                keyCode = LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT, isToggle = true,
                xPercent = 12f, yPercent = 80f, widthDp = 56, heightDp = 44,
                opacity = 0.9f, cornerRadiusDp = 14,
                showMode = ControlShowMode.MENU
            ),

            // Kanan: aksi utama di sekitar jempol
            CustomControlButton(
                id = "mouse_attack", name = "SERANG", icon = "attack",
                isMouseButton = true, mouseButton = LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT,
                xPercent = 84f, yPercent = 62f, widthDp = 72, heightDp = 72,
                opacity = 0.9f, cornerRadiusDp = 36,
                showMode = ControlShowMode.GAME
            ),
            CustomControlButton(
                id = "mouse_use", name = "PAKAI", icon = "use",
                isMouseButton = true, mouseButton = LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_RIGHT,
                xPercent = 75.5f, yPercent = 84f, widthDp = 60, heightDp = 60,
                opacity = 0.9f, cornerRadiusDp = 30,
                showMode = ControlShowMode.GAME
            ),
            CustomControlButton(
                id = "key_jump", name = "JUMP", icon = "jump",
                keyCode = LwjglGlfwKeycode.GLFW_KEY_SPACE,
                xPercent = 92f, yPercent = 80f, widthDp = 70, heightDp = 70,
                opacity = 0.9f, cornerRadiusDp = 35,
                showMode = ControlShowMode.GAME
            ),
            CustomControlButton(
                id = "widget_scroll", name = "HOTBAR", isScroll = true,
                xPercent = 96.5f, yPercent = 36f, widthDp = 40, heightDp = 92,
                opacity = 0.9f, cornerRadiusDp = 14,
                showMode = ControlShowMode.GAME
            )
        )
    }

    /** Layout yang tersimpan masih persis salah satu bawaan versi lama (belum pernah diubah pengguna). */
    private fun isUntouchedDefault(list: List<CustomControlButton>): Boolean =
        list == legacyDefaultButtons() || list == previousDefaultButtons()

    /**
     * Bawaan sebelum ada mode tampil (semua tombol selalu tampil, tanpa tombol Shift menu). Pemakai yang
     * tata letaknya masih ini dipindah ke bawaan dua mode; yang sudah mengubahnya tidak disentuh.
     */
    private fun previousDefaultButtons(): List<CustomControlButton> =
        getDefaultButtons()
            .filter { it.id != "key_shift_menu" }
            .map { it.copy(showMode = ControlShowMode.ALWAYS) }

    /** Tata letak bawaan versi lama; dipakai hanya untuk mengenali layout yang belum pernah diubah pengguna. */
    private fun legacyDefaultButtons(): List<CustomControlButton> {
        return listOf(
            // Top-Left FPS Indicator
            CustomControlButton(
                id = "sys_fps",
                name = "FPS",
                xPercent = 6f,
                yPercent = 7f,
                widthDp = 64,
                heightDp = 28,
                opacity = 0.85f,
                cornerRadiusDp = 8,
                isSystem = true,
                systemAction = "FPS"
            ),

            // Top-Left Utility
            CustomControlButton(
                id = "key_esc",
                name = "ESC",
                keyCode = LwjglGlfwKeycode.GLFW_KEY_ESCAPE,
                xPercent = 6f,
                yPercent = 20f,
                widthDp = 46,
                heightDp = 38,
                opacity = 0.85f,
                cornerRadiusDp = 8
            ),

            // Left Movement & Modifier Cluster (Clean D-Pad with Center SHIFT and Top-Left SPRINT)
            CustomControlButton(
                id = "key_ctrl",
                name = "SPRINT",
                keyCode = LwjglGlfwKeycode.GLFW_KEY_LEFT_CONTROL,
                xPercent = 6f,
                yPercent = 52f,
                widthDp = 48,
                heightDp = 48,
                opacity = 0.85f,
                cornerRadiusDp = 10,
                isToggle = true
            ),
            CustomControlButton(
                id = "key_w",
                name = "W",
                keyCode = LwjglGlfwKeycode.GLFW_KEY_W,
                xPercent = 14f,
                yPercent = 52f,
                widthDp = 48,
                heightDp = 48,
                opacity = 0.85f,
                cornerRadiusDp = 10
            ),
            CustomControlButton(
                id = "key_a",
                name = "A",
                keyCode = LwjglGlfwKeycode.GLFW_KEY_A,
                xPercent = 6f,
                yPercent = 70f,
                widthDp = 48,
                heightDp = 48,
                opacity = 0.85f,
                cornerRadiusDp = 10
            ),
            CustomControlButton(
                id = "key_shift",
                name = "SHIFT",
                keyCode = LwjglGlfwKeycode.GLFW_KEY_LEFT_SHIFT,
                xPercent = 14f,
                yPercent = 70f,
                widthDp = 48,
                heightDp = 48,
                opacity = 0.85f,
                cornerRadiusDp = 10,
                isToggle = true
            ),
            CustomControlButton(
                id = "key_d",
                name = "D",
                keyCode = LwjglGlfwKeycode.GLFW_KEY_D,
                xPercent = 22f,
                yPercent = 70f,
                widthDp = 48,
                heightDp = 48,
                opacity = 0.85f,
                cornerRadiusDp = 10
            ),
            CustomControlButton(
                id = "key_s",
                name = "S",
                keyCode = LwjglGlfwKeycode.GLFW_KEY_S,
                xPercent = 14f,
                yPercent = 88f,
                widthDp = 48,
                heightDp = 48,
                opacity = 0.85f,
                cornerRadiusDp = 10
            ),

            // Top-Center Quick Actions (Chat, Enter, Zoom)
            CustomControlButton(
                id = "key_t",
                name = "T",
                keyCode = LwjglGlfwKeycode.GLFW_KEY_T,
                xPercent = 46f,
                yPercent = 22f,
                widthDp = 40,
                heightDp = 40,
                opacity = 0.85f,
                cornerRadiusDp = 10
            ),
            CustomControlButton(
                id = "key_enter",
                name = "ENTER",
                keyCode = LwjglGlfwKeycode.GLFW_KEY_ENTER,
                xPercent = 52f,
                yPercent = 22f,
                widthDp = 50,
                heightDp = 40,
                opacity = 0.85f,
                cornerRadiusDp = 10
            ),
            CustomControlButton(
                id = "key_c",
                name = "C",
                keyCode = LwjglGlfwKeycode.GLFW_KEY_C,
                xPercent = 58f,
                yPercent = 22f,
                widthDp = 40,
                heightDp = 40,
                opacity = 0.85f,
                cornerRadiusDp = 10
            ),

            // Middle-Right Utility & Scroll (F5 / F3 stacked next to vertical scroll)
            CustomControlButton(
                id = "key_f5",
                name = "F5",
                keyCode = LwjglGlfwKeycode.GLFW_KEY_F5,
                xPercent = 56f,
                yPercent = 67f,
                widthDp = 44,
                heightDp = 34,
                opacity = 0.85f,
                cornerRadiusDp = 8
            ),
            CustomControlButton(
                id = "key_f3",
                name = "F3",
                keyCode = LwjglGlfwKeycode.GLFW_KEY_F3,
                xPercent = 56f,
                yPercent = 79f,
                widthDp = 44,
                heightDp = 34,
                opacity = 0.85f,
                cornerRadiusDp = 8
            ),
            CustomControlButton(
                id = "widget_scroll",
                name = "SCROLL",
                xPercent = 62f,
                yPercent = 73f,
                widthDp = 42,
                heightDp = 76,
                opacity = 0.85f,
                cornerRadiusDp = 12,
                isScroll = true
            ),

            // Top-Right System Controls
            CustomControlButton(
                id = "sys_keyboard",
                name = "KEYBOARD",
                xPercent = 78f,
                yPercent = 7f,
                widthDp = 76,
                heightDp = 28,
                opacity = 0.85f,
                cornerRadiusDp = 8,
                isSystem = true,
                systemAction = "KEYBOARD"
            ),
            CustomControlButton(
                id = "sys_hide_gui",
                name = "HIDE GUI",
                xPercent = 89f,
                yPercent = 7f,
                widthDp = 72,
                heightDp = 28,
                opacity = 0.85f,
                cornerRadiusDp = 8,
                isSystem = true,
                systemAction = "HIDE_GUI"
            ),
            CustomControlButton(
                id = "sys_close",
                name = "✕",
                xPercent = 96.5f,
                yPercent = 7f,
                widthDp = 30,
                heightDp = 28,
                opacity = 0.85f,
                cornerRadiusDp = 8,
                isSystem = true,
                systemAction = "CLOSE"
            ),

            // Right-Side Mouse Clicks (Underneath KEYBOARD and HIDE GUI)
            CustomControlButton(
                id = "mouse_left",
                name = "L-CLICK",
                isMouseButton = true,
                mouseButton = LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT,
                xPercent = 78f,
                yPercent = 22f,
                widthDp = 56,
                heightDp = 48,
                opacity = 0.85f,
                cornerRadiusDp = 10
            ),
            CustomControlButton(
                id = "mouse_right",
                name = "R-CLICK",
                isMouseButton = true,
                mouseButton = LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_RIGHT,
                xPercent = 87f,
                yPercent = 22f,
                widthDp = 56,
                heightDp = 48,
                opacity = 0.85f,
                cornerRadiusDp = 10
            ),

            // Bottom-Right In-Game Actions
            CustomControlButton(
                id = "key_inv",
                name = "INV",
                keyCode = LwjglGlfwKeycode.GLFW_KEY_E,
                xPercent = 77f,
                yPercent = 74f,
                widthDp = 54,
                heightDp = 48,
                opacity = 0.85f,
                cornerRadiusDp = 10
            ),
            CustomControlButton(
                id = "key_jump",
                name = "JUMP",
                keyCode = LwjglGlfwKeycode.GLFW_KEY_SPACE,
                xPercent = 87f,
                yPercent = 74f,
                widthDp = 64,
                heightDp = 50,
                opacity = 0.85f,
                cornerRadiusDp = 10
            )
        )
    }

    fun resetToDefaults(context: Context) {
        val defaults = getDefaultButtons()
        _buttons.value = defaults
        save(context, defaults)
    }

    fun saveButtons(context: Context, newButtons: List<CustomControlButton>) {
        _buttons.value = newButtons
        save(context, newButtons)
    }

    /**
     * Layout tersimpan sekarang dalam bentuk berkas ekspor (JSON). Dimuat dari penyimpanan dulu
     * bila belum pernah dimuat di proses ini. Panggil dari thread latar.
     */
    fun exportJson(context: Context): String {
        if (_buttons.value.isEmpty()) init(context)
        return ControlLayoutIO.toJson(_buttons.value)
    }

    /** Menulis layout tersimpan ke berkas pilihan pengguna (lewat pemilih berkas sistem). Panggil dari thread latar. */
    fun exportTo(context: Context, uri: Uri): Boolean {
        return try {
            val bytes = exportJson(context).toByteArray(Charsets.UTF_8)
            val stream = context.contentResolver.openOutputStream(uri, "wt") ?: return false
            stream.use { it.write(bytes) }
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Membaca dan memeriksa berkas layout pilihan pengguna tanpa mengubah apa pun. Pembacaan dibatasi
     * [ControlLayoutIO.MAX_FILE_BYTES] supaya berkas raksasa tidak memenuhi memori. Panggil dari thread latar.
     */
    fun readImport(context: Context, uri: Uri): ControlLayoutIO.ImportResult {
        return try {
            val input = context.contentResolver.openInputStream(uri)
                ?: return ControlLayoutIO.ImportResult.Failed(ControlLayoutIO.Reason.NOT_LAYOUT)
            val out = java.io.ByteArrayOutputStream()
            input.use {
                val buf = ByteArray(8 * 1024)
                while (out.size() <= ControlLayoutIO.MAX_FILE_BYTES) {
                    val n = it.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                }
            }
            if (out.size() > ControlLayoutIO.MAX_FILE_BYTES) {
                return ControlLayoutIO.ImportResult.Failed(ControlLayoutIO.Reason.TOO_LARGE)
            }
            ControlLayoutIO.parse(String(out.toByteArray(), Charsets.UTF_8).removePrefix("﻿"))
        } catch (_: Exception) {
            ControlLayoutIO.ImportResult.Failed(ControlLayoutIO.Reason.NOT_LAYOUT)
        }
    }

    /**
     * Mengganti seluruh layout dengan hasil impor lalu menyimpannya. Tombol sistem (FPS, keyboard,
     * sembunyikan GUI, menu) yang tidak ada di berkas ditambahkan lagi dari bawaan, supaya layout
     * milik orang lain tidak bisa membuat menu game tidak terjangkau. Panggil dari thread latar.
     */
    fun importLayout(context: Context, imported: List<CustomControlButton>): List<CustomControlButton> {
        fun actionOf(b: CustomControlButton) = if (b.systemAction == "CLOSE") "MENU" else b.systemAction

        val result = ArrayList<CustomControlButton>(imported)
        val present = result.filter { it.isSystem }.map { actionOf(it) }.toSet()
        for (d in getDefaultButtons()) {
            if (!d.isSystem || actionOf(d) in present) continue
            var id = d.id
            var n = 2
            while (result.any { it.id == id }) id = d.id + "_" + n++
            result.add(if (id == d.id) d else d.copy(id = id))
        }
        saveButtons(context, result)
        return result
    }

    fun addButton(context: Context, button: CustomControlButton) {
        val current = _buttons.value.toMutableList()
        current.add(button)
        saveButtons(context, current)
    }

    fun updateButton(context: Context, updated: CustomControlButton) {
        val current = _buttons.value.toMutableList()
        val index = current.indexOfFirst { it.id == updated.id }
        if (index >= 0) {
            current[index] = updated
            saveButtons(context, current)
        }
    }

    fun deleteButton(context: Context, buttonId: String) {
        val current = _buttons.value.filter { it.id != buttonId }
        saveButtons(context, current)
    }

    private fun save(context: Context, list: List<CustomControlButton>) {
        try {
            val file = getLayoutFile(context)
            val json = gson.toJson(list)
            file.writeText(json)
            if (list.isNotEmpty()) backupAsync(json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
