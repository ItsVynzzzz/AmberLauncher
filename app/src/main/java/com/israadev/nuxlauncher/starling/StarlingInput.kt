package com.israadev.nuxlauncher.starling

import com.israadev.nuxlauncher.core.crash.LauncherLog
import com.israadev.nuxlauncher.core.horizon.RustTouchLayout
import org.lwjgl.glfw.CallbackBridge

/**
 * Penerima input untuk kontrol Amber di proses :starling: apa yang ditekan kontrol (lewat [CallbackBridge], persis seperti
 * di game Java) diteruskan ke mesin Starling lewat [StarlingBridge], dengan nama tombol milik game.
 */
internal object StarlingInput : CallbackBridge.InputSink {
    @Volatile
    var bridge: StarlingBridge? = null

    override fun key(keycode: Int, isDown: Boolean) {
        val token = RustTouchLayout.keyToken(keycode) ?: return
        send(token, isDown)
    }

    override fun mouseButton(button: Int, isDown: Boolean) {
        val token = when (button) {
            0 -> "key.mouse.left"
            1 -> "key.mouse.right"
            2 -> "key.mouse.middle"
            else -> return
        }
        send(token, isDown)
    }

    /** Sudah ada geseran kamera yang diteruskan sejak fase bermain terakhir dimulai (hanya yang pertama dicatat di log). */
    private var lookNoted = false

    /** Dipanggil tiap fase bermain dimulai, supaya geseran pertamanya tercatat lagi. */
    fun resetLookNote() {
        lookNoted = false
    }

    override fun cursorDelta(dx: Float, dy: Float) {
        if (!lookNoted) {
            lookNoted = true
            // Menunjukkan di log apakah geseran jari sampai ke mesin; kalau baris ini ada tetapi kamera diam, yang menahan ada di mesin.
            try {
                LauncherLog.event("KontrolAmber", "Kamera: geseran pertama diteruskan ke mesin (dx=$dx, dy=$dy)")
            } catch (_: Throwable) {
            }
        }
        try {
            bridge?.look(dx, dy)
        } catch (_: Throwable) {
            // Mesin sedang ditutup: putaran kamera terakhir tidak penting.
        }
    }

    override fun scroll(xoffset: Double, yoffset: Double) {
        when {
            yoffset > 0.0 -> send("mouse.scroll.up", true)
            yoffset < 0.0 -> send("mouse.scroll.down", true)
        }
    }

    private fun send(token: String, down: Boolean) {
        try {
            bridge?.token(token, down)
        } catch (_: Throwable) {
            // Mesin sedang ditutup: tombol yang terlepas tidak perlu dilaporkan.
        }
    }
}
