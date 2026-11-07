package com.israadev.nuxlauncher.core.horizon

import android.content.Context
import com.israadev.nuxlauncher.core.controls.ControlLayoutManager
import com.israadev.nuxlauncher.core.controls.models.ControlShowMode
import com.israadev.nuxlauncher.core.controls.models.CustomControlButton
import com.israadev.nuxlauncher.core.crash.LauncherLog
import com.israadev.nuxlauncher.core.settings.SettingsManager
import com.movtery.inputmap.keycodes.LwjglGlfwKeycode as Key
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Membuat kontrol layar mesin Rust memakai tata letak DAN tampilan kontrol Amber, tanpa mengubah mesin dan tanpa
 * lapisan tambahan di atasnya.
 *
 * Mesin (Horizon 0.7.8 ke atas) punya gaya kontrol "GoyControl": joystick dan tombol statis yang bisa diatur
 * posisi, ukuran, warna, bentuk, dan labelnya lewat `horizon-touch.json` di folder data mesin. Di sini tata letak
 * Amber diterjemahkan ke gaya itu sebelum mesin dibuka (saat PLAY), jadi hanya ada satu set kontrol:
 *
 * - Joystick Amber menjadi joystick mesin (tetap di tempatnya, lari otomatis mengikuti pilihan Amber).
 * - Setiap tombol keyboard/mouse Amber menjadi tombol mesin di posisi dan ukuran yang sama, dengan label, sudut
 *   membulat, kaca gelap, garis putih tipis, dan warna kuning saat ditekan seperti tombol Amber. Pilihan tombol
 *   tahan/kunci (toggle), kombinasi tombol, dan kapan tampil (main/menu/selalu) ikut dibawa.
 * - Widget gulir hotbar menjadi dua tombol (naik/turun). Tombol sistem HIDE GUI menjadi F1 dan MENU menjadi ESC
 *   (mesin tidak punya menu Amber); FPS, keyboard, dan makro perintah/turbo tidak punya padanan dan dilewati.
 * - Geser dari tombol serang/pakai/lompat hanya ikut memutar kamera bila pilihan itu menyala di Pengaturan Amber.
 *
 * Hanya kunci yang diperlukan yang ditulis; kunci lain di berkas itu (sensitivitas, giroskop, dan seterusnya)
 * dibiarkan. Berkas aslinya disalin dulu ke `horizon-touch.amber-backup.json` dan dipulihkan saat pilihan ini
 * dimatikan. Tidak ada pekerjaan selain satu kali tulis berkas kecil per PLAY, dan setiap kegagalan hanya dicatat
 * (game tetap dibuka).
 */
object RustTouchLayout {
    private const val TAG = "RustTouchLayout"
    private const val CONFIG_FILE = "android-launcher.json"
    private const val TOUCH_FILE = "horizon-touch.json"
    private const val BACKUP_FILE = "horizon-touch.amber-backup.json"
    private const val TEMP_FILE = "horizon-touch.amber-tmp"

    private const val CONTROLS = 14 // jumlah kontrol bawaan mesin (enum Control)
    private const val MOVE = 0 // indeks kontrol gerak (joystick) di mesin
    private const val MAX_CUSTOM = 32 // batas tombol kustom mesin

    /** Tinggi ruang UI mesin dalam satuan "dp mesin" (mesin menargetkan 410..440): dasar skala sudut dan garis. */
    private const val ENGINE_UI_HEIGHT = 430f

    /** Tata letak bawaan mesin (x, y, ukuran) untuk 14 kontrol; hanya pengisi karena gaya Goy memakai tombol kustom. */
    private val FILLER = arrayOf(
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

    // Warna tampilan Amber (ControlSkin).
    private const val YELLOW = 0xFFD60A
    private const val YELLOW_SOFT = 0xFFE566
    private const val WHITE = 0xFFFFFF
    private const val BLACK = 0x000000

    /** Posisi dan ukuran satu elemen: pusat (0..1 dari layar), ukuran dasar sebagai pecahan tinggi layar, rasio lebar/tinggi. */
    private class Place(val x: Float, val y: Float, val size: Float, val wr: Float, val hr: Float)

    private class Joy(val place: Place, val opacity: Float, val autoSprint: Boolean, val inGame: Boolean, val inMenu: Boolean)

    private class Btn(
        val label: String,
        val keys: List<String>,
        val place: Place,
        val opacity: Float,
        val primary: Boolean,
        val cornerDp: Float,
        val toggle: Boolean,
        val passthrough: Boolean,
        val inGame: Boolean,
        val inMenu: Boolean
    )

    private class Plan(val joy: Joy?, val buttons: List<Btn>, val scale: Float)

    /**
     * Dipanggil tepat sebelum mesin dibuka. [enabled] = pilihan "Kontrol Amber" di Pengaturan Rust. Tidak pernah
     * melempar: kegagalan hanya dicatat dan mesin memakai kontrol bawaannya.
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
        val heightDp = surfaceHeightDp(context, config?.optString("orientation", "landscape"))
        val plan = plan(buttons, heightDp, SettingsManager.settings.value.buttonLookDrag)
        // Tata letak tanpa joystick dan tanpa tombol apa pun jangan diterapkan: game jadi tidak bisa dikendalikan.
        if (plan.joy == null && plan.buttons.isEmpty()) return

        // Salinan berkas asli disimpan sekali, sebelum Amber menyentuhnya pertama kali.
        if (!backup.isFile) {
            writeAtomic(backup, if (touch.isFile) touch.readText() else "{}")
        }

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

    private fun plan(buttons: List<CustomControlButton>, heightDp: Float, lookDrag: Boolean): Plan {
        val scale = ENGINE_UI_HEIGHT / heightDp
        var joy: Joy? = null
        val out = ArrayList<Btn>()

        fun add(btn: Btn) {
            if (out.size < MAX_CUSTOM) out.add(btn)
        }

        // Tombol sistem (HIDE GUI, MENU) menjadi tombol keyboard: ditambahkan terakhir dan dilewati bila tombol
        // keyboard yang sama (mis. ESC) sudah ada di tata letak, supaya tidak ada dua tombol yang sama.
        val system = ArrayList<CustomControlButton>()

        for (b in buttons) {
            val inGame = b.showMode != ControlShowMode.MENU
            val inMenu = b.showMode != ControlShowMode.GAME
            val k = b.opacity.coerceIn(0.15f, 1f)

            if (b.isJoystick) {
                if (joy == null) joy = Joy(placeOf(b.xPercent, b.yPercent, b.widthDp.toFloat(), b.heightDp.toFloat(), heightDp), k, b.autoSprint, inGame, inMenu)
                continue
            }
            if (b.isScroll) {
                // Widget gulir hotbar: separuh atas = gulir naik, separuh bawah = gulir turun.
                val halfH = b.heightDp / 2f
                val up = placeOf(b.xPercent, b.yPercent - quarter(b, heightDp), b.widthDp.toFloat(), halfH, heightDp)
                val down = placeOf(b.xPercent, b.yPercent + quarter(b, heightDp), b.widthDp.toFloat(), halfH, heightDp)
                add(Btn("UP", listOf("mouse.scroll.up"), up, k, false, b.cornerRadiusDp.toFloat(), false, false, inGame, inMenu))
                add(Btn("DN", listOf("mouse.scroll.down"), down, k, false, b.cornerRadiusDp.toFloat(), false, false, inGame, inMenu))
                continue
            }

            if (b.isSystem) {
                system.add(b)
                continue
            }
            buttonOf(b, heightDp, lookDrag)?.let { add(it) }
        }
        for (b in system) {
            val btn = buttonOf(b, heightDp, lookDrag) ?: continue
            if (out.none { it.keys == btn.keys }) add(btn)
        }
        return Plan(joy, out, scale)
    }

    /** Tombol Amber biasa (keyboard, mouse, kombinasi, atau sistem yang punya padanan) sebagai tombol mesin. */
    private fun buttonOf(b: CustomControlButton, heightDp: Float, lookDrag: Boolean): Btn? {
        val keys = keysOf(b)
        if (keys.isEmpty()) return null
        return Btn(
            label = labelOf(b),
            keys = keys,
            place = placeOf(b.xPercent, b.yPercent, b.widthDp.toFloat(), b.heightDp.toFloat(), heightDp),
            opacity = b.opacity.coerceIn(0.15f, 1f),
            primary = !b.isSystem && b.heightDp >= 56,
            cornerDp = b.cornerRadiusDp.toFloat(),
            toggle = b.isToggle || (b.isMacro && b.macroType == "COMBO"),
            passthrough = lookDrag && !b.isSystem && !b.isMacro && (b.isMouseButton || b.keyCode == Key.GLFW_KEY_SPACE),
            inGame = b.showMode != ControlShowMode.MENU,
            inMenu = b.showMode != ControlShowMode.GAME
        )
    }

    /** Pergeseran vertikal (persen layar) dari pusat widget gulir ke pusat separuh atas/bawahnya. */
    private fun quarter(b: CustomControlButton, heightDp: Float): Float = (b.heightDp / 4f) / heightDp * 100f

    private fun placeOf(xPercent: Float, yPercent: Float, widthDp: Float, heightDp: Float, screenHeightDp: Float): Place {
        val wFrac = widthDp / screenHeightDp
        val hFrac = heightDp / screenHeightDp
        val size = sqrt(wFrac * hFrac).coerceIn(0.045f, 0.45f)
        return Place(
            x = (xPercent / 100f).coerceIn(0.025f, 0.975f),
            y = (yPercent / 100f).coerceIn(0.025f, 0.975f),
            size = size,
            wr = (wFrac / size).coerceIn(0.4f, 3.2f),
            hr = (hFrac / size).coerceIn(0.4f, 3.2f)
        )
    }

    private fun labelOf(b: CustomControlButton): String {
        val name = b.name.trim()
        return if (name.isNotEmpty()) name.take(9) else ""
    }

    /** Daftar token tombol mesin untuk tombol Amber ini (satu sampai tiga); kosong bila tidak punya padanan. */
    private fun keysOf(b: CustomControlButton): List<String> {
        if (b.isSystem) {
            return when (b.systemAction) {
                "HIDE_GUI" -> listOf("key.keyboard.f1")
                "MENU", "CLOSE" -> listOf("key.keyboard.escape")
                else -> emptyList() // FPS dan keyboard layar milik launcher, tidak ada di mesin
            }
        }
        if (b.isMacro) {
            if (b.macroType != "COMBO") return emptyList()
            val codes = if (b.macroComboKeys.isNotEmpty()) b.macroComboKeys else if (b.macroComboKey != 0) listOf(b.macroComboKey) else emptyList()
            val tokens = ArrayList<String>()
            tokenOf(b)?.let { tokens.add(it) }
            for (code in codes) keyToken(code)?.let { if (it !in tokens) tokens.add(it) }
            return tokens.take(3)
        }
        return listOfNotNull(tokenOf(b))
    }

    /** Token tombol/mouse mesin untuk tombol utama Amber ini, atau null bila mesin tidak mengenalnya. */
    private fun tokenOf(b: CustomControlButton): String? {
        if (b.isMouseButton) {
            return when (b.mouseButton) {
                Key.GLFW_MOUSE_BUTTON_LEFT -> "key.mouse.left"
                Key.GLFW_MOUSE_BUTTON_RIGHT -> "key.mouse.right"
                Key.GLFW_MOUSE_BUTTON_MIDDLE -> "key.mouse.middle"
                else -> null
            }
        }
        return keyToken(b.keyCode)
    }

    /** Kode tombol GLFW ke token keyboard mesin (`key.keyboard.*`); null bila mesin tidak mengenalnya. */
    private fun keyToken(code: Int): String? {
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
        json.put("control_style", "goy-control")
        json.put("mode", "crosshair")
        json.put("split_controls", true)
        json.put("left_handed", false)
        json.put("button_scale", 1.0)
        json.put("horizontal_margin", 0.0)
        json.put("vertical_margin", 0.0)
        json.put("dynamic_joystick", false)
        // Menu kontrol mengambang milik mesin disembunyikan: tata letak diatur dari editor kontrol Amber.
        json.put("show_control_hub", false)
        plan.joy?.let { json.put("sprint_with_joystick", it.autoSprint) }
        // Selalu preset bawaan, supaya mesin tidak menyimpan tata letak Amber ke preset pemain.
        json.put("preset_id", "default")
        json.put("active_profile", "default")
        json.put("active_goy_preset", "default")
        json.put("joystick_style", joystickStyle(plan.joy?.opacity ?: 0.9f, plan.scale))

        // Tiga baris tata letak (satu per mode) wajib lengkap; gaya Goy hanya memakai baris joystick.
        val layouts = JSONArray()
        repeat(3) {
            val row = JSONArray()
            for (i in 0 until CONTROLS) {
                val f = FILLER[i]
                val p = placement(f[0], f[1], f[2], 1f, 1f, 0.65f)
                val joy = plan.joy
                if (i == MOVE && joy != null) {
                    row.put(
                        placement(joy.place.x, joy.place.y, joy.place.size, joy.place.wr, joy.place.hr, joy.opacity)
                            .put("visible_in_game", joy.inGame)
                            .put("visible_in_menu", joy.inMenu)
                    )
                } else {
                    row.put(p)
                }
            }
            layouts.put(row)
        }
        json.put("layouts", layouts)

        // Gaya Goy hanya memakai joystick bawaan mesin; kontrol bawaan lain diganti tombol kustom di bawah.
        val visibility = JSONArray()
        for (i in 0 until CONTROLS) visibility.put(if (i == MOVE && plan.joy != null) 1 else 2)
        json.put("button_visibility", visibility)

        val custom = JSONArray()
        for (b in plan.buttons) custom.put(customButton(b, plan.scale))
        json.put("custom_buttons", custom)
        return json
    }

    /** Joystick berwarna kaca gelap dengan garis putih tipis dan kenop terang, seperti joystick Amber. */
    private fun joystickStyle(k: Float, scale: Float): JSONObject = JSONObject()
        .put("stick_stroke", round(1.5f * scale))
        .put("stick_stroke_color", argb(fade(0.34f + 0.30f * k, k), WHITE))
        .put("stick_color", argb(fade(0.12f + 0.08f * k, k), WHITE))
        .put("stick_opacity", 1.0)
        .put("stick_size", 0.36)
        .put("base_stroke", round(1.2f * scale))
        .put("base_stroke_color", argb(fade(0.16f + 0.12f * k, k), WHITE))
        .put("base_color", argb(fade(0.20f * (0.4f + 0.6f * k), k), BLACK))
        .put("base_opacity", 1.0)
        .put("base_roundness", 50.0)
        .put("move_area", "absolute-fixed")

    /**
     * Satu tombol Amber sebagai tombol statis mesin. Mesin mengalikan alfa warna dengan kepekatan tombol, jadi
     * alfa yang diinginkan dibagi kepekatan (dibatasi 1) supaya hasil akhirnya sama dengan gambar Amber.
     */
    private fun customButton(b: Btn, scale: Float): JSONObject {
        val k = b.opacity
        val fillBase = if (b.primary) 0.30f else 0.22f
        val edgeBase = if (b.primary) 0.22f else 0.16f
        // Satu penempatan per mode mesin (tiga); isinya sama karena mode dikunci ke crosshair.
        fun styled(): JSONObject = placement(b.place.x, b.place.y, b.place.size, b.place.wr, b.place.hr, k)
            .put("passthrough", b.passthrough)
            .put("toggleable", b.toggle)
            .put("swipeable", false)
            .put("shape", 0)
            .put("icon_id", 0)
            .put("use_goy_style", true)
            .put("stroke_size", round((if (b.primary) 1.3f else 1.2f) * scale))
            .put("stroke_margin", 0.0)
            .put("stroke_roundness", round(min(b.cornerDp * scale, 50f)))
            .put("roundness", round(min(b.cornerDp * scale, 50f)))
            .put("shadow_size", 0.0)
            .put("stroke_color", argb(fade(edgeBase + 0.14f * k, k), WHITE))
            .put("button_color", argb(fade(fillBase * (0.4f + 0.6f * k), k), BLACK))
            .put("on_click_color", argb(fade(0.34f + 0.30f * k, k), YELLOW))
            .put("on_click_stroke_color", argb(fade(0.85f, k), YELLOW_SOFT))
            .put("on_toggle_color", argb(fade(0.18f, k), YELLOW))
            .put("on_toggle_stroke_color", argb(1f, YELLOW))
            .put("visible_in_game", b.inGame)
            .put("visible_in_menu", b.inMenu)
        val layouts = JSONArray()
        repeat(3) { layouts.put(styled()) }
        return JSONObject()
            .put("key", b.keys[0])
            .put("second_key", b.keys.getOrElse(1) { "" })
            .put("third_key", b.keys.getOrElse(2) { "" })
            .put("custom_label", b.label)
            .put("always_visible", b.inMenu)
            .put("layouts", layouts)
    }

    private fun placement(x: Float, y: Float, size: Float, wr: Float, hr: Float, opacity: Float): JSONObject =
        JSONObject()
            .put("center", JSONArray().put(round(x)).put(round(y)))
            .put("size", round(size))
            .put("opacity", round(opacity))
            .put("width_ratio", round(wr))
            .put("height_ratio", round(hr))

    /** Alfa yang dibagi kepekatan tombol (mesin mengalikannya lagi), dibatasi 0..1. */
    private fun fade(alpha: Float, opacity: Float): Float = (alpha / opacity).coerceIn(0f, 1f)

    /** Warna ARGB tanpa tanda (0xAARRGGBB) sebagai Long, bentuk yang dibaca mesin (u32). */
    private fun argb(alpha: Float, rgb: Int): Long =
        ((alpha * 255f).roundToInt().coerceIn(0, 255).toLong() shl 24) or (rgb.toLong() and 0xFFFFFFL)

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
