package com.israadev.nuxlauncher.ui.theme

import android.os.Build
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Jarak kiri dan kanan yang benar-benar dibutuhkan kamera (poni atau lubang).
 *
 * Yang dihitung hanya lebar kamera itu sendiri ditambah [gap] kecil. Navigasi sistem
 * (tombol home/back/recent) tidak dihitung sama sekali, dan sisi layar yang tidak punya
 * kamera mendapat 0. Latar tetap digambar sampai ke tepi layar, jadi tidak ada pita kosong
 * yang terlihat di screenshot atau rekaman.
 */
@Composable
fun rememberCameraSideInsets(gap: Dp = 4.dp): Pair<Dp, Dp> {
    val density = LocalDensity.current
    val layoutDir = LocalLayoutDirection.current
    val view = LocalView.current

    // Dibaca saat komposisi supaya dihitung ulang ketika layar diputar
    val cutout = WindowInsets.displayCutout
    val safeLeft = cutout.getLeft(density, layoutDir)
    val safeRight = cutout.getRight(density, layoutDir)

    var leftPx = 0
    var rightPx = 0
    if (safeLeft > 0 || safeRight > 0) {
        // Cadangan bila bentuk kamera tidak diketahui: batasi supaya tidak terlalu lebar
        val cap = with(density) { 24.dp.roundToPx() }
        leftPx = minOf(safeLeft, cap)
        rightPx = minOf(safeRight, cap)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val rects = view.rootWindowInsets?.displayCutout?.boundingRects
            val width = view.width
            if (!rects.isNullOrEmpty() && width > 0) {
                var camLeft = 0
                var camRight = 0
                for (r in rects) {
                    val touchesLeft = r.left <= 1
                    val touchesRight = r.right >= width - 1
                    if (touchesLeft && !touchesRight) camLeft = maxOf(camLeft, r.right)
                    if (touchesRight && !touchesLeft) camRight = maxOf(camRight, width - r.left)
                }
                leftPx = minOf(safeLeft, camLeft)
                rightPx = minOf(safeRight, camRight)
            }
        }
    }

    val left = if (leftPx > 0) with(density) { leftPx.toDp() } + gap else 0.dp
    val right = if (rightPx > 0) with(density) { rightPx.toDp() } + gap else 0.dp
    return Pair(left, right)
}
