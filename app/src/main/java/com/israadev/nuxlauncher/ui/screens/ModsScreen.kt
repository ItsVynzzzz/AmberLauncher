package com.israadev.nuxlauncher.ui.screens

import androidx.compose.ui.res.painterResource
import com.israadev.nuxlauncher.ui.theme.amberGlass
import android.net.Uri
import android.provider.OpenableColumns
import com.israadev.nuxlauncher.ui.i18n.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import com.israadev.nuxlauncher.ui.i18n.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.israadev.nuxlauncher.core.instance.InstanceManager
import com.israadev.nuxlauncher.core.models.*
import com.israadev.nuxlauncher.core.mods.NuxModManager
import com.israadev.nuxlauncher.core.mods.WorldInfo
import com.israadev.nuxlauncher.ui.components.*
import com.israadev.nuxlauncher.ui.theme.AmberUi
import com.israadev.nuxlauncher.ui.theme.NuxColors
import com.israadev.nuxlauncher.ui.theme.NuxSizes
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import com.israadev.nuxlauncher.ui.components.AmberSpinner
import com.israadev.nuxlauncher.ui.components.AmberWaitBar

private fun getMimeTypesForType(type: String): Array<String> {
    return when (type) {
        "mods" -> arrayOf(
            "application/java-archive",
            "application/x-java-archive",
            "application/x-jar",
            "application/jar",
            "application/octet-stream"
        )
        "modpacks" -> arrayOf(
            "application/zip",
            "application/x-zip-compressed",
            "application/octet-stream"
        )
        else -> arrayOf(
            "application/zip",
            "application/x-zip-compressed"
        )
    }
}

private fun isValidExtensionForType(itemType: String, filename: String): Boolean {
    val lower = filename.lowercase()
    return when (itemType) {
        "mods" -> lower.endsWith(".jar")
        "modpacks" -> lower.endsWith(".mrpack") || lower.endsWith(".zip")
        "shaderpacks", "resourcepacks", "datapacks" -> lower.endsWith(".zip")
        else -> false
    }
}

private fun getAllowedExtensionLabel(itemType: String): String {
    return when (itemType) {
        "mods" -> ".jar"
        "modpacks" -> ".mrpack, .zip"
        "shaderpacks" -> ".zip (Shaderpack)"
        "resourcepacks" -> ".zip (Resource Pack)"
        "datapacks" -> ".zip (Datapack)"
        else -> "format yang didukung"
    }
}

private fun getFileNameFromUri(context: android.content.Context, uri: Uri): String {
    var name: String? = null
    if (uri.scheme == "content") {
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex)
                }
            }
        } catch (_: Exception) {}
    }
    if (name.isNullOrBlank()) {
        name = uri.lastPathSegment?.substringAfterLast('/')
    }
    return name ?: "unknown_file"
}

data class ModCategoryItem(val id: String, val label: String)

private val CATEGORIES_MAP = mapOf(
    "mods" to listOf(
        ModCategoryItem("Semua", "Semua"),
        ModCategoryItem("optimization", "⚡ Optimization"),
        ModCategoryItem("technology", "⚙️ Technology"),
        ModCategoryItem("magic", "🪄 Magic"),
        ModCategoryItem("adventure", "🗺️ Adventure"),
        ModCategoryItem("utility", "🛠️ Utility"),
        ModCategoryItem("decoration", "🎨 Decoration")
    ),
    "modpacks" to listOf(
        ModCategoryItem("Semua", "Semua"),
        ModCategoryItem("adventure", "🗺️ Adventure"),
        ModCategoryItem("optimization", "⚡ Optimization"),
        ModCategoryItem("technology", "⚙️ Technology"),
        ModCategoryItem("magic", "🪄 Magic"),
        ModCategoryItem("quests", "📜 Quests")
    ),
    "resourcepacks" to listOf(
        ModCategoryItem("Semua", "Semua"),
        ModCategoryItem("16x", "16x"),
        ModCategoryItem("32x", "32x"),
        ModCategoryItem("simplistic", "✨ Simplistic"),
        ModCategoryItem("traditional", "🏛️ Traditional"),
        ModCategoryItem("medieval", "🏰 Medieval")
    ),
    "shaderpacks" to listOf(
        ModCategoryItem("Semua", "Semua"),
        ModCategoryItem("realistic", "🌅 Realistic"),
        ModCategoryItem("stylized", "🎨 Stylized"),
        ModCategoryItem("fantasy", "🔮 Fantasy")
    ),
    "datapacks" to listOf(
        ModCategoryItem("Semua", "Semua"),
        ModCategoryItem("adventure", "🗺️ Adventure"),
        ModCategoryItem("utility", "🛠️ Utility"),
        ModCategoryItem("magic", "🪄 Magic"),
        ModCategoryItem("worldgen", "🌍 Worldgen")
    )
)

/**
 * BROWSE = jelajah dan pasang dari Modrinth (tab Mod di Home).
 * MANAGE = kelola isi instance yang terpasang (dibuka dari Ubah instance).
 */
enum class ModsMode { BROWSE, MANAGE }

@Composable
fun ModsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenAccounts: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    mode: ModsMode = ModsMode.BROWSE,
    initialType: String = "mods",
    onOpenBrowse: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val instances by InstanceManager.instances.collectAsState()
    val selectedInstance by InstanceManager.selectedInstance.collectAsState()

    // Mode: "installed" (kelola isi instance) | "browse" (jelajah Modrinth)
    val mainMode = if (mode == ModsMode.MANAGE) "installed" else "browse"

    // Item Type: "mods" | "modpacks" | "resourcepacks" | "shaderpacks" | "datapacks"
    var activeType by remember { mutableStateOf(initialType) }

    // World state for Datapacks
    var worlds by remember { mutableStateOf<List<WorldInfo>>(emptyList()) }
    var selectedWorld by remember { mutableStateOf("") }
    var showWorldMenu by remember { mutableStateOf(false) }

    // Installed list state
    var installedItems by remember { mutableStateOf<List<InstalledModItem>>(emptyList()) }
    var isInstalledLoading by remember { mutableStateOf(false) }
    var installedSearch by remember { mutableStateOf("") }
    var itemToDelete by remember { mutableStateOf<InstalledModItem?>(null) }

    // Browse Modrinth state
    var browseQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Semua") }
    var searchHits by remember { mutableStateOf<List<ModrinthSearchHit>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var searchErrorMessage by remember { mutableStateOf<String?>(null) }
    var filterByGameVersion by remember { mutableStateOf(true) }
    var filterByLoader by remember { mutableStateOf(true) }
    var selectedSort by remember { mutableStateOf("relevance") }
    var showCategoryMenu by remember { mutableStateOf(false) }
    var showVersionMenu by remember { mutableStateOf(false) }
    var showLoaderMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var currentPage by remember { mutableStateOf(1) }
    var totalHits by remember { mutableStateOf(0) }
    var downloadingProjectIds by remember { mutableStateOf<Map<String, String>>(emptyMap()) } // projectId -> status

    // Detail Modal state
    var detailModalHit by remember { mutableStateOf<ModrinthSearchHit?>(null) }
    var detailProject by remember { mutableStateOf<ModrinthProject?>(null) }
    var compatibleVersions by remember { mutableStateOf<List<ModrinthVersion>>(emptyList()) }
    var selectedVersion by remember { mutableStateOf<ModrinthVersion?>(null) }
    var isDetailLoading by remember { mutableStateOf(false) }
    var isDetailInstalling by remember { mutableStateOf(false) }
    var detailProgressText by remember { mutableStateOf("") }
    var detailTab by remember { mutableStateOf("overview") } // "overview" | "versions"
    var previewGalleryUrl by remember { mutableStateOf<String?>(null) }

    // Instance switch dropdown
    var showInstanceMenu by remember { mutableStateOf(false) }

    // Load available worlds for selected instance
    fun reloadWorlds() {
        val inst = selectedInstance ?: return
        val list = NuxModManager.getInstanceWorlds(context, inst)
        worlds = list
        if (list.isNotEmpty()) {
            if (selectedWorld.isBlank() || list.none { it.folderName == selectedWorld }) {
                selectedWorld = list.first().folderName
            }
        } else {
            selectedWorld = ""
        }
    }

    // Reload installed items
    fun reloadInstalled() {
        val inst = selectedInstance ?: return
        isInstalledLoading = true
        scope.launch {
            val targetWorld = if (activeType == "datapacks") selectedWorld else null
            installedItems = NuxModManager.getInstalledItems(context, inst, activeType, targetWorld)
            isInstalledLoading = false
        }
    }

    // Search Modrinth
    fun performSearch(page: Int = 1) {
        val inst = selectedInstance ?: return
        isSearching = true
        searchErrorMessage = null
        scope.launch {
            val cat = if (selectedCategory == "Semua") null else selectedCategory
            val res = NuxModManager.searchProjects(
                query = browseQuery,
                itemType = activeType,
                instance = inst,
                category = cat,
                filterByGameVersion = filterByGameVersion,
                page = page,
                sortIndex = selectedSort,
                filterByLoader = filterByLoader
            )
            if (res.isSuccess) {
                val data = res.getOrNull()
                searchHits = data?.hits ?: emptyList()
                totalHits = data?.totalHits ?: 0
                currentPage = page
                searchErrorMessage = null
            } else {
                searchHits = emptyList()
                totalHits = 0
                searchErrorMessage = res.exceptionOrNull()?.message ?: "Gagal terhubung ke Modrinth"
            }
            isSearching = false
        }
    }

    // Trigger reload when tab, type, world or instance changes
    LaunchedEffect(selectedInstance, activeType, mainMode, selectedWorld, filterByGameVersion, filterByLoader, selectedSort) {
        if (selectedInstance != null) {
            reloadWorlds()
            reloadInstalled()
            if (mainMode == "browse") {
                performSearch(1)
            }
        }
    }

    // Debounce search query
    var searchJob by remember { mutableStateOf<Job?>(null) }
    fun onQueryChanged(newQuery: String) {
        browseQuery = newQuery
        searchJob?.cancel()
        searchJob = scope.launch {
            delay(300)
            currentPage = 1
            performSearch(1)
        }
    }

    var isImportingModpack by remember { mutableStateOf(false) }
    var modpackImportStatus by remember { mutableStateOf("") }
    var modpackImportCurrent by remember { mutableStateOf(0) }
    var modpackImportTotal by remember { mutableStateOf(0) }
    var modpackImportName by remember { mutableStateOf("") }

    // File Picker for local addon import with strict extension filtering and modpack extraction
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri>? ->
        if (!uris.isNullOrEmpty() && selectedInstance != null) {
            val inst = selectedInstance!!
            scope.launch {
                val targetWorld = if (activeType == "datapacks") selectedWorld else null

                // 1. Filter file berdasarkan ekstensi yang di-support
                val validUris = mutableListOf<Pair<Uri, String>>()
                val rejectedNames = mutableListOf<String>()

                for (uri in uris) {
                    val fileName = getFileNameFromUri(context, uri)
                    if (isValidExtensionForType(activeType, fileName)) {
                        validUris.add(Pair(uri, fileName))
                    } else {
                        rejectedNames.add(fileName)
                    }
                }

                if (rejectedNames.isNotEmpty()) {
                    val hint = getAllowedExtensionLabel(activeType)
                    val msg = if (rejectedNames.size == 1) {
                        "File \"${rejectedNames.first()}\" diabaikan! Format yang didukung untuk $activeType hanya $hint"
                    } else {
                        "${rejectedNames.size} file diabaikan karena format tidak didukung ($hint)!"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                }

                if (validUris.isEmpty()) return@launch

                // 2. Import file yang valid
                if (activeType == "modpacks") {
                    isImportingModpack = true
                    var successCount = 0
                    var lastError: String? = null

                    for ((uri, fileName) in validUris) {
                        modpackImportName = fileName
                        modpackImportStatus = "Membaca arsip modpack..."
                        modpackImportCurrent = 0
                        modpackImportTotal = 0

                        val importRes = NuxModManager.importLocalFile(
                            context = context,
                            instance = inst,
                            itemType = activeType,
                            uri = uri,
                            worldName = targetWorld,
                            onProgress = { statusText, cur, tot ->
                                modpackImportStatus = statusText
                                modpackImportCurrent = cur
                                modpackImportTotal = tot
                            }
                        )

                        if (importRes.isSuccess) {
                            successCount++
                        } else {
                            lastError = importRes.exceptionOrNull()?.message
                        }
                    }

                    isImportingModpack = false
                    reloadInstalled()

                    if (successCount > 0) {
                        Toast.makeText(
                            context,
                            "Berhasil mengimpor dan memasang $successCount modpack!",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else if (lastError != null) {
                        Toast.makeText(
                            context,
                            "Gagal mengimpor modpack: $lastError",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    var successCount = 0
                    var lastError: String? = null

                    for ((uri, _) in validUris) {
                        val importRes = NuxModManager.importLocalFile(
                            context = context,
                            instance = inst,
                            itemType = activeType,
                            uri = uri,
                            worldName = targetWorld
                        )
                        if (importRes.isSuccess) {
                            successCount++
                        } else {
                            lastError = importRes.exceptionOrNull()?.message
                        }
                    }

                    reloadInstalled()

                    if (successCount > 0) {
                        Toast.makeText(
                            context,
                            "Berhasil mengimpor $successCount file $activeType!",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else if (lastError != null) {
                        Toast.makeText(
                            context,
                            "Gagal mengimpor file: $lastError",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    // Apakah hasil pencarian ini sudah terpasang di instance aktif
    fun isHitInstalled(hit: ModrinthSearchHit): Boolean = installedItems.any { inst ->
        val titleMatches = inst.name.equals(hit.title, ignoreCase = true)
        val slugMatches = !hit.slug.isNullOrBlank() && inst.filename.contains(hit.slug, ignoreCase = true)
        val idMatches = inst.id.equals(hit.projectId, ignoreCase = true)
        titleMatches || slugMatches || idMatches
    }

    // Buka jendela detail untuk satu proyek Modrinth
    fun openDetail(hit: ModrinthSearchHit) {
        val inst = selectedInstance ?: return
        detailModalHit = hit
        detailProject = null
        detailTab = "overview"
        compatibleVersions = emptyList()
        selectedVersion = null
        isDetailLoading = true
        scope.launch {
            val pRes = NuxModManager.getProjectDetails(hit.projectId)
            val vRes = NuxModManager.getCompatibleVersions(hit.projectId, inst, activeType)
            detailProject = pRes.getOrNull()
            val vers = vRes.getOrNull() ?: emptyList()
            compatibleVersions = vers
            selectedVersion = vers.firstOrNull()
            isDetailLoading = false
        }
    }

    // Pasang cepat dari daftar (versi paling cocok + dependensi)
    fun quickInstall(hit: ModrinthSearchHit) {
        val inst = selectedInstance ?: return
        scope.launch {
            downloadingProjectIds = downloadingProjectIds + (hit.projectId to "Mencari versi...")
            val vRes = NuxModManager.getCompatibleVersions(hit.projectId, inst, activeType)
            val compList = vRes.getOrNull()
            if (compList.isNullOrEmpty()) {
                Toast.makeText(context, "Tidak ada versi cocok untuk ${inst.mcVersion}!", Toast.LENGTH_SHORT).show()
                downloadingProjectIds = downloadingProjectIds - hit.projectId
                return@launch
            }

            val targetVer = compList.first()
            downloadingProjectIds = downloadingProjectIds + (hit.projectId to "Mengunduh...")

            val targetWorld = if (activeType == "datapacks") selectedWorld else null
            val downRes = NuxModManager.downloadVersionAndDependencies(
                context = context,
                instance = inst,
                version = targetVer,
                itemType = activeType,
                worldName = targetWorld,
                onProgress = { status ->
                    downloadingProjectIds = downloadingProjectIds + (hit.projectId to status)
                }
            )

            downloadingProjectIds = downloadingProjectIds - hit.projectId

            if (downRes.isSuccess) {
                val downloaded = downRes.getOrNull() ?: emptyList()
                val depCount = (downloaded.size - 1).coerceAtLeast(0)
                val msg = if (activeType == "modpacks") {
                    "Modpack ${hit.title} berhasil dipasang!"
                } else if (depCount > 0) {
                    "Berhasil memasang ${hit.title} beserta $depCount dependensi!"
                } else {
                    "Berhasil memasang ${hit.title}!"
                }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                reloadInstalled()
            } else {
                Toast.makeText(context, "Gagal mengunduh: ${downRes.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Pasang dari jendela detail (versi yang dipilih)
    fun installFromDetail(hit: ModrinthSearchHit) {
        val ver = selectedVersion ?: compatibleVersions.firstOrNull()
        val inst = selectedInstance
        if (ver == null || inst == null) return
        scope.launch {
            isDetailInstalling = true
            detailProgressText = "Memulai pengunduhan..."

            val targetWorld = if (activeType == "datapacks") selectedWorld else null
            val res = NuxModManager.downloadVersionAndDependencies(
                context = context,
                instance = inst,
                version = ver,
                itemType = activeType,
                worldName = targetWorld,
                onProgress = { status ->
                    detailProgressText = status
                }
            )

            isDetailInstalling = false

            if (res.isSuccess) {
                val files = res.getOrNull() ?: emptyList()
                val depCount = (files.size - 1).coerceAtLeast(0)
                val msg = if (activeType == "modpacks") {
                    "Modpack ${hit.title} berhasil dipasang!"
                } else if (depCount > 0) {
                    "Berhasil memasang ${hit.title} beserta $depCount dependensi!"
                } else {
                    "Berhasil memasang ${hit.title}!"
                }
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                reloadInstalled()
            } else {
                Toast.makeText(context, "Gagal mengunduh: ${res.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 10.dp)
    ) {
        // =========================================================================
        // BARIS ATAS: navigasi (kembali, jenis, akun, pengaturan) | sumber | pencarian
        // =========================================================================
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AmberNavPill(
                active = "mods",
                activeLabel = modsTypeShortLabel(activeType),
                activeIcon = if (mainMode == "installed") Icons.Outlined.Inventory2 else Icons.Outlined.FileDownload,
                onBack = onNavigateBack,
                onMods = {},
                onAccounts = onOpenAccounts,
                onSettings = onOpenSettings
            )
            if (mainMode == "browse") {
                ModsSourcePill(
                    curseForgeLocked = CURSEFORGE_LOCKED,
                    onCurseForgeClick = {
                        Toast.makeText(context, "CurseForge belum tersedia di versi ini", Toast.LENGTH_SHORT).show()
                    }
                )
            } else {
                ModsActionPill(
                    onAdd = { onOpenBrowse(activeType) },
                    onImport = {
                        if (selectedInstance == null) {
                            Toast.makeText(context, "Pilih instance terlebih dahulu!", Toast.LENGTH_SHORT).show()
                        } else {
                            filePicker.launch(getMimeTypesForType(activeType))
                        }
                    }
                )
            }
            AmberSearchPill(
                value = if (mainMode == "browse") browseQuery else installedSearch,
                onValueChange = { if (mainMode == "browse") onQueryChanged(it) else installedSearch = it },
                placeholder = if (mainMode == "browse") modsSearchPlaceholder(activeType) else "Cari di ${installedItems.size} item...",
                modifier = Modifier.weight(1f)
            )
        }

        if (selectedInstance == null) {
            ModsMessage(
                icon = Icons.Outlined.ExtensionOff,
                title = "Belum ada instance",
                subtitle = "Pilih atau buat instance di Home untuk mulai memasang mod.",
                actionLabel = "Kembali ke Home",
                onAction = { onNavigateBack() }
            )
            return
        }

        val currentInst = selectedInstance!!
        val categories = CATEGORIES_MAP[activeType] ?: listOf(ModCategoryItem("Semua", "Semua"))
        val selectedCategoryLabel = categories.firstOrNull { it.id == selectedCategory }?.label ?: "Semua"
        val selectedSortLabel = MODS_SORT_OPTIONS.firstOrNull { it.first == selectedSort }?.second ?: "Relevansi"

        // =========================================================================
        // ISI: rel jenis addon di kiri, filter + daftar di kanan
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
                MODS_TYPE_ITEMS.forEach { (typeId, typeLabel, typeIcon) ->
                    AmberSideItem(
                        icon = typeIcon,
                        label = typeLabel,
                        selected = activeType == typeId,
                        onClick = {
                            activeType = typeId
                            selectedCategory = "Semua"
                        }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                // Filter (bisa digeser ke samping bila layar sempit)
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    val rowMinWidth = maxWidth
                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .widthIn(min = rowMinWidth),
                        horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AmberFilterPill(
                            label = "Instance",
                            value = currentInst.name,
                            showChevron = mainMode == "browse" && instances.size > 1,
                            onClick = { if (mainMode == "browse" && instances.size > 1) showInstanceMenu = true }
                        )

                        if (activeType == "datapacks") {
                            AmberFilterPill(
                                label = "World",
                                value = if (worlds.isNotEmpty()) selectedWorld else "Tidak ada world",
                                showChevron = worlds.isNotEmpty(),
                                onClick = { if (worlds.isNotEmpty()) showWorldMenu = true }
                            )
                        }

                        if (mainMode == "browse") {
                            AmberFilterPill(
                                label = "Kategori",
                                value = modsPlainLabel(selectedCategoryLabel),
                                onClick = { showCategoryMenu = true }
                            )
                            AmberFilterPill(
                                label = "Versi",
                                value = if (filterByGameVersion) "Auto" else "Semua",
                                onClick = { showVersionMenu = true }
                            )
                            if (activeType == "mods" || activeType == "modpacks") {
                                AmberFilterPill(
                                    label = "Loader",
                                    value = if (filterByLoader) "Auto" else "Semua",
                                    onClick = { showLoaderMenu = true }
                                )
                            }
                            AmberFilterPill(
                                label = "Urutkan",
                                value = selectedSortLabel,
                                onClick = { showSortMenu = true }
                            )
                        }
                    }
                }

                // =====================================================================
                // DAFTAR
                // =====================================================================
                if (mainMode == "installed") {
                    val filteredInstalled = remember(installedItems, installedSearch) {
                        if (installedSearch.isBlank()) installedItems
                        else installedItems.filter {
                            it.name.contains(installedSearch, ignoreCase = true) ||
                                it.filename.contains(installedSearch, ignoreCase = true)
                        }
                    }

                    if (isInstalledLoading) {
                        ModsLoading()
                    } else if (filteredInstalled.isEmpty()) {
                        ModsMessage(
                            icon = Icons.Outlined.FolderOpen,
                            title = if (activeType == "datapacks" && worlds.isEmpty())
                                "Belum ada world di instance ini. Buka game untuk membuat world terlebih dahulu."
                            else if (installedSearch.isBlank())
                                "Belum ada ${activeType} terpasang di instance ini"
                            else "Tidak ada item yang cocok",
                            actionLabel = "Jelajahi Modrinth",
                            onAction = { onOpenBrowse(activeType) }
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 12.dp)
                        ) {
                            items(filteredInstalled, key = { it.id }) { item ->
                                InstalledItemRow(
                                    item = item,
                                    onToggle = {
                                        scope.launch {
                                            val targetWorld = if (activeType == "datapacks") selectedWorld else null
                                            NuxModManager.toggleItemEnabled(context, currentInst, activeType, item, targetWorld)
                                            reloadInstalled()
                                        }
                                    },
                                    onDelete = { itemToDelete = item }
                                )
                            }
                        }
                    }
                } else {
                    // ==========================================
                    // JELAJAH MODRINTH
                    // ==========================================
                    if (isSearching) {
                        ModsLoading(text = "Memuat data dari Modrinth...")
                    } else if (searchErrorMessage != null) {
                        ModsMessage(
                            icon = Icons.Outlined.CloudOff,
                            iconTint = NuxColors.ErrorRed,
                            title = "Gagal memuat dari Modrinth",
                            subtitle = searchErrorMessage ?: "Terjadi kesalahan jaringan",
                            actionLabel = "Coba lagi",
                            onAction = { performSearch(currentPage) }
                        )
                    } else if (searchHits.isEmpty()) {
                        ModsMessage(
                            icon = Icons.Outlined.SearchOff,
                            title = if (filterByGameVersion) {
                                "Tidak ada hasil cocok untuk Minecraft ${currentInst.mcVersion} (${currentInst.loader.uppercase()})"
                            } else {
                                "Tidak ada hasil ditemukan untuk kata kunci ini"
                            },
                            subtitle = "Coba ubah kata kunci atau nonaktifkan filter versi",
                            actionLabel = if (filterByGameVersion) "Cari di semua versi Minecraft" else null,
                            onAction = {
                                filterByGameVersion = false
                                currentPage = 1
                                performSearch(1)
                            }
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 12.dp)
                        ) {
                            items(searchHits, key = { it.projectId }) { hit ->
                                ModrinthHitRow(
                                    hit = hit,
                                    isInstalled = isHitInstalled(hit),
                                    isDownloading = downloadingProjectIds.containsKey(hit.projectId),
                                    downloadStatus = downloadingProjectIds[hit.projectId] ?: "",
                                    onOpen = { openDetail(hit) },
                                    onInstall = { quickInstall(hit) }
                                )
                            }

                            // Halaman
                            item {
                                val totalPages = ((totalHits + 17) / 18).coerceAtLeast(1)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AmberPillButton(
                                        text = "Sebelumnya",
                                        filled = false,
                                        enabled = currentPage > 1,
                                        onClick = { performSearch(currentPage - 1) }
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "$currentPage / $totalPages",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = AmberUi.TextDim
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    AmberPillButton(
                                        text = "Selanjutnya",
                                        filled = false,
                                        enabled = currentPage < totalPages,
                                        onClick = { performSearch(currentPage + 1) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- PILIH INSTANCE ---
    if (showInstanceMenu) {
        AmberPickerDialog(
            title = "Pilih instance",
            options = instances.map { it.id to "${it.name} · ${it.mcVersion} · ${it.loader.uppercase()}" },
            selectedId = selectedInstance?.id,
            onPick = { id ->
                instances.firstOrNull { it.id == id }?.let { InstanceManager.selectInstance(it) }
                showInstanceMenu = false
            },
            onDismiss = { showInstanceMenu = false }
        )
    }

    // --- PILIH WORLD (DATAPACK) ---
    if (showWorldMenu) {
        AmberPickerDialog(
            title = "Pilih world",
            options = worlds.map { it.folderName to it.displayName },
            selectedId = selectedWorld,
            onPick = { id ->
                selectedWorld = id
                showWorldMenu = false
                reloadInstalled()
            },
            onDismiss = { showWorldMenu = false }
        )
    }

    // --- PILIH KATEGORI / VERSI / LOADER / URUTAN ---
    if (showCategoryMenu) {
        AmberPickerDialog(
            title = "Pilih kategori",
            options = (CATEGORIES_MAP[activeType] ?: listOf(ModCategoryItem("Semua", "Semua"))).map { it.id to it.label },
            selectedId = selectedCategory,
            onPick = { id ->
                selectedCategory = id
                showCategoryMenu = false
                currentPage = 1
                performSearch(1)
            },
            onDismiss = { showCategoryMenu = false }
        )
    }
    if (showVersionMenu) {
        AmberPickerDialog(
            title = "Pilih versi",
            options = listOf(
                "auto" to "Auto (${selectedInstance?.mcVersion ?: ""})",
                "all" to "Semua versi"
            ),
            selectedId = if (filterByGameVersion) "auto" else "all",
            onPick = { id ->
                filterByGameVersion = id == "auto"
                currentPage = 1
                showVersionMenu = false
            },
            onDismiss = { showVersionMenu = false }
        )
    }
    if (showLoaderMenu) {
        AmberPickerDialog(
            title = "Pilih loader",
            options = listOf(
                "auto" to "Auto (${(selectedInstance?.loader ?: "").uppercase()})",
                "all" to "Semua loader"
            ),
            selectedId = if (filterByLoader) "auto" else "all",
            onPick = { id ->
                filterByLoader = id == "auto"
                currentPage = 1
                showLoaderMenu = false
            },
            onDismiss = { showLoaderMenu = false }
        )
    }
    if (showSortMenu) {
        AmberPickerDialog(
            title = "Urutkan",
            options = MODS_SORT_OPTIONS,
            selectedId = selectedSort,
            onPick = { id ->
                selectedSort = id
                currentPage = 1
                showSortMenu = false
            },
            onDismiss = { showSortMenu = false }
        )
    }
    // --- DIALOG HAPUS ---
    itemToDelete?.let { item ->
        NuxDialog(
            onDismissRequest = { itemToDelete = null },
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .wrapContentHeight(),
            compact = true
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(NuxColors.ErrorRed.copy(alpha = 0.18f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = NuxColors.ErrorRed,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (activeType == "modpacks") "Hapus modpack?" else "Hapus ${item.category}?",
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = NuxColors.DarkGray
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (activeType == "modpacks") {
                        "Yakin ingin menghapus modpack \"${item.name}\"? Semua mod yang terpasang dari modpack ini juga akan dihapus dari instance."
                    } else {
                        "Yakin ingin menghapus \"${item.name}\" dari instance ini?"
                    },
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = NuxColors.GrayNeutral,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NuxButton(
                        onClick = { itemToDelete = null },
                        backgroundColor = Color.Transparent,
                        cornerRadius = 20.dp,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Batal", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    NuxButton(
                        onClick = {
                            if (selectedInstance != null) {
                                scope.launch {
                                    val targetWorld = if (activeType == "datapacks") selectedWorld else null
                                    NuxModManager.deleteItem(context, selectedInstance!!, activeType, item, targetWorld)
                                    itemToDelete = null
                                    reloadInstalled()
                                    val toastMsg = if (activeType == "modpacks") {
                                        "Modpack dan semua mod berhasil dihapus!"
                                    } else {
                                        "${item.name} berhasil dihapus!"
                                    }
                                    Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        backgroundColor = NuxColors.ErrorRed,
                        contentColor = NuxColors.DarkGray,
                        cornerRadius = 20.dp,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Hapus", fontWeight = FontWeight.Black, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    // --- PROGRES IMPOR MODPACK ---
    if (isImportingModpack) {
        NuxDialog(
            onDismissRequest = { /* Tidak bisa ditutup saat sedang mengunduh */ },
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .wrapContentHeight(),
            compact = true
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(NuxColors.YellowPale)
                        .border(NuxSizes.GlassBorder, NuxColors.Yellow.copy(alpha = 0.45f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    AmberSpinner(
                        modifier = Modifier.size(22.dp),
                        color = NuxColors.Yellow,
                        strokeWidth = 2.5.dp
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Mengimpor & memasang modpack",
                    fontWeight = FontWeight.Black,
                    fontSize = 15.5.sp,
                    color = NuxColors.DarkGray
                )

                if (modpackImportName.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = modpackImportName,
                        fontSize = 12.sp,
                        color = NuxColors.Yellow,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = if (modpackImportStatus.isNotBlank()) modpackImportStatus else "Membaca daftar mod dan mengunduh file...",
                    fontSize = 12.sp,
                    color = NuxColors.GrayNeutral,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (modpackImportTotal > 0) {
                    val progressRatio = (modpackImportCurrent.toFloat() / modpackImportTotal.toFloat()).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progressRatio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = NuxColors.Yellow,
                        trackColor = NuxColors.SurfaceElevated
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "$modpackImportCurrent / $modpackImportTotal file mod",
                        fontSize = 11.sp,
                        color = NuxColors.GrayNeutral
                    )
                } else {
                    AmberWaitBar(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = NuxColors.Yellow,
                        trackColor = NuxColors.SurfaceElevated
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Launcher sedang membaca daftar mod dan mengunduh modpack. Mohon tunggu.",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = NuxColors.GrayNeutral.copy(alpha = 0.75f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }

    // --- DETAIL PROYEK MODRINTH ---
    detailModalHit?.let { hit ->
        ModDetailDialog(
            hit = hit,
            project = detailProject,
            versions = compatibleVersions,
            selectedVersion = selectedVersion,
            isLoading = isDetailLoading,
            isInstalling = isDetailInstalling,
            progressText = detailProgressText,
            isInstalled = isHitInstalled(hit),
            tab = detailTab,
            instance = selectedInstance,
            onTab = { detailTab = it },
            onPickVersion = { selectedVersion = it },
            onOpenGallery = { previewGalleryUrl = it },
            onInstall = { installFromDetail(hit) },
            onClose = { detailModalHit = null }
        )
    }

    // --- PRATINJAU GAMBAR ---
    previewGalleryUrl?.let { url ->
        NuxDialog(onDismissRequest = { previewGalleryUrl = null }, compact = true) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tangkapan layar",
                        fontWeight = FontWeight.Black,
                        fontSize = 15.sp,
                        color = NuxColors.DarkGray
                    )
                    ModsIconButton(
                        icon = Icons.Outlined.Close,
                        description = "Tutup",
                        onClick = { previewGalleryUrl = null }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                NuxNetworkImage(
                    model = url,
                    contentDescription = "Pratinjau gambar",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .border(NuxSizes.GlassBorder, NuxColors.CardBorder, RoundedCornerShape(14.dp)),
                    shape = RoundedCornerShape(14.dp),
                    maxSidePx = 1280,
                    showSpinner = true
                )
            }
        }
    }
}

// ============================================================================
// BAHAN PENYUSUN LAYAR MOD
// ============================================================================

/** Tombol sakelar atas (Terpasang / Jelajah). Terpilih = isian kuning, teks Ink. */
/** Tombol ikon bulat 38dp. */
@Composable
private fun ModsIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = NuxColors.GrayNeutral,
    ring: Boolean = true,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(CircleShape)
            .then(
                if (ring) {
                    Modifier
                        .background(NuxColors.SurfaceElevated, CircleShape)
                        .border(NuxSizes.GlassBorder, NuxColors.CardBorder, CircleShape)
                } else {
                    Modifier
                }
            )
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}

// =============================================================================
// TAMPILAN BARU LAYAR MOD (kaca gelap dengan aksen biru, semua bentuk pil)
// =============================================================================

/** Sumber unduhan CurseForge dikunci sampai kunci API tersedia. Ubah ke false bila sudah siap dipakai. */
private const val CURSEFORGE_LOCKED = true

private val MODS_TYPE_ITEMS = listOf(
    Triple("modpacks", "Modpack", Icons.Outlined.Layers),
    Triple("mods", "Mod", Icons.Outlined.ViewInAr),
    Triple("shaderpacks", "Shader", Icons.Outlined.LightMode),
    Triple("resourcepacks", "Resource Pack", Icons.Outlined.Image),
    Triple("datapacks", "Datapack", Icons.Outlined.Public)
)

private val MODS_SORT_OPTIONS = listOf(
    "relevance" to "Relevansi",
    "downloads" to "Unduhan",
    "follows" to "Pengikut",
    "newest" to "Terbaru",
    "updated" to "Diperbarui"
)

private val MODS_LOADER_IDS = setOf("fabric", "forge", "neoforge", "quilt")

private fun modsTypeShortLabel(type: String): String = when (type) {
    "modpacks" -> "Modpack"
    "shaderpacks" -> "Shader"
    "resourcepacks" -> "Resource"
    "datapacks" -> "Datapack"
    else -> "Mod"
}

private fun modsSearchPlaceholder(type: String): String = when (type) {
    "modpacks" -> "Cari modpack..."
    "shaderpacks" -> "Cari shader..."
    "resourcepacks" -> "Cari resource pack..."
    "datapacks" -> "Cari datapack..."
    else -> "Cari mod..."
}

/** "⚡ Optimization" menjadi "Optimization" (buang emoji di depan label kategori). */
private fun modsPlainLabel(label: String): String {
    val firstSpace = label.indexOf(' ')
    return if (firstSpace > 0 && !label[0].isLetterOrDigit()) label.substring(firstSpace + 1) else label
}

private fun modsCategoryTag(id: String): String = when (id) {
    "optimization" -> "Optimasi"
    "technology" -> "Teknologi"
    "magic" -> "Sihir"
    "adventure" -> "Petualangan"
    "utility" -> "Utilitas"
    "decoration" -> "Dekorasi"
    "equipment" -> "Perlengkapan"
    "food" -> "Makanan"
    "storage" -> "Penyimpanan"
    "library" -> "Pustaka"
    "lightweight" -> "Ringan"
    "realistic" -> "Realistis"
    "fantasy" -> "Fantasi"
    else -> id.replace('-', ' ').replaceFirstChar { it.uppercase() }
}

private fun modsLoaderTag(id: String): String = when (id) {
    "neoforge" -> "NeoForge"
    else -> id.replaceFirstChar { it.uppercase() }
}

/** Label kecil di kartu: maksimal 2 kategori lalu 2 loader, mis. "Optimasi", "Fabric". */
private fun modsTagsFor(hit: ModrinthSearchHit): List<String> {
    val all = (hit.displayCategories ?: hit.categories ?: emptyList()).map { it.lowercase() }.distinct()
    val loaders = all.filter { it in MODS_LOADER_IDS }
    val topics = all.filter { it !in MODS_LOADER_IDS }
    return topics.take(2).map { modsCategoryTag(it) } + loaders.take(2).map { modsLoaderTag(it) }
}

/** Pil sumber: Modrinth (aktif) dan CurseForge (terkunci sampai ada kunci API). */
@Composable
private fun ModsSourcePill(
    curseForgeLocked: Boolean,
    onCurseForgeClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .height(48.dp)
            .clip(CircleShape)
            .amberGlass(CircleShape)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Modrinth: satu-satunya sumber yang aktif saat ini
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
                painter = painterResource(id = com.israadev.nuxlauncher.R.drawable.ic_source_modrinth),
                contentDescription = null,
                tint = AmberUi.Text,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Modrinth",
                color = AmberUi.Text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1
            )
        }

        // CurseForge: dikunci (pudar + ikon gembok), ketuk = pesan penjelasan
        Row(
            modifier = Modifier
                .height(40.dp)
                .alpha(if (curseForgeLocked) 0.5f else 1f)
                .clip(CircleShape)
                .clickable { onCurseForgeClick() }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(id = com.israadev.nuxlauncher.R.drawable.ic_source_curseforge),
                contentDescription = null,
                tint = AmberUi.Text,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "CurseForge",
                color = AmberUi.Text,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1
            )
            if (curseForgeLocked) {
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = "Terkunci",
                    tint = AmberUi.Text,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/** Pil aksi layar kelola: Tambah (buka jelajah Modrinth) dan Impor berkas dari penyimpanan. */
@Composable
private fun ModsActionPill(
    onAdd: () -> Unit,
    onImport: () -> Unit
) {
    Row(
        modifier = Modifier
            .height(48.dp)
            .clip(CircleShape)
            .amberGlass(CircleShape)
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AmberPillButton(text = "Tambah", icon = Icons.Outlined.Add, onClick = onAdd)
        AmberPillButton(text = "Impor", icon = Icons.Outlined.FileUpload, filled = false, onClick = onImport)
    }
}

/** Label kecil berbentuk pil di bawah deskripsi kartu (kategori, loader). */
@Composable
private fun ModsTagChip(text: String) {
    Box(
        modifier = Modifier
            .height(22.dp)
            .border(1.dp, AmberUi.Border, CircleShape)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = AmberUi.TextDim,
            fontSize = 12.sp,
            maxLines = 1
        )
    }
}

/** Pesan di tengah daftar (kosong, galat, belum ada instance) dengan tombol aksi opsional. */
@Composable
private fun ColumnScope.ModsMessage(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String? = null,
    iconTint: Color = AmberUi.TextDim,
    actionLabel: String? = null,
    onAction: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 340.dp)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(34.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                color = AmberUi.Text,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = AmberUi.TextDim,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            if (actionLabel != null) {
                Spacer(modifier = Modifier.height(12.dp))
                AmberPillButton(text = actionLabel, onClick = onAction)
            }
        }
    }
}

/** Indikator memuat di tengah daftar. */
@Composable
private fun ColumnScope.ModsLoading(text: String? = null) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AmberSpinner(
                color = AmberUi.AccentSoft,
                modifier = Modifier.size(28.dp)
            )
            if (text != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text, color = AmberUi.TextDim, fontSize = 12.sp)
            }
        }
    }
}

/** Baris addon yang sudah terpasang: ikon, nama, berkas, saklar aktif, tombol hapus. */
@Composable
private fun InstalledItemRow(
    item: InstalledModItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (item.isEnabled) AmberUi.Surface else AmberUi.Surface.copy(alpha = 0.3f), shape)
            .border(1.dp, AmberUi.Border, shape)
            .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ModIcon(
            item = item,
            size = 48.dp,
            dimmed = !item.isEnabled
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = if (item.isEnabled) AmberUi.Text else AmberUi.TextDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (!item.isEnabled) {
                    Spacer(modifier = Modifier.width(8.dp))
                    NuxBadge(
                        text = "NONAKTIF",
                        backgroundColor = NuxColors.SurfaceElevated,
                        textColor = NuxColors.GrayNeutral
                    )
                }
            }
            Text(
                text = "${item.filename} · ${formatBytes(item.sizeBytes)}",
                fontSize = 12.sp,
                color = AmberUi.TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Switch(
            checked = item.isEnabled,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AmberUi.Accent,
                checkedBorderColor = AmberUi.AccentEdge,
                uncheckedThumbColor = AmberUi.TextDim,
                uncheckedTrackColor = NuxColors.SurfaceElevated
            )
        )

        ModsIconButton(
            icon = Icons.Outlined.Delete,
            description = "Hapus",
            onClick = onDelete,
            tint = NuxColors.ErrorRed,
            ring = false
        )
    }
}

/** Kartu hasil pencarian Modrinth: ikon, judul, deskripsi, label, tombol Pasang / Terpasang. */
@Composable
private fun ModrinthHitRow(
    hit: ModrinthSearchHit,
    isInstalled: Boolean,
    isDownloading: Boolean,
    downloadStatus: String,
    onOpen: () -> Unit,
    onInstall: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    val iconShape = RoundedCornerShape(14.dp)
    val tags = remember(hit.projectId, hit.displayCategories, hit.categories) { modsTagsFor(hit) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .amberGlass(shape)
            .clickable { onOpen() }
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NuxNetworkImage(
            model = hit.iconUrl,
            contentDescription = hit.title,
            modifier = Modifier
                .size(52.dp)
                .border(1.dp, AmberUi.Border, iconShape),
            fallbackInitials = hit.title.take(2).uppercase(),
            shape = iconShape,
            maxSidePx = 192
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = hit.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = AmberUi.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (hit.projectType == "modpack") {
                    Spacer(modifier = Modifier.width(8.dp))
                    NuxBadge(
                        text = "PACK",
                        backgroundColor = AmberUi.Accent,
                        textColor = AmberUi.Text
                    )
                }
            }

            Text(
                text = hit.description ?: "Tidak ada deskripsi",
                fontSize = 13.sp,
                color = AmberUi.TextDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tags.forEach { tag -> ModsTagChip(tag) }
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        if (isDownloading) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AmberSpinner(
                    color = AmberUi.AccentSoft,
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = downloadStatus.ifBlank { "Unduh..." },
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = AmberUi.AccentSoft,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 96.dp)
                )
            }
        } else if (isInstalled) {
            AmberPillButton(
                text = "Terpasang",
                icon = Icons.Outlined.Check,
                filled = false,
                onClick = onOpen
            )
        } else {
            AmberPillButton(
                text = if (hit.projectType == "modpack") "Pasang pack" else "Pasang",
                onClick = onInstall
            )
        }
    }
}

/**
 * Jendela detail dua panel: kiri = identitas proyek + tombol pasang (selalu terlihat),
 * kanan = tab Ringkasan / Versi yang bisa digulir.
 */
@Composable
private fun ModDetailDialog(
    hit: ModrinthSearchHit,
    project: ModrinthProject?,
    versions: List<ModrinthVersion>,
    selectedVersion: ModrinthVersion?,
    isLoading: Boolean,
    isInstalling: Boolean,
    progressText: String,
    isInstalled: Boolean,
    tab: String,
    instance: Instance?,
    onTab: (String) -> Unit,
    onPickVersion: (ModrinthVersion) -> Unit,
    onOpenGallery: (String) -> Unit,
    onInstall: () -> Unit,
    onClose: () -> Unit
) {
    val typeColor = when (hit.projectType) {
        "mod" -> NuxColors.Yellow
        "modpack" -> NuxColors.PurpleDark
        "resourcepack" -> NuxColors.SkyBlueDark
        else -> NuxColors.AmberDark
    }

    NuxDialog(
        onDismissRequest = { if (!isInstalling) onClose() },
        modifier = Modifier.fillMaxWidth(0.92f),
        fillMaxHeight = true,
        compact = true
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---------------- KIRI ----------------
            Column(
                modifier = Modifier
                    .weight(0.36f)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    NuxNetworkImage(
                        model = hit.iconUrl,
                        contentDescription = hit.title,
                        modifier = Modifier
                            .size(56.dp)
                            .border(NuxSizes.GlassBorder, NuxColors.CardBorder, RoundedCornerShape(14.dp)),
                        fallbackInitials = hit.title.take(2).uppercase(),
                        shape = RoundedCornerShape(14.dp),
                        maxSidePx = 192
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = hit.title,
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                        lineHeight = 22.sp,
                        color = NuxColors.DarkGray,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "oleh ${hit.author ?: "Komunitas"}",
                        fontSize = 11.sp,
                        color = NuxColors.GrayNeutral
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        NuxBadge(
                            text = hit.projectType.uppercase(),
                            backgroundColor = typeColor.copy(alpha = 0.16f),
                            textColor = typeColor,
                            borderColor = typeColor.copy(alpha = 0.4f)
                        )
                        NuxBadge(
                            text = "📥 ${formatCompactNumber(hit.downloads)}",
                            backgroundColor = NuxColors.SurfaceElevated,
                            textColor = NuxColors.GrayNeutral
                        )
                    }
                    if (selectedVersion != null && !isInstalled) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Versi dipilih",
                            fontSize = 11.sp,
                            color = NuxColors.GrayNeutral
                        )
                        Text(
                            text = selectedVersion.name.ifBlank { selectedVersion.versionNumber },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = NuxColors.DarkGray,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (isInstalling) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        AmberSpinner(
                            color = NuxColors.Yellow,
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = progressText.ifBlank { "Sedang mengunduh dan memasang..." },
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = NuxColors.Yellow,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                } else if (isInstalled) {
                    val okShape = RoundedCornerShape(20.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clip(okShape)
                            .background(NuxColors.YellowPale, okShape)
                            .border(NuxSizes.GlassBorder, NuxColors.Yellow.copy(alpha = 0.45f), okShape),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = NuxColors.Yellow,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Sudah terpasang",
                            color = NuxColors.Yellow,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    NuxButton(
                        onClick = onInstall,
                        backgroundColor = NuxColors.Yellow,
                        enabled = versions.isNotEmpty(),
                        cornerRadius = 20.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Download,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (hit.projectType == "modpack") "Pasang modpack" else "Pasang ke instance",
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // ---------------- KANAN ----------------
            Column(
                modifier = Modifier
                    .weight(0.64f)
                    .fillMaxHeight()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SettingChip(
                            text = "Ringkasan",
                            selected = tab == "overview",
                            onClick = { onTab("overview") }
                        )
                        SettingChip(
                            text = "Versi (${versions.size})",
                            selected = tab == "versions",
                            onClick = { onTab("versions") }
                        )
                    }
                    ModsIconButton(
                        icon = Icons.Outlined.Close,
                        description = "Tutup",
                        onClick = onClose,
                        enabled = !isInstalling
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            AmberSpinner(color = NuxColors.Yellow, modifier = Modifier.size(28.dp))
                        }
                    } else if (tab == "overview") {
                        val galleryList = project?.gallery ?: emptyList()
                        if (galleryList.isNotEmpty()) {
                            Text(
                                text = "Tangkapan layar",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                color = NuxColors.GrayNeutral
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(galleryList) { gal ->
                                    Box(
                                        modifier = Modifier
                                            .width(160.dp)
                                            .height(92.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(NuxColors.SurfaceWhite)
                                            .border(NuxSizes.GlassBorder, NuxColors.CardBorder, RoundedCornerShape(12.dp))
                                            .clickable { onOpenGallery(gal.url) }
                                    ) {
                                        NuxNetworkImage(
                                            model = gal.url,
                                            contentDescription = gal.title ?: "Screenshot",
                                            modifier = Modifier.fillMaxSize(),
                                            shape = RoundedCornerShape(12.dp),
                                            maxSidePx = 400,
                                            showSpinner = true
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        Text(
                            text = "Deskripsi",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = NuxColors.GrayNeutral
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = project?.description ?: hit.description ?: "Tidak ada ringkasan deskripsi.",
                            fontSize = 13.sp,
                            color = NuxColors.DarkGray.copy(alpha = 0.88f),
                            lineHeight = 18.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        val allLoaders = (project?.loaders ?: hit.categories ?: emptyList()).distinct()
                        if (allLoaders.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                allLoaders.take(6).forEach { tag ->
                                    NuxBadge(
                                        text = tag.uppercase(),
                                        backgroundColor = NuxColors.SurfaceElevated,
                                        textColor = NuxColors.GrayNeutral
                                    )
                                }
                            }
                        }
                    } else {
                        if (versions.isEmpty()) {
                            val warnShape = RoundedCornerShape(12.dp)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(NuxColors.ErrorRed.copy(alpha = 0.12f), warnShape)
                                    .border(NuxSizes.GlassBorder, NuxColors.ErrorRed.copy(alpha = 0.4f), warnShape)
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = "Tidak ada versi yang didukung",
                                    color = NuxColors.ErrorRed,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Proyek ini tidak punya rilis yang cocok dengan Minecraft ${instance?.mcVersion ?: ""} (${(instance?.loader ?: "vanilla").uppercase()}).",
                                    color = NuxColors.GrayNeutral,
                                    fontSize = 11.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                versions.forEach { ver ->
                                    val isPicked = selectedVersion?.id == ver.id
                                    val rowShape = RoundedCornerShape(14.dp)
                                    val verColor = when (ver.versionType) {
                                        "release" -> NuxColors.Yellow
                                        "beta" -> NuxColors.AmberDark
                                        else -> NuxColors.ErrorRed
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(rowShape)
                                            .background(if (isPicked) NuxColors.YellowPale else NuxColors.SurfaceWhite, rowShape)
                                            .border(
                                                NuxSizes.GlassBorder,
                                                if (isPicked) NuxColors.Yellow.copy(alpha = 0.55f) else NuxColors.CardBorder,
                                                rowShape
                                            )
                                            .clickable { onPickVersion(ver) }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(if (isPicked) NuxColors.Yellow else Color.Transparent)
                                                .border(
                                                    NuxSizes.GlassBorder,
                                                    if (isPicked) NuxColors.Yellow else NuxColors.GrayNeutral,
                                                    CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isPicked) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Check,
                                                    contentDescription = null,
                                                    tint = NuxColors.Ink,
                                                    modifier = Modifier.size(11.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = ver.name.ifBlank { ver.versionNumber },
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = NuxColors.DarkGray,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "Versi ${ver.versionNumber} · MC ${ver.gameVersions.joinToString(", ")}",
                                                fontSize = 11.sp,
                                                color = NuxColors.GrayNeutral,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        NuxBadge(
                                            text = ver.versionType.uppercase(),
                                            backgroundColor = verColor.copy(alpha = 0.16f),
                                            textColor = verColor,
                                            borderColor = verColor.copy(alpha = 0.4f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (hit.projectType == "modpack") {
                            "Modpack diekstrak langsung ke instance (overrides, konfigurasi, dan manifest)."
                        } else {
                            "Dependensi wajib ikut terpasang otomatis."
                        },
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = NuxColors.GrayNeutral
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

/** Kolom cari berbentuk pil 38dp dengan ikon kaca pembesar dan tombol hapus teks. */
private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, 3)
    return DecimalFormat("#,##0.#").format(bytes / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
}

private fun formatCompactNumber(number: Long): String {
    return when {
        number >= 1_000_000 -> DecimalFormat("#,##0.#").format(number / 1_000_000.0) + "M"
        number >= 1_000 -> DecimalFormat("#,##0.#").format(number / 1_000.0) + "K"
        else -> number.toString()
    }
}
