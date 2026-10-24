package com.israadev.nuxlauncher.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.core.fabric.FabricService
import com.israadev.nuxlauncher.core.forge.ForgeBuild
import com.israadev.nuxlauncher.core.forge.ForgeFlavor
import com.israadev.nuxlauncher.core.forge.ForgeService
import com.israadev.nuxlauncher.core.manifest.MojangManifestService
import com.israadev.nuxlauncher.core.models.Instance
import com.israadev.nuxlauncher.core.models.VersionItem
import com.israadev.nuxlauncher.ui.components.AmberButton
import com.israadev.nuxlauncher.ui.components.AmberButtonStyle
import com.israadev.nuxlauncher.ui.components.AmberIcon
import com.israadev.nuxlauncher.ui.components.AmberSegmented
import com.israadev.nuxlauncher.ui.components.NuxDialog
import com.israadev.nuxlauncher.ui.components.amberTap
import com.israadev.nuxlauncher.ui.i18n.Text
import com.israadev.nuxlauncher.ui.theme.Amber
import java.util.UUID

/** Filter kategori versi Minecraft (seperti Zalith Launcher). */
enum class VersionCategory(val label: String) {
    RELEASE("Release"),
    SNAPSHOT("Snapshot / Beta"),
    OLD_BETA("Old Beta"),
    OLD_ALPHA("Old Alpha"),
    ALL("Semua")
}

/** Satu baris pilihan versi loader (Fabric/Forge/NeoForge): [tag] kosong bila tidak ada keterangan. */
private class LoaderChoice(val id: String, val tag: String)

private val LOADERS = listOf(
    "vanilla" to "Vanilla",
    "fabric" to "Fabric",
    "forge" to "Forge",
    "neoforge" to "NeoForge"
)
private val LOADER_LABELS = LOADERS.map { it.second }

/**
 * Dialog "Buat Instance Baru" bergaya Amber: dua panel datar (kiri: nama, loader, ringkasan, tombol
 * buat; kanan: filter, cari, daftar versi). Semua digambar dengan komponen Amber tanpa ripple,
 * tanpa animasi warna, dan tanpa indikator berputar atau popup, jadi menyentuh dialog ini tidak
 * menyusun ulang layar di belakangnya.
 */
@Composable
fun NuxAddInstanceDialog(
    onDismiss: () -> Unit,
    onInstanceCreated: (Instance) -> Unit
) {
    var instanceName by remember { mutableStateOf("") }
    var selectedVersion by remember { mutableStateOf("1.21.1") }
    var selectedLoader by remember { mutableStateOf("vanilla") } // "vanilla", "fabric", "forge", atau "neoforge"
    var selectedFabricVersion by remember { mutableStateOf("") }
    // Forge dan NeoForge memakai satu set state: daftar dimuat ulang tiap loader/versi Minecraft berganti
    var selectedForgeVersion by remember { mutableStateOf("") }

    var versionsList by remember { mutableStateOf<List<VersionItem>>(emptyList()) }
    var fabricVersionsList by remember { mutableStateOf<List<String>>(emptyList()) }
    var forgeBuildsList by remember { mutableStateOf<List<ForgeBuild>>(emptyList()) }
    var isLoadingVersions by remember { mutableStateOf(true) }
    var isLoadingFabric by remember { mutableStateOf(false) }
    var isLoadingForge by remember { mutableStateOf(false) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(VersionCategory.RELEASE) }

    // Daftar versi loader terbuka di tempat (bukan popup); menutup sendiri saat loader/versi berganti
    var pickerOpen by remember(selectedLoader, selectedVersion) { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val result = MojangManifestService.getVersionManifest()
        val manifest = result.getOrNull()
        if (manifest != null) {
            versionsList = manifest.versions
            val releases = manifest.versions.filter { it.type == "release" }
            val defaultChoice = releases.find { it.id == "1.21.1" }?.id
                ?: releases.find { it.id == "1.20.1" }?.id
                ?: releases.firstOrNull()?.id
                ?: "1.21.1"
            selectedVersion = defaultChoice
        }
        isLoadingVersions = false
    }

    LaunchedEffect(selectedVersion, selectedLoader) {
        if (selectedLoader == "fabric") {
            isLoadingFabric = true
            fabricVersionsList = emptyList()
            selectedFabricVersion = ""
            val result = FabricService.getFabricLoaders(selectedVersion)
            val loaders = result.getOrNull() ?: emptyList()
            fabricVersionsList = loaders
            if (loaders.isNotEmpty()) {
                selectedFabricVersion = loaders.first()
            }
            isLoadingFabric = false
        }
    }

    // Forge/NeoForge hanya dimuat saat dipilih (tidak ada permintaan jaringan sebelumnya)
    LaunchedEffect(selectedVersion, selectedLoader) {
        val flavor = ForgeFlavor.of(selectedLoader)
        if (flavor != null) {
            isLoadingForge = true
            forgeBuildsList = emptyList()
            selectedForgeVersion = ""
            val builds = ForgeService.getBuilds(selectedVersion, flavor).getOrNull() ?: emptyList()
            forgeBuildsList = builds
            selectedForgeVersion = ForgeService.pickDefault(builds)?.version ?: ""
            isLoadingForge = false
        }
    }

    val filteredVersions = remember(versionsList, searchQuery, selectedCategory) {
        versionsList.filter { item ->
            val matchesCategory = when (selectedCategory) {
                VersionCategory.ALL -> true
                VersionCategory.RELEASE -> item.type == "release"
                VersionCategory.SNAPSHOT -> item.type == "snapshot" || item.type == "pending" || item.type == "unobfuscated"
                VersionCategory.OLD_BETA -> item.type == "old_beta" || item.id.startsWith("b1.") || item.id.startsWith("b")
                VersionCategory.OLD_ALPHA -> item.type == "old_alpha" || item.id.startsWith("a1.") || item.id.startsWith("a")
            }
            val matchesQuery = if (searchQuery.isBlank()) true else item.id.contains(searchQuery.trim(), ignoreCase = true)
            matchesCategory && matchesQuery
        }
    }

    val forgeChoices = remember(forgeBuildsList) {
        forgeBuildsList.take(40).map { build ->
            LoaderChoice(
                id = build.version,
                tag = when {
                    build.recommended -> "Disarankan"
                    build.latest -> "Terbaru"
                    else -> ""
                }
            )
        }
    }
    val fabricChoices = remember(fabricVersionsList) {
        fabricVersionsList.take(30).map { LoaderChoice(it, "") }
    }

    val forgeFlavor = ForgeFlavor.of(selectedLoader)
    val forgeName = forgeFlavor?.displayName ?: "Forge"

    val canCreate = !(selectedLoader == "fabric" && (isLoadingFabric || selectedFabricVersion.isBlank())) &&
        !(forgeFlavor != null && (isLoadingForge || selectedForgeVersion.isBlank()))

    val loaderLabel = LOADERS.firstOrNull { it.first == selectedLoader }?.second ?: "Vanilla"
    val loaderVersionNow = when {
        selectedLoader == "fabric" -> selectedFabricVersion
        forgeFlavor != null -> selectedForgeVersion
        else -> ""
    }

    NuxDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 820.dp),
        fillMaxHeight = true,
        compact = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            // ---------------------------------------------------------------- judul
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Buat Instance Baru",
                    color = Amber.Text,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Amber.Fill)
                        .border(1.dp, Amber.Line, CircleShape)
                        .amberTap(onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    AmberIcon("close", 18.dp, Amber.TextDim)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // ============================================================ kiri: konfigurasi
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 1. Nama
                        Column {
                            AiLabel("NAMA INSTANCE")
                            Spacer(modifier = Modifier.height(6.dp))
                            AiField(
                                value = instanceName,
                                onChange = { instanceName = it },
                                placeholder = "Default: $selectedVersion $loaderLabel"
                            )
                        }

                        // 2. Mod loader
                        Column {
                            AiLabel("MOD LOADER")
                            Spacer(modifier = Modifier.height(6.dp))
                            AmberSegmented(
                                options = LOADER_LABELS,
                                selected = LOADERS.indexOfFirst { it.first == selectedLoader }.coerceAtLeast(0),
                                onSelect = { selectedLoader = LOADERS[it].first }
                            )

                            if (selectedLoader == "fabric") {
                                Spacer(modifier = Modifier.height(8.dp))
                                AiLoaderPicker(
                                    prefix = "Fabric",
                                    loading = isLoadingFabric,
                                    loadingText = "Memuat loader Fabric...",
                                    emptyText = "⚠️ Fabric belum tersedia untuk $selectedVersion",
                                    choices = fabricChoices,
                                    selectedId = selectedFabricVersion,
                                    open = pickerOpen,
                                    onToggle = { pickerOpen = !pickerOpen },
                                    onPick = {
                                        selectedFabricVersion = it
                                        pickerOpen = false
                                    }
                                )
                            }

                            if (forgeFlavor != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                AiLoaderPicker(
                                    prefix = forgeName,
                                    loading = isLoadingForge,
                                    loadingText = "Memuat versi $forgeName...",
                                    emptyText = if (ForgeService.isMcSupported(selectedVersion, forgeFlavor)) {
                                        "⚠️ $forgeName belum tersedia untuk $selectedVersion"
                                    } else {
                                        "⚠️ $forgeName hanya untuk Minecraft ${forgeFlavor.minMcVersion} ke atas"
                                    },
                                    choices = forgeChoices,
                                    selectedId = selectedForgeVersion,
                                    open = pickerOpen,
                                    onToggle = { pickerOpen = !pickerOpen },
                                    onPick = {
                                        selectedForgeVersion = it
                                        pickerOpen = false
                                    }
                                )
                                Text(
                                    text = "$forgeName dipasang sekali saat instance diunduh pertama kali.",
                                    color = Amber.TextDim,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                        }

                        // 3. Ringkasan
                        AiSummary(
                            mcVersion = selectedVersion,
                            loaderLabel = loaderLabel,
                            loaderVersion = loaderVersionNow
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    AmberButton(
                        text = when {
                            isLoadingFabric && selectedLoader == "fabric" -> "Memuat Fabric..."
                            selectedLoader == "fabric" && selectedFabricVersion.isBlank() -> "Fabric Tidak Tersedia"
                            isLoadingForge && forgeFlavor != null -> "Memuat $forgeName..."
                            forgeFlavor != null && selectedForgeVersion.isBlank() -> "$forgeName Tidak Tersedia"
                            else -> "Buat Instance Sekarang"
                        },
                        onClick = {
                            val defaultName = "$selectedVersion $loaderLabel"
                            val finalName = if (instanceName.isNotBlank()) instanceName else defaultName
                            onInstanceCreated(
                                Instance(
                                    id = "inst_${UUID.randomUUID()}",
                                    name = finalName,
                                    mcVersion = selectedVersion,
                                    loader = selectedLoader,
                                    loaderVersion = loaderVersionNow,
                                    icon = if (selectedLoader == "fabric") "fabric" else "crafting_table"
                                )
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        style = AmberButtonStyle.Primary,
                        enabled = canCreate,
                        height = 46.dp
                    )
                }

                // Garis pemisah tipis antar panel
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(Amber.Line)
                )

                // ============================================================ kanan: daftar versi
                Column(
                    modifier = Modifier
                        .weight(1.15f)
                        .fillMaxHeight()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        VersionCategory.values().forEach { category ->
                            AiChip(
                                label = category.label,
                                selected = selectedCategory == category,
                                onClick = { selectedCategory = category }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    AiField(
                        value = searchQuery,
                        onChange = { searchQuery = it },
                        placeholder = "Cari versi (${selectedCategory.label})...",
                        searchIcon = true,
                        onClear = { searchQuery = "" }
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AiLabel("DAFTAR VERSI")
                        Text(
                            text = "${filteredVersions.size} versi ditemukan",
                            color = Amber.TextDim,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    val listShape = RoundedCornerShape(12.dp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(listShape)
                            .background(Amber.Fill)
                            .border(1.dp, Amber.Line, listShape)
                    ) {
                        if (isLoadingVersions) {
                            AiCenterNote("Memuat manifest Mojang...")
                        } else if (filteredVersions.isEmpty()) {
                            AiCenterNote("Tidak ada versi yang cocok")
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(4.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                items(filteredVersions, key = { it.id }) { item ->
                                    AiVersionRow(
                                        item = item,
                                        selected = item.id == selectedVersion,
                                        onClick = { selectedVersion = item.id }
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

// -------------------------------------------------------------------- bagian kecil

/** Label bagian: huruf kapital kecil, redup. */
@Composable
private fun AiLabel(text: String) {
    Text(
        text = text,
        color = Amber.TextDim,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        maxLines = 1
    )
}

/** Pil filter: terpilih kuning berteks tinta, selain itu kaca bertepi tipis. */
@Composable
private fun AiChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(shape)
            .background(if (selected) Amber.Yellow else Amber.Fill)
            .border(1.dp, if (selected) Amber.Yellow else Amber.Line, shape)
            .amberTap(onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Amber.Ink else Amber.TextDim,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

/**
 * Kolom isian satu baris. [searchIcon] menambah kaca pembesar di kiri; [onClear] menampilkan tombol
 * hapus saat ada isi.
 */
@Composable
private fun AiField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    searchIcon: Boolean = false,
    onClear: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(shape)
            .background(Amber.Fill)
            .border(1.dp, Amber.LineStrong, shape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (searchIcon) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .drawBehind {
                        val s = size.minDimension
                        val sw = s * 0.12f
                        drawCircle(
                            color = Amber.TextDim,
                            radius = s * 0.30f,
                            center = Offset(s * 0.42f, s * 0.42f),
                            style = Stroke(width = sw)
                        )
                        drawLine(
                            color = Amber.TextDim,
                            start = Offset(s * 0.64f, s * 0.64f),
                            end = Offset(s * 0.90f, s * 0.90f),
                            strokeWidth = sw,
                            cap = StrokeCap.Round
                        )
                    }
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(
                color = Amber.Text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            ),
            cursorBrush = SolidColor(Amber.Yellow),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = Amber.TextFaint,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    inner()
                }
            }
        )
        if (onClear != null && value.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .amberTap(onClear),
                contentAlignment = Alignment.Center
            ) {
                AmberIcon("close", 16.dp, Amber.TextDim)
            }
        }
    }
}

/** Tanda centang kecil yang digambar langsung. */
@Composable
private fun AiCheck(color: Color, boxSize: Dp = 16.dp) {
    Box(
        modifier = Modifier
            .size(boxSize)
            .drawBehind {
                val s = size.minDimension
                val sw = s * 0.14f
                drawLine(color, Offset(s * 0.20f, s * 0.54f), Offset(s * 0.42f, s * 0.76f), sw, StrokeCap.Round)
                drawLine(color, Offset(s * 0.42f, s * 0.76f), Offset(s * 0.82f, s * 0.28f), sw, StrokeCap.Round)
            }
    )
}

/** Teks tengah untuk keadaan memuat atau kosong (tanpa indikator berputar). */
@Composable
private fun AiCenterNote(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = Amber.TextDim,
            fontSize = 13.sp,
            maxLines = 2,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

/** Warna dan teks lencana jenis versi (kode hijau/kuning hanya untuk rilis dan snapshot). */
private fun typeBadge(item: VersionItem): Pair<String, Color> = when {
    item.type == "release" -> "RELEASE" to Amber.Good
    item.type == "snapshot" -> "SNAPSHOT" to Amber.Yellow
    item.type == "old_beta" || item.id.startsWith("b") -> "BETA" to Amber.TextDim
    item.type == "old_alpha" || item.id.startsWith("a") -> "ALPHA" to Amber.TextDim
    else -> item.type.uppercase() to Amber.TextDim
}

/** Satu baris versi Minecraft di daftar kanan. */
@Composable
private fun AiVersionRow(item: VersionItem, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    val (badgeText, badgeColor) = typeBadge(item)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(shape)
            .background(if (selected) Amber.YellowTint else Color.Transparent)
            .amberTap(onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.id,
            color = if (selected) Amber.Yellow else Amber.Text,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(badgeColor.copy(alpha = 0.16f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = badgeText,
                color = badgeColor,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        if (selected) AiCheck(Amber.Yellow, 18.dp)
    }
}

/**
 * Pemilih versi loader (Fabric/Forge/NeoForge): baris ringkas yang membuka daftar di tempat. Saat memuat atau
 * kosong barisnya hanya menampilkan keterangan dan tidak bisa dibuka.
 */
@Composable
private fun AiLoaderPicker(
    prefix: String,
    loading: Boolean,
    loadingText: String,
    emptyText: String,
    choices: List<LoaderChoice>,
    selectedId: String,
    open: Boolean,
    onToggle: () -> Unit,
    onPick: (String) -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    val interactive = !loading && choices.isNotEmpty()
    val current = choices.firstOrNull { it.id == selectedId }

    Column(modifier = Modifier.fillMaxWidth()) {
        val frame = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(shape)
            .background(Amber.Fill)
            .border(1.dp, Amber.LineStrong, shape)
        val head = if (interactive) frame.amberTap(onToggle) else frame
        Row(
            modifier = head.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when {
                loading -> Text(
                    text = loadingText,
                    color = Amber.TextDim,
                    fontSize = 13.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                choices.isEmpty() -> Text(
                    text = emptyText,
                    color = Amber.DangerSoft,
                    fontSize = 12.5.sp,
                    maxLines = 2,
                    modifier = Modifier.weight(1f)
                )
                else -> {
                    Text(
                        text = "$prefix $selectedId",
                        color = Amber.Text,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (current != null && current.tag.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        AiTag(current.tag)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    AmberIcon(if (open) "chev_up" else "chev_down", 18.dp, Amber.TextDim)
                }
            }
        }

        if (open && interactive) {
            Column(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .heightIn(max = 164.dp)
                    .clip(shape)
                    .background(Amber.Fill)
                    .border(1.dp, Amber.Line, shape)
                    .verticalScroll(rememberScrollState())
                    .padding(4.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                choices.forEach { choice ->
                    val picked = choice.id == selectedId
                    val rowShape = RoundedCornerShape(8.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .clip(rowShape)
                            .background(if (picked) Amber.YellowTint else Color.Transparent)
                            .amberTap { onPick(choice.id) }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = choice.id,
                            color = if (picked) Amber.Yellow else Amber.Text,
                            fontSize = 13.5.sp,
                            fontWeight = if (picked) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1
                        )
                        if (choice.tag.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            AiTag(choice.tag)
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        if (picked) AiCheck(Amber.Yellow, 16.dp)
                    }
                }
            }
        }
    }
}

/** Lencana kecil (Disarankan / Terbaru). */
@Composable
private fun AiTag(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Amber.YellowTint)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = Amber.Yellow,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/** Ringkasan pilihan: versi Minecraft, loader dan versinya. */
@Composable
private fun AiSummary(mcVersion: String, loaderLabel: String, loaderVersion: String) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Amber.YellowTint)
            .border(1.dp, Amber.Line, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "SPESIFIKASI TARGET",
                color = Amber.Yellow,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "Minecraft $mcVersion",
                color = Amber.Text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (loaderVersion.isNotBlank()) {
                Text(
                    text = "$loaderLabel $loaderVersion",
                    color = Amber.TextDim,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Amber.Yellow)
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Text(
                text = loaderLabel.uppercase(),
                color = Amber.Ink,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1
            )
        }
    }
}
