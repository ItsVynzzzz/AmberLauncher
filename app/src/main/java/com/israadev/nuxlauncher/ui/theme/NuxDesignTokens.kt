package com.israadev.nuxlauncher.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * NuxLite Yellow & Black Palette (gaya kaca gelap, sama dengan Home)
 * Latar hitam, permukaan kaca transparan bertepi putih tipis, teks putih, kuning hanya sebagai aksen.
 * Kuning dipakai sebagai isian (tombol utama, pil terpilih) dan di atasnya selalu teks Ink (hitam).
 * Nama properti lama dipertahankan sebagai alias supaya kode lain tidak perlu diubah:
 * DarkGray = teks utama (putih), GrayNeutral = teks pendukung, CardBorder = garis tepi kaca,
 * SurfaceWhite/SurfaceElevated/SurfaceInput = isian kaca bertingkat, YellowPale = tint kuning.
 */
object NuxColors {
    // Kuning (isian)
    val Yellow = Color(0xFFFFD60A)       // Warna merek: tombol utama, pil terpilih, aksen
    val YellowSoft = Color(0xFFFFE566)   // Kuning muda: aksen, progres
    val YellowPale = Color(0x2EFFD60A)   // Tint kuning 18% di atas gelap: item terpilih, lencana
    val YellowMist = Color(0x14FFD60A)   // Tint kuning 8%

    // Hitam / kaca
    val Ink = Color(0xFF111111)          // Tinta: teks di atas kuning, bingkai luar, bayangan keras
    val Background = Color(0xFF0E0E0D)   // Latar panel (hitam hangat)
    val Panel = Color(0xF2131211)        // Latar dialog (hampir buram supaya isi tetap terbaca)
    val SurfaceWhite = Color(0x0FFFFFFF) // Kaca tingkat 1 (putih 6%)
    val SurfaceElevated = Color(0x17FFFFFF) // Kaca tingkat 2 (putih 9%)
    val SurfaceInput = Color(0x1FFFFFFF) // Kolom isian (putih 12%)
    val DarkGray = Color(0xFFF4F3EE)     // Teks utama (putih hangat)
    val GrayNeutral = Color(0xFFB2B0A6)  // Teks pendukung (kontras tinggi di atas gelap)
    val LightGray = Color(0x1FFFFFFF)    // Garis pemisah (putih 12%)
    val CardBorder = Color(0x33FFFFFF)   // Garis tepi kaca (putih 20%)
    val ErrorRed = Color(0xFFE11D48)     // Merah galat
    val Amber = Color(0xFFFF8A00)        // Oranye peringatan (dibedakan dari kuning merek)
    val AmberDark = Color(0xFFFBBF24)    // Oranye terang untuk teks di atas gelap
    val SkyBlueDark = Color(0xFF38BDF8)  // Biru terang untuk teks di atas gelap
    val PurpleDark = Color(0xFFC084FC)   // Ungu terang untuk teks di atas gelap
    val TextPrimary = DarkGray
    val SuccessGreen = Color(0xFF16A34A) // Hijau khusus status berhasil/online

    // Alias lama (jangan dihapus, masih dipakai di kode lain)
    val ForestGreen = Yellow             // Dulu hijau utama, sekarang kuning merek
    val MintGreen = YellowSoft
    val SageGreen = Yellow               // Teks/ikon aksen di atas gelap
    val SoftLime = YellowPale
    val LightGreen = YellowMist
    val Mint = YellowPale
    val MintDark = YellowSoft
    val ForestDark = Ink
    val ForestLight = GrayNeutral
    val ForestMuted = GrayNeutral
    val Cream = Background
    val Coral = ErrorRed
    val TextMuted = GrayNeutral
    val SkyBlue = Color(0xFF2AA9F0)
}

object NuxSizes {
    val BorderWidth = 2.dp               // Tombol utama kuning (garis tinta tebal seperti PLAY)
    val GlassBorder = 1.dp               // Tepi kaca tipis untuk kartu, kolom, dialog
    val CornerRadius = 20.dp
    val CornerRadiusLarge = 28.dp
    val CornerRadiusSmall = 14.dp
    val ShadowOffset = 3.dp
    val BorderDefault = BorderStroke(BorderWidth, NuxColors.CardBorder)
    val ShapeDefault = RoundedCornerShape(CornerRadius)
    val ShapeLarge = RoundedCornerShape(CornerRadiusLarge)
    val ShapeSmall = RoundedCornerShape(CornerRadiusSmall)
}
