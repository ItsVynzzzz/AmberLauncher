package com.israadev.nuxlauncher.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp

/**
 * Lapisan latar yang sudah diburamkan (dibuat DashboardScreen) untuk gaya Liquid Glass.
 * Null = gaya Default, atau HP di bawah Android 12 yang belum mendukung blur.
 */
val LocalGlassBackdrop = staticCompositionLocalOf<GraphicsLayer?> { null }

enum class AmberGlassKind {
    /** Panel gelap di layar Mod/Akun/Pengaturan. */
    Panel,

    /** Tombol dan pil di atas foto latar terang di Home. */
    Home
}

/**
 * Permukaan kaca.
 *
 * Gaya Default: isian gelap tembus pandang + tepi tipis (tema AmberLauncher sendiri).
 *
 * Gaya Liquid Glass: panel menjadi "jendela" ke wallpaper. Wallpaper di belakangnya diburamkan dan
 * dibuat lebih hidup warnanya (lapisan dibuat DashboardScreen), hanya digelapkan secukupnya supaya
 * teks putih terbaca (mengikuti slider Redupkan latar), lalu diberi kilau lembut di atas dan tepi
 * tipis yang terang di sudut kiri atas. Jadi warna panel selalu mengikuti wallpaper yang dipasang.
 * Tanpa dukungan blur (di bawah Android 12), otomatis kembali ke gaya Default.
 */
fun Modifier.amberGlass(shape: Shape, kind: AmberGlassKind = AmberGlassKind.Panel): Modifier = composed {
    val style = AmberUi.style
    val backdrop = LocalGlassBackdrop.current
    if (style == AmberStyle.LIQUID && backdrop != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        var origin by remember { mutableStateOf(Offset.Zero) }
        // Panel di layar biasa mengikuti slider; tombol di Home selalu agak gelap karena
        // fotonya sering terang.
        val shade = if (kind == AmberGlassKind.Home) 0.40f else 0.18f + 0.42f * AmberUi.dim
        val glow = Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.16f),
            0.5f to Color.White.copy(alpha = 0.03f),
            1f to Color.Transparent
        )
        val rim = Brush.linearGradient(
            0f to Color.White.copy(alpha = 0.60f),
            0.5f to Color.White.copy(alpha = 0.08f),
            1f to Color.White.copy(alpha = 0.30f)
        )
        this
            .onGloballyPositioned { origin = it.positionInRoot() }
            .clip(shape)
            .drawBehind {
                translate(-origin.x, -origin.y) { drawLayer(backdrop) }
                drawRect(Color.Black.copy(alpha = shade))
                drawRect(glow)
            }
            .border(0.8.dp, rim, shape)
    } else {
        val fill = if (kind == AmberGlassKind.Home) AmberUi.HomeGlass else AmberUi.Surface
        val edge = if (kind == AmberGlassKind.Home) AmberUi.HomeGlassEdge else AmberUi.Border
        this
            .background(fill, shape)
            .border(1.dp, edge, shape)
    }
}
