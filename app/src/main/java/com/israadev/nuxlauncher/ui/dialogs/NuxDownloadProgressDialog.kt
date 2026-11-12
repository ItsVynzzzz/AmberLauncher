package com.israadev.nuxlauncher.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.ui.components.AmberActionButton
import com.israadev.nuxlauncher.ui.components.AmberOverlay
import com.israadev.nuxlauncher.ui.i18n.Text
import com.israadev.nuxlauncher.ui.theme.AmberUi

private val ProgressCardShape = RoundedCornerShape(28.dp)
private val ProgressCardFill = Color(0xFF18171C)

/**
 * Pop up proses unduh game: kartu kecil di tengah layar dengan ikon unduh beraksen, nama instance,
 * persentase besar, batang kemajuan, dan keterangan tahap. Warna mengikuti aksen pilihan pengguna.
 *
 * Batang kemajuan digambar langsung tanpa animasi: lebarnya mengikuti nilai terbaru; tidak ada
 * indikator berputar yang menggambar ulang terus-menerus.
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

    AmberOverlay(
        onDismiss = onDismissRequest,
        dismissOnScrimTap = false,
        scrimAlpha = 0.7f
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .widthIn(max = 440.dp)
                .fillMaxWidth(0.88f)
                .clip(ProgressCardShape)
                .background(ProgressCardFill, ProgressCardShape)
                .border(1.dp, AmberUi.BorderStrong, ProgressCardShape)
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(AmberUi.Accent, CircleShape)
                        .border(1.dp, AmberUi.AccentEdge, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Download,
                        contentDescription = null,
                        tint = AmberUi.Text,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Mengunduh game",
                        color = AmberUi.Text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = instanceName,
                        color = AmberUi.TextDim,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "$percent%",
                    color = AmberUi.AccentSoft,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1
                )
            }

            Box(
                modifier = Modifier
                    .padding(top = 18.dp)
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(CircleShape)
                    .drawBehind {
                        drawRect(Color.White.copy(alpha = 0.08f))
                        if (fraction > 0f) {
                            // Isian minimal selebar tingginya supaya 1% pun terlihat sebagai pil
                            val w = (size.width * fraction).coerceAtLeast(size.height)
                            drawRoundRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(AmberUi.Accent, AmberUi.AccentSoft),
                                    startX = 0f,
                                    endX = w
                                ),
                                size = Size(w, size.height),
                                cornerRadius = CornerRadius(size.height / 2f)
                            )
                        }
                    }
                    .border(1.dp, AmberUi.Border, CircleShape)
            )

            Text(
                text = message.ifBlank { "Menyiapkan file game..." },
                color = AmberUi.TextDim,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 12.dp)
            )

            if (onCancel != null) {
                Spacer(modifier = Modifier.height(16.dp))
                AmberActionButton(
                    text = "Batal",
                    onClick = onCancel,
                    filled = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
