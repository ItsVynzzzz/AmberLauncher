package com.israadev.nuxlauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.israadev.nuxlauncher.ui.i18n.Text
import com.israadev.nuxlauncher.ui.theme.LocalNuxScaled
import com.israadev.nuxlauncher.ui.theme.NuxColors
import com.israadev.nuxlauncher.ui.theme.NuxSizes
import kotlin.math.max
import kotlin.math.min

/**
 * Keterangan ukuran panel untuk isi [AmberSheet], supaya isi bisa menyusun ulang dirinya.
 *
 * - [twoPane]: panel lebar tetapi pendek (HP landscape). Isi boleh dibagi dua kolom supaya tidak
 *   perlu digulir panjang.
 * - [narrow]: panel sempit (< 380dp). Buang ornamen dan tombol sekunder.
 */
@Immutable
class SheetInfo(val twoPane: Boolean, val narrow: Boolean)

/**
 * Kerangka dialog adaptif untuk semua dialog gaya baru.
 *
 * Masalah yang diselesaikan (dialog lama memakai NuxDialog):
 * 1. Tombol bawah tertutup bar sistem / taskbar tablet. Jendela dialog tidak ikut mode imersif
 *    Activity, jadi bar sistem muncul di atas isi. Di sini seluruh isi diberi padding
 *    WindowInsets.safeDrawing (bar sistem + poni layar + keyboard).
 * 2. Isi terpotong di HP kecil. Tinggi panel dibatasi sisa tinggi layar, judul dan tombol bawah
 *    SELALU terlihat, hanya bagian tengah yang bisa digulir (ada pudar di bawahnya sebagai
 *    petunjuk masih ada isi).
 * 3. Terlalu besar di HP kecil. Kerapatan (dp dan sp) dihitung dari tinggi layar yang benar-benar
 *    tersedia, bukan angka tetap: layar pendek otomatis lebih rapat, tablet tidak membengkak.
 *    Kerapatan dasar diambil dari sistem, jadi hasilnya sama di mana pun dialog dipanggil.
 *
 * Susunan: kepala (ikon, judul, tutup) -> badan yang bisa digulir -> kaki (tombol). Kaki opsional.
 *
 * @param panelWidth lebar maksimum panel. Pakai nilai besar (mis. 640dp) bila isi punya mode dua kolom.
 * @param dismissOnScrimTap ketuk area gelap di luar panel menutup dialog. Matikan bila ada isian
 *   yang belum disimpan supaya tidak hilang karena salah ketuk.
 */
@Composable
fun AmberSheet(
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    panelWidth: Dp = 460.dp,
    dismissOnScrimTap: Boolean = false,
    footer: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.(SheetInfo) -> Unit
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val systemDensity = remember(configuration) {
        Density(
            density = context.resources.displayMetrics.density,
            fontScale = context.resources.configuration.fontScale
        )
    }
    val dismiss by rememberUpdatedState(onDismiss)
    val imeOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val edge = remember {
        Brush.linearGradient(
            listOf(
                Color.White.copy(alpha = 0.42f),
                Color.White.copy(alpha = 0.08f),
                Color.White.copy(alpha = 0.22f)
            )
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        GamepadWindowHook()

        // Latar redup memenuhi jendela; isi di dalamnya dijauhkan dari bar sistem dan keyboard.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.62f))
                .then(
                    if (dismissOnScrimTap) {
                        Modifier.pointerInput(Unit) { detectTapGestures { dismiss() } }
                    } else {
                        Modifier
                    }
                )
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                // Tinggi yang benar-benar tersedia dalam dp fisik (bukan dp yang sudah diskalakan).
                val availDp = constraints.maxHeight / systemDensity.density
                val fit = min(max(availDp / 400f, 0.82f), 1.08f)
                val fontScale = min(max(systemDensity.fontScale, 0.85f), 1.15f)
                val sheetDensity = Density(
                    density = systemDensity.density * fit,
                    fontScale = fontScale
                )

                CompositionLocalProvider(
                    LocalDensity provides sheetDensity,
                    LocalContentColor provides NuxColors.DarkGray,
                    LocalNuxScaled provides true
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val panelW = if (this.maxWidth < panelWidth) this.maxWidth else panelWidth
                        val info = SheetInfo(
                            twoPane = panelW >= 540.dp && availDp < 380f,
                            narrow = panelW < 380.dp
                        )
                        SheetPanel(
                            modifier = modifier.widthIn(max = panelW).fillMaxWidth(),
                            title = title,
                            subtitle = if (imeOpen) null else subtitle,
                            icon = if (imeOpen) null else icon,
                            edge = edge,
                            onDismiss = onDismiss,
                            footer = footer,
                            info = info,
                            content = content
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetPanel(
    modifier: Modifier,
    title: String,
    subtitle: String?,
    icon: ImageVector?,
    edge: Brush,
    onDismiss: () -> Unit,
    footer: (@Composable RowScope.() -> Unit)?,
    info: SheetInfo,
    content: @Composable ColumnScope.(SheetInfo) -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    val scroll = rememberScrollState()
    val moreBelow by remember { derivedStateOf { scroll.canScrollForward } }

    Column(
        modifier = modifier
            .clip(shape)
            .background(NuxColors.Panel, shape)
            .border(NuxSizes.GlassBorder, edge, shape)
            // Menelan ketukan di dalam panel supaya tidak sampai ke latar (penutup dialog)
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        // Kepala
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(NuxColors.YellowPale),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = NuxColors.Yellow,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = NuxColors.DarkGray,
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    letterSpacing = (-0.1).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = NuxColors.GrayNeutral,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            NuxIconButton(
                icon = Icons.Outlined.Close,
                description = "Tutup",
                onClick = onDismiss
            )
        }
        SheetDivider()

        // Badan: satu-satunya bagian yang menyusut dan bisa digulir
        Box(modifier = Modifier.weight(1f, fill = false)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                content(info)
            }
            if (moreBelow) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(18.dp)
                        .background(
                            Brush.verticalGradient(listOf(Color.Transparent, NuxColors.Panel))
                        )
                )
            }
        }

        // Kaki: tombol selalu terlihat
        if (footer != null) {
            SheetDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = footer
            )
        }
    }
}

/** Garis pemisah tipis selebar penuh. */
@Composable
fun SheetDivider(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(NuxColors.LightGray)
    )
}

/** Judul kecil di atas satu bagian isi (Nama, Java, dll). */
@Composable
fun SheetLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = NuxColors.GrayNeutral,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 0.4.sp,
        modifier = modifier.padding(bottom = 6.dp)
    )
}

/**
 * Tombol standar dialog: tinggi dan sudut seragam.
 * [primary] = aksen penuh (Simpan/Tambah), [danger] = merah (Hapus), selain itu tombol berbingkai.
 * Warna teks dipilih NuxButton sendiri supaya tetap terbaca di semua warna aksen.
 */
@Composable
fun SheetButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    danger: Boolean = false,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    NuxButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        backgroundColor = when {
            danger -> NuxColors.ErrorRed
            primary -> NuxColors.Yellow
            else -> Color.Transparent
        },
        cornerRadius = 14.dp,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = text,
            fontWeight = if (primary || danger) FontWeight.Black else FontWeight.Bold,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Wadah berbingkai untuk sekumpulan baris ([SheetActionRow]) yang dipisah [SheetDivider]. */
@Composable
fun SheetGroup(
    modifier: Modifier = Modifier,
    borderColor: Color = NuxColors.CardBorder,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(NuxColors.SurfaceWhite, shape)
            .border(NuxSizes.GlassBorder, borderColor, shape),
        content = content
    )
}

/** Satu baris aksi: ikon dalam kotak berwarna, nama, keterangan kecil, dan panah bila membuka halaman lain. */
@Composable
fun SheetActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    showChevron: Boolean = false,
    danger: Boolean = false
) {
    val tone = if (danger) NuxColors.ErrorRed else NuxColors.Yellow
    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 46.dp)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(tone.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tone,
                modifier = Modifier.size(17.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            color = if (danger) NuxColors.ErrorRed else NuxColors.DarkGray,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(
                text = trailing,
                color = NuxColors.GrayNeutral,
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        if (showChevron) {
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = NuxColors.GrayNeutral,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
