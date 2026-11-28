package com.israadev.nuxlauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.israadev.nuxlauncher.ui.components.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.ui.i18n.Text
import com.israadev.nuxlauncher.ui.i18n.Tr
import com.israadev.nuxlauncher.ui.theme.AmberUi

private val LOADING_BACKGROUND = Color(0xFF0F1115)

/**
 * Layar loading sebelum logo Mojang, bergaya Amber tapi sengaja ringan: latar gelap dengan dua cahaya
 * aksen yang digambar sekali (tidak bergerak, tanpa blur), label kecil "MEMUAT GAME" di kiri atas, nama
 * instance besar di kiri bawah dengan dua pil (versi Minecraft dan loader), kartu tipis berisi baris
 * log terakhir, dan garis tunggu berwarna aksen di bawahnya. Warna aksen mengikuti pilihan pemain.
 *
 * Satu-satunya tombol adalah X kecil di kanan atas, untuk membatalkan dan keluar bila pemain tidak
 * sengaja menekan Main atau salah memilih instance. Tombol layar lain (ikon gerigi, FPS, kontrol)
 * disembunyikan GameScreen selama layar ini tampil dan baru muncul setelah logo Mojang mulai
 * digambar, atau setelah 60 detik bila game macet (saat itu X disembunyikan, keluar lewat gerigi).
 * Tanpa animasi masuk/keluar. Garis tunggu tidak bergerak halus tiap frame: segmennya hanya maju
 * satu langkah tiap 450 ms, supaya JVM yang sedang menyala tidak berebut CPU dengan UI.
 * Ukuran huruf dan garis mengikuti tinggi layar.
 */
@Composable
fun GameLoadingOverlay(
    visible: Boolean,
    instanceName: String,
    latestLog: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    showClose: Boolean = true,
    mcVersion: String = "",
    loaderLabel: String = ""
) {
    if (!visible) return

    val accent = AmberUi.Accent
    val accentSoft = AmberUi.AccentSoft

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(LOADING_BACKGROUND)
            // Dua cahaya aksen: besar di kiri bawah (di belakang nama), tipis di kanan atas.
            // Digambar sekali dan tidak pernah berubah, jadi tidak menambah beban saat game menyala.
            .drawBehind {
                val big = maxOf(size.width, size.height)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.30f), Color.Transparent),
                        center = Offset(size.width * 0.12f, size.height * 1.02f),
                        radius = big * 0.75f
                    )
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(accentSoft.copy(alpha = 0.10f), Color.Transparent),
                        center = Offset(size.width * 0.92f, size.height * 0.04f),
                        radius = big * 0.50f
                    )
                )
            }
    ) {
        val screenHeight = maxHeight.value
        val titleSize = (screenHeight * 0.105f).coerceIn(26f, 50f).sp
        val logSize = (screenHeight * 0.036f).coerceIn(12f, 17f).sp
        val labelSize = (screenHeight * 0.03f).coerceIn(10f, 13f).sp
        val barHeight = (screenHeight * 0.016f).coerceIn(5f, 8f).dp
        val edge = (screenHeight * 0.05f).coerceIn(16f, 28f).dp
        val closeSize = (screenHeight * 0.1f).coerceIn(36f, 44f).dp

        // Kiri atas: titik aksen + label kecil
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = edge, start = edge)
                .height(closeSize),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(accentSoft, CircleShape)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = Tr.t("Memuat game").uppercase(),
                color = Color.White.copy(alpha = 0.70f),
                fontSize = labelSize,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.6.sp,
                maxLines = 1
            )
        }

        // Tombol X: lingkaran kaca tipis berbingkai putih halus.
        if (showClose) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = edge, end = edge)
                    .size(closeSize)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = Tr.t("Batal dan keluar"),
                    tint = Color.White,
                    modifier = Modifier.size(closeSize * 0.48f)
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .widthIn(max = 560.dp)
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
            if (mcVersion.isNotBlank() || loaderLabel.isNotBlank()) {
                Row(
                    modifier = Modifier.padding(top = edge * 0.3f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (mcVersion.isNotBlank()) LoadingPill(mcVersion, logSize)
                    if (loaderLabel.isNotBlank()) LoadingPill(loaderLabel, logSize)
                }
            }
            Spacer(modifier = Modifier.height(edge * 0.6f))

            // Kartu log: baris terakhir dari keluaran game (atau keterangan awal sebelum ada log)
            val cardShape = RoundedCornerShape(12.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.06f), cardShape)
                    .border(1.dp, Color.White.copy(alpha = 0.10f), cardShape)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "›",
                    color = accentSoft,
                    fontSize = logSize,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (latestLog.isNotBlank()) latestLog else "Menyiapkan mesin Java dan grafis...",
                    color = Color.White.copy(alpha = 0.86f),
                    fontSize = logSize,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(edge * 0.5f))

            // Garis tunggu tanpa persentase (launcher tidak tahu seberapa jauh JVM memuat)
            AmberWaitBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight)
                    .clip(CircleShape),
                color = accentSoft,
                trackColor = Color.White.copy(alpha = 0.12f)
            )
        }
    }
}

/** Pil kecil berwarna aksen untuk versi Minecraft dan loader. */
@Composable
private fun LoadingPill(text: String, fontSize: TextUnit) {
    Box(
        modifier = Modifier
            .background(AmberUi.Accent.copy(alpha = 0.20f), CircleShape)
            .border(1.dp, AmberUi.AccentEdge.copy(alpha = 0.55f), CircleShape)
            .padding(horizontal = 11.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = AmberUi.AccentSoft,
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
