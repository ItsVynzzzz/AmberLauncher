package com.israadev.nuxlauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Jumlah posisi busur pemutar dan selang antar langkahnya (10 langkah per detik). */
private const val SPINNER_STEPS = 12
private const val SPINNER_STEP_MS = 100L

/** Jumlah posisi segmen pada garis tunggu dan selang antar langkahnya. */
private const val BAR_POSITIONS = 9
private const val BAR_STEP_MS = 450L

/**
 * Pengganti CircularProgressIndicator tanpa animasi halus. Busur maju satu langkah tiap 100 ms
 * (bukan 60 kali per detik), jadi menunggu unduhan atau daftar tidak membuat UI menggambar ulang
 * terus-menerus. Posisi hanya dibaca di fase gambar: tiap langkah menggambar ulang pemutar ini saja,
 * tidak menyusun ulang layar. Ukuran bawaan 24 dp; pemanggil mengatur ukuran lewat [modifier].
 */
@Composable
fun AmberSpinner(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    strokeWidth: Dp = 2.dp
) {
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(SPINNER_STEP_MS)
            step = (step + 1) % SPINNER_STEPS
        }
    }
    Box(
        modifier = modifier
            .size(24.dp)
            .drawBehind {
                val stroke = strokeWidth.toPx()
                drawArc(
                    color = color,
                    startAngle = step * (360f / SPINNER_STEPS) - 90f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(size.width - stroke, size.height - stroke),
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
    )
}

/**
 * Pengganti LinearProgressIndicator tanpa persentase (menunggu): segmen bolak-balik yang maju satu
 * langkah tiap 450 ms. Bentuk (sudut membulat) diatur pemanggil lewat `clip` di [modifier]; tinggi
 * dan lebar juga dari pemanggil.
 */
@Composable
fun AmberWaitBar(
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    trackColor: Color = Color.White.copy(alpha = 0.18f)
) {
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        val cycle = 2 * (BAR_POSITIONS - 1)
        while (true) {
            delay(BAR_STEP_MS)
            step = (step + 1) % cycle
        }
    }
    Box(
        modifier = modifier
            .background(trackColor)
            .drawBehind {
                val last = BAR_POSITIONS - 1
                val pos = if (step <= last) step else 2 * last - step
                val segment = size.width * 0.3f
                val x = (size.width - segment) * (pos.toFloat() / last)
                drawRect(
                    color = color,
                    topLeft = Offset(x, 0f),
                    size = Size(segment, size.height)
                )
            }
    )
}
