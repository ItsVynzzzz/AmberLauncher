package com.israadev.nuxlauncher.core.controls

/**
 * Batas ukuran tombol kontrol virtual (dp). Dipakai bersama oleh editor (slider) dan impor layout
 * supaya angkanya selalu sama.
 *
 * Yang naik hanya BATAS MAKSIMUM. Ukuran bawaan dan ukuran yang sudah tersimpan tidak disentuh,
 * jadi kontrol pengguna lama tidak tiba-tiba membesar setelah pembaruan.
 */
object ControlLimits {
    const val MIN_SIZE_DP = 24

    /** Lebar maksimum tombol biasa (dulu 160). */
    const val MAX_WIDTH_DP = 600

    /** Tinggi maksimum tombol biasa (dulu 160). */
    const val MAX_HEIGHT_DP = 400

    const val MIN_JOYSTICK_DP = 80

    /** Diameter maksimum joystick (dulu 220). */
    const val MAX_JOYSTICK_DP = 400

    /** Kebulatan sudut maksimum di slider editor. Joystick memakai diameter / 2 sendiri. */
    const val MAX_CORNER_DP = 28

    const val MIN_OPACITY = 0.1f
}
