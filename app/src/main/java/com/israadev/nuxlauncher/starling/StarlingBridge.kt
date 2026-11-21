package com.israadev.nuxlauncher.starling

/**
 * Jalur dari kontrol Amber ke mesin Starling. Diisi oleh GameActivity milik Starling (dev.horizon.android, dari sumber
 * Starling dan patch amber5/0005): kelas Java itu yang memegang fungsi native, jadi Kotlin di sini tidak pernah menyebut
 * kelas yang baru ada setelah mesin dipasang dan build tanpa mesin tetap utuh.
 */
interface StarlingBridge {
    /** Tombol keyboard atau mouse dengan token milik game (`key.keyboard.w`, `key.mouse.left`, `mouse.scroll.up`). */
    fun token(name: String, down: Boolean)

    /** Putaran kamera dalam piksel, seperti gerak mouse yang ditangkap. */
    fun look(dx: Float, dy: Float)

    /** Keadaan layar mesin pada frame terakhir: bit 1 = berjalan, 2 = menu/layar terbuka, 4 = penunjuk ditangkap. */
    fun state(): Int

    /** Menutup mesin. */
    fun exit()
}
