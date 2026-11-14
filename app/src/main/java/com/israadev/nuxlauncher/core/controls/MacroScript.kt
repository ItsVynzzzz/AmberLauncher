package com.israadev.nuxlauncher.core.controls

import com.israadev.nuxlauncher.core.controls.models.MacroRunMode
import com.movtery.inputmap.keycodes.LwjglGlfwKeycode

/**
 * Satu langkah makro "Urutan Aksi". Tombol keyboard memakai kode GLFW, tombol mouse memakai nomor
 * GLFW (0 kiri, 1 kanan, 2 tengah, 3-7 samping).
 */
sealed class MacroStep {
    /** Tekan lalu lepas sebentar. */
    data class Tap(val key: Int) : MacroStep()

    /** Tekan dan tahan sampai langkah [KeyUp] atau sampai makro berhenti. */
    data class KeyDown(val key: Int) : MacroStep()

    data class KeyUp(val key: Int) : MacroStep()

    /** Tahan selama [ms] milidetik lalu lepas. */
    data class KeyHold(val key: Int, val ms: Int) : MacroStep()

    data class Click(val button: Int) : MacroStep()

    data class MouseDown(val button: Int) : MacroStep()

    data class MouseUp(val button: Int) : MacroStep()

    data class MouseHold(val button: Int, val ms: Int) : MacroStep()

    /** [direction] positif = gulir naik (hotbar sebelumnya), negatif = turun. */
    data class Scroll(val direction: Int) : MacroStep()

    data class Wait(val ms: Int) : MacroStep()

    /** Ketik chat atau perintah (diawali "/" membuka chat dengan garis miring) lalu tekan Enter. */
    data class Type(val text: String) : MacroStep()
}

/**
 * Contoh siap pakai di editor. [title] dan [description] juga kunci terjemahan; [emoji] hanya hiasan
 * di kartu contoh.
 */
class MacroTemplate(
    val title: String,
    val steps: List<String>,
    val runMode: String,
    val repeat: Int = 1,
    val loopDelayMs: Int = 0,
    val description: String = "",
    val emoji: String = ""
)

/**
 * Bahasa makro: tiap langkah disimpan sebagai teks pendek `OP:ARG[:ARG2]` di
 * [com.israadev.nuxlauncher.core.controls.models.CustomControlButton.macroSteps], jadi mudah
 * disimpan, diekspor, dan dicek ketika layout orang lain diimpor.
 *
 *  - `TAP:<kunci>` `DOWN:<kunci>` `UP:<kunci>` `HOLD:<kunci>:<ms>`
 *  - `CLICK:<mouse>` `MDOWN:<mouse>` `MUP:<mouse>` `MHOLD:<mouse>:<ms>`
 *  - `SCROLL:<1|-1>` `WAIT:<ms>` `TYPE:<teks>`
 *
 * Semua angka dibatasi rentangnya ([parse]); teks tak dikenal dibuang. Murni logika (tanpa Android).
 */
object MacroScript {
    const val MAX_STEPS = 64
    const val MAX_TEXT = 256
    const val MIN_WAIT_MS = 10
    const val MAX_WAIT_MS = 10_000
    const val MIN_HOLD_MS = 20
    const val MAX_HOLD_MS = 30_000
    const val MAX_REPEAT = 99
    const val MAX_LOOP_DELAY_MS = 10_000
    const val MAX_JITTER = 50
    const val DEFAULT_HOLD_MS = 500
    const val DEFAULT_WAIT_MS = 200

    private const val MAX_KEYCODE = 1024
    private const val MAX_MOUSE = 7

    // ---- Baca / tulis ----

    /** Satu langkah dari teks, atau null bila bentuknya tidak dikenal. Angka di luar rentang dirapikan. */
    fun parse(token: String): MacroStep? {
        // Hanya spasi di awal yang dibuang: spasi di akhir teks "TYPE" bagian dari teks yang diketik.
        val t = token.trimStart()
        val colon = t.indexOf(':')
        val op = (if (colon < 0) t else t.substring(0, colon)).trim().uppercase()
        val arg = if (colon < 0) "" else t.substring(colon + 1)
        return when (op) {
            "TAP" -> keyOf(arg)?.let { MacroStep.Tap(it) }
            "DOWN" -> keyOf(arg)?.let { MacroStep.KeyDown(it) }
            "UP" -> keyOf(arg)?.let { MacroStep.KeyUp(it) }
            "HOLD" -> {
                val parts = arg.split(':')
                val key = keyOf(parts.getOrNull(0))
                if (key == null) null else MacroStep.KeyHold(key, holdOf(parts.getOrNull(1)))
            }
            "CLICK" -> mouseOf(arg)?.let { MacroStep.Click(it) }
            "MDOWN" -> mouseOf(arg)?.let { MacroStep.MouseDown(it) }
            "MUP" -> mouseOf(arg)?.let { MacroStep.MouseUp(it) }
            "MHOLD" -> {
                val parts = arg.split(':')
                val btn = mouseOf(parts.getOrNull(0))
                if (btn == null) null else MacroStep.MouseHold(btn, holdOf(parts.getOrNull(1)))
            }
            "SCROLL" -> arg.trim().toIntOrNull()?.let { MacroStep.Scroll(if (it >= 0) 1 else -1) }
            "WAIT" -> arg.trim().toIntOrNull()?.let { MacroStep.Wait(it.coerceIn(MIN_WAIT_MS, MAX_WAIT_MS)) }
            "TYPE" -> MacroStep.Type(cleanText(arg))
            else -> null
        }
    }

    fun toToken(step: MacroStep): String = when (step) {
        is MacroStep.Tap -> "TAP:${step.key}"
        is MacroStep.KeyDown -> "DOWN:${step.key}"
        is MacroStep.KeyUp -> "UP:${step.key}"
        is MacroStep.KeyHold -> "HOLD:${step.key}:${step.ms}"
        is MacroStep.Click -> "CLICK:${step.button}"
        is MacroStep.MouseDown -> "MDOWN:${step.button}"
        is MacroStep.MouseUp -> "MUP:${step.button}"
        is MacroStep.MouseHold -> "MHOLD:${step.button}:${step.ms}"
        is MacroStep.Scroll -> "SCROLL:${step.direction}"
        is MacroStep.Wait -> "WAIT:${step.ms}"
        is MacroStep.Type -> "TYPE:${step.text}"
    }

    /** Langkah yang valid dari daftar teks, paling banyak [MAX_STEPS]. Yang tidak dikenal dilewati. */
    fun parseAll(tokens: List<String>): List<MacroStep> {
        val out = ArrayList<MacroStep>()
        for (token in tokens) {
            if (out.size >= MAX_STEPS) break
            parse(token)?.let { out.add(it) }
        }
        return out
    }

    /** Daftar teks yang sudah dirapikan: langkah tak valid dibuang, angka dibatasi, teks dibersihkan. */
    fun sanitize(tokens: List<String>): List<String> = parseAll(tokens).map { toToken(it) }

    /** True bila ada langkah yang mengetik chat/perintah (tidak kosong). Dipakai untuk peringatan impor. */
    fun hasTyping(tokens: List<String>): Boolean {
        for (token in tokens) {
            val step = parse(token)
            if (step is MacroStep.Type && step.text.isNotBlank()) return true
        }
        return false
    }

    /** Perkiraan lama satu putaran (ms), termasuk jeda antar langkah. */
    fun estimateMs(steps: List<MacroStep>): Long {
        var total = 0L
        for (step in steps) {
            total += MacroRunner.STEP_GAP_MS
            total += when (step) {
                is MacroStep.Tap -> MacroRunner.TAP_MS
                is MacroStep.Click -> MacroRunner.TAP_MS
                is MacroStep.KeyHold -> step.ms.toLong()
                is MacroStep.MouseHold -> step.ms.toLong()
                is MacroStep.Wait -> step.ms.toLong()
                is MacroStep.Type -> 190L + step.text.trim().length * 10L
                else -> 0L
            }
        }
        return total
    }

    // ---- Untuk editor ----

    /**
     * Langkah baru dari pilihan di pemilih tombol. [kind]: TAP (tekan), HOLD (tahan sekian ms), DOWN
     * (tekan terus), UP (lepas). Tombol mouse menjadi langkah mouse, gulir hanya untuk TAP. Null bila
     * kombinasinya tidak masuk akal.
     */
    fun stepFromPick(kind: String, option: KeyOption): String? {
        if (option.isScroll) return if (kind == "TAP") "SCROLL:1" else null
        if (option.isMouseButton) {
            val b = option.mouseButton
            if (b !in 0..MAX_MOUSE) return null
            return when (kind) {
                "TAP" -> "CLICK:$b"
                "HOLD" -> "MHOLD:$b:$DEFAULT_HOLD_MS"
                "DOWN" -> "MDOWN:$b"
                "UP" -> "MUP:$b"
                else -> null
            }
        }
        val k = option.keyCode
        if (k <= 0 || k > MAX_KEYCODE) return null
        return when (kind) {
            "TAP" -> "TAP:$k"
            "HOLD" -> "HOLD:$k:$DEFAULT_HOLD_MS"
            "DOWN" -> "DOWN:$k"
            "UP" -> "UP:$k"
            else -> null
        }
    }

    /**
     * Teks pendek untuk satu langkah di daftar editor: (kata kerja, rincian). Kata kerja berupa kunci
     * terjemahan tetap; rincian adalah nama tombol/angka/teks.
     */
    fun label(step: MacroStep): Pair<String, String> = when (step) {
        is MacroStep.Tap -> "Tekan" to KeycodeCatalog.getKeyName(step.key)
        is MacroStep.KeyDown -> "Tekan terus" to KeycodeCatalog.getKeyName(step.key)
        is MacroStep.KeyUp -> "Lepas" to KeycodeCatalog.getKeyName(step.key)
        is MacroStep.KeyHold -> "Tahan" to "${KeycodeCatalog.getKeyName(step.key)} · ${step.ms} ms"
        is MacroStep.Click -> "Klik" to mouseName(step.button)
        is MacroStep.MouseDown -> "Tekan terus" to mouseName(step.button)
        is MacroStep.MouseUp -> "Lepas" to mouseName(step.button)
        is MacroStep.MouseHold -> "Tahan" to "${mouseName(step.button)} · ${step.ms} ms"
        is MacroStep.Scroll -> "Gulir" to (if (step.direction > 0) "Naik" else "Turun")
        is MacroStep.Wait -> "Tunggu" to "${step.ms} ms"
        is MacroStep.Type -> "Ketik" to step.text.ifBlank { "(kosong)" }
    }

    fun mouseName(button: Int): String = when (button) {
        0 -> "Mouse Kiri"
        1 -> "Mouse Kanan"
        2 -> "Mouse Tengah"
        else -> "Mouse ${button + 1}"
    }

    /** Templat siap pakai. Tombol yang ditahan oleh langkah DOWN/MDOWN dilepas otomatis saat makro berhenti. */
    val TEMPLATES: List<MacroTemplate> = listOf(
        MacroTemplate(
            title = "Auto Gali",
            steps = listOf("MDOWN:0", "WAIT:1000"),
            runMode = MacroRunMode.TOGGLE,
            description = "Menahan klik kiri terus-menerus untuk menggali atau menyerang.",
            emoji = "\u26cf\ufe0f"
        ),
        MacroTemplate(
            title = "Auto Klik Kanan",
            steps = listOf("CLICK:1", "WAIT:150"),
            runMode = MacroRunMode.TOGGLE,
            description = "Klik kanan berulang, cocok untuk membangun atau memancing.",
            emoji = "\ud83d\uddb1\ufe0f"
        ),
        MacroTemplate(
            title = "Lompat Terus",
            steps = listOf("TAP:${LwjglGlfwKeycode.GLFW_KEY_SPACE}", "WAIT:450"),
            runMode = MacroRunMode.TOGGLE,
            description = "Melompat terus-menerus tanpa berhenti.",
            emoji = "\ud83e\udd98"
        ),
        MacroTemplate(
            title = "Lari + Lompat",
            steps = listOf(
                "DOWN:${LwjglGlfwKeycode.GLFW_KEY_W}",
                "DOWN:${LwjglGlfwKeycode.GLFW_KEY_LEFT_CONTROL}",
                "TAP:${LwjglGlfwKeycode.GLFW_KEY_SPACE}",
                "WAIT:350"
            ),
            runMode = MacroRunMode.TOGGLE,
            description = "Berlari sambil melompat terus (bunny hop).",
            emoji = "\ud83c\udfc3"
        ),
        MacroTemplate(
            title = "Makan",
            steps = listOf("MHOLD:1:1800"),
            runMode = MacroRunMode.ONCE,
            description = "Menahan klik kanan 1,8 detik untuk makan atau minum.",
            emoji = "\ud83c\udf56"
        ),
        MacroTemplate(
            title = "Buang Item x10",
            steps = listOf("TAP:${LwjglGlfwKeycode.GLFW_KEY_Q}", "WAIT:80"),
            runMode = MacroRunMode.ONCE,
            repeat = 10,
            description = "Membuang item 10 kali berturut-turut.",
            emoji = "\ud83d\uddd1\ufe0f"
        )
    )

    // ---- Pembantu ----

    private fun keyOf(s: String?): Int? {
        val v = s?.trim()?.toIntOrNull() ?: return null
        return if (v in 1..MAX_KEYCODE) v else null
    }

    private fun mouseOf(s: String?): Int? {
        val v = s?.trim()?.toIntOrNull() ?: return null
        return if (v in 0..MAX_MOUSE) v else null
    }

    private fun holdOf(s: String?): Int =
        (s?.trim()?.toIntOrNull() ?: DEFAULT_HOLD_MS).coerceIn(MIN_HOLD_MS, MAX_HOLD_MS)

    /** Hanya karakter yang bisa diketik: tanpa karakter kendali, paling panjang [MAX_TEXT]. */
    private fun cleanText(s: String): String {
        val sb = StringBuilder()
        for (ch in s) {
            if (ch >= ' ' && ch != '\u007f') sb.append(ch)
            if (sb.length >= MAX_TEXT) break
        }
        return sb.toString()
    }
}
