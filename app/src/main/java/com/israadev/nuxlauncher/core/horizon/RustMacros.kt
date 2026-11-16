package com.israadev.nuxlauncher.core.horizon

import com.israadev.nuxlauncher.core.controls.MacroScript
import com.israadev.nuxlauncher.core.controls.MacroStep
import com.israadev.nuxlauncher.core.controls.models.CustomControlButton
import com.israadev.nuxlauncher.core.controls.models.MacroRunMode
import org.json.JSONArray
import org.json.JSONObject

/**
 * Makro Amber (perintah, turbo, urutan aksi) untuk mesin Rust.
 *
 * Mesin tidak mengenal makro. Amber menambahkan pemutar kecil di sisi mesin (`touch/amber_macro.rs` pada patch
 * mesin) yang membaca `amber-macros.json`, berkas di folder yang sama dengan `horizon-touch.json`. Di sini tiap
 * makro diterjemahkan ke langkah-langkah bertoken mesin: tombolnya di `horizon-touch.json` cukup berisi
 * `amber.macro:<id>`, sedangkan isinya ada di berkas ini. Aturan jalannya sama dengan [com.israadev.nuxlauncher.core.controls.MacroRunner]:
 * jeda antar langkah, jeda minimal antar putaran, jitter, dan semua tombol yang tertahan dilepas saat makro berhenti.
 *
 * Murni logika, selain pembuatan JSON-nya, supaya mudah diuji.
 */
internal object RustMacros {
    const val FILE = "amber-macros.json"
    const val TOKEN_PREFIX = "amber.macro:"

    /** Satu langkah dalam bentuk yang dibaca mesin. */
    class Step(
        val op: String,
        val key: String? = null,
        val ms: Long? = null,
        val text: String? = null,
        val up: Boolean? = null
    )

    class Def(
        val mode: String,
        val repeat: Int,
        val loopDelayMs: Long,
        val jitter: Int,
        val gapMs: Long,
        val minLoopMs: Long,
        val steps: List<Step>
    )

    /** Token tombol di `horizon-touch.json` untuk makro dengan [id] tombol ini. */
    fun tokenFor(id: String): String = TOKEN_PREFIX + id

    /**
     * Terjemahan makro tombol [b] (perintah, turbo, atau urutan aksi), atau null bila bukan makro jenis itu atau
     * tidak ada langkah yang bisa dijalankan mesin. Kombo tombol tidak lewat sini: itu tombol biasa dengan
     * sampai tiga token.
     *
     * [keyToken] = kode tombol GLFW ke token keyboard mesin; [buttonToken] = token tombol utama [b].
     */
    fun defOf(b: CustomControlButton, keyToken: (Int) -> String?, buttonToken: (CustomControlButton) -> String?): Def? {
        if (!b.isMacro) return null
        return when (b.macroType) {
            "COMMAND" -> {
                val text = b.macroCommand.trim().take(MacroScript.MAX_TEXT)
                if (text.isEmpty()) null
                else Def(MODE_ONCE, 1, 0L, 0, GAP_MS, MIN_LOOP_MS, listOf(Step("type", text = text)))
            }

            "TURBO" -> {
                val token = buttonToken(b) ?: return null
                // Sama dengan turbo di launcher: setengah putaran tekan, sisanya lepas, tanpa jeda tambahan.
                val interval = b.macroTurboIntervalMs.coerceAtLeast(30L)
                val down = (interval / 2).coerceAtLeast(15L)
                val up = (interval - down).coerceAtLeast(15L)
                Def(
                    mode = if (b.isToggle) MODE_TOGGLE else MODE_HOLD,
                    repeat = 0,
                    loopDelayMs = 0L,
                    jitter = b.macroJitterPercent.coerceIn(0, MacroScript.MAX_JITTER),
                    gapMs = 0L,
                    minLoopMs = 0L,
                    steps = listOf(Step("hold", key = token, ms = down), Step("wait", ms = up))
                )
            }

            "SEQUENCE" -> {
                val steps = ArrayList<Step>()
                for (step in MacroScript.parseAll(b.macroSteps)) stepOf(step, keyToken)?.let { steps.add(it) }
                if (steps.isEmpty()) return null
                val once = b.macroRunMode == MacroRunMode.ONCE || !MacroRunMode.isValid(b.macroRunMode)
                Def(
                    mode = when (b.macroRunMode) {
                        MacroRunMode.HOLD -> MODE_HOLD
                        MacroRunMode.TOGGLE -> MODE_TOGGLE
                        else -> MODE_ONCE
                    },
                    repeat = if (once) b.macroRepeat.coerceIn(1, MacroScript.MAX_REPEAT) else 0,
                    loopDelayMs = b.macroLoopDelayMs.toLong().coerceIn(0L, MacroScript.MAX_LOOP_DELAY_MS.toLong()),
                    jitter = b.macroJitterPercent.coerceIn(0, MacroScript.MAX_JITTER),
                    gapMs = GAP_MS,
                    minLoopMs = MIN_LOOP_MS,
                    steps = steps
                )
            }

            else -> null
        }
    }

    /** Satu langkah makro Amber ke langkah mesin; null bila mesin tidak punya tombol itu (langkahnya dilewati). */
    private fun stepOf(step: MacroStep, keyToken: (Int) -> String?): Step? = when (step) {
        is MacroStep.Tap -> keyToken(step.key)?.let { Step("tap", key = it) }
        is MacroStep.KeyDown -> keyToken(step.key)?.let { Step("down", key = it) }
        is MacroStep.KeyUp -> keyToken(step.key)?.let { Step("up", key = it) }
        is MacroStep.KeyHold -> keyToken(step.key)?.let { Step("hold", key = it, ms = step.ms.toLong()) }
        is MacroStep.Click -> mouseToken(step.button)?.let { Step("tap", key = it) }
        is MacroStep.MouseDown -> mouseToken(step.button)?.let { Step("down", key = it) }
        is MacroStep.MouseUp -> mouseToken(step.button)?.let { Step("up", key = it) }
        is MacroStep.MouseHold -> mouseToken(step.button)?.let { Step("hold", key = it, ms = step.ms.toLong()) }
        is MacroStep.Scroll -> Step("scroll", up = step.direction > 0)
        is MacroStep.Wait -> Step("wait", ms = step.ms.toLong())
        is MacroStep.Type -> if (step.text.isBlank()) null else Step("type", text = step.text)
    }

    /** Nomor tombol mouse GLFW (0 kiri, 1 kanan, 2 tengah, 3 dan 4 samping) ke token mouse mesin. */
    fun mouseToken(button: Int): String? = when (button) {
        0 -> "key.mouse.left"
        1 -> "key.mouse.right"
        2 -> "key.mouse.middle"
        3 -> "key.mouse.4"
        4 -> "key.mouse.5"
        else -> null
    }

    /** Isi `amber-macros.json`. Urutan kunci mengikuti [macros] supaya isinya stabil dan mudah dibandingkan. */
    fun toJson(macros: Map<String, Def>): String {
        val all = JSONObject()
        for ((id, def) in macros) {
            val steps = JSONArray()
            for (s in def.steps) {
                val o = JSONObject().put("op", s.op)
                s.key?.let { o.put("key", it) }
                s.ms?.let { o.put("ms", it) }
                s.text?.let { o.put("text", it) }
                s.up?.let { o.put("up", it) }
                steps.put(o)
            }
            all.put(
                id,
                JSONObject()
                    .put("mode", def.mode)
                    .put("repeat", def.repeat)
                    .put("loop_delay_ms", def.loopDelayMs)
                    .put("jitter", def.jitter)
                    .put("gap_ms", def.gapMs)
                    .put("min_loop_ms", def.minLoopMs)
                    .put("steps", steps)
            )
        }
        return JSONObject().put("version", 1).put("macros", all).toString(2)
    }

    private const val MODE_ONCE = "once"
    private const val MODE_HOLD = "hold"
    private const val MODE_TOGGLE = "toggle"

    // Sama dengan MacroRunner.STEP_GAP_MS dan MIN_LOOP_MS.
    private const val GAP_MS = 10L
    private const val MIN_LOOP_MS = 30L
}
