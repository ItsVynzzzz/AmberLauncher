package com.israadev.nuxlauncher.core.forge

import com.google.gson.Gson
import com.israadev.nuxlauncher.core.utils.SafePaths
import java.io.File

/**
 * Bahan peluncuran Forge yang dihitung dari profil Forge yang terpasang.
 *
 * - [mainClass]: kelas utama Forge (LaunchWrapper untuk 1.12.2 ke bawah, ModLauncher untuk 1.13-1.16,
 *   BootstrapLauncher untuk 1.17+).
 * - [libraryFiles]: library Forge yang ada di disk, untuk ditaruh di classpath.
 * - [libraryKeys]: "group:artifact" milik Forge; library Mojang dengan kunci sama dilewati (versi Forge menang).
 * - [jvmArgs]: argumen JVM tambahan Forge (module path, --add-opens, -DignoreList, dst.), sebelum `-cp`.
 * - [gameArgs]: argumen game tambahan Forge (--launchTarget, --fml.*), sesudah argumen game biasa.
 */
class ForgeLaunchInfo(
    val mainClass: String,
    val libraryFiles: List<File>,
    val libraryKeys: Set<String>,
    val jvmArgs: List<String>,
    val gameArgs: List<String>
)

object ForgeLaunch {

    /** Argumen JVM yang sudah diatur Amber sendiri; versi dari profil Forge dibuang agar tidak menimpa. */
    private val AMBER_MANAGED_JVM_PREFIXES = listOf(
        "-Djava.net.preferIPv6Addresses",
        "-Djava.library.path",
        "-Xmx",
        "-Xms",
        "-XstartOnFirstThread"
    )

    /**
     * Argumen game dari profil Forge lama yang diatur Amber sendiri; pasangannya dibuang agar tidak ganda
     * (Forge 1.12.2 menulis `--versionType Forge`, sedangkan Amber sudah memberi `--versionType`).
     */
    private val AMBER_MANAGED_GAME_ARGS = setOf("--versionType")

    private val gson = Gson()

    /**
     * Membaca [profileFile] (versions/<mc>/forge-<versi>.json). Mengembalikan null bila berkas tidak
     * terbaca atau tidak punya mainClass.
     */
    fun resolve(profileFile: File, libDir: File, mcVersion: String): ForgeLaunchInfo? {
        val parsed: ForgeVersionJson? = try {
            gson.fromJson(profileFile.readText(), ForgeVersionJson::class.java)
        } catch (_: Exception) {
            null
        }
        val version = parsed ?: return null
        val mainClass = version.mainClass?.takeIf { it.isNotBlank() } ?: return null

        val files = LinkedHashSet<File>()
        val keys = LinkedHashSet<String>()
        for (lib in version.libraries ?: emptyList()) {
            val name = lib.name ?: continue
            // LWJGL milik Amber (versi Android) dipakai; yang di profil Forge tidak boleh ikut
            if (name.contains("org.lwjgl")) continue
            // Library native diurus Amber (profil Forge lama kadang memuat salinan milik Minecraft)
            if (lib.natives != null) continue
            ForgeMaven.keyOf(name)?.let { keys.add(it) }
            val path = ForgeMaven.libraryPath(lib) ?: continue
            val file = SafePaths.resolveInside(libDir, path) ?: continue
            if (file.exists() && file.length() > 0L) files.add(file)
        }

        val vars = mapOf(
            "library_directory" to libDir.absolutePath,
            "classpath_separator" to File.pathSeparator,
            "version_name" to (version.id ?: mcVersion),
            "primary_jar_name" to "$mcVersion.jar"
        )

        val rawJvm = version.arguments?.jvm
        val rawGame = version.arguments?.game

        val jvmArgs = resolveArguments(rawJvm, vars, dropClasspath = true)
            .filterNot { arg -> AMBER_MANAGED_JVM_PREFIXES.any { arg.startsWith(it) } }
            .map { arg ->
                // bootstraplauncher 0.1.17+ hanya menerapkan ignoreList pada nama berkas di classpath, jadi
                // jar utama Minecraft (<mc>.jar) harus ikut dicantumkan agar tidak jadi modul ganda
                if (arg.startsWith("-DignoreList=") && !arg.split(',').contains("$mcVersion.jar")) "$arg,$mcVersion.jar" else arg
            }

        val gameArgs = if (rawGame != null) {
            resolveArguments(rawGame, vars)
        } else {
            legacyGameArguments(version.minecraftArguments)
        }

        return ForgeLaunchInfo(mainClass, files.toList(), keys, jvmArgs, gameArgs)
    }

    // ---------------------------------------------------------------- argumen

    /**
     * Mengurai daftar argumen gaya Mojang: teks biasa, atau objek `{rules, value}`.
     * Bila [dropClasspath], "-cp <nilai>" dibuang (Amber menyusun `-cp` sendiri). Nilai yang gugur karena
     * berisi isian tak dikenal ikut membuang flag di depannya, agar tidak ada flag yatim yang menelan
     * argumen berikutnya.
     */
    private fun resolveArguments(raw: List<Any>?, vars: Map<String, String>, dropClasspath: Boolean = false): List<String> {
        if (raw == null) return emptyList()
        val out = ArrayList<String>()

        fun add(text: String) {
            val value = substitute(text, vars)
            if (value != null) {
                out.add(value)
            } else if (!text.startsWith("-") && out.isNotEmpty()) {
                val last = out.last()
                if (last.startsWith("-") && !last.contains("=")) out.removeAt(out.size - 1)
            }
        }

        var i = 0
        while (i < raw.size) {
            val item = raw[i]
            i++
            when (item) {
                is String -> {
                    if (dropClasspath && (item == "-cp" || item == "-classpath")) {
                        i++ // lewati nilainya juga
                        continue
                    }
                    add(item)
                }
                is Map<*, *> -> {
                    if (!rulesAllow(item["rules"] as? List<*>)) continue
                    when (val value = item["value"]) {
                        is String -> add(value)
                        is List<*> -> value.forEach { v -> (v as? String)?.let { add(it) } }
                    }
                }
            }
        }
        return out
    }

    /** Mengganti ${nama}; null (argumen dibuang) bila ada isian yang tidak dikenal. */
    private fun substitute(arg: String, vars: Map<String, String>): String? {
        if (!arg.contains("\${")) return arg
        var result = arg
        for ((k, v) in vars) result = result.replace("\${$k}", v)
        return if (result.contains("\${")) null else result
    }

    /** Aturan Mojang: tanpa `rules` selalu berlaku; aturan dengan `features` tidak pernah berlaku di Amber. */
    private fun rulesAllow(rules: List<*>?): Boolean {
        if (rules == null) return true
        var allowed = false
        for (r in rules) {
            val rule = r as? Map<*, *> ?: continue
            if (rule["features"] != null) continue
            val os = rule["os"] as? Map<*, *>
            val matches = os == null || (os["name"] == "linux" && os["arch"] == null)
            if (matches) allowed = rule["action"] == "allow"
        }
        return allowed
    }

    /**
     * Profil bergaya lama (`minecraftArguments` berupa satu teks); pasangan berisian ${...} dan pasangan
     * yang diatur Amber sendiri dibuang, sehingga yang tersisa hanya tambahan Forge (`--tweakClass ...`).
     */
    internal fun legacyGameArguments(text: String?): List<String> {
        if (text.isNullOrBlank()) return emptyList()
        val tokens = text.trim().split(Regex("\\s+"))
        val out = ArrayList<String>()
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            if (t.startsWith("--") && i + 1 < tokens.size &&
                (tokens[i + 1].contains("\${") || t in AMBER_MANAGED_GAME_ARGS)
            ) {
                i += 2
                continue
            }
            if (!t.contains("\${")) out.add(t)
            i++
        }
        return out
    }

    // ---------------------------------------------------------------- Forge lama

    /**
     * Forge 1.12.2 ke bawah menampilkan layar muat sendiri yang memakai kelas LWJGL 2 (`SharedDrawable`)
     * yang tidak ada di jembatan lwjglx milik Amber, sehingga game crash sebelum menu muncul. Pengaturan
     * resminya (`config/splash.properties`, kunci `enabled`) dimatikan sebelum game dimulai; layar muat
     * bawaan Minecraft tetap tampil. Gagal menulis tidak menghentikan peluncuran.
     */
    fun disableLegacySplash(gameDir: File) {
        try {
            val file = File(File(gameDir, "config"), "splash.properties")
            val old = if (file.isFile) file.readLines() else emptyList()
            val updated = withSplashDisabled(old)
            if (updated != old) {
                file.parentFile?.mkdirs()
                file.writeText(updated.joinToString("\n") + "\n")
            }
        } catch (_: Exception) {
        }
    }

    /** Baris-baris splash.properties dengan `enabled=false`; baris lain dibiarkan apa adanya. */
    internal fun withSplashDisabled(lines: List<String>): List<String> {
        val enabledLine = Regex("""^\s*enabled\s*[=:].*$""")
        var found = false
        val out = lines.map { line ->
            if (enabledLine.matches(line)) {
                found = true
                "enabled=false"
            } else {
                line
            }
        }
        return if (found) out else out + "enabled=false"
    }
}
