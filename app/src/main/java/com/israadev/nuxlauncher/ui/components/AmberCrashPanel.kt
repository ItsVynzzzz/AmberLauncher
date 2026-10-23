package com.israadev.nuxlauncher.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.core.crash.CrashAdvisor
import com.israadev.nuxlauncher.core.crash.CrashSource
import com.israadev.nuxlauncher.core.crash.CrashUtils
import com.israadev.nuxlauncher.core.crash.GameCrashInfo
import com.israadev.nuxlauncher.core.crash.MCLogsUploader
import com.israadev.nuxlauncher.ui.i18n.Text
import com.israadev.nuxlauncher.ui.i18n.Toast
import com.israadev.nuxlauncher.ui.i18n.Tr
import com.israadev.nuxlauncher.ui.theme.Amber
import kotlinx.coroutines.launch
import java.io.File

private const val INTRO_GAME =
    "Crash tidak selalu berarti launcher-nya rusak. Mod, RAM HP, dan driver grafis jauh lebih sering " +
        "menjadi penyebabnya, jadi cari tahu dulu sebelum melapor ke pengembang."

private const val INTRO_LAUNCHER =
    "Launcher berhenti mendadak karena kesalahan tak terduga. Kirim log-nya ke pengembang supaya bisa diperbaiki."

private const val AI_BODY =
    "Salin log beserta pertanyaannya, lalu tempel di chat AI. Biasanya AI bisa menunjukkan penyebab dan " +
        "cara memperbaikinya. Kirim teks log, bukan screenshot."

private const val REPORT_BODY_GAME =
    "Kalau AI menyimpulkan masalahnya ada di launcher, baru laporkan di GitHub. Log disalin otomatis, " +
        "tinggal tempel di laporan."

private const val REPORT_BODY_LAUNCHER =
    "Laporkan di GitHub supaya bisa diperbaiki. Log disalin otomatis, tinggal tempel di laporan."

private const val MAX_SHOWN_LOG_CHARS = 40_000

/**
 * Panel crash AmberLauncher. Menjelaskan dari mana crash berasal (belum tentu dari launcher), lalu
 * mengajak pemain mencari solusinya dulu dengan menyalin log ke AI (Claude, ChatGPT, Gemini)
 * sebelum melapor ke pengembang. Dipakai oleh dialog crash di Beranda dan halaman Error mandiri.
 *
 * Tampilan statis dan hanya dibuka setelah game berhenti, jadi tidak membebani performa game.
 */
@Composable
fun AmberCrashPanel(
    info: GameCrashInfo,
    logText: String,
    logFile: File,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onBackToLauncher: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcherCrash = info.isLauncherCrash
    val source = remember(info) { CrashAdvisor.sourceOf(info) }
    val log = remember(logText) {
        if (logText.isBlank()) Tr.t("Tidak ada rincian log yang tersedia.") else logText
    }
    var uploading by remember { mutableStateOf(false) }
    var uploadUrl by remember { mutableStateOf<String?>(null) }

    val copyForAi: () -> Unit = {
        copyToClipboard(context, "Crash Log", CrashAdvisor.buildAiPrompt(context, info, log))
        Toast.makeText(context, "Log untuk AI disalin. Tempel di kolom chat AI.", Toast.LENGTH_LONG).show()
    }
    val openAi: (String) -> Unit = { url ->
        copyForAi()
        openUrl(context, url)
    }
    val openReport: () -> Unit = {
        copyToClipboard(context, "Crash Log", CrashAdvisor.logForSharing(log))
        Toast.makeText(context, "Log disalin. Tempel di laporan.", Toast.LENGTH_LONG).show()
        openUrl(context, CrashAdvisor.reportUrl)
    }
    val shareFile: () -> Unit = { CrashUtils.shareLogFile(context, logFile) }
    val upload: () -> Unit = {
        val readyUrl = uploadUrl
        if (readyUrl != null) {
            openUrl(context, readyUrl)
        } else if (!uploading) {
            uploading = true
            scope.launch {
                val result = if (logFile.exists()) {
                    MCLogsUploader.uploadLogFile(logFile)
                } else {
                    MCLogsUploader.uploadLog(log)
                }
                uploading = false
                result.fold(
                    onSuccess = { link ->
                        uploadUrl = link
                        copyToClipboard(context, "mclo.gs link", link)
                        Toast.makeText(context, "Tautan berhasil diunggah & disalin!", Toast.LENGTH_LONG).show()
                        openUrl(context, link)
                    },
                    onFailure = { err ->
                        Toast.makeText(context, "Gagal mengunggah: ${err.message}", Toast.LENGTH_LONG).show()
                    }
                )
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        CrashHeader(
            title = if (launcherCrash) "AmberLauncher berhenti" else "Minecraft berhenti mendadak",
            subtitle = if (launcherCrash) {
                "Aplikasi tertutup karena kesalahan tak terduga"
            } else {
                "${info.instanceName} - Minecraft ${info.mcVersion}"
            },
            onBackToLauncher = onBackToLauncher,
            onClose = onClose
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CrashExplainPane(
                info = info,
                source = source,
                logText = log,
                logFile = logFile,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(Amber.Line)
            )
            CrashHelpPane(
                launcherCrash = launcherCrash,
                uploading = uploading,
                uploaded = uploadUrl != null,
                onCopyForAi = copyForAi,
                onOpenAi = openAi,
                onReport = openReport,
                onShareFile = shareFile,
                onUpload = upload,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
private fun CrashHeader(
    title: String,
    subtitle: String,
    onBackToLauncher: (() -> Unit)?,
    onClose: () -> Unit
) {
    val corner = RoundedCornerShape(12.dp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(corner)
                .background(Amber.DangerTint),
            contentAlignment = Alignment.Center
        ) {
            AmberIcon("warning", 22.dp, Amber.Danger)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Amber.Text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = Amber.TextDim,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (onBackToLauncher != null) {
            Spacer(modifier = Modifier.width(10.dp))
            AmberButton(
                text = "Kembali ke launcher",
                onClick = onBackToLauncher,
                style = AmberButtonStyle.Ghost,
                height = 38.dp
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(corner)
                .background(Amber.Fill)
                .border(1.dp, Amber.Line, corner)
                .amberTap(onClose),
            contentAlignment = Alignment.Center
        ) {
            AmberIcon("close", 20.dp, Amber.Text)
        }
    }
}

/** Kolom kiri: penjelasan crash (tab Penjelasan) atau isi log (tab Log). */
@Composable
private fun CrashExplainPane(
    info: GameCrashInfo,
    source: CrashSource,
    logText: String,
    logFile: File,
    modifier: Modifier
) {
    var tab by remember { mutableStateOf(0) }
    Column(modifier = modifier) {
        AmberSegmented(
            options = listOf("Penjelasan", "Log"),
            selected = tab,
            onSelect = { tab = it }
        )
        Spacer(modifier = Modifier.height(10.dp))
        if (tab == 0) {
            CrashExplainContent(info, source, Modifier.weight(1f))
        } else {
            CrashLogContent(logText, logFile, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CrashExplainContent(info: GameCrashInfo, source: CrashSource, modifier: Modifier) {
    val launcherCrash = info.isLauncherCrash
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = if (launcherCrash) INTRO_LAUNCHER else INTRO_GAME,
            color = Amber.Text,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
        AmberInfoBox {
            CrashCaption("Dugaan penyebab")
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = sourceLabel(source),
                color = if (source == CrashSource.LAUNCHER) Amber.Danger else Amber.Yellow,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = sourceDetail(source),
                color = Amber.TextDim,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        }
        AmberInfoBox {
            CrashCaption("Detail teknis")
            Spacer(modifier = Modifier.height(6.dp))
            if (launcherCrash) {
                Text(
                    text = "AmberLauncher",
                    color = Amber.Text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Kesalahan tak terduga di dalam aplikasi",
                    color = Amber.TextDim,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            } else {
                Text(
                    text = "${info.instanceName} - Minecraft ${info.mcVersion}",
                    color = Amber.Text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                val hasLoader = !info.loader.equals("vanilla", ignoreCase = true)
                if (hasLoader || info.installedMods.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (hasLoader) {
                            val loaderName = info.loader.replaceFirstChar { it.uppercase() }
                            Text(
                                text = "$loaderName ${info.loaderVersion ?: ""}".trim(),
                                color = Amber.TextDim,
                                fontSize = 13.sp
                            )
                        }
                        if (info.installedMods.isNotEmpty()) {
                            Text(
                                text = "${info.installedMods.size} Mod",
                                color = Amber.TextDim,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Status keluar: ${CrashAdvisor.describeExit(info)}",
                    color = Amber.TextDim,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
private fun CrashLogContent(logText: String, logFile: File, modifier: Modifier) {
    val shape = RoundedCornerShape(12.dp)
    val shown = remember(logText) {
        if (logText.length > MAX_SHOWN_LOG_CHARS) logText.takeLast(MAX_SHOWN_LOG_CHARS) else logText
    }
    val showName = remember(logFile) { logFile.name.isNotBlank() && logFile.exists() }
    Column(modifier = modifier.fillMaxWidth()) {
        if (showName) {
            Text(
                text = logFile.name,
                color = Amber.TextFaint,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(shape)
                .background(Amber.Fill)
                .border(1.dp, Amber.Line, shape)
                .padding(10.dp)
        ) {
            // Teks log memakai Text bawaan Material (tanpa penerjemah): isinya log mentah, bukan teks UI.
            androidx.compose.material3.Text(
                text = shown,
                color = Amber.TextDim,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 15.sp,
                softWrap = false,
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
            )
        }
    }
}

/** Kolom kanan: langkah mencari solusi (AI dulu), melapor, dan cara lain membagikan log. */
@Composable
private fun CrashHelpPane(
    launcherCrash: Boolean,
    uploading: Boolean,
    uploaded: Boolean,
    onCopyForAi: () -> Unit,
    onOpenAi: (String) -> Unit,
    onReport: () -> Unit,
    onShareFile: () -> Unit,
    onUpload: () -> Unit,
    modifier: Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (launcherCrash) {
            ReportSection(primary = true, launcherCrash = true, onReport = onReport)
            CrashRule()
            AiSection(primary = false, onCopy = onCopyForAi, onOpenAi = onOpenAi)
        } else {
            AiSection(primary = true, onCopy = onCopyForAi, onOpenAi = onOpenAi)
            CrashRule()
            ReportSection(primary = false, launcherCrash = false, onReport = onReport)
        }
        CrashRule()
        CrashCaption("Cara lain membagikan log")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AmberButton(
                text = "Bagikan file",
                onClick = onShareFile,
                modifier = Modifier.weight(1f),
                style = AmberButtonStyle.Ghost,
                icon = "share",
                height = 40.dp
            )
            AmberButton(
                text = when {
                    uploading -> "Mengunggah..."
                    uploaded -> "Buka tautan"
                    else -> "Tautan mclo.gs"
                },
                onClick = onUpload,
                modifier = Modifier.weight(1f),
                style = AmberButtonStyle.Ghost,
                enabled = !uploading,
                height = 40.dp
            )
        }
    }
}

@Composable
private fun AiSection(primary: Boolean, onCopy: () -> Unit, onOpenAi: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CrashSectionTitle("Cari solusi dengan AI")
        Text(text = AI_BODY, color = Amber.TextDim, fontSize = 13.sp, lineHeight = 19.sp)
        AmberButton(
            text = "Salin log untuk AI",
            onClick = onCopy,
            modifier = Modifier.fillMaxWidth(),
            style = if (primary) AmberButtonStyle.Primary else AmberButtonStyle.Ghost,
            icon = "copy"
        )
        Text(text = "Atau buka langsung (log otomatis disalin)", color = Amber.TextFaint, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AmberButton(
                text = "Claude",
                onClick = { onOpenAi(CrashAdvisor.URL_CLAUDE) },
                modifier = Modifier.weight(1f),
                style = AmberButtonStyle.Ghost,
                height = 40.dp
            )
            AmberButton(
                text = "ChatGPT",
                onClick = { onOpenAi(CrashAdvisor.URL_CHATGPT) },
                modifier = Modifier.weight(1f),
                style = AmberButtonStyle.Ghost,
                height = 40.dp
            )
            AmberButton(
                text = "Gemini",
                onClick = { onOpenAi(CrashAdvisor.URL_GEMINI) },
                modifier = Modifier.weight(1f),
                style = AmberButtonStyle.Ghost,
                height = 40.dp
            )
        }
    }
}

@Composable
private fun ReportSection(primary: Boolean, launcherCrash: Boolean, onReport: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CrashSectionTitle("Lapor ke pengembang")
        Text(
            text = if (launcherCrash) REPORT_BODY_LAUNCHER else REPORT_BODY_GAME,
            color = Amber.TextDim,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
        AmberButton(
            text = "Lapor di GitHub",
            onClick = onReport,
            modifier = Modifier.fillMaxWidth(),
            style = if (primary) AmberButtonStyle.Primary else AmberButtonStyle.Ghost,
            icon = "external"
        )
    }
}

@Composable
private fun CrashSectionTitle(text: String) {
    Text(text = text, color = Amber.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
}

@Composable
private fun CrashCaption(text: String) {
    Text(text = text, color = Amber.TextFaint, fontSize = 12.sp, fontWeight = FontWeight.Medium)
}

@Composable
private fun CrashRule() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Amber.Line)
    )
}

private fun sourceLabel(source: CrashSource): String = when (source) {
    CrashSource.LAUNCHER -> "Kesalahan di dalam AmberLauncher"
    CrashSource.MEMORY -> "Memori (RAM) HP habis"
    CrashSource.GRAPHICS -> "Renderer atau driver grafis"
    CrashSource.GAME_OR_MODS -> "Mod, versi Java, atau file game"
    CrashSource.UNKNOWN -> "Belum bisa dipastikan"
}

private fun sourceDetail(source: CrashSource): String = when (source) {
    CrashSource.LAUNCHER ->
        "Kali ini launcher sendiri yang berhenti, bukan game atau mod. Laporan ke pengembang sangat " +
            "membantu supaya bisa diperbaiki."
    CrashSource.MEMORY ->
        "Game dihentikan sistem karena RAM HP tidak cukup untuk game, mod, atau pengaturan grafis yang " +
            "dipakai. Biasanya bukan kerusakan launcher. Coba kurangi alokasi RAM game (jangan lebih dari " +
            "setengah RAM HP), kurangi mod, dan tutup aplikasi lain."
    CrashSource.GRAPHICS ->
        "Game berhenti di bagian grafis atau pustaka bawaan HP. Penyebab umumnya renderer yang kurang " +
            "cocok dengan HP ini. Coba ganti renderer di pengaturan instance. Kalau semua renderer gagal, " +
            "barulah mungkin masalahnya ada di launcher."
    CrashSource.GAME_OR_MODS ->
        "Game mengalami error saat berjalan. Paling sering karena mod yang tidak cocok dengan versi game " +
            "atau loader, mod yang kekurangan pendukung (dependency), atau versi Java yang tidak sesuai. " +
            "Biasanya bukan kerusakan launcher."
    CrashSource.UNKNOWN ->
        "Penyebabnya baru kelihatan dari isi log. AI bisa membantu membacanya untukmu."
}

private fun copyToClipboard(context: Context, label: String, text: String) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    } catch (_: Throwable) {
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "Tidak bisa membuka tautan.", Toast.LENGTH_SHORT).show()
    }
}
