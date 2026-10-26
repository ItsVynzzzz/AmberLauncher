package com.israadev.nuxlauncher.core.controls.models

import com.movtery.inputmap.keycodes.LwjglGlfwKeycode
import java.util.UUID

/**
 * Model konfigurasi sebuah tombol virtual touch kustom
 */
data class CustomControlButton(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "BTN",
    val keyCode: Int = LwjglGlfwKeycode.GLFW_KEY_SPACE,
    val isMouseButton: Boolean = false,
    val mouseButton: Int = LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_LEFT,
    val xPercent: Float = 50f, // 0f - 100f
    val yPercent: Float = 50f, // 0f - 100f
    val widthDp: Int = 50,
    val heightDp: Int = 48,
    val opacity: Float = 0.85f, // 0.1f - 1.0f
    val cornerRadiusDp: Int = 8,
    val isToggle: Boolean = false,
    val isScroll: Boolean = false,
    val isSystem: Boolean = false,
    val systemAction: String = "", // "FPS", "KEYBOARD", "HIDE_GUI", "MENU" ("CLOSE" = nama lama MENU)
    val isJoystick: Boolean = false, // Virtual analog WASD joystick
    // Khusus joystick: dorong sampai tepi lingkaran = lari otomatis (Ctrl). Mati = tidak pernah lari
    // lewat joystick. Layout lama tanpa kolom ini tetap lari otomatis (nilai bawaan).
    val autoSprint: Boolean = true,
    // Macro Settings
    val isMacro: Boolean = false,
    val macroType: String = "COMMAND", // "COMMAND", "COMBO", "TURBO"
    val macroCommand: String = "",      // e.g. "/gamemode creative", "/home"
    val macroComboKey: Int = 0,        // Backward compatibility for single key
    val macroComboKeys: List<Int> = emptyList(), // Multi-key sequence executed from top to bottom
    val macroTurboIntervalMs: Long = 100L, // Auto-click speed interval in ms
    // Nama ikon gambar (jump, sneak, attack, use, inventory, chat, pause, ...); kosong = tampilkan nama sebagai teks
    val icon: String = "",
    // Kapan tombol tampil: ALWAYS = selalu, GAME = hanya saat main (kursor dikunci game),
    // MENU = hanya saat ada menu/GUI game (inventori, chat, jeda). Layout lama tanpa kolom ini = ALWAYS.
    val showMode: String = ControlShowMode.ALWAYS
)

/** Nilai [CustomControlButton.showMode] dan aturan tampilnya. Nilai tak dikenal diperlakukan sebagai ALWAYS. */
object ControlShowMode {
    const val ALWAYS = "ALWAYS"
    const val GAME = "GAME"
    const val MENU = "MENU"

    /** [inMenu] = game sedang menampilkan kursor (menu, inventori, chat, layar judul). */
    fun visible(mode: String?, inMenu: Boolean): Boolean = when (mode) {
        GAME -> !inMenu
        MENU -> inMenu
        else -> true
    }

    fun isValid(mode: String): Boolean = mode == ALWAYS || mode == GAME || mode == MENU
}
