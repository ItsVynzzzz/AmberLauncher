package com.israadev.nuxlauncher.ui.dialogs

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.AppLinks
import com.israadev.nuxlauncher.ui.components.AmberActionButton
import com.israadev.nuxlauncher.ui.components.AmberChoicePill
import com.israadev.nuxlauncher.ui.components.AmberPage
import com.israadev.nuxlauncher.ui.components.AmberPageLabel
import com.israadev.nuxlauncher.ui.components.AmberPanel
import com.israadev.nuxlauncher.ui.components.AmberRailItem
import com.israadev.nuxlauncher.ui.components.AmberTag
import com.israadev.nuxlauncher.ui.i18n.Text
import com.israadev.nuxlauncher.ui.screens.AmberPillButton
import com.israadev.nuxlauncher.ui.theme.AmberUi
import com.israadev.nuxlauncher.ui.theme.amberGlass

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
 * Halaman Tentang: ringkasan proyek, daftar pustaka open source, dan ketentuan GPL-3.0 (termasuk
 * syarat tambahan Zalith).
 *
 * Bentuknya halaman kaca layar penuh seperti layar Mod/Akun/Pengaturan. Di layar lebar: rail kiri
 * (kartu merek + tiga tab berbentuk pil) dan isi di kanan yang digulir; kartu pustaka/proyek
 * disusun dua kolom bila ruangnya cukup. Di layar sempit: satu kolom yang digulir (merek, tiga
 * pil tab, lalu isi). Tombol Tutup diganti panah kembali di bar atas, jadi tidak ada tombol di
 * dasar layar. Tombol Buy Me a Coffee hanya muncul kalau AppLinks.DONATE_URL diisi.
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
    val tabIcons = listOf(Icons.Outlined.Info, Icons.Outlined.Extension, Icons.Outlined.Description)

    AmberPage(
        onDismiss = onDismissRequest,
        title = "Tentang",
        icon = Icons.Outlined.Info,
        subtitle = if (version.isNotBlank()) "Versi $version" else null,
        actions = { info ->
            if (onDonate != null) {
                if (info.narrow) {
                    // Layar sempit: tombol bulat saja supaya bar atas tidak sesak
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(AmberUi.Accent, CircleShape)
                            .border(1.dp, AmberUi.AccentEdge, CircleShape)
                            .clickable { onDonate() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Favorite,
                            contentDescription = "Buy me a coffee",
                            tint = AmberUi.Text,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    AmberPillButton(
                        text = "Buy me a coffee",
                        onClick = onDonate,
                        icon = Icons.Outlined.Favorite
                    )
                }
            }
        }
    ) { info ->
        if (info.narrow) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BrandCard(version)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    tabs.forEachIndexed { index, label ->
                        AmberChoicePill(
                            text = label,
                            selected = index == tab,
                            onClick = { tab = index },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                AboutContent(tab = tab, onOpen = onOpen)
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .width(232.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BrandCard(version)
                    tabs.forEachIndexed { index, label ->
                        AmberRailItem(
                            title = label,
                            icon = tabIcons[index],
                            selected = index == tab,
                            onClick = { tab = index }
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(scroll)
                ) {
                    AboutContent(tab = tab, onOpen = onOpen)
                }
            }
        }
    }
}

@Composable
private fun AboutContent(
    tab: Int,
    onOpen: (String) -> Unit
) {
    // Dua kolom hanya bila lebar isi cukup untuk dua kartu yang nyaman dibaca
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val columns = if (maxWidth >= 520.dp) 2 else 1
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (tab) {
                0 -> OverviewTab(onOpen, columns)
                1 -> LibrariesTab(onOpen, columns)
                else -> LicenseTab(onOpen)
            }
        }
    }
}

@Composable
private fun OverviewTab(onOpen: (String) -> Unit, columns: Int) {
    AboutTitle(
        title = "Tentang AmberLauncher",
        subtitle = "Peluncur Minecraft Java untuk Android, ringan dan sederhana."
    )
    AboutNotice(
        title = "Unofficial Modified Version",
        body = "AmberLauncher adalah peluncur independen hasil modifikasi dari NUX Launcher, yang sendirinya berasal dari Zalith Launcher 2 dan PojavLauncher. Aplikasi ini bukan aplikasi resmi Zalith Launcher Team, NUX Launcher, maupun Mojang Studios.",
        highlight = true
    )
    Column {
        AmberPageLabel("Proyek dan hak cipta")
        CardGrid(items = PROJECTS, columns = columns) { project, cardModifier ->
            ProjectCard(project, onOpen, cardModifier)
        }
    }
    AboutNotice(
        title = "Penafian Minecraft",
        body = "Bukan produk resmi Minecraft. Tidak disetujui oleh atau terkait dengan Mojang Studios atau Microsoft. Minecraft adalah merek dagang milik Mojang AB / Microsoft Corporation. Seluruh aset game diunduh langsung dari server distribusi resmi Mojang.",
        highlight = false
    )
}

@Composable
private fun LibrariesTab(onOpen: (String) -> Unit, columns: Int) {
    AboutTitle(
        title = "Pustaka open source",
        subtitle = "Ketuk untuk membuka halaman proyeknya."
    )
    CardGrid(items = LIBRARIES, columns = columns) { lib, cardModifier ->
        LibraryCard(lib, onOpen, cardModifier)
    }
}

@Composable
private fun LicenseTab(onOpen: (String) -> Unit) {
    AboutTitle(
        title = "GNU GPL-3.0",
        subtitle = "Lisensi yang menaungi AmberLauncher."
    )
    AmberPanel {
        Text(
            text = "Proyek ini dilisensikan di bawah ketentuan GNU General Public License versi 3.0.",
            color = AmberUi.TextDim,
            fontSize = 13.sp,
            lineHeight = 20.sp
        )
    }
    Column {
        AmberPageLabel("Empat kebebasan perangkat lunak bebas")
        AmberPanel {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Numbered(1, "Kebebasan untuk menjalankan program untuk keperluan apa pun.")
                Numbered(2, "Kebebasan untuk mempelajari cara kerja program dan menyesuaikannya dengan kebutuhan Anda.")
                Numbered(3, "Kebebasan untuk mendistribusikan kembali salinan untuk membantu sesama.")
                Numbered(4, "Kebebasan untuk menyempurnakan program dan merilis penyempurnaan tersebut kepada publik.")
            }
        }
    }
    Column {
        AmberPageLabel("Ketentuan tambahan (GPLv3 Pasal 7)")
        AmberPanel {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Bullet("Modifikasi tidak boleh menyalahgunakan nama dagang 'ZalithLauncher' atau 'ZL'.")
                Bullet("Modifikasi wajib menampilkan keterangan bahwa ini adalah 'Unofficial Modified Version'.")
                Bullet("Hak cipta penulis asli (Copyright \u00A9 MovTery & PojavLauncherTeam) tidak boleh dihapus.")
            }
        }
    }
    AmberActionButton(
        text = "Baca teks lisensi lengkap",
        onClick = { onOpen(GPL_URL) },
        icon = Icons.Outlined.OpenInBrowser,
        filled = false,
        modifier = Modifier.fillMaxWidth()
    )
}

// ---------------------------------------------------------------------------------------------
// Bagian kecil
// ---------------------------------------------------------------------------------------------

/**
 * Menyusun kartu satu kolom, atau dua kolom dengan tinggi sama per baris. [cell] menerima
 * modifier yang harus dipasang di kartunya (lebar/tinggi sudah diatur di sini).
 */
@Composable
private fun <T> CardGrid(
    items: List<T>,
    columns: Int,
    cell: @Composable (T, Modifier) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (columns <= 1) {
            items.forEach { item -> cell(item, Modifier.fillMaxWidth()) }
        } else {
            items.chunked(columns).forEach { rowItems ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowItems.forEach { item ->
                        cell(item, Modifier.weight(1f).fillMaxHeight())
                    }
                    repeat(columns - rowItems.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/** Kartu merek: logo "A", nama, versi, dan keterangan versi modifikasi tidak resmi. */
@Composable
private fun BrandCard(version: String) {
    AmberPanel(padding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(AmberUi.Accent, RoundedCornerShape(15.dp))
                    .border(1.dp, AmberUi.AccentEdge, RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "A",
                    color = AmberUi.Text,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AmberLauncher",
                    color = AmberUi.Text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (version.isNotBlank()) {
                    Text(
                        text = "Versi $version",
                        color = AmberUi.TextDim,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        // Pemberitahuan versi modifikasi tidak resmi (dulu tampil di Home, kini cukup di sini)
        Text(
            text = "UNOFFICIAL MODIFIED VERSION",
            color = AmberUi.TextDim,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            maxLines = 2
        )
    }
}

@Composable
private fun AboutTitle(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(start = 4.dp, top = 2.dp)) {
        Text(
            text = title,
            color = AmberUi.Text,
            fontSize = 20.sp,
            fontWeight = FontWeight.Black
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            color = AmberUi.TextDim,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )
    }
}

/** Kartu pemberitahuan. [highlight] = isian aksen (penting), selain itu kaca biasa. */
@Composable
private fun AboutNotice(title: String, body: String, highlight: Boolean) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (highlight) {
                    Modifier
                        .background(AmberUi.Accent.copy(alpha = 0.30f), shape)
                        .border(1.dp, AmberUi.AccentEdge, shape)
                } else {
                    Modifier.amberGlass(shape)
                }
            )
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (highlight) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = AmberUi.AccentSoft,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = title,
                color = if (highlight) AmberUi.AccentSoft else AmberUi.Text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = body,
            color = AmberUi.TextDim,
            fontSize = 12.5.sp,
            lineHeight = 18.sp
        )
    }
}

/** Kartu kaca yang bisa diketuk (membuka halaman proyek). */
@Composable
private fun AboutCard(
    onClick: () -> Unit,
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .amberGlass(shape)
            .clickable { onClick() }
            .padding(14.dp),
        content = content
    )
}

@Composable
private fun ProjectCard(project: AboutProject, onOpen: (String) -> Unit, modifier: Modifier) {
    AboutCard(onClick = { onOpen(project.url) }, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = project.title,
                color = AmberUi.Text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Outlined.OpenInBrowser,
                contentDescription = null,
                tint = AmberUi.TextDim,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = project.copyright,
            color = AmberUi.TextDim.copy(alpha = 0.75f),
            fontSize = 11.5.sp
        )
        Text(
            text = project.license,
            color = AmberUi.TextDim.copy(alpha = 0.75f),
            fontSize = 11.5.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = project.description,
            color = AmberUi.TextDim,
            fontSize = 12.5.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun LibraryCard(lib: OpenSourceLibrary, onOpen: (String) -> Unit, modifier: Modifier) {
    AboutCard(onClick = { onOpen(lib.url) }, modifier = modifier) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = lib.name,
                color = AmberUi.Text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            AmberTag(lib.license.removeSuffix(" License"))
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = lib.copyright,
            color = AmberUi.TextDim.copy(alpha = 0.75f),
            fontSize = 11.5.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = lib.role,
            color = AmberUi.TextDim,
            fontSize = 12.5.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun Numbered(number: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(AmberUi.Accent.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.toString(),
                color = AmberUi.Text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            color = AmberUi.TextDim,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun Bullet(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp, start = 8.dp)
                .size(6.dp)
                .background(AmberUi.AccentSoft, CircleShape)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = text,
            color = AmberUi.TextDim,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier.weight(1f)
        )
    }
}
