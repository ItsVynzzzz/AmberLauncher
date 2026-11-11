package com.israadev.nuxlauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.israadev.nuxlauncher.ui.i18n.Text
import com.israadev.nuxlauncher.ui.screens.AmberRoundIcon
import com.israadev.nuxlauncher.ui.theme.AmberUi
import com.israadev.nuxlauncher.ui.theme.LocalGlassBackdrop
import com.israadev.nuxlauncher.ui.theme.LocalNuxScaled
import com.israadev.nuxlauncher.ui.theme.NuxReadable
import com.israadev.nuxlauncher.ui.theme.amberGlass

// =============================================================================
// HALAMAN KACA untuk dialog: bahasa tampilan yang sama dengan layar Mod/Akun/Pengaturan
// (pil kaca, bar atas melayang, kartu bersudut besar) supaya dialog terasa bagian dari launcher,
// bukan kotak asing di tengah layar.
// =============================================================================

/**
 * Ukuran ruang yang tersedia untuk isi halaman.
 * - [narrow]: lebar < 560dp (HP portrait): susun satu kolom.
 * - [short]: tinggi < 300dp (HP landscape kecil): rapatkan jarak.
 */
@Immutable
class PageInfo(val narrow: Boolean, val short: Boolean)

/**
 * Jendela dialog layar penuh dasar untuk semua dialog gaya baru.
 *
 * - Bar status dan navigasi Android disembunyikan (lihat [ImmersiveWindowHook]); isi tidak
 *   mengalah ke bar tersebut. Yang dihindari hanya poni layar, keyboard, dan bar navigasi/taskbar
 *   di bawah bila masih tampil (supaya isi paling bawah tidak tertutup di tablet).
 * - Ukuran huruf dan jarak sama dengan layar lain (0.9x), jadi tidak terasa raksasa.
 * - Kaca selalu gaya Default di sini; kaca buram Liquid Glass tidak bisa dipakai di jendela terpisah.
 */
@Composable
fun AmberOverlay(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissOnScrimTap: Boolean = false,
    scrimAlpha: Float = 0.82f,
    content: @Composable BoxScope.() -> Unit
) {
    val inScaledScreen = LocalNuxScaled.current
    val dismiss by rememberUpdatedState(onDismiss)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        GamepadWindowHook()
        NuxReadable(scale = if (inScaledScreen) 1f else 0.9f, boost = 1f) {
            CompositionLocalProvider(LocalGlassBackdrop provides null) {
                Box(
                    modifier = modifier
                        .fillMaxSize()
                        .background(Color(0xFF121115).copy(alpha = scrimAlpha))
                        .then(
                            if (dismissOnScrimTap) {
                                Modifier.pointerInput(Unit) { detectTapGestures { dismiss() } }
                            } else {
                                Modifier
                            }
                        )
                        .windowInsetsPadding(
                            WindowInsets.displayCutout
                                .union(WindowInsets.ime)
                                .union(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                        ),
                    content = content
                )
            }
        }
    }
}

/**
 * Halaman dialog: bar atas melayang (panah kembali + judul dalam pil kaca, keterangan, tombol aksi di
 * kanan), lalu isi di bawahnya. Tidak ada tombol di dasar layar, jadi tidak ada yang bisa tertutup bar
 * sistem di tablet; aksi utama (Simpan) ada di bar atas.
 *
 * Panah kembali menutup halaman tanpa menyimpan. Isi mengatur gulirannya sendiri.
 */
@Composable
fun AmberPage(
    onDismiss: () -> Unit,
    title: String,
    icon: ImageVector? = null,
    subtitle: String? = null,
    dismissOnScrimTap: Boolean = false,
    scrimAlpha: Float = 0.82f,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable BoxScope.(PageInfo) -> Unit
) {
    AmberOverlay(
        onDismiss = onDismiss,
        dismissOnScrimTap = dismissOnScrimTap,
        scrimAlpha = scrimAlpha
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val info = PageInfo(narrow = maxWidth < 560.dp, short = maxHeight < 300.dp)
            Column(modifier = Modifier.fillMaxSize()) {
                AmberPageBar(
                    title = title,
                    icon = icon,
                    subtitle = if (info.narrow) null else subtitle,
                    onDismiss = onDismiss,
                    actions = actions
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    content(info)
                }
            }
        }
    }
}

@Composable
private fun AmberPageBar(
    title: String,
    icon: ImageVector?,
    subtitle: String?,
    onDismiss: () -> Unit,
    actions: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pil kiri: panah kembali + judul, bentuknya sama dengan pil navigasi di layar Mod/Akun
        Row(
            modifier = Modifier
                .height(48.dp)
                .clip(CircleShape)
                .amberGlass(CircleShape)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AmberRoundIcon(
                icon = Icons.AutoMirrored.Outlined.ArrowBack,
                description = "Tutup",
                onClick = onDismiss
            )
            Row(
                modifier = Modifier
                    .height(40.dp)
                    .clip(CircleShape)
                    .background(AmberUi.SurfaceRaised, CircleShape)
                    .border(1.dp, AmberUi.BorderStrong, CircleShape)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = AmberUi.Text,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = title,
                    color = AmberUi.Text,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = AmberUi.TextDim,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions
        )
    }
}

/** Judul kecil huruf renggang di atas satu bagian isi (NAMA, JAVA, dll). */
@Composable
fun AmberPageLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = AmberUi.TextDim,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp,
        maxLines = 1,
        modifier = modifier.padding(start = 4.dp, bottom = 6.dp)
    )
}

/** Kolom isian berbentuk pil kaca, sama dengan kolom cari di layar Mod. */
@Composable
fun AmberField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(CircleShape)
            .amberGlass(CircleShape)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AmberUi.Text,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterStart
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = AmberUi.TextDim,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(
                    color = AmberUi.Text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                ),
                cursorBrush = SolidColor(AmberUi.AccentSoft),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** Pil pilihan: terpilih = isian aksen dengan tepi terang, selain itu kaca. */
@Composable
fun AmberChoicePill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(CircleShape)
            .then(
                if (selected) {
                    Modifier
                        .background(AmberUi.Accent, CircleShape)
                        .border(1.dp, AmberUi.AccentEdge, CircleShape)
                } else {
                    Modifier.amberGlass(CircleShape)
                }
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = AmberUi.Text,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 13.sp,
            maxLines = 1
        )
    }
}

/**
 * Baris aksi berbentuk pil: ikon dalam lingkaran, nama, keterangan kecil, dan panah bila membuka
 * halaman lain. [danger] = bingkai dan teks merah (hapus).
 */
@Composable
fun AmberActionPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    showChevron: Boolean = false,
    danger: Boolean = false
) {
    val tone = if (danger) AmberUi.Danger else AmberUi.AccentSoft
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(CircleShape)
            .then(
                if (danger) {
                    Modifier
                        .background(AmberUi.Danger.copy(alpha = 0.08f), CircleShape)
                        .border(1.dp, AmberUi.Danger.copy(alpha = 0.55f), CircleShape)
                } else {
                    Modifier.amberGlass(CircleShape)
                }
            )
            .clickable { onClick() }
            .padding(start = 10.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(tone.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tone,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            color = if (danger) AmberUi.Danger else AmberUi.Text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(
                text = trailing,
                color = AmberUi.TextDim,
                fontSize = 12.sp,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        if (showChevron) {
            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = AmberUi.TextDim,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
