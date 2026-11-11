package com.israadev.nuxlauncher.ui.components

import android.os.Build
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.israadev.nuxlauncher.core.controls.GamepadNav
import kotlinx.coroutines.launch

private val RingColor = Color(0xFFFFD23F)
private val FocusTint = Color.White.copy(alpha = 0.07f)
private val PressTint = Color.White.copy(alpha = 0.12f)

/** Bingkai kuning tipis di sekeliling yang sedang disorot (untuk navigasi controller/D-pad/keyboard). */
fun DrawScope.drawGamepadFocusRing() {
    val stroke = 2.dp.toPx()
    drawRect(FocusTint)
    drawRoundRect(
        color = RingColor,
        topLeft = Offset(stroke / 2f, stroke / 2f),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(10.dp.toPx()),
        style = Stroke(width = stroke)
    )
}

/**
 * Pengganti umpan balik klik bawaan: tint tipis saat ditekan dan bingkai saat disorot lewat
 * D-pad/controller. Semuanya digambar di fase gambar, tanpa menyusun ulang layar.
 */
object FocusRingIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        FocusRingNode(interactionSource)

    override fun hashCode(): Int = -1

    override fun equals(other: Any?): Boolean = other === this
}

private class FocusRingNode(
    private val interactionSource: InteractionSource
) : Modifier.Node(), DrawModifierNode {
    private var pressed = false
    private var focused = false

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> pressed = true
                    is PressInteraction.Release -> pressed = false
                    is PressInteraction.Cancel -> pressed = false
                    is FocusInteraction.Focus -> focused = true
                    is FocusInteraction.Unfocus -> focused = false
                }
                invalidateDraw()
            }
        }
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (pressed) drawRect(PressTint)
        if (focused) drawGamepadFocusRing()
    }
}

/** Pasang di akar layar: semua `clickable` di bawahnya otomatis punya sorotan fokus. */
@Composable
fun GamepadFocusProvider(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalIndication provides FocusRingIndication) {
        content()
    }
}

/**
 * Letakkan di dalam isi sebuah Dialog: jendela dialog ikut bisa dinavigasi dengan controller
 * (A pilih, B tutup, stick pindah sorotan).
 */
@Composable
fun GamepadWindowHook() {
    // Semua dialog memanggil hook ini, jadi bar sistem disembunyikan di sini sekali untuk semuanya.
    ImmersiveWindowHook()
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        val undo = if (window != null) GamepadNav.install(window, allowBumper = false) else null
        onDispose { undo?.invoke() }
    }
}

/**
 * Menyembunyikan bar status dan bar navigasi Android di jendela dialog.
 *
 * Kenapa perlu: dialog adalah jendela terpisah dan TIDAK ikut mode layar penuh milik Activity.
 * Begitu dialog (atau menu samping in-game) terbuka, Android memunculkan lagi bar status dan
 * navigasi di atasnya, sehingga tampilan terdorong atau tertutup bar. Di sini jendela dialog
 * meminta hal yang sama dengan Activity: bar disembunyikan, dan hanya muncul sebentar bila
 * pengguna menggesernya dari tepi layar.
 *
 * Dipanggil lagi tiap jendela dapat fokus, karena sebagian HP memunculkan bar kembali saat itu.
 */
@Composable
fun ImmersiveWindowHook() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window == null) {
            return@DisposableEffect onDispose { }
        }
        val hideBars: () -> Unit = {
            try {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val attrs = window.attributes
                    if (attrs.layoutInDisplayCutoutMode !=
                        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    ) {
                        attrs.layoutInDisplayCutoutMode =
                            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                        window.attributes = attrs
                    }
                }
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                    )
            } catch (_: Throwable) {
                // Bar sistem tetap tampil di perangkat yang menolak; tampilan tetap berfungsi.
            }
        }
        hideBars()
        // Sekali lagi setelah jendela benar-benar tampil: panggilan pertama bisa terlalu awal.
        view.post { hideBars() }
        val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) hideBars()
        }
        try {
            val observer = view.viewTreeObserver
            if (observer.isAlive) observer.addOnWindowFocusChangeListener(focusListener)
        } catch (_: Throwable) {
        }
        onDispose {
            try {
                val observer = view.viewTreeObserver
                if (observer.isAlive) observer.removeOnWindowFocusChangeListener(focusListener)
            } catch (_: Throwable) {
            }
        }
    }
}

/**
 * Letakkan di dalam isi sebuah Dialog: dialog muncul dan hilang langsung, tanpa animasi jendela
 * bawaan sistem (memudar/membesar). Dipanggil sekali per dialog; tidak ada pekerjaan setelahnya.
 */
@Composable
fun NoWindowAnimationHook() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window != null && window.attributes.windowAnimations != 0) {
            window.setWindowAnimations(0)
        }
        onDispose { }
    }
}
