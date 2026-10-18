package com.israadev.nuxlauncher.ui.dialogs

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.ui.components.AmberAlert
import com.israadev.nuxlauncher.ui.theme.Amber

/**
 * Dialog proses unduh game. Batang kemajuan digambar langsung dan nilainya hanya dibaca saat
 * menggambar, jadi animasi kemajuan tidak menyusun ulang dialog; tidak ada indikator berputar
 * yang menggambar ulang terus-menerus.
 */
@Composable
fun NuxDownloadProgressDialog(
    instanceName: String,
    progress: Float,
    message: String,
    onDismissRequest: () -> Unit = {}
) {
    val animated = animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 250),
        label = "dlProgress"
    )
    val percent = (progress.coerceIn(0f, 1f) * 100f).toInt()

    AmberAlert(
        onDismissRequest = onDismissRequest,
        title = "Mengunduh game",
        icon = "download",
        accent = Amber.Yellow
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = instanceName,
                color = Amber.Text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "$percent%",
                color = Amber.Yellow,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        Box(
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .height(10.dp)
                .clip(CircleShape)
                .drawBehind {
                    drawRect(Amber.Fill)
                    val w = size.width * animated.value
                    if (w > 0f) {
                        drawRoundRect(
                            color = Amber.Yellow,
                            size = Size(w, size.height),
                            cornerRadius = CornerRadius(size.height / 2f)
                        )
                    }
                }
                .border(1.dp, Amber.Line, CircleShape)
        )
        Text(
            text = message.ifBlank { "Menyiapkan file game..." },
            color = Amber.TextDim,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}
