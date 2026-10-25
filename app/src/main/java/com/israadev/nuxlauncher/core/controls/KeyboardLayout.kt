package com.israadev.nuxlauncher.core.controls

import com.movtery.inputmap.keycodes.LwjglGlfwKeycode as K

/**
 * Satu kotak di papan ketik pemilih tombol.
 *
 * @param label tulisan di kotak (nama tombol seperti di keyboard asli, tidak diterjemahkan).
 * @param code kode tombol GLFW; null = ruang kosong selebar [w].
 * @param w lebar relatif, 1f = satu tombol huruf. Baris keyboard utama berjumlah 15f supaya kolomnya sejajar.
 * @param name nama pendek yang dipakai sebagai label tombol baru di layar (huruf besar, muat di tombol kecil).
 */
class KeyCap(
    val label: String,
    val code: Int?,
    val w: Float = 1f,
    name: String = label.uppercase()
) {
    /** Pilihan yang dikirim ke editor saat kotak diketuk. Dibuat sekali; null untuk ruang kosong. */
    val option: KeyOption? = if (code == null) null else KeyOption(name, code)
}

/**
 * Susunan keyboard untuk pemilih tombol (dua halaman, seperti keyboard asli):
 * [MAIN] = huruf, angka, F1-F12, dan modifier; [NAV] = navigasi, panah, dan numpad.
 * Data statis yang dibuat sekali, tidak ada pekerjaan saat dialog dibuka.
 */
object KeyboardLayout {
    private fun k(label: String, code: Int, w: Float = 1f, name: String = label.uppercase()) =
        KeyCap(label, code, w, name)

    private fun gap(w: Float) = KeyCap("", null, w)

    val MAIN: List<List<KeyCap>> = listOf(
        listOf(
            k("Esc", K.GLFW_KEY_ESCAPE), gap(1f),
            k("F1", K.GLFW_KEY_F1), k("F2", K.GLFW_KEY_F2), k("F3", K.GLFW_KEY_F3), k("F4", K.GLFW_KEY_F4), gap(0.5f),
            k("F5", K.GLFW_KEY_F5), k("F6", K.GLFW_KEY_F6), k("F7", K.GLFW_KEY_F7), k("F8", K.GLFW_KEY_F8), gap(0.5f),
            k("F9", K.GLFW_KEY_F9), k("F10", K.GLFW_KEY_F10), k("F11", K.GLFW_KEY_F11), k("F12", K.GLFW_KEY_F12)
        ),
        listOf(
            k("`", K.GLFW_KEY_GRAVE_ACCENT),
            k("1", K.GLFW_KEY_1), k("2", K.GLFW_KEY_2), k("3", K.GLFW_KEY_3), k("4", K.GLFW_KEY_4), k("5", K.GLFW_KEY_5),
            k("6", K.GLFW_KEY_6), k("7", K.GLFW_KEY_7), k("8", K.GLFW_KEY_8), k("9", K.GLFW_KEY_9), k("0", K.GLFW_KEY_0),
            k("-", K.GLFW_KEY_MINUS), k("=", K.GLFW_KEY_EQUAL),
            k("Backspace", K.GLFW_KEY_BACKSPACE, 2f, "BKSP")
        ),
        listOf(
            k("Tab", K.GLFW_KEY_TAB, 1.5f),
            k("Q", K.GLFW_KEY_Q), k("W", K.GLFW_KEY_W), k("E", K.GLFW_KEY_E), k("R", K.GLFW_KEY_R), k("T", K.GLFW_KEY_T),
            k("Y", K.GLFW_KEY_Y), k("U", K.GLFW_KEY_U), k("I", K.GLFW_KEY_I), k("O", K.GLFW_KEY_O), k("P", K.GLFW_KEY_P),
            k("[", K.GLFW_KEY_LEFT_BRACKET), k("]", K.GLFW_KEY_RIGHT_BRACKET),
            k("\\", K.GLFW_KEY_BACKSLASH, 1.5f)
        ),
        listOf(
            k("Capslock", K.GLFW_KEY_CAPS_LOCK, 1.75f, "CAPS"),
            k("A", K.GLFW_KEY_A), k("S", K.GLFW_KEY_S), k("D", K.GLFW_KEY_D), k("F", K.GLFW_KEY_F), k("G", K.GLFW_KEY_G),
            k("H", K.GLFW_KEY_H), k("J", K.GLFW_KEY_J), k("K", K.GLFW_KEY_K), k("L", K.GLFW_KEY_L),
            k(";", K.GLFW_KEY_SEMICOLON), k("'", K.GLFW_KEY_APOSTROPHE),
            k("Enter", K.GLFW_KEY_ENTER, 2.25f)
        ),
        listOf(
            k("Shift", K.GLFW_KEY_LEFT_SHIFT, 2.25f),
            k("Z", K.GLFW_KEY_Z), k("X", K.GLFW_KEY_X), k("C", K.GLFW_KEY_C), k("V", K.GLFW_KEY_V),
            k("B", K.GLFW_KEY_B), k("N", K.GLFW_KEY_N), k("M", K.GLFW_KEY_M),
            k(",", K.GLFW_KEY_COMMA), k(".", K.GLFW_KEY_PERIOD), k("/", K.GLFW_KEY_SLASH),
            k("Shift", K.GLFW_KEY_RIGHT_SHIFT, 2.75f)
        ),
        listOf(
            k("Ctrl", K.GLFW_KEY_LEFT_CONTROL, 1.25f), k("Win", K.GLFW_KEY_LEFT_SUPER, 1.25f),
            k("Alt", K.GLFW_KEY_LEFT_ALT, 1.25f),
            k("Space", K.GLFW_KEY_SPACE, 6.25f),
            k("Alt", K.GLFW_KEY_RIGHT_ALT, 1.25f), k("Win", K.GLFW_KEY_RIGHT_SUPER, 1.25f),
            k("Menu", K.GLFW_KEY_MENU, 1.25f), k("Ctrl", K.GLFW_KEY_RIGHT_CONTROL, 1.25f)
        )
    )

    // Kiri: 3 kolom (sistem, navigasi, panah). Tengah: jarak 8f. Kanan: numpad 4 kolom. Total tiap baris 15f.
    val NAV: List<List<KeyCap>> = listOf(
        listOf(
            k("PrtSc", K.GLFW_KEY_PRINT_SCREEN), k("ScrLk", K.GLFW_KEY_SCROLL_LOCK), k("Pause", K.GLFW_KEY_PAUSE),
            gap(12f)
        ),
        listOf(
            k("Insert", K.GLFW_KEY_INSERT), k("Home", K.GLFW_KEY_HOME), k("PgUp", K.GLFW_KEY_PAGE_UP),
            gap(8f),
            k("NumLk", K.GLFW_KEY_NUM_LOCK), k("/", K.GLFW_KEY_KP_DIVIDE, name = "NP/"),
            k("*", K.GLFW_KEY_KP_MULTIPLY, name = "NP*"), k("-", K.GLFW_KEY_KP_SUBTRACT, name = "NP-")
        ),
        listOf(
            k("Delete", K.GLFW_KEY_DELETE, name = "DEL"), k("End", K.GLFW_KEY_END), k("PgDn", K.GLFW_KEY_PAGE_DOWN),
            gap(8f),
            k("7", K.GLFW_KEY_KP_7, name = "NP7"), k("8", K.GLFW_KEY_KP_8, name = "NP8"),
            k("9", K.GLFW_KEY_KP_9, name = "NP9"), k("+", K.GLFW_KEY_KP_ADD, name = "NP+")
        ),
        listOf(
            gap(11f),
            k("4", K.GLFW_KEY_KP_4, name = "NP4"), k("5", K.GLFW_KEY_KP_5, name = "NP5"),
            k("6", K.GLFW_KEY_KP_6, name = "NP6"), k("=", K.GLFW_KEY_KP_EQUAL, name = "NP=")
        ),
        listOf(
            gap(1f), k("↑", K.GLFW_KEY_UP), gap(1f),
            gap(8f),
            k("1", K.GLFW_KEY_KP_1, name = "NP1"), k("2", K.GLFW_KEY_KP_2, name = "NP2"),
            k("3", K.GLFW_KEY_KP_3, name = "NP3"), k("Enter", K.GLFW_KEY_KP_ENTER, name = "NP↵")
        ),
        listOf(
            k("←", K.GLFW_KEY_LEFT), k("↓", K.GLFW_KEY_DOWN), k("→", K.GLFW_KEY_RIGHT),
            gap(9f),
            k("0", K.GLFW_KEY_KP_0, name = "NP0"), k(".", K.GLFW_KEY_KP_DECIMAL, name = "NP."),
            gap(1f)
        )
    )
}
