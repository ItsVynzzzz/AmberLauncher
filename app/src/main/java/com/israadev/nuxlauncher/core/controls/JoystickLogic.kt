package com.israadev.nuxlauncher.core.controls

import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Hitungan joystick WASD (murni, tanpa Android supaya mudah diuji). Tidak ada alokasi per gerakan jari.
 *
 * Dibanding joystick tengah-tetap biasa, ada tiga pengaman agar sentuhan sekecil apa pun tidak membuat
 * karakter berjalan:
 *  - Titik tumpu. Jari yang mendarat di dekat pusat memakai pusat sebagai tumpu. Jari yang mendarat
 *    jauh dari pusat (menyenggol tepi, telapak, jempol yang meleset) memakai titik pendaratannya sebagai
 *    tumpu, jadi menyentuh saja tidak menggerakkan apa pun: baru setelah digeser dari titik itu.
 *  - Zona mati lebar: arah baru menyala setelah kenop bergeser [ON_FRACTION] dari jarak geser maksimum.
 *  - Histeresis: setelah menyala, arah baru padam di bawah [OFF_FRACTION]. Jempol yang bergetar di sekitar
 *    ambang tidak membuat tombol arah berkedip nyala-mati.
 *
 * Pemetaan arah tetap 8 arah (tiap sektor 45 derajat) dan kunci lari tetap: dorong sampai tepi lingkaran
 * sambil maju, tertahan selama maju masih ditekan.
 */
class JoystickLogic {

    // Keluaran (diperbarui oleh begin/update/release)
    var w = false
        private set
    var a = false
        private set
    var s = false
        private set
    var d = false
        private set
    var sprint = false
        private set

    /** Posisi kenop terhadap pusat lingkaran, sudah dijepit ke jarak geser maksimum. */
    var knobX = 0f
        private set
    var knobY = 0f
        private set

    private var anchorX = 0f
    private var anchorY = 0f
    private var maxDrag = 1f
    private var radius = 1f
    private var active = false
    private var touching = false

    /**
     * Jari mendarat di ([x], [y]) pada lingkaran berpusat ([cx], [cy]). [radius] jari-jari lingkaran luar,
     * [maxDrag] jarak geser kenop maksimum dari tumpu (keduanya piksel).
     */
    fun begin(x: Float, y: Float, cx: Float, cy: Float, radius: Float, maxDrag: Float) {
        this.radius = radius
        this.maxDrag = if (maxDrag > 1f) maxDrag else 1f
        val fromCenter = hypot(x - cx, y - cy)
        if (fromCenter <= OFF_FRACTION * this.maxDrag) {
            anchorX = cx
            anchorY = cy
        } else {
            anchorX = x
            anchorY = y
        }
        active = false
        touching = true
        clearOutput()
    }

    /** Jari bergeser ke ([x], [y]). [autoSprint] false = joystick tidak pernah menahan Ctrl. */
    fun update(x: Float, y: Float, autoSprint: Boolean) {
        if (!touching) return
        val dx = x - anchorX
        val dy = y - anchorY
        val dist = hypot(dx, dy)

        // Kenop digambar searah jari, dijepit ke jarak geser maksimum.
        if (dist > 0f) {
            val k = if (dist > maxDrag) maxDrag / dist else 1f
            knobX = dx * k
            knobY = dy * k
        } else {
            knobX = 0f
            knobY = 0f
        }

        val onAt = ON_FRACTION * maxDrag
        val offAt = OFF_FRACTION * maxDrag
        active = if (active) dist >= offAt else dist >= onAt

        if (!active) {
            w = false
            a = false
            s = false
            d = false
            sprint = false
            return
        }

        val deg = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
        w = deg >= -157.5 && deg <= -22.5
        s = deg >= 22.5 && deg <= 157.5
        d = deg >= -67.5 && deg <= 67.5
        a = !(deg >= -112.5 && deg <= 112.5)

        // Kunci lari: sampai tepi lingkaran sambil maju; berhenti saat tidak maju lagi.
        if (!w || !autoSprint) {
            sprint = false
        } else if (dist >= radius * SPRINT_FRACTION) {
            sprint = true
        }
    }

    /** Jari diangkat atau joystick dilepas: semua keluaran kembali netral. */
    fun release() {
        touching = false
        active = false
        clearOutput()
    }

    private fun clearOutput() {
        w = false
        a = false
        s = false
        d = false
        sprint = false
        knobX = 0f
        knobY = 0f
    }

    companion object {
        /** Arah menyala setelah kenop bergeser sejauh ini (bagian dari jarak geser maksimum). */
        const val ON_FRACTION = 0.30f

        /** Arah padam setelah kenop kembali di bawah ini (histeresis). Juga batas "mendarat di pusat". */
        const val OFF_FRACTION = 0.20f

        /** Lari menyala setelah jari menjauh sejauh ini dari tumpu (bagian dari jari-jari lingkaran). */
        const val SPRINT_FRACTION = 0.98f
    }
}
