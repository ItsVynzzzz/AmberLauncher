package com.israadev.nuxlauncher.ui.dialogs

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import com.israadev.nuxlauncher.ui.i18n.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.AppLinks
import com.israadev.nuxlauncher.ui.components.AmberButton
import com.israadev.nuxlauncher.ui.components.AmberButtonStyle
import com.israadev.nuxlauncher.ui.components.AmberIcon
import com.israadev.nuxlauncher.ui.components.AmberSegmented
import com.israadev.nuxlauncher.ui.components.NuxDialog
import com.israadev.nuxlauncher.ui.components.amberTap
import com.israadev.nuxlauncher.ui.theme.Amber

private const val COPY = "Copyright \u00A9"
private const val GPL_URL = "https://www.gnu.org/licenses/gpl-3.0.html"

private data class AboutProject(
    val title: String,
    val copyright: String,
    val license: String,
    val description: String,
    val url: String
)

private data class OpenSourceLibrary(
    val name: String,
    val copyright: String,
    val license: String,
    val role: String,
    val url: String
)

private val PROJECTS = listOf(
    AboutProject(
        title = "AmberLauncher",
        copyright = "$COPY 2026 ItsVynzzzz",
        license = "GNU General Public License v3.0 (GPL-3.0)",
        description = "Peluncur ini. Kode sumbernya terbuka dan boleh dipelajari, diubah, dan dibagikan lagi dengan syarat GPL-3.0 yang sama.",
        url = "https://github.com/ItsVynzzzz/AmberLauncher"
    ),
    AboutProject(
        title = "Zalith Launcher 2",
        copyright = "$COPY 2024-2026 MovTery & Contributors",
        license = "GNU General Public License v3.0 (GPL-3.0)",
        description = "Komponen arsitektur bridge JNI native (ZLBridge), integrasi SDL3 surface, direct gamepad input, penanganan resolusi DNS, dan optimasi eksekusi OpenJDK mobile.",
        url = "https://github.com/ZalithLauncher/ZalithLauncher2"
    ),
    AboutProject(
        title = "PojavLauncher",
        copyright = "$COPY 2020-present PojavLauncherTeam & Contributors",
        license = "GNU Lesser General Public License v3.0 (LGPL-3.0)",
        description = "Proyek pionir peluncuran Minecraft Java Edition di Android. Menyediakan porting LWJGL 3, GLFW Stubs, AWT stubs, dan integrasi grafis renderer mobile.",
        url = "https://github.com/PojavLauncherTeam/PojavLauncher"
    ),
    AboutProject(
        title = "NUX Launcher (sumber asal fork)",
        copyright = "$COPY 2026 IsraaDeveloper & Contributors",
        license = "GNU General Public License v3.0 (GPL-3.0)",
        description = "Proyek asal yang di-fork oleh AmberLauncher. Kode sumber terbuka di bawah GNU GPL-3.0.",
        url = "https://github.com/IsraaDeveloper/nuxlabs"
    )
)

private val LIBRARIES = listOf(
    OpenSourceLibrary(
        name = "Zalith Launcher 2",
        copyright = "$COPY 2024-2026 MovTery & Contributors",
        license = "GPL-3.0 License",
        role = "Arsitektur runtime JVM, ZLBridge JNI, integrasi SDL3, direct gamepad, dan optimasi Android",
        url = "https://github.com/ZalithLauncher/ZalithLauncher2"
    ),
    OpenSourceLibrary(
        name = "PojavLauncher",
        copyright = "$COPY 2020-present PojavLauncherTeam & Contributors",
        license = "LGPL-3.0 License",
        role = "Mesin peluncuran Java Minecraft Android, LWJGL 3 Android Natives, GLFW Stub",
        url = "https://github.com/PojavLauncherTeam/PojavLauncher"
    ),
    OpenSourceLibrary(
        name = "MobileGlues",
        copyright = "$COPY 2023-2026 MobileGlues Team",
        license = "LGPL-2.1 License",
        role = "Penerjemah modern OpenGL 4.0 ke OpenGL ES 3.2 untuk Minecraft 1.17+",
        url = "https://github.com/FCL-Team/MobileGlues"
    ),
    OpenSourceLibrary(
        name = "LWJGL - Lightweight Java Game Library",
        copyright = "$COPY 2012-present LWJGL All rights reserved",
        license = "BSD 3-Clause License",
        role = "Pustaka binding grafis, audio (OpenAL), dan jendela game Java",
        url = "https://github.com/LWJGL/lwjgl3"
    ),
    OpenSourceLibrary(
        name = "SDL3 & sdl2-compat",
        copyright = "$COPY 1997-2026 Sam Lantinga",
        license = "Zlib License",
        role = "Manajemen surface rendering native, touch input, dan audio output",
        url = "https://github.com/libsdl-org/SDL"
    ),
    OpenSourceLibrary(
        name = "Mesa 3D (Zink / Turnip / VirGL)",
        copyright = "$COPY The Mesa Authors",
        license = "MIT License",
        role = "Vulkan-to-OpenGL translation & driver akselerasi GPU mobile",
        url = "https://mesa3d.org/"
    ),
    OpenSourceLibrary(
        name = "ANGLE",
        copyright = "$COPY 2018 The ANGLE Project Authors",
        license = "BSD 3-Clause License",
        role = "Mesin penerjemah OpenGL ES berbasis backend Vulkan",
        url = "https://angleproject.org/"
    ),
    OpenSourceLibrary(
        name = "ByteHook",
        copyright = "$COPY 2020-2024 ByteDance, Inc.",
        license = "MIT License",
        role = "Dynamic binary instrumentation dan hook Android library",
        url = "https://github.com/bytedance/bhook"
    ),
    OpenSourceLibrary(
        name = "TouchController (proxy client)",
        copyright = "$COPY fifth_light",
        license = "LGPL-3.0",
        role = "Klien proxy untuk mod kontrol sentuh TouchController",
        url = "https://github.com/TouchController/TouchController"
    ),
    OpenSourceLibrary(
        name = "Jetpack Compose & Material 3",
        copyright = "$COPY The Android Open Source Project",
        license = "Apache 2.0",
        role = "Toolkit UI modern, deklaratif, dan responsif",
        url = "https://developer.android.com/jetpack/compose"
    ),
    OpenSourceLibrary(
        name = "Modrinth API",
        copyright = "$COPY Rinth, Inc. & Community",
        license = "Open API / AGPL",
        role = "Katalog publik mod, modpack, shaderpack, dan resource pack",
        url = "https://modrinth.com"
    ),
    OpenSourceLibrary(
        name = "sora-editor",
        copyright = "Copyright (C) 2020-2026 Rosemoe",
        license = "LGPL-2.1 License",
        role = "Editor teks dan log penampil performa tinggi",
        url = "https://github.com/Rosemoe/sora-editor"
    ),
    OpenSourceLibrary(
        name = "skinview3d",
        copyright = "$COPY Kent Rasmussen & contributors",
        license = "MIT License",
        role = "Viewer interaktif 3D skin & jubah Minecraft berbasis WebGL",
        url = "https://github.com/bs-community/skinview3d"
    ),
    OpenSourceLibrary(
        name = "OkHttp & Okio",
        copyright = "$COPY Square, Inc.",
        license = "Apache 2.0",
        role = "Klien HTTP jaringan performa tinggi",
        url = "https://github.com/square/okhttp"
    )
)

private fun openLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        // Tidak ada peramban: tidak ada yang bisa dilakukan, biarkan dialog tetap terbuka.
    }
}

private fun appVersionName(context: Context): String {
    return try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
    } catch (e: Exception) {
        ""
    }
}

/**
 * Dialog Tentang: ringkasan proyek, daftar pustaka open source, dan ketentuan GPL-3.0 (termasuk
 * syarat tambahan Zalith). Di layar lebar tabnya jadi menu samping, di layar sempit jadi segmen
 * di atas. Tombol Buy Me a Coffee hanya muncul kalau AppLinks.DONATE_URL diisi.
 */
@Composable
fun NuxAboutDialog(
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    val scroll = rememberScrollState()
    LaunchedEffect(tab) { scroll.scrollTo(0) }

    val version = remember(context) { appVersionName(context) }
    val onOpen: (String) -> Unit = remember(context) { { url: String -> openLink(context, url) } }
    val donateUrl = AppLinks.DONATE_URL.trim()
    val onDonate: (() -> Unit)? = if (donateUrl.isNotEmpty()) ({ onOpen(donateUrl) }) else null
    val tabs = remember { listOf("Ringkasan", "Pustaka (${LIBRARIES.size})", "GPL-3.0") }

    NuxDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.widthIn(max = 720.dp),
        fillMaxHeight = true,
        compact = true
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            if (maxWidth >= 520.dp) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .width(208.dp)
                            .fillMaxHeight()
                            .padding(horizontal = 12.dp, vertical = 14.dp)
                    ) {
                        Brand(version)
                        Spacer(modifier = Modifier.height(14.dp))
                        tabs.forEachIndexed { index, label ->
                            RailTab(label = label, selected = index == tab) { tab = index }
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        if (onDonate != null) {
                            DonateButton(onClick = onDonate, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        CloseButton(onClick = onDismissRequest, modifier = Modifier.fillMaxWidth())
                    }
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(Amber.Line)
                    )
                    AboutContent(
                        tab = tab,
                        onOpen = onOpen,
                        scroll = scroll,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp)
                ) {
                    Brand(version)
                    Spacer(modifier = Modifier.height(10.dp))
                    AmberSegmented(options = tabs, selected = tab, onSelect = { tab = it })
                    AboutContent(
                        tab = tab,
                        onOpen = onOpen,
                        scroll = scroll,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (onDonate != null) {
                            DonateButton(onClick = onDonate, modifier = Modifier.weight(1f))
                        }
                        CloseButton(onClick = onDismissRequest, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutContent(
    tab: Int,
    onOpen: (String) -> Unit,
    scroll: ScrollState,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(scroll)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        when (tab) {
            0 -> OverviewTab(onOpen)
            1 -> LibrariesTab(onOpen)
            else -> LicenseTab(onOpen)
        }
    }
}

@Composable
private fun OverviewTab(onOpen: (String) -> Unit) {
    AboutTitle(
        title = "Tentang AmberLauncher",
        subtitle = "Peluncur Minecraft Java untuk Android, ringan dan sederhana."
    )
    Notice(
        title = "Unofficial Modified Version",
        body = "AmberLauncher adalah peluncur independen hasil modifikasi dari NUX Launcher, yang sendirinya berasal dari Zalith Launcher 2 dan PojavLauncher. Aplikasi ini bukan aplikasi resmi Zalith Launcher Team, NUX Launcher, maupun Mojang Studios.",
        highlight = true
    )
    SubHeading("Proyek dan hak cipta")
    for (project in PROJECTS) {
        ProjectRow(project, onOpen)
    }
    Notice(
        title = "Penafian Minecraft",
        body = "Bukan produk resmi Minecraft. Tidak disetujui oleh atau terkait dengan Mojang Studios atau Microsoft. Minecraft adalah merek dagang milik Mojang AB / Microsoft Corporation. Seluruh aset game diunduh langsung dari server distribusi resmi Mojang.",
        highlight = false
    )
}

@Composable
private fun LibrariesTab(onOpen: (String) -> Unit) {
    AboutTitle(
        title = "Pustaka open source",
        subtitle = "Ketuk untuk membuka halaman proyeknya."
    )
    for (lib in LIBRARIES) {
        LibraryRow(lib, onOpen)
    }
}

@Composable
private fun LicenseTab(onOpen: (String) -> Unit) {
    AboutTitle(
        title = "GNU GPL-3.0",
        subtitle = "Lisensi yang menaungi AmberLauncher."
    )
    Body("Proyek ini dilisensikan di bawah ketentuan GNU General Public License versi 3.0.")
    SubHeading("Empat kebebasan perangkat lunak bebas")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Numbered(1, "Kebebasan untuk menjalankan program untuk keperluan apa pun.")
        Numbered(2, "Kebebasan untuk mempelajari cara kerja program dan menyesuaikannya dengan kebutuhan Anda.")
        Numbered(3, "Kebebasan untuk mendistribusikan kembali salinan untuk membantu sesama.")
        Numbered(4, "Kebebasan untuk menyempurnakan program dan merilis penyempurnaan tersebut kepada publik.")
    }
    SubHeading("Ketentuan tambahan (GPLv3 Pasal 7)")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Bullet("Modifikasi tidak boleh menyalahgunakan nama dagang 'ZalithLauncher' atau 'ZL'.")
        Bullet("Modifikasi wajib menampilkan keterangan bahwa ini adalah 'Unofficial Modified Version'.")
        Bullet("Hak cipta penulis asli (Copyright \u00A9 MovTery & PojavLauncherTeam) tidak boleh dihapus.")
    }
    Spacer(modifier = Modifier.height(2.dp))
    AmberButton(
        text = "Baca teks lisensi lengkap",
        onClick = { onOpen(GPL_URL) },
        modifier = Modifier.fillMaxWidth(),
        style = AmberButtonStyle.Ghost,
        icon = "external"
    )
}

// ---------------------------------------------------------------------------------------------
// Bagian kecil
// ---------------------------------------------------------------------------------------------

@Composable
private fun Brand(version: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Amber.Yellow),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "A",
                color = Amber.Ink,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = "AmberLauncher",
                color = Amber.Text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            if (version.isNotBlank()) {
                Text(
                    text = "Versi $version",
                    color = Amber.TextDim,
                    fontSize = 12.5.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun RailTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(shape)
            .background(if (selected) Amber.YellowTint else Color.Transparent)
            .amberTap(onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(16.dp)
                .background(
                    if (selected) Amber.Yellow else Color.Transparent,
                    RoundedCornerShape(2.dp)
                )
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            color = if (selected) Amber.Yellow else Amber.TextDim,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun DonateButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    AmberButton(
        text = "Buy me a coffee",
        onClick = onClick,
        modifier = modifier,
        style = AmberButtonStyle.Primary,
        icon = "coffee"
    )
}

@Composable
private fun CloseButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    AmberButton(
        text = "Tutup",
        onClick = onClick,
        modifier = modifier,
        style = AmberButtonStyle.Ghost
    )
}

@Composable
private fun AboutTitle(title: String, subtitle: String) {
    Column {
        Text(
            text = title,
            color = Amber.Text,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            color = Amber.TextDim,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun SubHeading(text: String) {
    Text(
        text = text,
        color = Amber.Yellow,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun Body(text: String) {
    Text(
        text = text,
        color = Amber.TextDim,
        fontSize = 14.sp,
        lineHeight = 21.sp
    )
}

@Composable
private fun Notice(title: String, body: String, highlight: Boolean) {
    val shape = RoundedCornerShape(12.dp)
    val fill = if (highlight) Amber.YellowTint else Amber.Fill
    val line = if (highlight) Amber.Yellow.copy(alpha = 0.40f) else Amber.Line
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(fill)
            .border(1.dp, line, shape)
            .padding(12.dp)
    ) {
        Text(
            text = title,
            color = if (highlight) Amber.Yellow else Amber.Text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = body,
            color = Amber.TextDim,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
    }
}

@Composable
private fun ProjectRow(project: AboutProject, onOpen: (String) -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Amber.Fill)
            .border(1.dp, Amber.Line, shape)
            .amberTap { onOpen(project.url) }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = project.title,
                color = Amber.Text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            AmberIcon("external", 16.dp, Amber.TextDim)
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = project.copyright,
            color = Amber.TextFaint,
            fontSize = 12.sp
        )
        Text(
            text = project.license,
            color = Amber.TextFaint,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = project.description,
            color = Amber.TextDim,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
    }
}

@Composable
private fun LibraryRow(lib: OpenSourceLibrary, onOpen: (String) -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Amber.Fill)
            .border(1.dp, Amber.Line, shape)
            .amberTap { onOpen(lib.url) }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = lib.name,
                color = Amber.Text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Chip(lib.license.removeSuffix(" License"))
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = lib.copyright,
            color = Amber.TextFaint,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = lib.role,
            color = Amber.TextDim,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
    }
}

@Composable
private fun Chip(text: String) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(Amber.YellowTint)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = Amber.Yellow,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun Numbered(number: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(Amber.YellowTint, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.toString(),
                color = Amber.Yellow,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            color = Amber.TextDim,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun Bullet(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp, start = 7.dp)
                .size(6.dp)
                .background(Amber.Yellow, CircleShape)
        )
        Spacer(modifier = Modifier.width(13.dp))
        Text(
            text = text,
            color = Amber.TextDim,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            modifier = Modifier.weight(1f)
        )
    }
}
