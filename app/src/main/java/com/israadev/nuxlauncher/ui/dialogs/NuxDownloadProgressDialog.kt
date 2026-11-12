package com.israadev.nuxlauncher.ui.dialogs

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.ui.components.AmberActionButton
import com.israadev.nuxlauncher.ui.components.AmberOverlay
import com.israadev.nuxlauncher.ui.i18n.Text
import com.israadev.nuxlauncher.ui.theme.AmberUi
import kotlin.math.sin

private val ProgressCardShape = RoundedCornerShape(30.dp)
private val ProgressCardFill = Color(0xFF18171C)
private val TankInnerFill = Color(0xFF101014)
private const val TWO_PI = 6.2831855f
private const val PI_F = 3.1415927f

/**
 * Pop up proses unduh game: kartu kecil di tengah layar. Di kiri ada "tangki cair": cincin
 * kemajuan di tepi dan air beraksen yang naik dengan gelombang pelan (persentase di tengahnya).
 * Di kanan: judul, nama instance, keterangan tahap di kotak kaca kecil, dan tombol Batal.
 * Di layar sempit (HP berdiri) tangki pindah ke atas dan isinya rata tengah.
 *
 * Warna mengikuti aksen pilihan pengguna. Satu-satunya animasi yang berjalan terus adalah fase
 * gelombang; nilainya hanya dibaca saat menggambar, jadi tidak ada recompose per frame.
 *
 * [onCancel] bila diisi memunculkan tombol Batal. Pop up tetap tidak bisa ditutup dengan ketukan di
 * luar atau tombol kembali, jadi unduhan tidak terhenti tanpa sengaja.
 */
@Composable
fun NuxDownloadProgressDialog(
    instanceName: String,
    progress: Float,
    message: String,
    onDismissRequest: () -> Unit = {},
    onCancel: (() -> Unit)? = null
) {
    val fraction = progress.coerceIn(0f, 1f)
    val percent = (fraction * 100f).toInt()

    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val enter = animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "downloadEnter"
    )

    AmberOverlay(
        onDismiss = onDismissRequest,
        dismissOnScrimTap = false,
        scrimAlpha = 0.72f
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val compact = maxHeight < 330.dp
            val stacked = maxWidth < 440.dp && !compact
            val tankSize = when {
                compact -> 104.dp
                stacked -> 150.dp
                else -> 136.dp
            }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(max = if (stacked) 380.dp else 560.dp)
                    .fillMaxWidth(if (stacked) 0.9f else 0.92f)
                    .graphicsLayer {
                        val e = enter.value
                        alpha = e
                        scaleX = 0.94f + 0.06f * e
                        scaleY = 0.94f + 0.06f * e
                    }
                    .clip(ProgressCardShape)
                    .background(ProgressCardFill, ProgressCardShape)
                    .border(1.dp, AmberUi.BorderStrong, ProgressCardShape)
                    .padding(if (compact) 14.dp else 20.dp)
            ) {
                if (stacked) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LiquidTank(fraction = fraction, percent = percent, diameter = tankSize)
                        DownloadInfo(
                            instanceName = instanceName,
                            message = message,
                            onCancel = onCancel,
                            centered = true,
                            modifier = Modifier
                                .padding(top = 18.dp)
                                .fillMaxWidth()
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LiquidTank(fraction = fraction, percent = percent, diameter = tankSize)
                        Spacer(modifier = Modifier.width(if (compact) 14.dp else 20.dp))
                        DownloadInfo(
                            instanceName = instanceName,
                            message = message,
                            onCancel = onCancel,
                            centered = false,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/** Kolom info: judul kecil, nama instance, kotak keterangan tahap, dan tombol Batal. */
@Composable
private fun DownloadInfo(
    instanceName: String,
    message: String,
    onCancel: (() -> Unit)?,
    centered: Boolean,
    modifier: Modifier = Modifier
) {
    val insetShape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier,
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Download,
                contentDescription = null,
                tint = AmberUi.AccentSoft,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Mengunduh game",
                color = AmberUi.AccentSoft,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                maxLines = 1
            )
        }

        Text(
            text = instanceName,
            color = AmberUi.Text,
            fontSize = 21.sp,
            lineHeight = 26.sp,
            fontWeight = FontWeight.Black,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )

        Row(
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .clip(insetShape)
                .background(Color.White.copy(alpha = 0.05f), insetShape)
                .border(1.dp, AmberUi.Border, insetShape)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(AmberUi.AccentSoft, CircleShape)
            )
            Spacer(modifier = Modifier.width(9.dp))
            Text(
                text = message.ifBlank { "Menyiapkan file game..." },
                color = AmberUi.TextDim,
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        if (onCancel != null) {
            Row(
                modifier = Modifier
                    .padding(top = 14.dp)
                    .fillMaxWidth(),
                horizontalArrangement = if (centered) Arrangement.Center else Arrangement.End
            ) {
                AmberActionButton(
                    text = "Batal",
                    onClick = onCancel,
                    filled = false,
                    height = 38.dp
                )
            }
        }
    }
}

/**
 * Tangki cair berbentuk lingkaran: cincin kemajuan di luar, di dalamnya air beraksen dengan dua
 * gelombang dan beberapa gelembung. Tinggi air dan sapuan cincin ikut bergerak halus ke nilai
 * terbaru. Persentase ditulis di tengah.
 */
@Composable
private fun LiquidTank(
    fraction: Float,
    percent: Int,
    diameter: Dp,
    modifier: Modifier = Modifier
) {
    val level = animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "tankLevel"
    )
    val wave = rememberInfiniteTransition(label = "tankWave").animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI,
        animationSpec = infiniteRepeatable(tween(durationMillis = 3200, easing = LinearEasing)),
        label = "tankPhase"
    )
    val backPath = remember { Path() }
    val frontPath = remember { Path() }
    val accent = AmberUi.Accent
    val accentSoft = AmberUi.AccentSoft
    val ringTrack = AmberUi.Border
    val ringStroke = diameter * 0.045f
    val inner = diameter - (ringStroke + 6.dp) * 2

    Box(
        modifier = modifier
            .size(diameter)
            .drawBehind {
                // Cahaya aksen lembut di belakang tangki
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.30f), Color.Transparent),
                        radius = size.minDimension * 0.9f
                    ),
                    radius = size.minDimension * 0.9f
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = ringStroke.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = ringTrack,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke)
            )
            val sweep = 360f * level.value
            if (sweep > 0.5f) {
                drawArc(
                    color = accentSoft,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }

        Box(
            modifier = Modifier
                .size(inner)
                .clip(CircleShape)
                .background(TankInnerFill, CircleShape)
                .drawBehind {
                    drawLiquid(
                        level = level.value,
                        phase = wave.value,
                        back = backPath,
                        front = frontPath,
                        accent = accent,
                        soft = accentSoft
                    )
                }
                .border(1.dp, AmberUi.Border, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$percent%",
                color = Color.White,
                fontSize = if (diameter >= 120.dp) 30.sp else 24.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
        }
    }
}

/** Isi bidang gelombang (dari permukaan air sampai dasar) ke [path]. */
private fun waveInto(
    path: Path,
    w: Float,
    h: Float,
    base: Float,
    amp: Float,
    wavelength: Float,
    phase: Float
) {
    path.rewind()
    val steps = 40
    val dx = w / steps
    path.moveTo(0f, h)
    for (i in 0..steps) {
        val x = dx * i
        path.lineTo(x, base + amp * sin(x / wavelength * TWO_PI + phase))
    }
    path.lineTo(w, h)
    path.close()
}

/** Air dengan dua gelombang berlawanan arah ditambah tiga gelembung yang naik pelan. */
private fun DrawScope.drawLiquid(
    level: Float,
    phase: Float,
    back: Path,
    front: Path,
    accent: Color,
    soft: Color
) {
    if (level <= 0.001f) return
    val w = size.width
    val h = size.height
    // Air tidak pernah lebih tipis dari 4% supaya awal unduhan sudah terlihat
    val fill = 0.04f + 0.96f * level.coerceIn(0f, 1f)
    val base = h * (1f - fill)
    // Gelombang mengecil saat hampir kosong atau hampir penuh
    val amp = h * 0.045f * minOf(1f, minOf(fill, 1f - fill) * 10f)

    waveInto(back, w, h, base, amp, w * 0.9f, phase + 1.3f)
    drawPath(
        path = back,
        brush = Brush.verticalGradient(
            colors = listOf(soft.copy(alpha = 0.35f), accent.copy(alpha = 0.35f)),
            startY = base - amp,
            endY = h
        )
    )

    waveInto(front, w, h, base + amp * 0.3f, amp * 0.85f, w * 0.65f, -phase)
    drawPath(
        path = front,
        brush = Brush.verticalGradient(
            colors = listOf(soft.copy(alpha = 0.92f), accent),
            startY = base - amp,
            endY = h
        )
    )

    // Gelembung naik dari dasar menuju permukaan
    if (h - base > h * 0.25f) {
        val bubbleStroke = Stroke(width = 1.2.dp.toPx())
        val loop = phase / TWO_PI
        for (i in 0 until 3) {
            val t = (loop + i / 3f) % 1f
            val r = h * (0.018f + 0.008f * i)
            val bx = w * (0.30f + 0.20f * i) + sin(t * TWO_PI) * w * 0.03f
            val by = h - (h - base - r * 3f) * t
            val a = sin(t * PI_F) * 0.5f
            drawCircle(
                color = Color.White.copy(alpha = a),
                radius = r,
                center = Offset(bx, by),
                style = bubbleStroke
            )
        }
    }
}
