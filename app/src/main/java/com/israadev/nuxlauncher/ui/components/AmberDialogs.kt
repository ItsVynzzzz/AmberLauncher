package com.israadev.nuxlauncher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import com.israadev.nuxlauncher.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.ui.theme.Amber

/** Gaya tombol AmberLauncher: utama (kuning), bahaya (merah), dan kaca (hanya garis tepi). */
enum class AmberButtonStyle { Primary, Danger, Ghost }

/**
 * Tombol datar AmberLauncher. Tanpa bayangan, tanpa animasi pegas; umpan balik tekan hanya
 * lapisan putih tipis yang digambar di fase gambar (lihat [amberTap]).
 */
@Composable
fun AmberButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: AmberButtonStyle = AmberButtonStyle.Primary,
    icon: String? = null,
    enabled: Boolean = true,
    height: Dp = 44.dp
) {
    val shape = RoundedCornerShape(12.dp)
    val fill = when (style) {
        AmberButtonStyle.Primary -> Amber.Yellow
        AmberButtonStyle.Danger -> Amber.Danger
        AmberButtonStyle.Ghost -> Color.Transparent
    }
    val ink = when {
        !enabled -> Amber.TextFaint
        style == AmberButtonStyle.Ghost -> Amber.Text
        else -> Amber.Ink
    }
    val base = modifier
        .height(height)
        .clip(shape)
        .background(if (enabled) fill else Amber.Fill)
    val framed = if (style == AmberButtonStyle.Ghost || !enabled) {
        base.border(1.dp, Amber.LineStrong, shape)
    } else {
        base
    }
    val tappable = if (enabled) framed.amberTap(onClick) else framed

    Row(
        modifier = tappable.padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            AmberIcon(icon, 18.dp, ink)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            color = ink,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/**
 * Kerangka dialog kecil (konfirmasi, peringatan, proses): ikon berwarna + judul, isi, lalu baris
 * tombol (boleh kosong untuk dialog proses). Memakai NuxDialog sehingga tepi kaca dan latar redupnya sama dengan dialog lain.
 */
@Composable
fun AmberAlert(
    onDismissRequest: () -> Unit,
    title: String,
    icon: String,
    accent: Color,
    modifier: Modifier = Modifier,
    actions: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    NuxDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.widthIn(max = 460.dp),
        compact = true
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    AmberIcon(icon, 22.dp, accent)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    color = Amber.Text,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
            if (actions != null) {
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    content = actions
                )
            }
        }
    }
}

/** Kotak info kecil di dalam dialog (nama instance, dsb.): isian kaca + tepi tipis. */
@Composable
fun AmberInfoBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Amber.Fill)
            .border(1.dp, Amber.Line, shape)
            .padding(12.dp),
        content = content
    )
}
