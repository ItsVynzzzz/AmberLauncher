package com.israadev.nuxlauncher.core.controls

import com.israadev.nuxlauncher.core.controls.models.ControlShowMode
import com.israadev.nuxlauncher.core.controls.models.CustomControlButton
import com.movtery.inputmap.keycodes.LwjglGlfwKeycode

/**
 * Preset bawaan Amber. Dibuat dari layout bawaan ([defaults]) supaya tombol sistemnya (FPS, keyboard, menu) dan
 * tombol aksinya tetap sama; yang berbeda hanya cara bergerak:
 * - Standar: layout bawaan apa adanya (joystick).
 * - WASD tanpa analog: joystick diganti empat tombol W, A, S, D, ditambah tombol lari (Ctrl).
 * - Panah tanpa analog: sama, tetapi memakai tombol panah (untuk pemain yang mengatur gerak game ke tombol panah).
 *
 * Preset bawaan hanya disalin sekali ke daftar preset ([ControlPresetManager.ensureBuiltins]) sebagai preset biasa,
 * jadi bisa diubah namanya, dan dihapus kalau mengganggu. Murni logika (tanpa Android) supaya mudah diuji.
 */
object ControlPresetBuiltins {
    const val ID_STANDARD = "p_bawaanstandar"
    const val ID_WASD = "p_bawaanwasd"
    const val ID_ARROWS = "p_bawaanpanah"

    val IDS: List<String> = listOf(ID_STANDARD, ID_WASD, ID_ARROWS)

    /**
     * @param order dipakai sebagai waktu simpan: angka kecil supaya preset bawaan selalu berada di bawah preset
     * buatan pengguna (daftar diurutkan dari waktu simpan terbaru), dengan urutan tetap di antara mereka.
     */
    class Builtin(val id: String, val name: String, val order: Long, val buttons: List<CustomControlButton>)

    fun all(defaults: List<CustomControlButton>): List<Builtin> = listOf(
        Builtin(ID_STANDARD, "Standar (joystick)", 3, defaults),
        Builtin(
            ID_WASD, "WASD tanpa analog", 2,
            withoutAnalog(
                defaults,
                labels = listOf("W", "A", "S", "D"),
                keys = listOf(
                    LwjglGlfwKeycode.GLFW_KEY_W, LwjglGlfwKeycode.GLFW_KEY_A,
                    LwjglGlfwKeycode.GLFW_KEY_S, LwjglGlfwKeycode.GLFW_KEY_D
                )
            )
        ),
        Builtin(
            ID_ARROWS, "Panah tanpa analog", 1,
            withoutAnalog(
                defaults,
                labels = listOf("\u2191", "\u2190", "\u2193", "\u2192"),
                keys = listOf(
                    LwjglGlfwKeycode.GLFW_KEY_UP, LwjglGlfwKeycode.GLFW_KEY_LEFT,
                    LwjglGlfwKeycode.GLFW_KEY_DOWN, LwjglGlfwKeycode.GLFW_KEY_RIGHT
                )
            )
        )
    )

    /** [labels] dan [keys] berurutan: atas, kiri, bawah, kanan. */
    private fun withoutAnalog(
        defaults: List<CustomControlButton>,
        labels: List<String>,
        keys: List<Int>
    ): List<CustomControlButton> {
        // Papan arah di tempat joystick bawaan. Posisi dalam persen layar sedangkan ukuran tombol tetap dalam dp, jadi
        // jaraknya diatur supaya tombol tidak bertumpuk pada layar landscape mulai sekitar 640 x 300 dp.
        val centerX = 13.2f
        val centerY = 72f
        val stepX = 8.3f
        val stepY = 16.5f
        val positions = listOf(
            Pair(centerX, centerY - stepY),
            Pair(centerX - stepX, centerY),
            Pair(centerX, centerY + stepY),
            Pair(centerX + stepX, centerY)
        )
        val ids = listOf("dpad_up", "dpad_left", "dpad_down", "dpad_right")
        val pad = ArrayList<CustomControlButton>()
        for (i in 0 until 4) {
            pad.add(
                CustomControlButton(
                    id = ids[i], name = labels[i], keyCode = keys[i],
                    xPercent = positions[i].first, yPercent = positions[i].second,
                    widthDp = 46, heightDp = 46, opacity = 0.9f, cornerRadiusDp = 14,
                    showMode = ControlShowMode.GAME
                )
            )
        }
        val sprint = CustomControlButton(
            id = "key_sprint", name = "SPRINT", keyCode = LwjglGlfwKeycode.GLFW_KEY_LEFT_CONTROL, isToggle = true,
            xPercent = 31f, yPercent = 62f, widthDp = 52, heightDp = 52, opacity = 0.9f, cornerRadiusDp = 26,
            showMode = ControlShowMode.GAME
        )
        return defaults.filter { !it.isJoystick } + pad + sprint
    }
}
