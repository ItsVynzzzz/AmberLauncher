package com.israadev.nuxlauncher.core.crash

import android.content.Context
import com.google.gson.JsonParser
import com.israadev.nuxlauncher.core.device.DeviceProfiles
import com.israadev.nuxlauncher.core.instance.InstanceManager
import com.israadev.nuxlauncher.core.renderer.NuxRendererInfo
import com.israadev.nuxlauncher.core.renderer.NuxRendererRegistry
import com.israadev.nuxlauncher.core.settings.SettingsManager
import com.israadev.nuxlauncher.core.utils.NuxVersionUtils
import java.io.File

/** Perbaikan sekali ketuk yang ditawarkan setelah game crash. */
enum class RecoveryAction { LOWER_RAM, RESET_RENDERER, DISABLE_SHADERS, DISABLE_MODS, RESET_JAVA }

/**
 * Satu tawaran perbaikan. [label] dan [detail] ditulis dalam bahasa Indonesia dan diterjemahkan
 * lewat kamus saat ditampilkan. [arg] membawa nilai yang akan diterapkan: RAM baru (MB), id
 * renderer, atau daftar nama berkas mod dipisah tanda "|".
 */
data class RecoveryOption(
    val action: RecoveryAction,
    val label: String,
    val detail: String,
    val arg: String = ""
)

data class RecoveryResult(val ok: Boolean, val message: String)

/**
 * Pemulihan sekali ketuk setelah crash. Hanya menawarkan perbaikan yang masuk akal untuk dugaan
 * penyebab crash ([CrashAdvisor.sourceOf]), dan semuanya bisa dibatalkan pengguna: RAM dan renderer
 * tinggal diubah lagi di Pengaturan, mod hanya diberi akhiran ".disabled" (diaktifkan lagi di menu
 * Mod), dan shader tinggal dinyalakan lagi dari menu video game.
 *
 * Fungsi [suggest] dan [apply] membaca atau menulis berkas, jadi panggil dari thread latar.
 */
object CrashRecovery {

    private const val RAM_STEP_MB = 256
    private const val RAM_ROUND_MB = 128
    private const val RAM_MIN_MB = 512
    private const val MAX_SUSPECT_MODS = 3

    private val BUILTIN_RENDERER_IDS = setOf("auto", "krypton", "mobileglues", "gl4es", "zink", "freedreno")

    /** Berkas pengaturan shader (relatif terhadap folder game) yang memakai kunci enableShaders. */
    private val SHADER_PROPERTY_FILES = listOf("config/iris.properties", "config/oculus.properties")
    private const val OPTIFINE_SHADER_FILE = "optionsshaders.txt"

    /** Id mod yang tidak pernah dianggap tersangka (pustaka inti: mematikannya membuat game makin rusak). */
    private val PROTECTED_MOD_IDS = setOf(
        "minecraft", "java", "fabricloader", "fabric", "fabric-api", "forge", "neoforge", "quilt_loader", "mixinextras"
    )

    private val MOD_ID_PATTERNS = listOf(
        Regex("""Mod ID: '([A-Za-z0-9_.\-]+)'"""),
        Regex("""Mixin apply for mod ([A-Za-z0-9_.\-]+) failed"""),
        Regex("""Mod '[^']*' \(([A-Za-z0-9_.\-]+)\)""")
    )

    // ---------------------------------------------------------------- logika murni (mudah diuji)

    /** True bila heap Java kehabisan ruang (bukan RAM HP): menurunkan RAM justru memperburuk. */
    fun isJavaHeapOutOfMemory(log: String): Boolean {
        return log.contains("OutOfMemoryError: Java heap space") ||
            log.contains("GC overhead limit exceeded")
    }

    /**
     * RAM game baru (MB) bila diturunkan 256 MB dari heap yang benar-benar dipakai
     * ([effectiveMb], sudah memperhitungkan batas profil), atau null bila tidak ada ruang untuk turun.
     */
    fun loweredRamMb(requestedMb: Int, effectiveMb: Int): Int? {
        val target = ((effectiveMb - RAM_STEP_MB) / RAM_ROUND_MB * RAM_ROUND_MB).coerceAtLeast(RAM_MIN_MB)
        return if (target < requestedMb) target else null
    }

    /** True bila log menyinggung shader sehingga mematikannya masuk akal. */
    fun mentionsShaders(log: String): Boolean {
        return log.contains("shader", ignoreCase = true) ||
            log.contains("iris", ignoreCase = true) ||
            log.contains("oculus", ignoreCase = true) ||
            log.contains("optifine", ignoreCase = true)
    }

    /** True bila log menunjukkan versi Java tidak cocok dengan game atau mod. */
    fun mentionsJavaMismatch(log: String): Boolean {
        return log.contains("UnsupportedClassVersionError") ||
            log.contains("Unsupported class file major version") ||
            log.contains("Unsupported major.minor version") ||
            log.contains("compiled by a more recent version of the Java Runtime")
    }

    /** Teks iris.properties/oculus.properties dengan shader dimatikan, atau null bila tidak perlu diubah. */
    fun disableShaderProperty(text: String): String? {
        val lines = text.split("\n")
        var changed = false
        val out = lines.map { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("enableShaders") && trimmed.substringAfter("=", "").trim().equals("true", ignoreCase = true)) {
                changed = true
                "enableShaders=false"
            } else {
                line
            }
        }
        return if (changed) out.joinToString("\n") else null
    }

    /** Teks optionsshaders.txt (OptiFine) dengan shader dimatikan, atau null bila tidak perlu diubah. */
    fun disableOptifineShader(text: String): String? {
        val lines = text.split("\n")
        var changed = false
        val out = lines.map { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("shaderPack=")) {
                val value = trimmed.substringAfter("=").trim()
                if (value.isNotEmpty() && !value.equals("OFF", ignoreCase = true)) {
                    changed = true
                    "shaderPack=OFF"
                } else {
                    line
                }
            } else {
                line
            }
        }
        return if (changed) out.joinToString("\n") else null
    }

    /** Nama dasar berkas mod tanpa versi, mis. "sodium-fabric-0.5.8+mc1.20.1.jar" -> "sodium-fabric". */
    fun modBaseName(fileName: String): String {
        var base = fileName.lowercase().removeSuffix(".disabled").removeSuffix(".jar")
        val cut = Regex("""[-_+ ]v?\d""").find(base)
        if (cut != null && cut.range.first > 0) base = base.substring(0, cut.range.first)
        return base
    }

    /**
     * Mod aktif yang disebut di [log]: nama berkasnya muncul utuh, atau id mod yang disebut crash
     * report cocok dengan nama berkasnya. Paling banyak [MAX_SUSPECT_MODS]; lebih dari itu dianggap
     * terlalu tidak pasti, hasilnya kosong. Pustaka inti (mis. Fabric API) tidak pernah dipilih.
     */
    fun findSuspectMods(log: String, installedMods: List<String>): List<String> {
        val enabled = installedMods.filter { it.endsWith(".jar", ignoreCase = true) }
        if (enabled.isEmpty() || log.isBlank()) return emptyList()

        val ids = HashSet<String>()
        for (pattern in MOD_ID_PATTERNS) {
            for (m in pattern.findAll(log)) {
                val id = m.groupValues[1].lowercase()
                if (id.length >= 3 && id !in PROTECTED_MOD_IDS) ids.add(id)
            }
        }

        val found = LinkedHashSet<String>()
        for (name in enabled) {
            val base = modBaseName(name)
            if (isProtectedJar(base)) continue
            if (log.contains(name)) {
                found.add(name)
                continue
            }
            val squashed = base.replace(Regex("""[^a-z0-9]"""), "")
            for (id in ids) {
                val idSquashed = id.replace(Regex("""[^a-z0-9]"""), "")
                if (base == id || base.startsWith("$id-") || base.startsWith("${id}_") || (idSquashed.length >= 4 && squashed == idSquashed)) {
                    found.add(name)
                    break
                }
            }
        }
        return if (found.size in 1..MAX_SUSPECT_MODS) found.toList() else emptyList()
    }

    private fun isProtectedJar(base: String): Boolean {
        return base.startsWith("fabric-api") || base == "fabricapi" || base.startsWith("forgified-fabric-api") ||
            base.startsWith("quilted-fabric-api") || base == "fabric-language-kotlin"
    }

    /**
     * Renderer lain yang layak dicoba setelah [currentId] crash: urutan Krypton, MobileGlues, GL4ES,
     * hanya yang pustakanya ada di HP ini dan mendukung versi Minecraft-nya. Null bila tidak ada pilihan lain.
     */
    fun alternativeRenderer(currentId: String, chain: List<NuxRendererInfo>): NuxRendererInfo? {
        if (chain.size < 2) return null
        val idx = chain.indexOfFirst { it.id == currentId }
        return if (idx < 0) chain.first() else chain[(idx + 1) % chain.size]
    }

    // ---------------------------------------------------------------- tawaran dan penerapan

    /** Perbaikan yang layak ditawarkan untuk crash ini. Kosong untuk crash launcher. */
    fun suggest(context: Context, info: GameCrashInfo, logText: String): List<RecoveryOption> {
        if (info.isLauncherCrash) return emptyList()
        val options = ArrayList<RecoveryOption>()
        try {
            // Halaman Error bisa berjalan di proses yang baru dibuka: muat setelan dari berkas dulu.
            SettingsManager.init(context)
            val log = if (logText.isNotBlank()) logText else info.logSnippet
            val source = CrashAdvisor.sourceOf(info)
            val settings = SettingsManager.settings.value
            val gameDir = info.gameDirPath?.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.isDirectory }

            // 1. RAM: hanya bila HP kehabisan RAM (bukan heap Java yang sempit)
            if (source == CrashSource.MEMORY && !isJavaHeapOutOfMemory(log)) {
                val target = loweredRamMb(settings.ramMb, effectiveHeapMb(context, settings.ramMb, settings.deviceProfileEnabled))
                if (target != null) {
                    options.add(
                        RecoveryOption(
                            RecoveryAction.LOWER_RAM,
                            "Turunkan RAM ke $target MB",
                            "Memberi ruang lebih bagi HP. Bisa dinaikkan lagi di Pengaturan > Memori.",
                            target.toString()
                        )
                    )
                }
            }

            // 2. Renderer: hanya untuk crash grafis
            if (source == CrashSource.GRAPHICS) {
                rendererOption(context, info, settings.selectedRenderer)?.let { options.add(it) }
            }

            // 3. Shader: bila masih menyala dan crash berkaitan dengan grafis, memori, atau log menyebut shader
            if (gameDir != null && shadersEnabled(gameDir) &&
                (source == CrashSource.GRAPHICS || source == CrashSource.MEMORY || mentionsShaders(log))
            ) {
                options.add(
                    RecoveryOption(
                        RecoveryAction.DISABLE_SHADERS,
                        "Matikan shader",
                        "Shader membebani GPU dan memori. Bisa dinyalakan lagi dari menu video game."
                    )
                )
            }

            // 4. Mod yang disebut di log
            if (gameDir != null) {
                val suspects = findSuspectMods(log, info.installedMods).filter { File(File(gameDir, "mods"), it).isFile }
                if (suspects.isNotEmpty()) {
                    options.add(
                        RecoveryOption(
                            RecoveryAction.DISABLE_MODS,
                            "Nonaktifkan mod tersangka",
                            "Mod yang disebut di log: ${suspects.joinToString(", ")}. Bisa diaktifkan lagi di menu Mod.",
                            suspects.joinToString("|")
                        )
                    )
                }
            }

            // 5. Java: bila versi dipilih manual dan log menunjukkan ketidakcocokan
            if ((mentionsJavaMismatch(log) || source == CrashSource.GAME_OR_MODS) && javaIsManual(info)) {
                options.add(
                    RecoveryOption(
                        RecoveryAction.RESET_JAVA,
                        "Reset Java ke Otomatis",
                        "Versi Java yang dipilih mungkin tidak cocok dengan game ini. Otomatis memilih versi yang sesuai."
                    )
                )
            }
        } catch (_: Throwable) {
            // Tawaran pemulihan hanya bonus: kegagalan di sini tidak boleh mengganggu panel crash.
        }
        return options
    }

    fun apply(context: Context, info: GameCrashInfo, option: RecoveryOption): RecoveryResult {
        return try {
            when (option.action) {
                RecoveryAction.LOWER_RAM -> {
                    val mb = option.arg.toIntOrNull() ?: return RecoveryResult(false, FAIL_MESSAGE)
                    SettingsManager.init(context)
                    SettingsManager.updateSettings(context, SettingsManager.settings.value.copy(ramMb = mb))
                    RecoveryResult(true, "RAM game diturunkan ke $mb MB.")
                }
                RecoveryAction.RESET_RENDERER -> {
                    val id = option.arg.ifBlank { "auto" }
                    SettingsManager.init(context)
                    SettingsManager.updateSettings(context, SettingsManager.settings.value.copy(selectedRenderer = id))
                    if (id == "auto") {
                        RecoveryResult(true, "Renderer dikembalikan ke Otomatis.")
                    } else {
                        RecoveryResult(true, "Renderer diganti ke ${NuxRendererRegistry.findRendererById(id).displayName}.")
                    }
                }
                RecoveryAction.DISABLE_SHADERS -> {
                    val gameDir = info.gameDirPath?.let { File(it) } ?: return RecoveryResult(false, FAIL_MESSAGE)
                    if (disableShaders(gameDir) > 0) RecoveryResult(true, "Shader dimatikan.") else RecoveryResult(false, FAIL_MESSAGE)
                }
                RecoveryAction.DISABLE_MODS -> {
                    val gameDir = info.gameDirPath?.let { File(it) } ?: return RecoveryResult(false, FAIL_MESSAGE)
                    val done = disableMods(gameDir, option.arg.split("|").filter { it.isNotBlank() })
                    if (done.isNotEmpty()) {
                        RecoveryResult(true, "Mod dinonaktifkan: ${done.joinToString(", ")}.")
                    } else {
                        RecoveryResult(false, FAIL_MESSAGE)
                    }
                }
                RecoveryAction.RESET_JAVA -> {
                    resetJava(context, info)
                    RecoveryResult(true, "Java dikembalikan ke Otomatis.")
                }
            }
        } catch (_: Throwable) {
            RecoveryResult(false, FAIL_MESSAGE)
        }
    }

    private const val FAIL_MESSAGE = "Gagal menerapkan. Coba lewat Pengaturan."

    // ---------------------------------------------------------------- bagian yang menyentuh berkas

    private fun effectiveHeapMb(context: Context, requestedMb: Int, profileEnabled: Boolean): Int {
        val profile = DeviceProfiles.detect() ?: return requestedMb
        if (!profileEnabled) return requestedMb
        val totalMb = SettingsManager.getTotalDeviceMemoryMb(context)
        return if (DeviceProfiles.heapCapApplies(profile, totalMb)) minOf(requestedMb, profile.smallRamHeapCapMb) else requestedMb
    }

    private fun rendererOption(context: Context, info: GameCrashInfo, selectedId: String): RecoveryOption? {
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val selected = selectedId.trim().ifEmpty { "auto" }.lowercase()

        // Renderer tambahan (plugin) atau pilihan manual yang tidak dikenal: kembali ke Otomatis.
        if (selected !in BUILTIN_RENDERER_IDS) {
            return resetToAuto("Renderer pilihanmu bisa jadi kurang cocok dengan HP ini. Otomatis memilih yang paling sesuai.")
        }

        val current = NuxRendererRegistry.resolveRenderer(selected, info.mcVersion, nativeDir, null)
        // Pilihan manual yang berbeda dari pilihan Otomatis: kembali ke Otomatis dulu.
        if (selected != "auto") {
            val auto = NuxRendererRegistry.resolveRenderer("auto", info.mcVersion, nativeDir, null)
            if (auto.id != current.id) {
                return resetToAuto("Renderer pilihanmu bisa jadi kurang cocok dengan HP ini. Otomatis memilih yang paling sesuai.")
            }
        }

        val chain = listOf(
            NuxRendererRegistry.RENDERER_KRYPTON,
            NuxRendererRegistry.RENDERER_MOBILEGLUES,
            NuxRendererRegistry.RENDERER_GL4ES
        ).filter { File(nativeDir, it.libraryName).exists() && NuxVersionUtils.isRendererSupported(it, info.mcVersion) }
        val alt = alternativeRenderer(current.id, chain) ?: return null
        val name = alt.displayName.substringBefore(" (")
        return RecoveryOption(
            RecoveryAction.RESET_RENDERER,
            "Coba renderer $name",
            "Renderer sekarang tampaknya bermasalah di HP ini. Bisa diganti lagi di Pengaturan > Grafis.",
            alt.id
        )
    }

    private fun resetToAuto(detail: String) = RecoveryOption(
        RecoveryAction.RESET_RENDERER,
        "Pakai renderer Otomatis",
        detail,
        "auto"
    )

    private fun shadersEnabled(gameDir: File): Boolean {
        for (rel in SHADER_PROPERTY_FILES) {
            val f = File(gameDir, rel)
            if (f.isFile && f.length() < 65_536 && disableShaderProperty(f.readText()) != null) return true
        }
        val of = File(gameDir, OPTIFINE_SHADER_FILE)
        return of.isFile && of.length() < 65_536 && disableOptifineShader(of.readText()) != null
    }

    /** Mematikan shader di semua berkas pengaturan yang dikenal. Mengembalikan jumlah berkas yang diubah. */
    fun disableShaders(gameDir: File): Int {
        var changed = 0
        for (rel in SHADER_PROPERTY_FILES) {
            val f = File(gameDir, rel)
            if (!f.isFile || f.length() >= 65_536) continue
            val updated = disableShaderProperty(f.readText()) ?: continue
            f.writeText(updated)
            changed++
        }
        val of = File(gameDir, OPTIFINE_SHADER_FILE)
        if (of.isFile && of.length() < 65_536) {
            val updated = disableOptifineShader(of.readText())
            if (updated != null) {
                of.writeText(updated)
                changed++
            }
        }
        return changed
    }

    /** Memberi akhiran ".disabled" pada mod yang disebut. Mengembalikan nama berkas yang berhasil. */
    fun disableMods(gameDir: File, names: List<String>): List<String> {
        val modsDir = File(gameDir, "mods")
        val done = ArrayList<String>()
        for (raw in names) {
            // Hanya nama berkas biasa di folder mods, tidak boleh berisi jalur.
            val name = File(raw).name
            if (name != raw || !name.endsWith(".jar", ignoreCase = true)) continue
            val src = File(modsDir, name)
            val dst = File(modsDir, "$name.disabled")
            if (src.isFile && !dst.exists() && src.renameTo(dst)) done.add(name)
        }
        return done
    }

    private fun resolveInstanceId(info: GameCrashInfo): String? {
        val dir = info.gameDirPath?.takeIf { it.isNotBlank() }?.let { File(it) }
        return dir?.parentFile?.name?.takeIf { it.isNotBlank() }
    }

    private fun instanceJavaRuntime(info: GameCrashInfo): String? {
        val instDir = info.gameDirPath?.takeIf { it.isNotBlank() }?.let { File(it).parentFile } ?: return null
        val f = File(instDir, "instance.json")
        if (!f.isFile || f.length() > 65_536) return null
        return try {
            JsonParser.parseString(f.readText()).asJsonObject.get("javaRuntime")?.asString
        } catch (_: Throwable) {
            null
        }
    }

    private fun javaIsManual(info: GameCrashInfo): Boolean {
        if (!SettingsManager.settings.value.defaultJavaRuntime.equals("auto", ignoreCase = true)) return true
        val runtime = instanceJavaRuntime(info) ?: return false
        return !runtime.equals("auto", ignoreCase = true)
    }

    private fun resetJava(context: Context, info: GameCrashInfo) {
        SettingsManager.init(context)
        SettingsManager.updateSettings(context, SettingsManager.settings.value.copy(defaultJavaRuntime = "auto"))
        // Daftar instance dimuat dulu supaya menyimpan satu instance tidak menimpa daftar dengan isi kosong.
        InstanceManager.init(context)
        if (InstanceManager.instances.value.isEmpty()) return
        val id = resolveInstanceId(info)
        val inst = InstanceManager.instances.value.firstOrNull { (id != null && it.id == id) || it.name == info.instanceName }
        if (inst != null && !inst.javaRuntime.equals("auto", ignoreCase = true)) {
            InstanceManager.updateInstance(context, inst.copy(javaRuntime = "auto"))
        }
    }
}
