package com.israadev.nuxlauncher.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.ui.i18n.Text

private val LOADING_BACKGROUND = Color(0xFF1B1B1B)

/**
 * Layar loading sebelum logo Mojang. Polos dan datar: latar gelap, nama instance besar di kiri bawah,
 * baris log terakhir tepat di bawahnya, dan garis kemajuan putih selebar layar di paling bawah.
 * Tanpa tombol: tombol layar (ikon gerigi, FPS, kontrol) disembunyikan GameScreen selama layar ini
 * tampil dan baru muncul setelah logo Mojang mulai digambar, atau setelah 60 detik bila game macet.
 * Satu-satunya animasi yang berjalan terus adalah garis kemajuan, dan tidak ada pengatur waktu lain,
 * supaya tidak mengganggu JVM yang sedang menyala. Ukuran huruf dan garis mengikuti tinggi layar.
 */
@Composable
fun GameLoadingOverlay(
    visible: Boolean,
    instanceName: String,
    latestLog: String,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(500)),
        modifier = modifier
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(LOADING_BACKGROUND)
        ) {
            val screenHeight = maxHeight.value
            val titleSize = (screenHeight * 0.11f).coerceIn(26f, 52f).sp
            val logSize = (screenHeight * 0.042f).coerceIn(13f, 20f).sp
            val barHeight = (screenHeight * 0.034f).coerceIn(10f, 18f).dp
            val edge = (screenHeight * 0.05f).coerceIn(16f, 28f).dp

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = edge, end = edge, bottom = edge)
            ) {
                Text(
                    text = instanceName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = titleSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (latestLog.isNotBlank()) latestLog else "Menyiapkan mesin Java dan grafis...",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = logSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(edge * 0.45f))
                // Garis kemajuan: menunggu tanpa persentase (launcher tidak tahu seberapa jauh JVM memuat)
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(barHeight)
                        .clip(CircleShape),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.18f)
                )
            }
        }
    }
}
