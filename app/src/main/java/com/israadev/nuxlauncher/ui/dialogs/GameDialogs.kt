package com.israadev.nuxlauncher.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.israadev.nuxlauncher.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.israadev.nuxlauncher.ui.components.AmberIcon
import com.israadev.nuxlauncher.ui.components.FpsReadout
import com.israadev.nuxlauncher.ui.components.GamepadWindowHook
import com.israadev.nuxlauncher.ui.components.amberTap
import com.israadev.nuxlauncher.ui.theme.Amber

/** Konfirmasi keluar dari game: kartu kecil, dua tombol pil. */
@Composable
fun ExitConfirmDialog(
    onCancel: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        GamepadWindowHook()
        val shape = RoundedCornerShape(22.dp)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(330.dp)
                    .clip(shape)
                    .background(Amber.Panel)
                    .border(1.dp, Amber.Line, shape)
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                Text(
                    text = "Keluar dari game?",
                    color = Amber.Text,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Minecraft akan dihentikan dan kamu kembali ke launcher. Dunia yang belum tersimpan bisa hilang.",
                    color = Amber.TextDim,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PillButton(label = "Batal", filled = false, onClick = onCancel)
                    PillButton(label = "Keluar", filled = true, onClick = onConfirm)
                }
            }
        }
    }
}

@Composable
private fun PillButton(label: String, filled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .height(40.dp)
            .clip(shape)
            .background(if (filled) Amber.Danger else Amber.Fill)
            .border(1.dp, if (filled) Amber.DangerSoft else Amber.LineStrong, shape)
            .amberTap(onClick)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (filled) Amber.Ink else Amber.Text,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/**
 * Panel log mesin yang melayang di kiri atas. Daftar log dibaca di sini saja, jadi baris log baru
 * hanya menyusun ulang panel ini, bukan seluruh layar game.
 */
@Composable
fun GameConsolePanel(
    modifier: Modifier,
    fps: () -> Int,
    logs: List<String>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onFpsClick: () -> Unit,
    onClose: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .width(if (expanded) 420.dp else 300.dp)
            .clip(shape)
            .background(Amber.Glass)
            .border(1.dp, Amber.Line, shape)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .amberTap(onFpsClick)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                FpsReadout(fps)
            }
            Box(modifier = Modifier.weight(1f))
            PanelIconButton(if (expanded) "chev_up" else "chev_down", onToggleExpanded)
            PanelIconButton("close", onClose)
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (expanded) {
            val listState = rememberLazyListState()
            // Gulir otomatis ke baris terbaru (hanya aktif saat log lengkap tampil)
            LaunchedEffect(logs.size, logs.lastOrNull()) {
                if (logs.isNotEmpty()) listState.scrollToItem(logs.size - 1)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                items(logs) { line -> LogLine(line, maxLines = 4) }
            }
        } else {
            logs.takeLast(4).forEach { line -> LogLine(line, maxLines = 1) }
        }
    }
}

@Composable
private fun PanelIconButton(icon: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .amberTap(onClick),
        contentAlignment = Alignment.Center
    ) {
        AmberIcon(icon, 16.dp, Amber.TextDim)
    }
}

@Composable
private fun LogLine(text: String, maxLines: Int) {
    val color = when {
        text.contains("error", ignoreCase = true) ||
            text.contains("exception", ignoreCase = true) ||
            text.contains("fatal", ignoreCase = true) -> Amber.DangerSoft
        text.startsWith("[Amber") -> Amber.YellowSoft
        else -> Color(0xFFD8D6CC)
    }
    Text(
        text = text,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis
    )
}
