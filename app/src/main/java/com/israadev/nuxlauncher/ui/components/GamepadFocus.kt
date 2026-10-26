package com.israadev.nuxlauncher.ui.components

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
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        val undo = if (window != null) GamepadNav.install(window, allowBumper = false) else null
        onDispose { undo?.invoke() }
    }
}
