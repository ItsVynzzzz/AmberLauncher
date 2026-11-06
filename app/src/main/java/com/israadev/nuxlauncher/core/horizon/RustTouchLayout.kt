package com.israadev.nuxlauncher.core.horizon

import android.content.Context
import com.israadev.nuxlauncher.core.controls.ControlLayoutManager
import com.israadev.nuxlauncher.core.controls.models.ControlShowMode
import com.israadev.nuxlauncher.core.controls.models.CustomControlButton
import com.israadev.nuxlauncher.core.crash.LauncherLog
import com.movtery.inputmap.keycodes.LwjglGlfwKeycode as Key
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.min

/**
 * Membuat tombol layar mesin Rust mengikuti tata letak kontrol Amber, tanpa mengubah mesin dan tanpa lapisan
 * tambahan di atasnya.
 *
 * Mesin menggambar dan menangani sentuhan tombolnya sendiri (itu satu-satunya cara tombol bisa disentuh di dalam
 * jendela mesin), tetapi posisi, ukuran, dan tombol mana yang tampil dibaca dari `horizon-touch.json` di folder
 * data mesin. Di sini tata letak Amber diterjemahkan ke berkas itu sebelum mesin dibuka (saat PLAY):
 *
 * - Joystick, serang, pakai, lompat, jongkok, lari, inventori, chat, ESC, F3, F5, dan tukar tangan memakai tombol
 *   mesin yang sesuai, di posisi dan ukuran milik Amber.
 * - Tombol keyboard/mouse lain di tata letak Amber ditambahkan sebagai tombol kustom mesin.
 * - Tombol mesin yang tidak ada di tata letak Amber disembunyikan, kecuali gerak, lompat, serang, dan pakai
 *   (tanpa itu game tidak bisa dimainkan; kalau tidak ada di Amber, tombol mesin dipakai di tempat bawaannya).
 *
 * Hanya kunci yang diperlukan yang ditulis; kunci lain di berkas itu dibiarkan. Berkas aslinya disalin dulu ke
 * `horizon-touch.amber-backup.json` dan dipulihkan saat pilihan ini dimatikan. Tidak ada pekerjaan selain
 * satu kali tulis berkas kecil per PLAY, dan setiap kegagalan hanya dicatat (game tetap dibuka).
 */
object RustTouchLayout {
    private const val TAG = "RustTouchLayout"
    private const val CONFIG_FILE = "android-launcher.json"
    private const val TOUCH_FILE = "horizon-touch.json"
    private const val BACKUP_FILE = "horizon-touch.amber-backup.json"
    private const val TEMP_FILE = "horizon-touch.amber-tmp"

    // Urutan kontrol di mesin (enum Control): dipakai sebagai indeks larik tata letak dan visibilitas.
    private const val MOVE = 0
    private const val SPRINT = 1
    private const val JUMP = 2
    private const val SNEAK = 3
    private const val ATTACK = 4
    private const val USE = 5
    private const val CHAT = 6
    private const val PAUSE = 7
    private const val INVENTORY = 9
    private const val DEBUG = 10
    private const val PERSPECTIVE = 11
    private const val OFFHAND = 12
    private const val CONTROLS = 14
    private const val MAX_CUSTOM = 32

    // Nilai button_visibility mesin: 0 otomatis, 1 selalu tampil, 2 disembunyikan.
    private const val SHOWN = 1
    private const val HIDDEN = 2

    /** Gerak, lompat, serang, dan pakai: tanpa ini game tidak bisa dimainkan lewat sentuhan. */
    private val ESSENTIAL = intArrayOf(MOVE, JUMP, ATTACK, USE)

    /** Tata letak bawaan mesin (x, y, ukuran), untuk kontrol yang tidak diatur Amber dan sebagai isi awal. */
    private val DEFAULTS = arrayOf(
        floatArrayOf(0.13f, 0.76f, 0.32f),
        floatArrayOf(0.72f, 0.78f, 0.105f),
        floatArrayOf(0.92f, 0.78f, 0.16f),
        floatArrayOf(0.81f, 0.86f, 0.115f),
        floatArrayOf(0.80f, 0.64f, 0.16f),
        floatArrayOf(0.92f, 0.52f, 0.16f),
        floatArrayOf(0.46f, 0.055f, 0.075f),
        floatArrayOf(0.50f, 0.055f, 0.075f),
        floatArrayOf(0.54f, 0.055f, 0.075f),
        floatArrayOf(0.73f, 0.95f, 0.075f),
        floatArrayOf(0.42f, 0.055f, 0.075f),
        floatArrayOf(0.58f, 0.055f, 0.075f),
        floatArrayOf(0.80f, 0.96f, 0.075f),
        floatArrayOf(0.92f, 0.36f, 0.12f)
    )
    private const val DEFAULT_OPACITY = 0.65f

    /** Satu penempatan: pusat (x, y) 0..1 dari layar, ukuran sebagai pecahan tinggi layar, dan kepekatan. */
    private class Place(val x: Float, val y: Float, val size: Float, val opacity: Float)

    private class Plan(
        val slots: Array<Place?>,
        val custom: List<Pair<String, Place>>,
        val autoSprint: Boolean?
    )

    /**
     * Dipanggil tepat sebelum mesin dibuka. [enabled] = pilihan "Kontrol Amber" di Pengaturan Rust. Tidak pernah
     * melempar: kegagalan hanya dicatat dan mesin memakai tombol bawaannya.
     */
    fun sync(context: Context, enabled: Boolean) {
        try {
            apply(context.applicationContext, enabled)
        } catch (e: Throwable) {
            LauncherLog.w(TAG, "Tata letak kontrol Amber tidak bisa diterapkan ke mesin Rust: ${e.message}", e)
        }
    }

    private fun apply(context: Context, enabled: Boolean) {
        val home = HorizonEngine.engineHome(context)
        val config = readObject(File(home, CONFIG_FILE))
        val profile = config?.optInt("profile", 0) ?: 0
        val dir = if (profile > 0) File(home, "profiles/profile-$profile") else home
        if (!dir.isDirectory && (!enabled || !dir.mkdirs())) return

        val touch = File(dir, TOUCH_FILE)
        val backup = File(dir, BACKUP_FILE)
        if (!enabled) {
            restore(touch, backup)
            return
        }

        val buttons = ControlLayoutManager.buttons.value.ifEmpty {
            ControlLayoutManager.init(context)
            ControlLayoutManager.buttons.value
        }
        if (buttons.isEmpty()) return

        // Salinan berkas asli disimpan sekali, sebelum Amber menyentuhnya pertama kali.
        if (!backup.isFile) {
            writeAtomic(backup, if (touch.isFile) touch.readText() else "{}")
        }

        val plan = plan(buttons, surfaceHeightDp(context, config?.optString("orientation", "landscape")))
        val json = build(readObject(touch) ?: JSONObject(), plan)
        val text = json.toString(2)
        if (touch.isFile && touch.readText() == text) return
        writeAtomic(touch, text)
    }

    /** Mengembalikan berkas mesin seperti sebelum Amber menerapkan tata letaknya (bila pernah). */
    private fun restore(touch: File, backup: File) {
        if (!backup.isFile) return
        val saved = backup.readText().trim()
        if (saved.isEmpty() || saved == "{}") {
            // Dulu tidak ada berkasnya: mesin kembali ke bawaannya.
            touch.delete()
        } else {
            writeAtomic(touch, saved)
        }
        backup.delete()
    }

    // ---- terjemahan tata letak ----

    private fun plan(buttons: List<CustomControlButton>, heightDp: Float): Plan {
        val slots = arrayOfNulls<Place>(CONTROLS)
        val custom = ArrayList<Pair<String, Place>>()
        var autoSprint: Boolean? = null
        for (b in buttons) {
            if (b.isSystem || b.isScroll || b.isMacro) continue
            // Tombol khusus menu (mis. Shift di inventori) tidak punya padanan: mesin mengatur menunya sendiri.
            if (b.showMode == ControlShowMode.MENU) continue
            val place = placeOf(b, heightDp)
            val slot = slotOf(b)
            if (slot >= 0 && slots[slot] == null) {
                slots[slot] = place
                if (slot == MOVE) autoSprint = b.autoSprint
                continue
            }
            if (b.isJoystick) continue
            val token = tokenOf(b) ?: continue
            if (custom.size < MAX_CUSTOM && custom.none { it.first == token }) custom.add(token to place)
        }
        return Plan(slots, custom, autoSprint)
    }

    private fun placeOf(b: CustomControlButton, heightDp: Float): Place {
        val size = ((b.widthDp + b.heightDp) / 2f) / heightDp
        return Place(
            x = (b.xPercent / 100f).coerceIn(0.025f, 0.975f),
            y = (b.yPercent / 100f).coerceIn(0.025f, 0.975f),
            size = size.coerceIn(0.045f, 0.45f),
            opacity = b.opacity.coerceIn(0.1f, 1f)
        )
    }

    /** Tombol Amber ini sama dengan kontrol bawaan mesin yang mana? -1 = tidak ada padanannya. */
    private fun slotOf(b: CustomControlButton): Int = when {
        b.isJoystick -> MOVE
        b.isMouseButton -> when (b.mouseButton) {
            Key.GLFW_MOUSE_BUTTON_LEFT -> ATTACK
            Key.GLFW_MOUSE_BUTTON_RIGHT -> USE
            else -> -1
        }
        else -> when (b.keyCode) {
            Key.GLFW_KEY_SPACE -> JUMP
            Key.GLFW_KEY_LEFT_SHIFT -> SNEAK
            Key.GLFW_KEY_LEFT_CONTROL -> SPRINT
            Key.GLFW_KEY_E -> INVENTORY
            Key.GLFW_KEY_T -> CHAT
            Key.GLFW_KEY_ESCAPE -> PAUSE
            Key.GLFW_KEY_F3 -> DEBUG
            Key.GLFW_KEY_F5 -> PERSPECTIVE
            Key.GLFW_KEY_F -> OFFHAND
            else -> -1
        }
    }

    /** Token tombol kustom mesin untuk tombol Amber ini, atau null bila mesin tidak mengenal tombolnya. */
    private fun tokenOf(b: CustomControlButton): String? {
        if (b.isMouseButton) {
            return when (b.mouseButton) {
                Key.GLFW_MOUSE_BUTTON_LEFT -> "action:key.attack"
                Key.GLFW_MOUSE_BUTTON_RIGHT -> "action:key.use"
                Key.GLFW_MOUSE_BUTTON_MIDDLE -> "action:key.pickItem"
                else -> null
            }
        }
        val code = b.keyCode
        val name = when (code) {
            in 65..90 -> ('a' + (code - 65)).toString() // A..Z
            in 48..57 -> (code - 48).toString() // 0..9
            in 290..301 -> "f${code - 289}" // F1..F12
            32 -> "space"
            39 -> "apostrophe"
            44 -> "comma"
            45 -> "minus"
            46 -> "period"
            47 -> "slash"
            59 -> "semicolon"
            61 -> "equal"
            91 -> "left.bracket"
            92 -> "backslash"
            93 -> "right.bracket"
            96 -> "grave.accent"
            256 -> "escape"
            257 -> "enter"
            258 -> "tab"
            259 -> "backspace"
            260 -> "insert"
            261 -> "delete"
            262 -> "right"
            263 -> "left"
            264 -> "down"
            265 -> "up"
            266 -> "page.up"
            267 -> "page.down"
            268 -> "home"
            269 -> "end"
            280 -> "caps.lock"
            340 -> "left.shift"
            341 -> "left.control"
            342 -> "left.alt"
            344 -> "right.shift"
            345 -> "right.control"
            346 -> "right.alt"
            else -> return null
        }
        return "key.keyboard.$name"
    }

    /** Tinggi layar game dalam dp, sebagai pembagi ukuran tombol (mesin menyatakan ukuran terhadap tinggi layar). */
    private fun surfaceHeightDp(context: Context, orientation: String?): Float {
        val m = context.resources.displayMetrics
        val shortPx = min(m.widthPixels, m.heightPixels)
        val longPx = max(m.widthPixels, m.heightPixels)
        val portrait = orientation == "portrait" || orientation == "reverse-portrait"
        return max(1f, (if (portrait) longPx else shortPx) / m.density)
    }

    // ---- berkas mesin ----

    private fun build(json: JSONObject, plan: Plan): JSONObject {
        json.put("mode", "crosshair")
        json.put("split_controls", false)
        plan.autoSprint?.let { json.put("sprint_with_joystick", it) }

        // Tiga baris tata letak (satu per mode) wajib lengkap; hanya baris mode crosshair yang diubah.
        val rows = readRows(json.optJSONArray("layouts"))
        for (slot in 0 until CONTROLS) {
            plan.slots[slot]?.let { rows[0][slot] = floatArrayOf(it.x, it.y, it.size, it.opacity) }
        }
        val layouts = JSONArray()
        for (row in rows) {
            val out = JSONArray()
            for (p in row) out.put(placement(p[0], p[1], p[2], p[3]))
            layouts.put(out)
        }
        json.put("layouts", layouts)

        val visibility = JSONArray()
        for (slot in 0 until CONTROLS) {
            val shown = plan.slots[slot] != null || slot in ESSENTIAL
            visibility.put(if (shown) SHOWN else HIDDEN)
        }
        json.put("button_visibility", visibility)

        val custom = JSONArray()
        for ((token, p) in plan.custom) {
            // Satu penempatan per mode mesin (tiga); semuanya sama karena mode dikunci ke crosshair.
            val perMode = JSONArray()
            repeat(3) { perMode.put(placement(p.x, p.y, p.size, p.opacity)) }
            custom.put(JSONObject().put("key", token).put("layouts", perMode))
        }
        json.put("custom_buttons", custom)
        return json
    }

    /** Baris tata letak yang ada di berkas (dilengkapi sampai 14 kontrol), atau bawaan mesin bila tidak valid. */
    private fun readRows(array: JSONArray?): Array<Array<FloatArray>> {
        if (array == null || array.length() != 3) return defaultRows()
        val rows = defaultRows()
        for (r in 0 until 3) {
            val row = array.optJSONArray(r) ?: return defaultRows()
            val n = row.length()
            if (n != 10 && n != 12 && n != 14) return defaultRows()
            for (i in 0 until n) {
                val p = row.optJSONObject(i) ?: continue
                val c = p.optJSONArray("center") ?: continue
                if (c.length() != 2) continue
                val x = c.optDouble(0, Double.NaN)
                val y = c.optDouble(1, Double.NaN)
                val size = p.optDouble("size", Double.NaN)
                val opacity = p.optDouble("opacity", Double.NaN)
                if (x.isNaN() || y.isNaN() || size.isNaN() || opacity.isNaN()) continue
                rows[r][i] = floatArrayOf(x.toFloat(), y.toFloat(), size.toFloat(), opacity.toFloat())
            }
        }
        return rows
    }

    /** Tiga baris tata letak bawaan mesin; baris D-Pad (ketiga) punya lompat dan jongkok yang sedikit berbeda. */
    private fun defaultRows(): Array<Array<FloatArray>> {
        val rows = Array(3) {
            Array(CONTROLS) { i -> floatArrayOf(DEFAULTS[i][0], DEFAULTS[i][1], DEFAULTS[i][2], DEFAULT_OPACITY) }
        }
        rows[2][SNEAK] = floatArrayOf(0.13f, 0.76f, 0.10f, DEFAULT_OPACITY)
        rows[2][JUMP] = floatArrayOf(0.90f, 0.78f, 0.14f, DEFAULT_OPACITY)
        return rows
    }

    private fun placement(x: Float, y: Float, size: Float, opacity: Float): JSONObject =
        JSONObject()
            .put("center", JSONArray().put(round(x)).put(round(y)))
            .put("size", round(size))
            .put("opacity", round(opacity))

    private fun round(v: Float): Double = Math.round(v * 1000f) / 1000.0

    private fun readObject(file: File): JSONObject? {
        if (!file.isFile) return null
        return try {
            JSONObject(file.readText())
        } catch (_: Exception) {
            null
        }
    }

    /** Tulis ke berkas sementara lalu pindahkan, supaya mesin tidak pernah membaca separuh isi. */
    private fun writeAtomic(target: File, text: String) {
        val temp = File(target.parentFile, TEMP_FILE)
        temp.writeText(text)
        if (!temp.renameTo(target)) {
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) {
                temp.delete()
                throw IOException("Tidak bisa menyimpan $target")
            }
        }
    }
}
