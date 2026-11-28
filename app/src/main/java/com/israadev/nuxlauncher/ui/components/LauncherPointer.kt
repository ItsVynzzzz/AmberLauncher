package com.israadev.nuxlauncher.ui.components

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import com.israadev.nuxlauncher.core.settings.SettingsManager
import com.israadev.nuxlauncher.ui.control.PointerStyle
import com.movtery.zalithlauncher.bridge.CursorShape
import kotlin.math.roundToInt
import androidx.compose.foundation.clickable as foundationClickable

/*
 * Penunjuk mouse fisik di launcher (layar utama, pengaturan, dialog), memakai pengaturan yang sama dengan
 * Pengaturan > Kontrol > Penunjuk kursor:
 *  - gambar kustom bentuk Default (panah) dipakai sebagai penunjuk biasa;
 *  - gambar kustom bentuk Tangan dipakai di atas tombol dan baris yang bisa diklik;
 *  - gambar kustom bentuk Kursor Teks dipakai di atas kolom teks;
 *  - ukuran mengikuti "Ukuran kursor virtual" dan titik klik mengikuti hotspot masing-masing bentuk.
 * Bentuk tanpa gambar kustom memakai penunjuk bawaan Android (panah, tangan, kursor teks). Bila "Pakai kursor sistem
 * Android" menyala, gambar kustom tidak dipakai di launcher dan semuanya memakai penunjuk bawaan Android.
 *
 * Hanya layar launcher (MainActivity) yang membungkus isinya dengan [LauncherPointerHost]. Di luar itu, misalnya
 * menu dalam game yang menggambar kursornya sendiri, modifier di bawah tidak berbuat apa-apa.
 */

internal class LauncherPointers(
    /** Penunjuk biasa kustom, atau null bila memakai panah bawaan Android. */
    val arrow: PointerIcon?,
    val hand: PointerIcon,
    val text: PointerIcon
)

internal val LocalLauncherPointers = staticCompositionLocalOf<LauncherPointers?> { null }

/** Menyediakan ikon penunjuk untuk seluruh isi; dibangun ulang hanya saat gambar, ukuran, atau hotspot berubah. */
@Composable
fun LauncherPointerHost(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val settings by SettingsManager.settings.collectAsState()
    val density = LocalDensity.current.density
    // Naik tiap kali gambar kursor diganti atau dihapus di pengaturan.
    val revision = PointerStyle.revision
    val sizeDp = settings.mouseSizeDp
    val hotspots = settings.pointerHotspots
    val systemPointer = settings.physicalMouseSystemPointer
    val pointers = remember(context, sizeDp, hotspots, systemPointer, density, revision) {
        buildPointers(context, sizeDp, hotspots, systemPointer, density)
    }
    CompositionLocalProvider(LocalLauncherPointers provides pointers, content = content)
}

private fun buildPointers(
    context: Context,
    sizeDp: Int,
    hotspots: Map<String, Int>,
    systemPointer: Boolean,
    density: Float
): LauncherPointers {
    val sizePx = (sizeDp.coerceIn(16, 48) * density).roundToInt().coerceIn(16, 192)

    fun custom(shape: CursorShape): PointerIcon? {
        if (systemPointer) return null
        return try {
            val source = PointerStyle.bitmap(context, shape)?.asAndroidBitmap() ?: return null
            val scaled = Bitmap.createScaledBitmap(source, sizePx, sizePx, true)
            val hotX = (PointerStyle.hotspotX(hotspots, shape) / 100f * sizePx).coerceIn(0f, sizePx - 1f)
            val hotY = (PointerStyle.hotspotY(hotspots, shape) / 100f * sizePx).coerceIn(0f, sizePx - 1f)
            PointerIcon(android.view.PointerIcon.create(scaled, hotX, hotY))
        } catch (_: Throwable) {
            null
        }
    }

    return LauncherPointers(
        arrow = custom(CursorShape.Arrow),
        hand = custom(CursorShape.Hand) ?: PointerIcon.Hand,
        text = custom(CursorShape.IBeam) ?: PointerIcon.Text
    )
}

/** Penunjuk biasa di seluruh area ini (hanya bila ada gambar kustom untuk bentuk Default). */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.launcherPointer(): Modifier = composed {
    val arrow = LocalLauncherPointers.current?.arrow
    if (arrow != null) Modifier.pointerHoverIcon(arrow) else Modifier
}

/** Penunjuk tangan di atas elemen yang bisa diklik. [enabled] false (tombol nonaktif) = tidak berubah. */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.handPointer(enabled: Boolean = true): Modifier = composed {
    val pointers = LocalLauncherPointers.current
    if (enabled && pointers != null) Modifier.pointerHoverIcon(pointers.hand) else Modifier
}

/** Penunjuk kursor teks di atas kolom teks; menang atas penunjuk milik elemen di dalamnya. */
@OptIn(ExperimentalComposeUiApi::class)
fun Modifier.textPointer(): Modifier = composed {
    val pointers = LocalLauncherPointers.current
    if (pointers != null) Modifier.pointerHoverIcon(pointers.text, overrideDescendants = true) else Modifier
}

/*
 * clickable milik launcher: sama persis dengan androidx.compose.foundation.clickable, ditambah penunjuk tangan di
 * atas elemennya selama aktif. File yang memakai clickable mengimpor yang ini (bukan yang dari foundation), jadi
 * semua tombol dan baris yang bisa diklik ikut tanpa mengubah tiap pemanggilnya.
 */
fun Modifier.clickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit
): Modifier = this
    .handPointer(enabled)
    .foundationClickable(enabled = enabled, onClickLabel = onClickLabel, role = role, onClick = onClick)

fun Modifier.clickable(
    interactionSource: MutableInteractionSource?,
    indication: Indication?,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit
): Modifier = this
    .handPointer(enabled)
    .foundationClickable(
        interactionSource = interactionSource,
        indication = indication,
        enabled = enabled,
        onClickLabel = onClickLabel,
        role = role,
        onClick = onClick
    )
