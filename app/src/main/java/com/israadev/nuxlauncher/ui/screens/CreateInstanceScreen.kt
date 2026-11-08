package com.israadev.nuxlauncher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.core.fabric.FabricService
import com.israadev.nuxlauncher.core.forge.ForgeFlavor
import com.israadev.nuxlauncher.core.forge.ForgeService
import com.israadev.nuxlauncher.core.horizon.HorizonEngine
import com.israadev.nuxlauncher.core.manifest.MojangManifestService
import com.israadev.nuxlauncher.core.models.Instance
import com.israadev.nuxlauncher.core.models.VersionItem
import com.israadev.nuxlauncher.ui.i18n.Text
import com.israadev.nuxlauncher.ui.theme.AmberUi
import com.israadev.nuxlauncher.ui.theme.amberGlass
import java.util.UUID
import kotlinx.coroutines.launch

/** Filter jenis versi Minecraft di layar Buat Instance. */
enum class VersionCategory(val label: String) {
    RELEASE("Release"),
    SNAPSHOT("Snapshot"),
    OLD_BETA("Old Beta"),
    OLD_ALPHA("Old Alpha"),
    ALL("Semua")
}

/** Satu loader di rel kiri. */
private class CiLoader(val id: String, val label: String, val icon: ImageVector)

private val CI_LOADERS = listOf(
    CiLoader("fabric", "Fabric", Icons.Outlined.Layers),
    CiLoader("forge", "Forge", Icons.Outlined.Extension),
    CiLoader("neoforge", "NeoForge", Icons.Outlined.Memory),
    CiLoader(HorizonEngine.LOADER_ID, HorizonEngine.DISPLAY_NAME, Icons.Outlined.ViewInAr),
    CiLoader("vanilla", "Vanilla", Icons.Outlined.Public)
)

/**
 * Kartu versi untuk mesin Rust bila daftar Mojang belum atau gagal dimuat: mesinnya hanya mengerjakan satu
 * versi dan tidak butuh daftar itu. releaseTime kosong membuat kartu menampilkan nama mesin, bukan tanggal.
 */
private val CI_ENGINE_VERSION = VersionItem(
    id = HorizonEngine.SUPPORTED_MC_VERSION,
    type = "release",
    url = "",
    time = "",
    releaseTime = ""
)

/** Satu baris versi loader (Fabric/Forge/NeoForge): [tag] kosong bila tidak ada keterangan. */
private class CiChoice(val id: String, val tag: String)

private val CI_RELEASE_COLOR = Color(0xFF5FD38D)
private val CI_SNAPSHOT_COLOR = Color(0xFFF2B84B)

/**
 * Layar penuh "Buat Instance", polanya sama dengan layar Mod: rel loader di kiri (Fabric, Forge,
 * NeoForge, Vanilla), daftar versi Minecraft yang didukung loader itu di kanan. Mengetuk sebuah
 * versi membuka daftar versi loader tepat di bawah kartunya; loader terbaru/disarankan terpilih
 * otomatis, tapi bisa diganti (mis. loader versi lama). Nama instance dan tombol Install ada di
 * panel atas.
 */
@Composable
fun CreateInstanceScreen(
    onNavigateBack: () -> Unit,
    onInstanceCreated: (Instance) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var selectedLoader by remember { mutableStateOf("fabric") }
    var instanceName by remember { mutableStateOf("") }
    var selectedVersion by remember { mutableStateOf<String?>(null) }
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(VersionCategory.RELEASE) }

    var versionsList by remember { mutableStateOf<List<VersionItem>>(emptyList()) }
    var isLoadingVersions by remember { mutableStateOf(true) }
    var manifestError by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableStateOf(0) }

    // Versi Minecraft yang punya loader terpilih. null = belum ada / gagal dimuat (pakai perkiraan).
    var supported by remember { mutableStateOf<Set<String>?>(null) }
    var supportedKey by remember { mutableStateOf("") }

    // Versi loader untuk versi Minecraft terpilih; loaderLoadedKey = "<loader>:<mc>" saat sudah selesai dimuat.
    var loaderChoices by remember { mutableStateOf<List<CiChoice>>(emptyList()) }
    var loaderVersion by remember { mutableStateOf("") }
    var loaderLoadedKey by remember { mutableStateOf("") }

    LaunchedEffect(reloadTick) {
        isLoadingVersions = true
        manifestError = false
        val manifest = MojangManifestService.getVersionManifest().getOrNull()
        if (manifest != null) {
            versionsList = manifest.versions
        } else {
            manifestError = true
        }
        isLoadingVersions = false
    }

    LaunchedEffect(selectedLoader) {
        supported = null
        supportedKey = ""
        val result = when (selectedLoader) {
            "fabric" -> FabricService.getSupportedGameVersions()
            "forge" -> ForgeService.getSupportedMcVersions(ForgeFlavor.FORGE)
            "neoforge" -> ForgeService.getSupportedMcVersions(ForgeFlavor.NEOFORGE)
            else -> null
        }
        supported = result?.getOrNull()
        supportedKey = selectedLoader
    }

    LaunchedEffect(selectedVersion, selectedLoader) {
        loaderChoices = emptyList()
        loaderVersion = ""
        loaderLoadedKey = ""
        val mc = selectedVersion
        if (mc == null || selectedLoader == "vanilla" || selectedLoader == HorizonEngine.LOADER_ID) return@LaunchedEffect
        if (selectedLoader == "fabric") {
            val loaders = FabricService.getFabricLoaders(mc).getOrNull() ?: emptyList()
            loaderChoices = loaders.take(40).mapIndexed { index, v ->
                CiChoice(v, if (index == 0) "Terbaru" else "")
            }
            loaderVersion = loaders.firstOrNull() ?: ""
        } else {
            val flavor = ForgeFlavor.of(selectedLoader)
            if (flavor != null) {
                val builds = ForgeService.getBuilds(mc, flavor).getOrNull() ?: emptyList()
                loaderChoices = builds.take(40).map { build ->
                    CiChoice(
                        build.version,
                        when {
                            build.recommended -> "Disarankan"
                            build.latest -> "Terbaru"
                            else -> ""
                        }
                    )
                }
                loaderVersion = ForgeService.pickDefault(builds)?.version ?: ""
            }
        }
        loaderLoadedKey = "$selectedLoader:$mc"
    }

    val isVanilla = selectedLoader == "vanilla"
    val isEngine = selectedLoader == HorizonEngine.LOADER_ID
    // Vanilla dan mesin Rust tidak punya versi loader untuk dipilih.
    val noLoaderVersions = isVanilla || isEngine
    val loaderInfo = CI_LOADERS.firstOrNull { it.id == selectedLoader } ?: CI_LOADERS.first()
    val flavor = ForgeFlavor.of(selectedLoader)

    // Vanilla punya semua jenis; loader hanya rilis dan snapshot; mesin Rust hanya satu versi, tanpa penyaring.
    val categories = when {
        isEngine -> listOf(VersionCategory.ALL)
        isVanilla -> VersionCategory.values().toList()
        else -> listOf(VersionCategory.RELEASE, VersionCategory.SNAPSHOT, VersionCategory.ALL)
    }
    val effectiveCategory = if (category in categories) category else categories.first()

    // Perkiraan bila daftar resmi gagal dimuat: Fabric mulai Minecraft 1.14; Forge/NeoForge sesuai batas Amber.
    val fabricMinTime = remember(versionsList) { versionsList.firstOrNull { it.id == "1.14" }?.releaseTime ?: "" }
    val supportedNow = supported
    val visibleVersions = remember(versionsList, supportedNow, selectedLoader, effectiveCategory, query) {
        val q = query.trim()
        versionsList.filter { item ->
            val matchesCategory = when (effectiveCategory) {
                VersionCategory.ALL -> true
                VersionCategory.RELEASE -> item.type == "release"
                VersionCategory.SNAPSHOT -> item.type == "snapshot" || item.type == "pending" || item.type == "unobfuscated"
                VersionCategory.OLD_BETA -> item.type == "old_beta" || item.id.startsWith("b")
                VersionCategory.OLD_ALPHA -> item.type == "old_alpha" || item.id.startsWith("a")
            }
            val matchesQuery = q.isEmpty() || item.id.contains(q, ignoreCase = true)
            val matchesLoader = when {
                selectedLoader == "vanilla" -> true
                supportedNow != null -> supportedNow.contains(item.id)
                flavor != null -> ForgeService.isMcSupported(item.id, flavor)
                else -> item.type != "old_beta" && item.type != "old_alpha" &&
                    (fabricMinTime.isEmpty() || item.releaseTime >= fabricMinTime)
            }
            matchesCategory && matchesQuery && matchesLoader
        }
    }

    // Mesin Rust hanya mengerjakan satu versi dan tidak butuh daftar Mojang: kartunya tetap tampil walau daftar
    // itu belum atau gagal dimuat.
    val shownVersions = if (isEngine) {
        listOf(versionsList.firstOrNull { it.id == HorizonEngine.SUPPORTED_MC_VERSION } ?: CI_ENGINE_VERSION)
    } else {
        visibleVersions
    }

    val supportedPending = !noLoaderVersions && supportedKey != selectedLoader
    val listLoading = !isEngine && (isLoadingVersions || supportedPending)

    val currentKey = if (selectedVersion != null) "$selectedLoader:$selectedVersion" else ""
    val loaderPending = !noLoaderVersions && selectedVersion != null && loaderLoadedKey != currentKey
    val chosenMc = selectedVersion

    // Mesin Rust: build ini harus membawa mesinnya dan HP-nya harus mampu. Alasan bila tidak, tampil di CiEngineNotice.
    val engineStatus = remember(isEngine) { if (isEngine) HorizonEngine.availability(context) else null }
    val engineBlocked = engineStatus is HorizonEngine.Availability.Unavailable
    val canInstall = chosenMc != null && !engineBlocked &&
        (noLoaderVersions || (!loaderPending && loaderVersion.isNotBlank()))
    val defaultName = if (chosenMc != null) "$chosenMc ${loaderInfo.label}" else ""

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 10.dp)
    ) {
        // =========================================================================
        // BARIS ATAS: navigasi | ringkasan pilihan + nama instance + tombol Install
        // =========================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CiNavPill(onBack = onNavigateBack)

            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(CircleShape)
                    .amberGlass(CircleShape)
                    .padding(start = 18.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.widthIn(min = 96.dp, max = 190.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (chosenMc != null) "$chosenMc ${loaderInfo.label}" else "Pilih versi",
                        color = if (chosenMc != null) AmberUi.Text else AmberUi.TextDim,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (chosenMc != null && !noLoaderVersions && loaderVersion.isNotBlank()) {
                        Text(
                            text = "${loaderInfo.label} $loaderVersion",
                            color = AmberUi.AccentSoft,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .width(1.dp)
                        .height(24.dp)
                        .background(AmberUi.Border)
                )
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (instanceName.isEmpty()) {
                        Text(
                            text = if (defaultName.isNotEmpty()) defaultName else "Nama instance",
                            color = AmberUi.TextDim,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = instanceName,
                        onValueChange = { instanceName = it.take(48) },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = AmberUi.Text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(AmberUi.AccentSoft),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                AmberPillButton(
                    text = "Install",
                    icon = Icons.Outlined.Download,
                    enabled = canInstall,
                    onClick = {
                        val mc = selectedVersion
                        if (mc != null) {
                            val label = loaderInfo.label
                            onInstanceCreated(
                                Instance(
                                    id = "inst_${UUID.randomUUID()}",
                                    name = instanceName.trim().ifBlank { "$mc $label" },
                                    mcVersion = mc,
                                    loader = selectedLoader,
                                    loaderVersion = if (noLoaderVersions) "" else loaderVersion,
                                    icon = if (selectedLoader == "fabric") "fabric" else "crafting_table",
                                    // Mesin Rust tidak mengunduh apa pun lewat launcher (sumber dayanya diunduh
                                    // mesinnya sendiri): langsung siap dimainkan.
                                    isDownloaded = isEngine
                                )
                            )
                        }
                    }
                )
            }
        }

        // =========================================================================
        // ISI: rel loader di kiri, filter + daftar versi di kanan
        // =========================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .width(148.dp)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                CI_LOADERS.forEach { loader ->
                    AmberSideItem(
                        icon = loader.icon,
                        label = loader.label,
                        selected = selectedLoader == loader.id,
                        onClick = {
                            if (selectedLoader != loader.id) {
                                selectedLoader = loader.id
                                // Versi sebelumnya belum tentu didukung loader baru: pilih ulang. Mesin Rust
                                // hanya punya satu versi, jadi langsung terpilih.
                                selectedVersion = if (loader.id == HorizonEngine.LOADER_ID) {
                                    HorizonEngine.SUPPORTED_MC_VERSION
                                } else {
                                    null
                                }
                                expanded = false
                                scope.launch { listState.scrollToItem(0) }
                            }
                        }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // Jenis versi + pencarian. Mesin Rust hanya punya satu versi, jadi keduanya tidak perlu.
                if (!isEngine) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            categories.forEach { entry ->
                                CiChip(
                                    label = entry.label,
                                    selected = effectiveCategory == entry,
                                    onClick = { category = entry }
                                )
                            }
                        }
                        AmberSearchPill(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = "Cari versi...",
                            modifier = Modifier.width(200.dp)
                        )
                    }
                }

                when {
                    listLoading -> CiNote(
                        text = if (isLoadingVersions) "Memuat versi Minecraft..." else "Memuat versi yang didukung ${loaderInfo.label}..."
                    )
                    manifestError && !isEngine -> CiNote(
                        text = "Gagal memuat daftar versi Minecraft",
                        actionLabel = "Coba lagi",
                        onAction = { reloadTick += 1 }
                    )
                    shownVersions.isEmpty() -> CiNote(text = "Tidak ada versi yang cocok")
                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 12.dp)
                    ) {
                        if (isEngine) {
                            item(key = "engine-notice") { CiEngineNotice(status = engineStatus) }
                        }
                        items(shownVersions, key = { it.id }) { item ->
                            val isSelected = item.id == selectedVersion
                            CiVersionCard(
                                item = item,
                                loaderLabel = loaderInfo.label,
                                loaderIcon = loaderInfo.icon,
                                isVanilla = noLoaderVersions,
                                selected = isSelected,
                                expanded = isSelected && expanded,
                                loading = isSelected && loaderPending,
                                choices = if (isSelected) loaderChoices else emptyList(),
                                chosenId = if (isSelected) loaderVersion else "",
                                onClick = {
                                    if (isSelected) {
                                        expanded = !expanded
                                    } else {
                                        selectedVersion = item.id
                                        expanded = true
                                        scope.launch {
                                            val index = shownVersions.indexOfFirst { it.id == item.id }
                                            if (index >= 0) listState.animateScrollToItem(index)
                                        }
                                    }
                                },
                                onPick = { loaderVersion = it }
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------- bagian kecil

/** Pil navigasi kiri atas: panah kembali ke Home dan penanda layar ini. */
@Composable
private fun CiNavPill(onBack: () -> Unit) {
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
            description = "Kembali ke Home",
            onClick = onBack
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
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                tint = AmberUi.Text,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Buat Instance",
                color = AmberUi.Text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1
            )
        }
    }
}

/** Pil filter jenis versi: terpilih berisi warna aksen, selain itu kaca. */
@Composable
private fun CiChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(40.dp)
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
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = AmberUi.Text,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = 13.sp,
            maxLines = 1
        )
    }
}

/** Teks tengah untuk keadaan memuat, kosong, atau gagal (tanpa indikator berputar). */
@Composable
private fun ColumnScope.CiNote(
    text: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            color = AmberUi.TextDim,
            fontSize = 14.sp,
            maxLines = 2,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        if (actionLabel != null) {
            Spacer(modifier = Modifier.height(12.dp))
            AmberPillButton(text = actionLabel, onClick = onAction)
        }
    }
}

/**
 * Keterangan mesin Rust di atas kartu versi: apa bedanya dengan loader lain, batas versinya, dan alasan bila
 * mesinnya belum bisa dipakai di build atau HP ini. Isi teksnya persis sama dengan kunci di
 * assets/lang/<bahasa>/horizon.tsv supaya ikut diterjemahkan.
 */
@Composable
private fun CiEngineNotice(status: HorizonEngine.Availability?) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .amberGlass(shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "MESIN RUST (HORIZON)",
            color = AmberUi.TextDim,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            maxLines = 1
        )
        Text(
            text = "Mesin Rust menggantikan seluruh client Java Minecraft: tanpa Java, tanpa mod Fabric, Forge, atau NeoForge.",
            color = AmberUi.Text,
            fontSize = 13.sp,
            lineHeight = 18.sp
        )
        Text(
            text = "Hanya mendukung Minecraft ${HorizonEngine.SUPPORTED_MC_VERSION}. Sumber daya game (sekitar 500 MiB) diunduh mesinnya sendiri saat pertama dibuka.",
            color = AmberUi.TextDim,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
        Text(
            text = if (android.os.Process.is64Bit()) {
                "Eksperimental: butuh Android 9 ke atas dan HP 64-bit."
            } else {
                "Eksperimental di HP 32-bit: butuh Android 9 ke atas dan bisa lebih lambat."
            },
            color = AmberUi.TextDim,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
        if (status is HorizonEngine.Availability.Unavailable) {
            Text(
                text = status.reason,
                color = AmberUi.Danger,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 18.sp
            )
        } else if (status is HorizonEngine.Availability.Ready && status.lowRam) {
            Text(
                text = HorizonEngine.LOW_RAM_WARNING,
                color = CI_SNAPSHOT_COLOR,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
        }
    }
}

/** Lencana kecil (jenis versi, Terbaru, Disarankan). */
@Composable
private fun CiTag(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/** Teks dan warna lencana jenis versi. */
private fun ciTypeBadge(item: VersionItem): Pair<String, Color> = when {
    item.type == "release" -> "RELEASE" to CI_RELEASE_COLOR
    item.type == "snapshot" -> "SNAPSHOT" to CI_SNAPSHOT_COLOR
    item.type == "old_beta" || item.id.startsWith("b") -> "BETA" to AmberUi.TextDim
    item.type == "old_alpha" || item.id.startsWith("a") -> "ALPHA" to AmberUi.TextDim
    else -> item.type.uppercase() to AmberUi.TextDim
}

/**
 * Kartu satu versi Minecraft. Saat terpilih dan terbuka (bukan Vanilla), di bawahnya muncul daftar
 * versi loader: yang terbaru/disarankan sudah terpilih, dan bisa diganti ke versi lain.
 */
@Composable
private fun CiVersionCard(
    item: VersionItem,
    loaderLabel: String,
    loaderIcon: ImageVector,
    isVanilla: Boolean,
    selected: Boolean,
    expanded: Boolean,
    loading: Boolean,
    choices: List<CiChoice>,
    chosenId: String,
    onClick: () -> Unit,
    onPick: (String) -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val iconShape = RoundedCornerShape(14.dp)
    val (badgeText, badgeColor) = ciTypeBadge(item)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .amberGlass(shape)
            .then(if (selected) Modifier.border(1.5.dp, AmberUi.AccentEdge, shape) else Modifier)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(iconShape)
                    .background(if (selected) AmberUi.Accent else AmberUi.SurfaceRaised, iconShape)
                    .border(1.dp, AmberUi.Border, iconShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = loaderIcon,
                    contentDescription = null,
                    tint = if (selected) Color.White else AmberUi.AccentSoft,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.id,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = AmberUi.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    CiTag(badgeText, badgeColor)
                }
                if (selected && !isVanilla && chosenId.isNotBlank()) {
                    Text(
                        text = "$loaderLabel $chosenId",
                        fontSize = 13.sp,
                        color = AmberUi.AccentSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = if (item.releaseTime.isBlank()) loaderLabel else "Dirilis ${item.releaseTime.take(10)}",
                        fontSize = 13.sp,
                        color = AmberUi.TextDim,
                        maxLines = 1
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Icon(
                imageVector = when {
                    selected && isVanilla -> Icons.Outlined.Check
                    expanded -> Icons.Outlined.KeyboardArrowDown
                    else -> Icons.Outlined.ChevronRight
                },
                contentDescription = null,
                tint = if (selected) AmberUi.AccentSoft else AmberUi.TextDim,
                modifier = Modifier.size(22.dp)
            )
        }

        if (expanded && !isVanilla) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(AmberUi.Border)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 14.dp)
            ) {
                Text(
                    text = "LOADER ${loaderLabel.uppercase()}",
                    color = AmberUi.TextDim,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(8.dp))
                when {
                    loading -> Text(
                        text = "Memuat loader $loaderLabel...",
                        color = AmberUi.TextDim,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                    choices.isEmpty() -> Text(
                        text = "Loader $loaderLabel belum tersedia untuk Minecraft ${item.id}",
                        color = AmberUi.Danger,
                        fontSize = 13.sp,
                        maxLines = 2
                    )
                    else -> {
                        Text(
                            text = "Versi terbaru dipilih otomatis. Ketuk versi lain bila butuh loader yang lebih lama.",
                            color = AmberUi.TextDim,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 190.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            choices.forEach { choice ->
                                CiLoaderRow(
                                    choice = choice,
                                    picked = choice.id == chosenId,
                                    onClick = { onPick(choice.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Satu baris versi loader di dalam kartu versi. Terpilih = isian aksen + centang. */
@Composable
private fun CiLoaderRow(choice: CiChoice, picked: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(shape)
            .background(if (picked) AmberUi.Accent else AmberUi.SurfaceRaised, shape)
            .border(1.dp, if (picked) AmberUi.AccentEdge else AmberUi.Border, shape)
            .clickable { onClick() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = choice.id,
            color = AmberUi.Text,
            fontSize = 14.sp,
            fontWeight = if (picked) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (choice.tag.isNotEmpty()) {
            Spacer(modifier = Modifier.width(8.dp))
            CiTag(choice.tag, if (picked) Color.White else AmberUi.AccentSoft)
        }
        Spacer(modifier = Modifier.weight(1f))
        if (picked) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
