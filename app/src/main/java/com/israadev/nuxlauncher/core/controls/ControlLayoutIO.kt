package com.israadev.nuxlauncher.core.controls

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.israadev.nuxlauncher.core.controls.models.ControlShowMode
import com.israadev.nuxlauncher.core.controls.models.CustomControlButton
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Ekspor dan impor tata letak tombol kontrol sebagai berkas JSON yang bisa dibagikan.
 *
 * Penulisan dan pembacaan memakai nama kolom yang tertulis di sini (bukan refleksi pada kelas
 * model), jadi berkas tetap terbaca walau nama kelas berubah atau kode diperkecil (R8). Berkas dari
 * orang lain tidak dipercaya: setiap kolom dicek tipenya, dibatasi rentangnya, dan kolom yang
 * hilang diisi nilai bawaan model. Tidak ada refleksi, tidak ada kelas yang dimuat dari berkas.
 *
 * Murni logika (tanpa Android) supaya mudah diuji.
 */
object ControlLayoutIO {
    const val FORMAT = "amber-controls"
    const val VERSION = 1

    /** Batas ukuran berkas yang dibaca. Layout normal hanya beberapa KB. */
    const val MAX_FILE_BYTES = 512 * 1024
    const val MAX_BUTTONS = 200

    private const val MAX_ID = 64
    private const val MAX_NAME = 32
    private const val MAX_ICON = 24
    private const val MAX_COMMAND = 256
    private const val MAX_COMBO_KEYS = 32
    private const val MIN_KEYCODE = -1
    private const val MAX_KEYCODE = 1024
    private const val MIN_TURBO_MS = 40L
    private const val MAX_TURBO_MS = 1000L

    private val MACRO_TYPES = setOf("COMMAND", "COMBO", "TURBO")
    private val SYSTEM_ACTIONS = setOf("FPS", "KEYBOARD", "HIDE_GUI", "MENU", "CLOSE")

    enum class Reason { TOO_LARGE, NOT_LAYOUT, NO_BUTTONS }

    sealed class ImportResult {
        /**
         * @param skipped jumlah entri yang dilewati (bukan objek, atau melebihi [MAX_BUTTONS]).
         * @param macroCommands jumlah tombol makro pengetik chat/perintah, ditampilkan sebagai peringatan.
         */
        class Ok(
            val buttons: List<CustomControlButton>,
            val skipped: Int,
            val macroCommands: Int
        ) : ImportResult()

        class Failed(val reason: Reason) : ImportResult()
    }

    // Tanpa escape HTML supaya perintah makro seperti "a=b" tetap terbaca di berkas.
    private val pretty = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    // ---- Ekspor ---------------------------------------------------------------------------

    fun toJson(buttons: List<CustomControlButton>): String {
        val root = JsonObject()
        root.addProperty("format", FORMAT)
        root.addProperty("version", VERSION)
        val arr = JsonArray()
        for (b in buttons) arr.add(buttonToJson(b))
        root.add("buttons", arr)
        return pretty.toJson(root)
    }

    private fun buttonToJson(b: CustomControlButton): JsonObject {
        val o = JsonObject()
        o.addProperty("id", b.id)
        o.addProperty("name", b.name)
        o.addProperty("keyCode", b.keyCode)
        o.addProperty("isMouseButton", b.isMouseButton)
        o.addProperty("mouseButton", b.mouseButton)
        o.addProperty("xPercent", b.xPercent)
        o.addProperty("yPercent", b.yPercent)
        o.addProperty("widthDp", b.widthDp)
        o.addProperty("heightDp", b.heightDp)
        o.addProperty("opacity", b.opacity)
        o.addProperty("cornerRadiusDp", b.cornerRadiusDp)
        o.addProperty("isToggle", b.isToggle)
        o.addProperty("isScroll", b.isScroll)
        o.addProperty("isSystem", b.isSystem)
        o.addProperty("systemAction", b.systemAction)
        o.addProperty("isJoystick", b.isJoystick)
        o.addProperty("autoSprint", b.autoSprint)
        o.addProperty("isMacro", b.isMacro)
        o.addProperty("macroType", b.macroType)
        o.addProperty("macroCommand", b.macroCommand)
        o.addProperty("macroComboKey", b.macroComboKey)
        val combo = JsonArray()
        for (k in b.macroComboKeys) combo.add(k)
        o.add("macroComboKeys", combo)
        o.addProperty("macroTurboIntervalMs", b.macroTurboIntervalMs)
        o.addProperty("icon", b.icon)
        o.addProperty("showMode", b.showMode)
        return o
    }

    // ---- Impor ----------------------------------------------------------------------------

    /**
     * Membaca isi berkas. Menerima dua bentuk: berkas ekspor (objek dengan "format") dan larik
     * telanjang seperti berkas cadangan otomatis (controls_layout.json).
     */
    fun parse(json: String): ImportResult {
        if (json.length > MAX_FILE_BYTES) return ImportResult.Failed(Reason.TOO_LARGE)

        val root = try {
            JsonParser.parseString(json)
        } catch (_: Exception) {
            return ImportResult.Failed(Reason.NOT_LAYOUT)
        }

        val arr: JsonArray = if (root.isJsonArray) {
            root.asJsonArray
        } else if (root.isJsonObject) {
            val o = root.asJsonObject
            if (o.str("format", "", 32) != FORMAT) return ImportResult.Failed(Reason.NOT_LAYOUT)
            val b = o.get("buttons")
            if (b == null || !b.isJsonArray) return ImportResult.Failed(Reason.NOT_LAYOUT)
            b.asJsonArray
        } else {
            return ImportResult.Failed(Reason.NOT_LAYOUT)
        }

        val usedIds = HashSet<String>()
        val out = ArrayList<CustomControlButton>()
        var skipped = 0
        var macros = 0
        for (el in arr) {
            if (out.size >= MAX_BUTTONS || !el.isJsonObject) {
                skipped++
                continue
            }
            val b = parseButton(el.asJsonObject, usedIds)
            if (b.isMacro && b.macroType == "COMMAND" && b.macroCommand.isNotBlank()) macros++
            out.add(b)
        }
        if (out.isEmpty()) return ImportResult.Failed(Reason.NO_BUTTONS)
        return ImportResult.Ok(out, skipped, macros)
    }

    private fun parseButton(o: JsonObject, usedIds: MutableSet<String>): CustomControlButton {
        val d = CustomControlButton()

        var id = o.str("id", "", MAX_ID).trim()
        if (id.isEmpty() || !usedIds.add(id)) {
            id = "btn_" + UUID.randomUUID().toString().take(8)
            usedIds.add(id)
        }

        val isJoystick = o.bool("isJoystick", d.isJoystick)
        val maxW = if (isJoystick) ControlLimits.MAX_JOYSTICK_DP else ControlLimits.MAX_WIDTH_DP
        val maxH = if (isJoystick) ControlLimits.MAX_JOYSTICK_DP else ControlLimits.MAX_HEIGHT_DP

        val isSystem = o.bool("isSystem", d.isSystem)
        var systemAction = o.str("systemAction", d.systemAction, 16)
        if (!isSystem || systemAction !in SYSTEM_ACTIONS) systemAction = ""

        var macroType = o.str("macroType", d.macroType, 16)
        if (macroType !in MACRO_TYPES) macroType = d.macroType

        // Mode tampil hanya berlaku untuk tombol biasa. Tombol sistem (menu, FPS, dll.) selalu tampil
        // supaya layout orang lain tidak bisa membuat menu game tidak terjangkau.
        var showMode = o.str("showMode", d.showMode, 8)
        if (isSystem || !ControlShowMode.isValid(showMode)) showMode = ControlShowMode.ALWAYS

        return CustomControlButton(
            id = id,
            name = o.str("name", d.name, MAX_NAME),
            keyCode = o.int("keyCode", d.keyCode, MIN_KEYCODE, MAX_KEYCODE),
            isMouseButton = o.bool("isMouseButton", d.isMouseButton),
            mouseButton = o.int("mouseButton", d.mouseButton, 0, 7),
            xPercent = o.float("xPercent", d.xPercent, 0f, 100f),
            yPercent = o.float("yPercent", d.yPercent, 0f, 100f),
            widthDp = o.int("widthDp", d.widthDp, ControlLimits.MIN_SIZE_DP, maxW),
            heightDp = o.int("heightDp", d.heightDp, ControlLimits.MIN_SIZE_DP, maxH),
            opacity = o.float("opacity", d.opacity, ControlLimits.MIN_OPACITY, 1f),
            cornerRadiusDp = o.int("cornerRadiusDp", d.cornerRadiusDp, 0, ControlLimits.MAX_JOYSTICK_DP / 2),
            isToggle = o.bool("isToggle", d.isToggle),
            isScroll = o.bool("isScroll", d.isScroll),
            isSystem = isSystem,
            systemAction = systemAction,
            isJoystick = isJoystick,
            autoSprint = o.bool("autoSprint", d.autoSprint),
            isMacro = o.bool("isMacro", d.isMacro),
            macroType = macroType,
            macroCommand = o.str("macroCommand", d.macroCommand, MAX_COMMAND),
            macroComboKey = o.int("macroComboKey", d.macroComboKey, 0, MAX_KEYCODE),
            macroComboKeys = o.intList("macroComboKeys", MAX_COMBO_KEYS),
            macroTurboIntervalMs = o.long("macroTurboIntervalMs", d.macroTurboIntervalMs, MIN_TURBO_MS, MAX_TURBO_MS),
            icon = o.str("icon", d.icon, MAX_ICON),
            showMode = showMode
        )
    }

    // ---- Pembaca kolom yang toleran ---------------------------------------------------------

    private fun JsonObject.str(key: String, def: String, max: Int): String {
        val e = get(key)
        if (e == null || !e.isJsonPrimitive) return def
        return e.asString.take(max)
    }

    private fun JsonObject.bool(key: String, def: Boolean): Boolean {
        val e = get(key)
        if (e == null || !e.isJsonPrimitive) return def
        val p = e.asJsonPrimitive
        return if (p.isBoolean) p.asBoolean else def
    }

    private fun JsonObject.num(key: String): Double? {
        val e = get(key)
        if (e == null || !e.isJsonPrimitive) return null
        val p = e.asJsonPrimitive
        if (!p.isNumber) return null
        val v = p.asDouble
        return if (v.isNaN() || v.isInfinite()) null else v
    }

    private fun JsonObject.int(key: String, def: Int, min: Int, max: Int): Int {
        val v = num(key) ?: return def
        return v.roundToInt().coerceIn(min, max)
    }

    private fun JsonObject.long(key: String, def: Long, min: Long, max: Long): Long {
        val v = num(key) ?: return def
        return v.toLong().coerceIn(min, max)
    }

    private fun JsonObject.float(key: String, def: Float, min: Float, max: Float): Float {
        val v = num(key) ?: return def
        return v.toFloat().coerceIn(min, max)
    }

    private fun JsonObject.intList(key: String, maxItems: Int): List<Int> {
        val e = get(key)
        if (e == null || !e.isJsonArray) return emptyList()
        val out = ArrayList<Int>()
        for (item in e.asJsonArray) {
            if (out.size >= maxItems) break
            if (!item.isJsonPrimitive) continue
            val p = item.asJsonPrimitive
            if (!p.isNumber) continue
            val v = p.asDouble
            if (v.isNaN() || v.isInfinite()) continue
            out.add(v.roundToInt().coerceIn(0, MAX_KEYCODE))
        }
        return out
    }
}
