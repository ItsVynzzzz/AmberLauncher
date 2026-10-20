package com.israadev.nuxlauncher.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import com.israadev.nuxlauncher.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.ui.theme.NuxColors
import kotlinx.coroutines.delay

private val GAME_TIPS = listOf(
    "Ketuk ikon gerigi atau angka FPS di layar untuk membuka menu. Keluar dari game lewat tombol merah di dalam menu.",
    "Bug itu hal yang biasa, karena tiada yang sepurna melainkan tuhan yang maha kuasa.",
    "Matikan Vertical Sync (VSync) di pengaturan Renderer jika mengalami stuttering atau layar blank hitam setelah logo Mojang.",
    "Jika tampilan layar Minecraft terpotong atau vertikal, aktifkan rotasi otomatis HP dan posisikan layar mendatar (Landscape) sebelum menekan Mainkan.",
    "Gunakan MobileGlues Renderer untuk efisiensi baterai dan kompatibilitas shaderpack terbaik.",
    "Jika Minecraft crash saat startup pada GPU Mali/Adreno, coba aktifkan 'DISABLE_SUBGROUP' di pengaturan renderer.",
    "Aktifkan OpenGL NoError di pengaturan MobileGlues untuk meningkatkan FPS saat bermain.",
    "Gunakan tombol HIDE GUI untuk menyembunyikan tombol virtual di layar saat bermain dengan keyboard & mouse fisik.",
    "Atur alokasi RAM sesuai spesifikasi HP: 2GB–3GB sudah sangat cukup untuk Minecraft versi 1.20+ tanpa modpack berat.",
    "Ubah sensitivitas kursor di Pengaturan Kontrol untuk membidik dan mengarahkan pandangan lebih akurat.",
    "Pilih Vulkan Driver Turnip di perangkat Snapdragon untuk performa grafis dan stabilitas maksimal.",
    "Kamu bisa menyesuaikan posisi dan ukuran tombol kontrol sesukamu di menu Kustomisasi Tombol.",
    "Gunakan fitur Import Addon untuk memasang Modpack (.mrpack), Mod (.jar), dan Resource Pack secara instan!",
    "Tekan tombol ESC di layar untuk membuka pause menu atau kembali ke menu sebelumnya.",
    "Tekan tombol F3 untuk melihat informasi koordinat, biome, dan grafik performa FPS di dalam game.",
    "Aktifkan mode Fullscreen di Pengaturan untuk mengabaikan notch/kamera depan dan memperluas pandangan."
)

/**
 * Layar loading sebelum logo Mojang: satu kartu transparan dengan tepi putih tipis, nama instance,
 * garis kemajuan kuning tipis, baris log terakhir, dan satu tips. Hanya satu animasi yang berjalan
 * terus (garis kemajuan), supaya tidak mengganggu JVM yang sedang menyala.
 */
@Composable
fun GameLoadingOverlay(
    visible: Boolean,
    instanceName: String,
    mcVersion: String,
    latestLog: String,
    onClose: () -> Unit,
    onViewLog: () -> Unit,
    modifier: Modifier = Modifier
) {
    var tipIndex by remember { mutableIntStateOf(0) }

    // Ganti tips setiap 6 detik
    LaunchedEffect(visible) {
        if (visible) {
            tipIndex = (GAME_TIPS.indices).random()
            while (true) {
                delay(6000)
                tipIndex = (tipIndex + 1) % GAME_TIPS.size
            }
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(500)),
        modifier = modifier
    ) {
        val cardShape = RoundedCornerShape(24.dp)
        val cardEdge = remember {
            Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = 0.42f),
                    Color.White.copy(alpha = 0.08f),
                    Color.White.copy(alpha = 0.22f)
                )
            )
        }
        val ghostEdge = Color.White.copy(alpha = 0.26f)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF1D1809),
                            Color(0xFF0D0D0D),
                            Color(0xFF090909)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .widthIn(max = 430.dp)
                    .clip(cardShape)
                    .background(Color.White.copy(alpha = 0.06f), cardShape)
                    .border(1.dp, cardEdge, cardShape)
                    .padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 20.dp)
            ) {
                // Nama instance + tombol tutup (untuk mengintip game di baliknya)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = instanceName,
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 23.sp,
                            letterSpacing = (-0.3).sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Memuat Minecraft $mcVersion",
                            color = Color.White.copy(alpha = 0.62f),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .border(1.dp, ghostEdge, CircleShape)
                            .clickable { onClose() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup loading",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Garis kemajuan tipis (menunggu, tanpa persentase)
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = NuxColors.Yellow,
                    trackColor = Color.White.copy(alpha = 0.12f)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Log terakhir
                Text(
                    text = if (latestLog.isNotBlank()) latestLog else "Menyiapkan mesin Java dan grafis...",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.12f))
                )
                Spacer(modifier = Modifier.height(14.dp))

                // Tips (tinggi dikunci supaya kartu tidak bergeser saat tips berganti)
                Box(modifier = Modifier.heightIn(min = 54.dp)) {
                    AnimatedContent(
                        targetState = GAME_TIPS.getOrElse(tipIndex) { GAME_TIPS[0] },
                        transitionSpec = {
                            fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
                        },
                        label = "tipAnimation"
                    ) { tipText ->
                        Text(
                            text = tipText,
                            color = Color.White.copy(alpha = 0.86f),
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Lihat log
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    val pillShape = RoundedCornerShape(16.dp)
                    Row(
                        modifier = Modifier
                            .clip(pillShape)
                            .border(1.dp, ghostEdge, pillShape)
                            .clickable { onViewLog() }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Lihat log",
                            color = Color.White.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}
