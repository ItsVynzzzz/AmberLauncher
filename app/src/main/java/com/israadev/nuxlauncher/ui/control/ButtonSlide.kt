package com.israadev.nuxlauncher.ui.control

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

/**
 * Geser antar tombol. Satu jari yang mulai di tombol W tetap "dimiliki" tombol W walau jarinya sudah keluar
 * dari kotaknya, jadi tanpa bantuan, menggeser ke D tidak menekan D. Di sini tiap tombol keyboard biasa mendaftar
 * (posisi dan ukurannya di layar), lalu gesture tombol yang sedang ditahan bertanya "tombol mana di bawah
 * jariku sekarang?" dan memindahkan tekanan: tombol baru ditekan dulu, baru tombol lama dilepas, supaya
 * gerakan berpindah-pindah tidak tersendat sesaat.
 *
 * Tiap tombol menghitung berapa jari yang menahannya ([Target.holds]). Tombol baru benar-benar ditekan pada
 * jari pertama dan dilepas pada jari terakhir, jadi dua jari di tombol yang sama atau satu jari yang melintas
 * di atas tombol yang sedang ditahan jari lain tidak melepaskannya terlalu cepat.
 *
 * Hanya dipakai dari thread utama (gesture dan komposisi), jadi tanpa kunci.
 */
internal object ButtonSlide {

    class Target(
        /**
         * Tombol keyboard biasa yang boleh dilewati jari. Tombol mouse (pukul/pakai) tidak: terlewat satu kali
         * saat berlari berarti memukul tanpa sengaja.
         */
        val slideable: Boolean
    ) {
        /** Pojok kiri atas tombol di layar (koordinat akar), diperbarui tiap tata letak. */
        var origin: Offset = Offset.Zero
        var size: Size = Size.Zero
        var holds: Int = 0

        /** Menekan atau melepas tombol di game sekaligus mengubah tampilannya. Diisi oleh tombolnya. */
        var setDown: (Boolean) -> Unit = {}
    }

    private val targets = ArrayList<Target>()

    fun register(t: Target) {
        if (!targets.contains(t)) targets.add(t)
    }

    /** Tombol hilang dari layar: bila masih tertahan, dilepas di game supaya tidak ada tombol yang menyangkut. */
    fun unregister(t: Target) {
        targets.remove(t)
        if (t.holds > 0) {
            t.holds = 0
            t.setDown(false)
        }
    }

    fun press(t: Target) {
        t.holds += 1
        if (t.holds == 1) t.setDown(true)
    }

    fun release(t: Target) {
        if (t.holds <= 0) return
        t.holds -= 1
        if (t.holds == 0) t.setDown(false)
    }

    /**
     * Tombol yang boleh dilewati dan berada di bawah titik [root] (koordinat akar), selain [except].
     * [inset] mengecilkan kotak tiap tombol: jari harus masuk sedikit ke dalam tombol baru sebelum berpindah,
     * jadi jari yang bergetar tepat di garis antara dua tombol yang berdempetan tidak menekan-lepas bergantian.
     */
    fun find(root: Offset, inset: Float, except: Target): Target? {
        for (t in targets) {
            if (t === except || !t.slideable) continue
            if (t.size.width <= inset * 2f || t.size.height <= inset * 2f) continue
            val x = root.x - t.origin.x
            val y = root.y - t.origin.y
            if (x >= inset && y >= inset && x <= t.size.width - inset && y <= t.size.height - inset) return t
        }
        return null
    }
}
