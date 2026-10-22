package com.israadev.nuxlauncher.ui.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.israadev.nuxlauncher.ui.i18n.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import com.israadev.nuxlauncher.ui.i18n.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.core.crash.CrashUtils
import com.israadev.nuxlauncher.core.crash.GameCrashInfo
import com.israadev.nuxlauncher.core.crash.MCLogsUploader
import com.israadev.nuxlauncher.ui.components.*
import com.israadev.nuxlauncher.ui.theme.NuxColors
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun NuxCrashDialog(
    crashInfo: GameCrashInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isUploading by remember { mutableStateOf(false) }
    var uploadSuccessUrl by remember { mutableStateOf<String?>(null) }

    var isCursorBlinkVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(500)
            isCursorBlinkVisible = !isCursorBlinkVisible
        }
    }

    val logFile = remember(crashInfo.fullLogPath) {
        if (crashInfo.fullLogPath.isNotBlank()) File(crashInfo.fullLogPath)
        else File(context.filesDir, "latestlog.txt")
    }

    val exitDetailMessage = remember(crashInfo) {
        if (crashInfo.isLauncherCrash) {
            "Peluncur mengalami kesalahan internal pada komponen UI atau proses background sistem."
        } else {
            CrashUtils.getExitMessage(crashInfo.exitCode, crashInfo.isSignal)
        }
    }

    val statusBadge = remember(crashInfo) {
        crashInfo.getStatusBadgeText()
    }

    val mainMessage = remember(crashInfo) {
        crashInfo.getMainMessage()
    }

    val outerShape = RoundedCornerShape(20.dp)
    val cardShape = RoundedCornerShape(14.dp)
    val innerShape = RoundedCornerShape(10.dp)

    NuxDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.96f),
        fillMaxHeight = true
    ) {
        // Outer Shell Container with subtle Crimson/Rose ambient glow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            NuxColors.SurfaceInput,
                            NuxColors.Background
                        )
                    )
                )
                .padding(14.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(NuxColors.ErrorRed, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "INCIDENT DIAGNOSTICS · JVM TERMINATION",
                                color = NuxColors.ErrorRed.copy(alpha = 0.85f),
                                fontWeight = FontWeight.Bold,
                                fontSize = 8.5.sp,
                                letterSpacing = 1.2.sp
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = crashInfo.getDisplayTitle(),
                                    color = NuxColors.DarkGray,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp,
                                    letterSpacing = (-0.3).sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(NuxColors.ErrorRed.copy(alpha = 0.2f))
                                        .border(1.dp, NuxColors.ErrorRed.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = statusBadge,
                                        color = NuxColors.ErrorRed,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }
                    }

                    // Close Button
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(NuxColors.ErrorRed.copy(alpha = 0.12f))
                            .border(1.dp, NuxColors.DarkGray.copy(alpha = 0.40f), CircleShape)
                            .clickable { onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = NuxColors.ErrorRed,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Two-Column Content
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // ==========================================
                    // LEFT COLUMN: Details, Diagnosis & Actions
                    // ==========================================
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(cardShape)
                            .background(NuxColors.SurfaceInput)
                            .border(1.dp, NuxColors.ErrorRed.copy(alpha = 0.15f), cardShape)
                            .padding(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // 1. Session Information Card
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(innerShape)
                                        .background(NuxColors.ErrorRed.copy(alpha = 0.12f))
                                        .border(1.dp, NuxColors.DarkGray.copy(alpha = 0.20f), innerShape)
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = if (crashInfo.isLauncherCrash) "STATUS PELUNCUR" else "INFORMASI SESI GAME",
                                        color = NuxColors.GrayNeutral,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 8.5.sp,
                                        letterSpacing = 0.8.sp
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = if (crashInfo.isLauncherCrash) "AmberLauncher Engine" else "${crashInfo.instanceName} (MC ${crashInfo.mcVersion})",
                                        color = NuxColors.DarkGray,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 12.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(NuxColors.ErrorRed.copy(alpha = 0.12f))
                                                .padding(horizontal = 5.dp, vertical = 1.5.dp)
                                        ) {
                                            Text(
                                                text = "${crashInfo.loader.uppercase()} ${crashInfo.loaderVersion?.let { "v$it" } ?: ""}".trim(),
                                                color = NuxColors.SkyBlueDark,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 8.sp
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(NuxColors.ErrorRed.copy(alpha = 0.12f))
                                                .padding(horizontal = 5.dp, vertical = 1.5.dp)
                                        ) {
                                            Text(
                                                text = "${crashInfo.installedMods.size} Mod",
                                                color = NuxColors.GrayNeutral,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 8.sp
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = mainMessage,
                                        color = NuxColors.ErrorRed,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp
                                    )
                                    if (crashInfo.fullLogPath.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(NuxColors.Background)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = "Log: ${File(crashInfo.fullLogPath).name}",
                                                color = NuxColors.GrayNeutral,
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }

                                // 2. Root Cause & Process Termination Status Card
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(innerShape)
                                        .background(NuxColors.ErrorRed.copy(alpha = 0.12f))
                                        .border(1.dp, NuxColors.ErrorRed.copy(alpha = 0.45f), innerShape)
                                        .padding(10.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("⚠️", fontSize = 11.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "DIAGNOSA KELUAR SISTEM",
                                            color = NuxColors.ErrorRed,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 9.sp,
                                            letterSpacing = 0.8.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = exitDetailMessage,
                                        color = NuxColors.GrayNeutral,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                // 3. Analysis Note Card
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(innerShape)
                                        .background(NuxColors.SurfaceInput)
                                        .border(1.dp, NuxColors.DarkGray.copy(alpha = 0.20f), innerShape)
                                        .padding(9.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("💡", fontSize = 11.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "SOLUSI & SARAN PEMULIHAN",
                                            color = NuxColors.GrayNeutral,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 8.5.sp,
                                            letterSpacing = 0.8.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = CrashUtils.CRASH_LOG_NOTE,
                                        color = NuxColors.GrayNeutral,
                                        fontSize = 9.sp,
                                        lineHeight = 13.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Action Buttons
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                // Primary Action: Upload to mclo.gs
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (uploadSuccessUrl != null) Brush.horizontalGradient(
                                                listOf(NuxColors.ForestGreen, NuxColors.ForestGreen)
                                            ) else Brush.horizontalGradient(
                                                listOf(NuxColors.ErrorRed, NuxColors.ErrorRed)
                                            )
                                        )
                                        .border(
                                            1.dp,
                                            if (uploadSuccessUrl != null) NuxColors.Yellow else NuxColors.ErrorRed.copy(alpha = 0.40f),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            if (isUploading) return@clickable
                                            if (uploadSuccessUrl != null) {
                                                try {
                                                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uploadSuccessUrl)).apply {
                                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                    }
                                                    context.startActivity(browserIntent)
                                                } catch (_: Exception) {}
                                                return@clickable
                                            }

                                            isUploading = true
                                            coroutineScope.launch {
                                                val result = if (logFile.exists()) {
                                                    MCLogsUploader.uploadLogFile(logFile)
                                                } else {
                                                    MCLogsUploader.uploadLog(crashInfo.logSnippet)
                                                }
                                                isUploading = false
                                                result.fold(
                                                    onSuccess = { url ->
                                                        uploadSuccessUrl = url
                                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                        clipboard.setPrimaryClip(ClipData.newPlainText("mclo.gs link", url))
                                                        Toast.makeText(context, "Log berhasil diunggah & tautan disalin!", Toast.LENGTH_LONG).show()
                                                        try {
                                                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                            }
                                                            context.startActivity(browserIntent)
                                                        } catch (_: Exception) {}
                                                    },
                                                    onFailure = { err ->
                                                        Toast.makeText(context, "Gagal mengunggah: ${err.message}", Toast.LENGTH_LONG).show()
                                                    }
                                                )
                                            }
                                        }
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        if (isUploading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(13.dp),
                                                color = NuxColors.DarkGray,
                                                strokeWidth = 1.5.dp
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "MENGUNGGAH LOG...",
                                                color = NuxColors.DarkGray,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 11.sp
                                            )
                                        } else if (uploadSuccessUrl != null) {
                                            Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, tint = NuxColors.Ink, modifier = Modifier.size(13.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "BUKA TAUTAN MCLO.GS",
                                                color = NuxColors.Ink,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 11.sp
                                            )
                                        } else {
                                            Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, tint = NuxColors.DarkGray, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "UNGGAH TAUTAN LOG (MCLO.GS)",
                                                color = NuxColors.DarkGray,
                                                fontWeight = FontWeight.Black,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }

                                // Secondary Actions Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // Bagikan
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(34.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(NuxColors.ErrorRed.copy(alpha = 0.12f))
                                            .border(1.dp, NuxColors.DarkGray.copy(alpha = 0.36f), RoundedCornerShape(8.dp))
                                            .clickable { CrashUtils.shareLogFile(context, logFile) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = NuxColors.GrayNeutral, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "BAGIKAN",
                                                color = NuxColors.DarkGray,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }

                                    // Salin
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(34.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(NuxColors.ErrorRed.copy(alpha = 0.12f))
                                            .border(1.dp, NuxColors.DarkGray.copy(alpha = 0.36f), RoundedCornerShape(8.dp))
                                            .clickable {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(ClipData.newPlainText("Crash Log", crashInfo.logSnippet))
                                                Toast.makeText(context, "Log disalin ke clipboard!", Toast.LENGTH_SHORT).show()
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, tint = NuxColors.GrayNeutral, modifier = Modifier.size(12.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "SALIN",
                                                color = NuxColors.DarkGray,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }

                                    // Tutup
                                    Box(
                                        modifier = Modifier
                                            .weight(0.9f)
                                            .height(34.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(NuxColors.SurfaceElevated)
                                            .border(1.dp, NuxColors.DarkGray.copy(alpha = 0.40f), RoundedCornerShape(8.dp))
                                            .clickable { onDismiss() },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "TUTUP",
                                            color = NuxColors.DarkGray,
                                            fontWeight = FontWeight.Black,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ==========================================
                    // RIGHT COLUMN: Log crash mentah
                    // ==========================================
                    Box(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight()
                            .clip(cardShape)
                            .background(NuxColors.Background)
                            .border(2.dp, NuxColors.CardBorder, cardShape)
                            .padding(8.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Header: judul + tombol salin
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 3.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "LOG CRASH",
                                    color = NuxColors.DarkGray,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 9.sp,
                                    letterSpacing = 0.8.sp
                                )
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(NuxColors.SurfaceElevated)
                                        .clickable {
                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            cm.setPrimaryClip(ClipData.newPlainText("Crash Log", crashInfo.logSnippet))
                                            Toast.makeText(context, "Disalin ke papan klip!", Toast.LENGTH_SHORT).show()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Salin",
                                        tint = NuxColors.GrayNeutral,
                                        modifier = Modifier.size(11.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // Content Box
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .clip(innerShape)
                                    .background(NuxColors.Background)
                                    .border(1.dp, NuxColors.DarkGray.copy(alpha = 0.20f), innerShape)
                                    .padding(8.dp)
                            ) {
                                run {
                                    // View Raw Terminal Log
                                    val verticalScroll = rememberScrollState()
                                    val horizontalScroll = rememberScrollState()
                                    Text(
                                        text = crashInfo.logSnippet,
                                        color = NuxColors.GrayNeutral,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 9.5.sp,
                                        lineHeight = 13.5.sp,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(verticalScroll)
                                            .horizontalScroll(horizontalScroll)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
