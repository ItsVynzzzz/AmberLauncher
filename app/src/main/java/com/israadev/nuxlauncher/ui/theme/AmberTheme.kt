package com.israadev.nuxlauncher.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Warna identitas AmberLauncher: hitam hangat, kaca bertepi putih tipis, kuning amber sebagai aksen.
 * Dipakai oleh layar dan dialog baru. Kode lama masih memakai NuxColors (alias) sampai dipindahkan
 * bertahap.
 */
object Amber {
    val Ink = Color(0xFF111111)
    val Yellow = Color(0xFFFFD60A)
    val YellowSoft = Color(0xFFFFE566)
    val YellowTint = Color(0x26FFD60A)

    /** Panel hampir buram supaya isi tetap terbaca di atas game (tanpa blur, hemat GPU). */
    val Panel = Color(0xF2121110)
    /** Kaca gelap untuk panel kecil yang melayang di atas game. */
    val Glass = Color(0xD9111111)

    val Fill = Color(0x0FFFFFFF)
    val Line = Color(0x26FFFFFF)
    val LineStrong = Color(0x4DFFFFFF)
    val Press = Color(0x1AFFFFFF)

    val Text = Color(0xFFF4F3EE)
    val TextDim = Color(0xFFB2B0A6)
    val TextFaint = Color(0xFF8C8774)

    val Danger = Color(0xFFFF5A4D)
    val DangerSoft = Color(0xFFFFA399)
    val DangerTint = Color(0x1FFF5A4D)
    val Good = Color(0xFF34D399)
}
